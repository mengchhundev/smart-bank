# Kubernetes Deployment Guide

Complete guide for deploying SmartBank to a Kubernetes cluster (local or remote).

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Local Cluster Setup](#2-local-cluster-setup)
3. [Deploy with Helm (recommended)](#3-deploy-with-helm-recommended)
4. [Deploy with Raw Manifests](#4-deploy-with-raw-manifests)
5. [Verify Deployment](#5-verify-deployment)
6. [Accessing Services](#6-accessing-services)
7. [Helm Values Reference](#7-helm-values-reference)
8. [Production Considerations](#8-production-considerations)
9. [Operations](#9-operations)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| `kubectl` | v1.28+ | Kubernetes CLI |
| `helm` | v3.14+ | Package manager |
| `docker` | v24+ | Build images |
| `minikube` or `kind` | latest | Local cluster |

Verify installation:

```bash
kubectl version --client
helm version
docker version
```

---

## 2. Local Cluster Setup

### Option A — Minikube

```bash
# Start cluster with sufficient resources
minikube start \
  --cpus=4 \
  --memory=8192 \
  --disk-size=30g \
  --driver=docker

# Enable required addons
minikube addons enable ingress
minikube addons enable metrics-server

# Point docker to minikube's daemon (to use local images)
eval $(minikube docker-env)
```

### Option B — Kind

```bash
# Create cluster with ingress-ready config
cat <<EOF | kind create cluster --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
  - role: worker
  - role: worker
EOF

# Install NGINX Ingress Controller for Kind
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.10.1/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

### DNS Setup (both options)

Add to your hosts file (`/etc/hosts` on Linux/Mac, `C:\Windows\System32\drivers\etc\hosts` on Windows):

```
127.0.0.1 smartbank.local
```

For minikube, use the minikube IP instead:

```bash
echo "$(minikube ip) smartbank.local" | sudo tee -a /etc/hosts
```

---

## 3. Deploy with Helm (recommended)

The Helm chart wraps all K8s manifests and manages Bitnami subcharts for PostgreSQL, Kafka, Redis, and the kube-prometheus-stack.

### 3.1 Build Docker Images

```bash
# Build all service images locally
for svc in config-server discovery-server api-gateway auth-service account-service transaction-service notification-service; do
  docker build \
    --build-arg SERVICE_NAME=$svc \
    --build-arg SERVICE_PORT=$(grep -A1 "$svc" helm/smartbank/values.yaml | grep port | head -1 | awk '{print $2}') \
    -t smartbank-$svc:latest .
done
```

Or build a single service:

```bash
docker build --build-arg SERVICE_NAME=auth-service --build-arg SERVICE_PORT=8081 -t smartbank-auth-service:latest .
```

### 3.2 Add Helm Repos & Build Dependencies

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm dependency build helm/smartbank
```

### 3.3 Install the Chart

```bash
helm upgrade --install smartbank helm/smartbank \
  --namespace smartbank \
  --create-namespace \
  --wait \
  --timeout 10m
```

With a custom image registry:

```bash
helm upgrade --install smartbank helm/smartbank \
  --namespace smartbank \
  --create-namespace \
  --set global.imageRegistry=docker.io/YOUR_DOCKERHUB_USER \
  --set global.imageTag=main-abc12345
```

### 3.4 Override Secrets for Production

```bash
helm upgrade --install smartbank helm/smartbank \
  --namespace smartbank \
  --create-namespace \
  --set secrets.dbPassword=$(echo -n 'STRONG_DB_PASSWORD' | base64) \
  --set secrets.jwtSecret=$(echo -n 'YOUR_JWT_SECRET_256BIT' | base64) \
  --set secrets.redisPassword=$(echo -n 'REDIS_PASSWORD' | base64) \
  --set postgresql.auth.password=STRONG_DB_PASSWORD \
  --set redis.auth.password=REDIS_PASSWORD
```

---

## 4. Deploy with Raw Manifests

For environments without Helm, apply manifests directly. Infrastructure (PostgreSQL, Kafka, Redis) must be deployed separately.

### 4.1 Install Infrastructure via Bitnami Helm Charts

```bash
kubectl create namespace smartbank

# PostgreSQL
helm install smartbank-postgresql bitnami/postgresql \
  --namespace smartbank \
  --set auth.username=smartbank \
  --set auth.password=changeme \
  --set auth.database=smartbank \
  --set primary.initdb.scripts."init-databases\.sql"="CREATE DATABASE auth_db; CREATE DATABASE account_db; CREATE DATABASE transaction_db; CREATE DATABASE notification_db;"

# Kafka
helm install smartbank-kafka bitnami/kafka \
  --namespace smartbank \
  --set controller.replicaCount=1

# Redis
helm install smartbank-redis bitnami/redis \
  --namespace smartbank \
  --set architecture=standalone \
  --set auth.password=changeme
```

### 4.2 Apply Manifests in Order

```bash
# Namespace
kubectl apply -f k8s/namespace.yaml

# Infrastructure (Zipkin, PVC)
kubectl apply -f k8s/infrastructure/

# Config Server (must be ready before other services)
kubectl apply -f k8s/config-server/
kubectl rollout status deployment/config-server -n smartbank --timeout=180s

# Discovery Server
kubectl apply -f k8s/discovery-server/
kubectl rollout status deployment/discovery-server -n smartbank --timeout=180s

# Business Services (parallel)
kubectl apply -f k8s/auth-service/
kubectl apply -f k8s/account-service/
kubectl apply -f k8s/transaction-service/
kubectl apply -f k8s/notification-service/

# API Gateway (after services are registered)
kubectl apply -f k8s/api-gateway/

# Ingress
kubectl apply -f k8s/ingress.yaml
```

**Important:** Update the `image:` field in each `deployment.yaml` to point to your actual Docker registry/tag before applying.

---

## 5. Verify Deployment

### Check all pods are running

```bash
kubectl get pods -n smartbank
```

Expected output (all should be `Running` with `READY 1/1`):

```
NAME                                    READY   STATUS    RESTARTS
config-server-xxx                       1/1     Running   0
discovery-server-xxx                    1/1     Running   0
api-gateway-xxx                         1/1     Running   0
api-gateway-yyy                         1/1     Running   0
auth-service-xxx                        1/1     Running   0
auth-service-yyy                        1/1     Running   0
account-service-xxx                     1/1     Running   0
account-service-yyy                     1/1     Running   0
transaction-service-xxx                 1/1     Running   0
transaction-service-yyy                 1/1     Running   0
notification-service-xxx                1/1     Running   0
notification-service-yyy                1/1     Running   0
zipkin-xxx                              1/1     Running   0
smartbank-postgresql-0                  1/1     Running   0
smartbank-kafka-controller-0            1/1     Running   0
smartbank-redis-master-0                1/1     Running   0
```

### Check services

```bash
kubectl get svc -n smartbank
```

### Health checks

```bash
# Via port-forward
kubectl port-forward svc/api-gateway 8080:80 -n smartbank &
curl http://localhost:8080/actuator/health

# Via Ingress (if DNS is configured)
curl http://smartbank.local/api/v1/auth/register
```

---

## 6. Accessing Services

### Via Ingress (recommended)

| URL | Target |
|---|---|
| `http://smartbank.local/api/*` | API Gateway → microservices |
| `http://smartbank.local/swagger-ui/*` | Swagger UI (via gateway) |
| `http://smartbank.local/zipkin` | Zipkin tracing UI |
| `http://smartbank.local/grafana` | Grafana dashboards |

### Via Port-Forward (debugging)

```bash
# API Gateway
kubectl port-forward svc/api-gateway 8080:80 -n smartbank

# Direct service access
kubectl port-forward svc/auth-service 8081:8081 -n smartbank
kubectl port-forward svc/account-service 8082:8082 -n smartbank

# Infrastructure
kubectl port-forward svc/zipkin 9411:9411 -n smartbank
kubectl port-forward svc/smartbank-postgresql 5432:5432 -n smartbank
```

### Via Minikube Tunnel

```bash
minikube tunnel    # opens LoadBalancer services on localhost
```

---

## 7. Helm Values Reference

Key values in `helm/smartbank/values.yaml`:

### Global

| Value | Default | Description |
|---|---|---|
| `global.imageRegistry` | `""` | Docker registry prefix (e.g., `docker.io/myuser`) |
| `global.imageTag` | `latest` | Image tag for all services |
| `global.imagePullPolicy` | `IfNotPresent` | Kubernetes pull policy |

### Per-Service Overrides

Each service block supports:

| Value | Default | Description |
|---|---|---|
| `{service}.enabled` | `true` | Enable/disable the service |
| `{service}.replicas` | `2` | Number of pod replicas |
| `{service}.port` | varies | Container port |
| `{service}.resources` | see defaults | CPU/memory requests and limits |

### Secrets (base64 encoded)

| Value | Description |
|---|---|
| `secrets.dbPassword` | PostgreSQL password |
| `secrets.jwtSecret` | JWT signing key (256-bit min) |
| `secrets.configServerPassword` | Config Server basic auth |
| `secrets.redisPassword` | Redis password |
| `secrets.smtpUsername` | SMTP username |
| `secrets.smtpPassword` | SMTP password |

### HPA Defaults

| Value | Default |
|---|---|
| `defaults.hpa.enabled` | `true` |
| `defaults.hpa.minReplicas` | `2` |
| `defaults.hpa.maxReplicas` | `5` |
| `defaults.hpa.cpuUtilization` | `70` |

### Subchart Overrides

| Value | Description |
|---|---|
| `postgresql.auth.password` | PG password (must match `secrets.dbPassword` decoded) |
| `postgresql.primary.persistence.size` | Storage size (default: `10Gi`) |
| `kafka.controller.replicaCount` | Kafka brokers (1 dev, 3 prod) |
| `redis.architecture` | `standalone` (dev) or `replication` (prod) |
| `kube-prometheus-stack.grafana.adminPassword` | Grafana admin password |

---

## 8. Production Considerations

### Resource Sizing

| Service | CPU Request | Memory Request | CPU Limit | Memory Limit |
|---|---|---|---|---|
| config-server | 200m | 256Mi | 500m | 512Mi |
| discovery-server | 200m | 256Mi | 500m | 512Mi |
| api-gateway | 300m | 512Mi | 1000m | 1Gi |
| auth-service | 300m | 512Mi | 1000m | 1Gi |
| account-service | 300m | 512Mi | 1000m | 1Gi |
| transaction-service | 300m | 512Mi | 1000m | 1Gi |
| notification-service | 300m | 512Mi | 1000m | 1Gi |

### Storage

- PostgreSQL: 50Gi+ for production (`postgresql.primary.persistence.size`)
- Kafka: 8Gi per broker
- Redis: 2Gi
- Grafana: 2Gi
- Prometheus: scaled by retention (`kube-prometheus-stack.prometheus.prometheusSpec.retention`)

### High Availability

```bash
# Production overrides
helm upgrade --install smartbank helm/smartbank \
  --set kafka.controller.replicaCount=3 \
  --set redis.architecture=replication \
  --set postgresql.primary.persistence.size=50Gi \
  --set kube-prometheus-stack.prometheus.prometheusSpec.retention=30d
```

### Secrets Management

For production, replace base64-encoded K8s Secrets with:
- **Sealed Secrets** (Bitnami) — encrypted secrets in Git
- **External Secrets Operator** — syncs from AWS Secrets Manager, Vault, etc.
- **HashiCorp Vault** — with CSI driver or sidecar injector

---

## 9. Operations

### Scaling

```bash
# Manual scale
kubectl scale deployment/api-gateway --replicas=4 -n smartbank

# HPA handles auto-scaling; check status:
kubectl get hpa -n smartbank
```

### Rolling Updates

```bash
# Update image tag
helm upgrade smartbank helm/smartbank \
  --set global.imageTag=main-newsha12 \
  --reuse-values

# Or update a single deployment
kubectl set image deployment/auth-service \
  auth-service=YOUR_REGISTRY/smartbank-auth-service:new-tag \
  -n smartbank
```

### Rollback

```bash
# Helm rollback to previous release
helm rollback smartbank -n smartbank

# Rollback to specific revision
helm history smartbank -n smartbank
helm rollback smartbank 3 -n smartbank
```

### Logs

```bash
# Single pod
kubectl logs -f deployment/auth-service -n smartbank

# All pods of a service
kubectl logs -l app.kubernetes.io/name=auth-service -n smartbank --all-containers

# Previous crashed container
kubectl logs deployment/auth-service -n smartbank --previous
```

### Exec into Pod

```bash
kubectl exec -it deployment/auth-service -n smartbank -- /bin/sh
```

---

## 10. Troubleshooting

### Pods stuck in CrashLoopBackOff

```bash
kubectl describe pod <pod-name> -n smartbank
kubectl logs <pod-name> -n smartbank --previous
```

Common causes:
- Config Server not ready — check `config-server` pod logs
- Database not reachable — verify `smartbank-postgresql` pod is `Running`
- Wrong secret values — verify with `kubectl get secret smartbank-shared-secret -n smartbank -o jsonpath='{.data}'`

### Pods stuck in Pending

```bash
kubectl describe pod <pod-name> -n smartbank
```

Common causes:
- Insufficient CPU/memory — check node resources with `kubectl top nodes`
- PVC not bound — check `kubectl get pvc -n smartbank`
- For minikube: increase resources with `minikube start --cpus=4 --memory=8192`

### Ingress not routing

```bash
kubectl get ingress -n smartbank
kubectl describe ingress smartbank-ingress -n smartbank
```

Verify:
- NGINX Ingress Controller is running: `kubectl get pods -n ingress-nginx`
- Host entry exists in `/etc/hosts`
- For minikube: `minikube addons enable ingress`

### Services cannot find each other

```bash
# Test DNS resolution from inside a pod
kubectl exec -it deployment/api-gateway -n smartbank -- \
  wget -qO- http://auth-service.smartbank.svc.cluster.local:8081/actuator/health
```

### Database connection errors

```bash
# Check PostgreSQL pod
kubectl get pods -l app.kubernetes.io/name=postgresql -n smartbank

# Check databases exist
kubectl exec -it smartbank-postgresql-0 -n smartbank -- \
  psql -U smartbank -c "\l"
```

### HPA not scaling

```bash
# Verify metrics-server is running
kubectl get pods -n kube-system | grep metrics-server

# For minikube
minikube addons enable metrics-server

# Check HPA status
kubectl describe hpa -n smartbank
```
