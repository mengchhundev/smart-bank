#!/usr/bin/env bash
# =============================================================================
# SmartBank Production Deployment Script
# =============================================================================
# Usage:
#   ./deploy.sh                          # Deploy with default values
#   ./deploy.sh -f values-production.yaml # Deploy with env-specific overrides
#   ./deploy.sh --dry-run                # Helm template only (no apply)
#
# Prerequisites:
#   - kubectl configured with target cluster context
#   - helm 3.x installed
#   - Access to Bitnami, Prometheus, Grafana, and ingress-nginx Helm repos
# =============================================================================
set -euo pipefail

# ─── Configuration ───────────────────────────────────────────────────────────
RELEASE_NAME="smartbank"
CHART_DIR="$(cd "$(dirname "$0")/smartbank" && pwd)"
NAMESPACE_DEFAULT="smartbank-infra"   # Helm release home namespace
TIMEOUT="600s"
VALUES_OVERRIDE=""
DRY_RUN=false

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -f|--values)   VALUES_OVERRIDE="$2"; shift 2 ;;
    --dry-run)     DRY_RUN=true; shift ;;
    *)             echo "Unknown option: $1"; exit 1 ;;
  esac
done

# ─── Colors ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ─── Pre-flight checks ──────────────────────────────────────────────────────
command -v kubectl >/dev/null 2>&1 || error "kubectl not found"
command -v helm    >/dev/null 2>&1 || error "helm not found"

info "Cluster context: $(kubectl config current-context)"
info "Chart directory: ${CHART_DIR}"

# ─── Step 1: Add Helm repos & update dependencies ───────────────────────────
info "Step 1/7: Adding Helm repositories..."
helm repo add bitnami https://charts.bitnami.com/bitnami 2>/dev/null || true
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo add grafana https://grafana.github.io/helm-charts 2>/dev/null || true
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx 2>/dev/null || true
helm repo update

info "Building chart dependencies..."
helm dependency update "${CHART_DIR}"

# ─── Step 2: Create namespaces (idempotent) ─────────────────────────────────
info "Step 2/7: Creating namespaces..."
for NS in smartbank-infra smartbank-apps smartbank-data smartbank-obs smartbank-ingress; do
  kubectl create namespace "${NS}" --dry-run=client -o yaml | kubectl apply -f -
  kubectl label namespace "${NS}" app.kubernetes.io/part-of=smartbank --overwrite
done

# ─── Dry-run gate ────────────────────────────────────────────────────────────
if $DRY_RUN; then
  info "Dry-run mode: rendering templates only..."
  EXTRA_ARGS=""
  [[ -n "${VALUES_OVERRIDE}" ]] && EXTRA_ARGS="-f ${VALUES_OVERRIDE}"
  helm template "${RELEASE_NAME}" "${CHART_DIR}" \
    --namespace "${NAMESPACE_DEFAULT}" \
    ${EXTRA_ARGS}
  info "Dry-run complete."
  exit 0
fi

# ─── Helper: wait for deployment ─────────────────────────────────────────────
wait_for_deployment() {
  local ns="$1" name="$2" timeout="${3:-300s}"
  info "  Waiting for ${ns}/${name} to be ready (timeout: ${timeout})..."
  if ! kubectl rollout status deployment/"${name}" -n "${ns}" --timeout="${timeout}"; then
    error "Deployment ${ns}/${name} failed to become ready"
  fi
  info "  ${ns}/${name} ✓ ready"
}

wait_for_statefulset() {
  local ns="$1" name="$2" timeout="${3:-300s}"
  info "  Waiting for ${ns}/${name} StatefulSet (timeout: ${timeout})..."
  if ! kubectl rollout status statefulset/"${name}" -n "${ns}" --timeout="${timeout}"; then
    error "StatefulSet ${ns}/${name} failed to become ready"
  fi
  info "  ${ns}/${name} ✓ ready"
}

# ─── Step 3: Deploy data layer first ────────────────────────────────────────
info "Step 3/7: Deploying data layer (PostgreSQL, Redis, Kafka)..."
HELM_ARGS=(
  upgrade --install "${RELEASE_NAME}" "${CHART_DIR}"
  --namespace "${NAMESPACE_DEFAULT}"
  --timeout "${TIMEOUT}"
  --wait
  --set "configServer.enabled=false"
  --set "discoveryServer.enabled=false"
  --set "apps.api-gateway.enabled=false"
  --set "apps.auth-service.enabled=false"
  --set "apps.account-service.enabled=false"
  --set "apps.transaction-service.enabled=false"
  --set "apps.notification-service.enabled=false"
  --set "zipkin.enabled=false"
  --set "prometheusStack.enabled=false"
  --set "grafana.enabled=false"
  --set "ingressNginx.enabled=false"
  --set "ingress.enabled=false"
  --set "networkPolicies.enabled=false"
)
[[ -n "${VALUES_OVERRIDE}" ]] && HELM_ARGS+=(-f "${VALUES_OVERRIDE}")
helm "${HELM_ARGS[@]}"

info "Verifying data layer health..."
wait_for_statefulset smartbank-data "${RELEASE_NAME}-postgresql" 300s
wait_for_statefulset smartbank-data "${RELEASE_NAME}-redis-master" 300s
wait_for_statefulset smartbank-data "${RELEASE_NAME}-kafka" 300s

# ─── Step 4: Deploy infrastructure (config-server, discovery-server) ─────────
info "Step 4/7: Deploying infrastructure services..."
HELM_ARGS=(
  upgrade --install "${RELEASE_NAME}" "${CHART_DIR}"
  --namespace "${NAMESPACE_DEFAULT}"
  --timeout "${TIMEOUT}"
  --wait
  --set "configServer.enabled=true"
  --set "discoveryServer.enabled=true"
  --set "apps.api-gateway.enabled=false"
  --set "apps.auth-service.enabled=false"
  --set "apps.account-service.enabled=false"
  --set "apps.transaction-service.enabled=false"
  --set "apps.notification-service.enabled=false"
  --set "zipkin.enabled=false"
  --set "prometheusStack.enabled=false"
  --set "grafana.enabled=false"
  --set "ingressNginx.enabled=false"
  --set "ingress.enabled=false"
  --set "networkPolicies.enabled=false"
)
[[ -n "${VALUES_OVERRIDE}" ]] && HELM_ARGS+=(-f "${VALUES_OVERRIDE}")
helm "${HELM_ARGS[@]}"

wait_for_deployment smartbank-infra config-server 300s
wait_for_deployment smartbank-infra discovery-server 300s

# ─── Step 5: Deploy observability ────────────────────────────────────────────
info "Step 5/7: Deploying observability stack (Prometheus, Grafana, Zipkin)..."
HELM_ARGS=(
  upgrade --install "${RELEASE_NAME}" "${CHART_DIR}"
  --namespace "${NAMESPACE_DEFAULT}"
  --timeout "${TIMEOUT}"
  --wait
  --set "zipkin.enabled=true"
  --set "prometheusStack.enabled=true"
  --set "grafana.enabled=true"
  --set "apps.api-gateway.enabled=false"
  --set "apps.auth-service.enabled=false"
  --set "apps.account-service.enabled=false"
  --set "apps.transaction-service.enabled=false"
  --set "apps.notification-service.enabled=false"
  --set "ingressNginx.enabled=false"
  --set "ingress.enabled=false"
  --set "networkPolicies.enabled=false"
)
[[ -n "${VALUES_OVERRIDE}" ]] && HELM_ARGS+=(-f "${VALUES_OVERRIDE}")
helm "${HELM_ARGS[@]}"

wait_for_deployment smartbank-obs zipkin 180s

# ─── Step 6: Deploy application services + ingress ───────────────────────────
info "Step 6/7: Deploying application services and ingress..."
HELM_ARGS=(
  upgrade --install "${RELEASE_NAME}" "${CHART_DIR}"
  --namespace "${NAMESPACE_DEFAULT}"
  --timeout "${TIMEOUT}"
  --wait
)
[[ -n "${VALUES_OVERRIDE}" ]] && HELM_ARGS+=(-f "${VALUES_OVERRIDE}")
helm "${HELM_ARGS[@]}"

info "Verifying application services..."
for SVC in api-gateway auth-service account-service transaction-service notification-service; do
  wait_for_deployment smartbank-apps "${SVC}" 300s
done

# ─── Step 7: Final validation ────────────────────────────────────────────────
info "Step 7/7: Running post-deployment validation..."
echo ""
info "=== Namespace Pod Summary ==="
for NS in smartbank-infra smartbank-apps smartbank-data smartbank-obs smartbank-ingress; do
  READY=$(kubectl get pods -n "${NS}" --no-headers 2>/dev/null | grep -c "Running" || echo 0)
  TOTAL=$(kubectl get pods -n "${NS}" --no-headers 2>/dev/null | wc -l | tr -d ' ')
  info "  ${NS}: ${READY}/${TOTAL} pods running"
done

echo ""
info "=== Ingress Endpoints ==="
kubectl get ingress -n smartbank-apps 2>/dev/null || warn "No ingress resources found"

echo ""
info "=== Load Balancer IPs ==="
kubectl get svc -n smartbank-apps api-gateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null && echo "" || warn "api-gateway LB IP not yet assigned"
kubectl get svc -n smartbank-ingress -o wide 2>/dev/null || true

echo ""
info "=================================================="
info "  SmartBank deployment complete!"
info "  Release: ${RELEASE_NAME}"
info "  Cluster: $(kubectl config current-context)"
info "=================================================="
