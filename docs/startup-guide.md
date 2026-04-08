# Startup Guide

Two deployment methods are available: Docker Compose for local development, and Kubernetes (Helm) for staging/production.

---

## Kubernetes Startup (Helm)

### Prerequisites

- `kubectl`, `helm` v3.14+, and a running cluster (minikube, kind, or remote)
- See [Kubernetes Deployment Guide](kubernetes-deployment.md) for full cluster setup

### One-Command Deploy

```bash
helm dependency build helm/smartbank

helm upgrade --install smartbank helm/smartbank \
  --namespace smartbank \
  --create-namespace \
  --wait \
  --timeout 10m
```

Helm handles startup ordering via readiness probes — no manual sequencing needed.

### Verify

```bash
kubectl get pods -n smartbank           # all pods Running & Ready
kubectl get svc -n smartbank            # all services listed
kubectl get ingress -n smartbank        # ingress configured
```

### Access

```bash
# Port-forward the gateway
kubectl port-forward svc/api-gateway 8080:80 -n smartbank

# Smoke test
curl http://localhost:8080/actuator/health
```

Or via Ingress at `http://smartbank.local/api/*` (requires hosts file entry).

### Useful URLs (K8s)

| URL | Purpose | Access |
|---|---|---|
| `http://smartbank.local/api/*` | API Gateway | Ingress |
| `http://smartbank.local/swagger-ui` | Swagger UI | Ingress |
| `http://smartbank.local/zipkin` | Distributed tracing | Ingress |
| `http://smartbank.local/grafana` | Grafana dashboards | Ingress |

For direct pod access, use `kubectl port-forward`.

---

## Docker Compose Startup (local development)

Services must start in dependency order. Each group depends on the previous being fully up.

---

## Step 1 — Infrastructure (Docker)

```bash
docker-compose up -d
```

Verify all containers are running:
```bash
docker-compose ps
```

| Container  | Port(s)     | Must be healthy before starting |
|------------|-------------|----------------------------------|
| postgres   | 5433        | Step 2                           |
| kafka      | 29092       | Step 4 (transaction, notification)|
| zookeeper  | 2181        | kafka                            |
| zipkin     | 9411        | optional (tracing only)          |
| mailhog    | 1025 / 8025 | optional (email only)            |

---

## Step 2 — Config Server

All services fetch their configuration from here before doing anything else.

```bash
./gradlew :config-server:bootRun
```

**Ready when:** `Started ConfigServerApplication` appears in the log on port `8888`.

---

## Step 3 — Discovery Server (Eureka)

Services register themselves here after fetching config. The API Gateway uses Eureka for load-balanced routing.

```bash
./gradlew :discovery-server:bootRun
```

**Ready when:** Eureka dashboard is accessible at http://localhost:8761 (`eureka / eureka123`).

---

## Step 4 — Business Services

Can be started in parallel once Eureka is up. Open a separate terminal for each.

```bash
./gradlew :auth-service:bootRun
./gradlew :account-service:bootRun
./gradlew :transaction-service:bootRun
./gradlew :notification-service:bootRun
```

**Ready when:** Each service appears as `UP` in the Eureka dashboard at http://localhost:8761.

| Service              | Port |
|----------------------|------|
| auth-service         | 8081 |
| account-service      | 8082 |
| transaction-service  | 8083 |
| notification-service | 8085 |

---

## Step 5 — API Gateway

Start last, after all downstream services are registered in Eureka. If the gateway starts before the services, routes will have nothing to forward to.

```bash
./gradlew :api-gateway:bootRun
```

**Ready when:** `Started ApiGatewayApplication` appears in the log on port `8080`.

---

## Full Startup Checklist

```
[ ] docker-compose up -d          → all containers healthy
[ ] config-server :8888           → Started ConfigServerApplication
[ ] discovery-server :8761        → Eureka dashboard loads
[ ] auth-service :8081            → registered in Eureka
[ ] account-service :8082         → registered in Eureka
[ ] transaction-service :8083     → registered in Eureka
[ ] notification-service :8085    → registered in Eureka
[ ] api-gateway :8080             → Started ApiGatewayApplication
```

---

## Quick Smoke Test

Once everything is up, verify the full stack with a register call:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "fullName": "Test User"
  }'
```

Expected: `201 Created` with `access_token` and `refresh_token` in the response.

---

## Useful URLs

| URL                        | Purpose                        | Credentials         |
|----------------------------|--------------------------------|---------------------|
| http://localhost:8761      | Eureka dashboard               | eureka / eureka123  |
| http://localhost:8888      | Config Server                  | config / config123  |
| http://localhost:9411      | Zipkin distributed tracing     | —                   |
| http://localhost:8025      | MailHog email UI               | —                   |
| http://localhost:8081/swagger-ui.html | Auth Service API docs | —          |
| http://localhost:8082/swagger-ui.html | Account Service API docs | —       |
| http://localhost:8083/swagger-ui.html | Transaction Service API docs | — |
| http://localhost:8085/swagger-ui.html | Notification Service API docs| — |
