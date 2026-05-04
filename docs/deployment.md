# Deployment Guide

Covers Kubernetes deployment, Helm chart configuration, CI/CD pipeline details, and environment promotion.

---

## Environments

| Environment | Trigger | Approval |
|---|---|---|
| Staging | Auto on push to `main` | None |
| Production | After staging smoke tests pass | Manual (GitHub environment gate) |

---

## Helm Chart

**Location:** `helm/smartbank-chart/`  
**Chart version:** 1.0.0  
**App version:** 1.0.1

### Install / Upgrade

```bash
helm upgrade --install smartbank helm/smartbank-chart \
  --namespace smartbank \
  --create-namespace \
  --values helm/smartbank-chart/values.yaml \
  --set global.imageRegistry=chhundev \
  --set global.imageTag=main-<sha8> \
  --set secrets.postgresPassword=<value> \
  --set secrets.jwtSecret=<value> \
  --set secrets.redisPassword=<value>
```

### Key Helm Values

```yaml
global:
  imageRegistry: chhundev       # Docker Hub username
  imageTag: latest              # Overridden by CI/CD with commit SHA

replicaCount:
  discoveryServer: 1
  configServer: 1
  apiGateway: 2
  authService: 2
  accountService: 2
  transactionService: 2
  notificationService: 2

postgres:
  storageSize:
    auth: 2Gi
    account: 2Gi
    transaction: 5Gi            # Larger: stores full TX history
    notification: 2Gi

kafka:
  storageSize: 5Gi
  version: "3.7.0"

ingress:
  enabled: true
  host: smartbank.local         # Override with real domain
  tls: true                     # Let's Encrypt via cert-manager
```

### Production Overrides

Pass these additional flags for production:

```bash
--set postgres.storageSize.auth=50Gi \
--set postgres.storageSize.account=50Gi \
--set postgres.storageSize.transaction=50Gi \
--set postgres.storageSize.notification=50Gi \
--set redis.mode=replication \
--set kafka.replicaCount=3 \
--set prometheus.retention=30d
```

### Uninstall

```bash
helm uninstall smartbank --namespace smartbank
```

---

## Kustomize (Raw Manifests)

Alternative to Helm for simpler environments.

**Location:** `k8s/`

Manifests are numbered for sequential application:

| File | Purpose |
|---|---|
| `00-namespace.yaml` | Create `smartbank` namespace |
| `01-configmap.yaml` | Application configuration |
| `02-secrets.yaml` | Secrets (fill in before deploying) |
| `03-postgres.yaml` | PostgreSQL with 4 databases |
| `04-redis.yaml` | Redis for rate limiting |
| `05-kafka.yaml` | Apache Kafka |
| `06-platform-services.yaml` | Discovery Server, Config Server, API Gateway |
| `07-business-services.yaml` | Auth, Account, Transaction, Notification services |
| `08-ingress.yaml` | NGINX Ingress (host: `smartbank.local`) |
| `09-zipkin.yaml` | Distributed tracing |
| `10-cluster-issuer.yaml` | Let's Encrypt cert-manager issuer |

**Before deploying:**

1. Replace all placeholder values in `k8s/02-secrets.yaml`
2. Update the host in `k8s/08-ingress.yaml`

```bash
kubectl apply -k k8s/
kubectl -n smartbank get pods -w
```

See [`k8s/README.md`](../k8s/README.md) for full pre-deployment checklist.

---

## CI/CD Pipeline

### CI Workflow (`ci.yml`)

Triggered on push or pull request to `main` and `develop` branches.

```
Build & Test
  ├─ JDK 17 setup + Gradle cache
  ├─ ./gradlew test (unit + integration via Testcontainers)
  └─ JaCoCo coverage report (fails if < 70%)
        │
        ▼
Code Quality (SonarCloud)
  └─ Runs only on internal pushes (not fork PRs)
        │
        ▼
Docker Image Build (matrix — 7 services in parallel)
  ├─ Tags: <registry>/smartbank-<service>:<branch>-<sha8>
  └─ BuildKit layer caching enabled
        │
        ▼
Security Scan (Trivy)
  ├─ Scans each image for CRITICAL / HIGH CVEs
  ├─ Fails on unfixed critical vulnerabilities
  └─ SARIF uploaded to GitHub Security tab
        │
        ▼
Helm Lint
  └─ Validates chart syntax + dependency builds
```

### CD Workflow (`cd.yml`)

Triggered on push to `main` only. One concurrent deployment per environment.

```
Deploy to Staging
  ├─ helm upgrade --install with image tag: main-<sha8>
  ├─ kubectl rollout status for all 7 Deployments (10m timeout)
  └─ Secrets injected via --set flags from GitHub environment secrets
        │
        ▼
Smoke Tests (Staging)
  ├─ Health check: GET /actuator/health on each service
  ├─ End-to-end: register → login → access protected endpoint
  └─ Port-forwards if LoadBalancer IP unavailable (minikube/kind)
        │
        ▼ (requires manual approval via GitHub Environment)
Deploy to Production
  ├─ Same as staging + production resource overrides
  ├─ POST-deploy health checks
  └─ Git tag created: release-<date>-<sha8>
```

### Required GitHub Secrets

Set these in your repository's **Settings → Secrets and variables → Actions**:

| Secret | Description |
|---|---|
| `DOCKER_USERNAME` | Docker Hub username |
| `DOCKER_PASSWORD` | Docker Hub access token |
| `SONAR_TOKEN` | SonarCloud token |
| `STAGING_KUBECONFIG` | Base64-encoded kubeconfig for staging cluster |
| `PRODUCTION_KUBECONFIG` | Base64-encoded kubeconfig for production cluster |
| `JWT_SECRET` | 256-bit JWT signing key |
| `POSTGRES_PASSWORD` | Database password |
| `REDIS_PASSWORD` | Redis password |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP app password |
| `GRAFANA_PASSWORD` | Grafana admin password |

### GitHub Environments

Create two environments in **Settings → Environments**:

- `staging` — no protection rules (auto-deploys)
- `production` — add required reviewers for manual approval gate

---

## Resource Requirements

### Minimum Cluster (Development / Staging)

| Resource | Value |
|---|---|
| Nodes | 1–2 |
| CPU (total requests) | ~1.5 cores |
| Memory (total requests) | ~3.5 GiB |
| Storage | ~20 GiB |

### Recommended Cluster (Production)

| Resource | Value |
|---|---|
| Nodes | 3+ |
| CPU (total requests) | ~3 cores |
| Memory (total requests) | ~6 GiB |
| Storage | ~200 GiB (with 50Gi per PVC) |

### Per-Service Resource Limits (from Helm defaults)

| Service | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---|---|---|---|---|
| discovery-server | 100m | 500m | 256Mi | 512Mi |
| config-server | 100m | 500m | 256Mi | 512Mi |
| api-gateway | 200m | 1000m | 512Mi | 1Gi |
| auth-service | 200m | 1000m | 512Mi | 1Gi |
| account-service | 200m | 1000m | 512Mi | 1Gi |
| transaction-service | 250m | 1000m | 512Mi | 1Gi |
| notification-service | 150m | 500m | 256Mi | 512Mi |

---

## Rollback

```bash
# View release history
helm history smartbank -n smartbank

# Roll back to a previous revision
helm rollback smartbank <revision> -n smartbank

# Verify rollback
kubectl -n smartbank rollout status deployment/api-gateway
```

---

## TLS / Ingress

The Ingress manifest uses **cert-manager** with a Let's Encrypt ClusterIssuer (`10-cluster-issuer.yaml`).

```bash
# Install cert-manager (if not present)
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml

# Verify cert-manager pods
kubectl -n cert-manager get pods
```

For local testing without TLS:

```bash
# Add to /etc/hosts
127.0.0.1  smartbank.local

# Port-forward instead of Ingress
kubectl -n smartbank port-forward svc/api-gateway 8080:8080
```
