# Getting Started

A step-by-step guide to running SmartBank locally and making your first API call.

---

## Prerequisites

| Tool | Minimum Version | Purpose |
|---|---|---|
| Java | 17 | Build and run services |
| Docker | 24 | Build images |
| kubectl | 1.28 | Manage Kubernetes resources |
| Helm | 3.12 | Deploy the chart |
| minikube / kind | latest | Local Kubernetes cluster |

---

## 1. Clone and Configure

```bash
git clone <repository-url>
cd smart-bank

# Copy the environment template and fill in your values
cp .env.example .env
```

### Required `.env` values

| Variable | Description | Example |
|---|---|---|
| `POSTGRES_USER` | PostgreSQL username | `smartbank` |
| `POSTGRES_PASSWORD` | PostgreSQL password | `changeme123` |
| `REDIS_PASSWORD` | Redis password | `redis_secret` |
| `JWT_SECRET` | 256-bit base64 secret | `openssl rand -base64 32` |
| `MAIL_HOST` | SMTP host | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP port | `587` |
| `MAIL_USERNAME` | SMTP user | `you@gmail.com` |
| `MAIL_PASSWORD` | SMTP app password | `app_password` |
| `CONFIG_SERVER_PASSWORD` | Config Server auth | `config123` |
| `GRAFANA_PASSWORD` | Grafana admin password | `admin123` |

Generate a secure JWT secret:

```bash
openssl rand -base64 32
```

---

## 2. Build Docker Images

Build all seven service images and push them to Docker Hub:

```bash
scripts/build-images.sh --registry <your-dockerhub-username> --tag latest --push
```

Build only specific services:

```bash
scripts/build-images.sh --registry <your-dockerhub-username> --tag latest \
  auth-service api-gateway
```

Each service produces an image named `<registry>/smartbank-<service>:<tag>`:

| Service | Image Name |
|---|---|
| config-server | `smartbank-config-server` |
| discovery-server | `smartbank-discovery-server` |
| api-gateway | `smartbank-api-gateway` |
| auth-service | `smartbank-auth-service` |
| account-service | `smartbank-account-service` |
| transaction-service | `smartbank-transaction-service` |
| notification-service | `smartbank-notification-service` |

---

## 3. Start a Local Kubernetes Cluster

Using **minikube**:

```bash
minikube start --memory=6g --cpus=4
```

Using **kind**:

```bash
kind create cluster --name smartbank
```

---

## 4. Deploy with Helm

```bash
helm upgrade --install smartbank helm/smartbank-chart \
  --namespace smartbank \
  --create-namespace \
  --set global.imageRegistry=<your-dockerhub-username> \
  --set secrets.postgresUser=smartbank \
  --set secrets.postgresPassword=<db-password> \
  --set secrets.redisPassword=<redis-password> \
  --set secrets.jwtSecret=<your-256bit-secret> \
  --set secrets.mailUsername=<smtp-user> \
  --set secrets.mailPassword=<smtp-pass> \
  --set secrets.grafanaPassword=<grafana-pass>
```

Alternatively, use **Kustomize** with the raw manifests:

```bash
# First update k8s/02-secrets.yaml with your real values, then:
kubectl apply -k k8s/
```

---

## 5. Verify the Deployment

```bash
# Watch all pods come up (may take 2-3 minutes)
kubectl -n smartbank get pods -w

# All pods should reach Running/Ready state:
# discovery-server   1/1   Running
# config-server      1/1   Running
# api-gateway        2/2   Running
# auth-service       2/2   Running
# account-service    2/2   Running
# transaction-service 2/2  Running
# notification-service 2/2 Running
```

---

## 6. Access the API

**Port-forward the gateway:**

```bash
kubectl -n smartbank port-forward svc/api-gateway 8080:8080
```

**Open Swagger UI:**

```
http://localhost:8080/swagger-ui.html
```

---

## 7. First API Call Walkthrough

### Register a user

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "SecurePass123!"
  }' | jq
```

### Login and capture tokens

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username": "alice", "password": "SecurePass123!"}' \
  | jq -r '.accessToken')

echo "Token: $TOKEN"
```

### Open a bank account

```bash
curl -s -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "CHECKING",
    "currency": "USD"
  }' | jq
```

### Create a transaction

```bash
curl -s -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "idempotencyKey": "'"$(uuidgen)"'",
    "sourceAccountId": 1,
    "destinationAccountId": 2,
    "amount": 100.00,
    "currency": "USD",
    "description": "Test transfer"
  }' | jq
```

---

## Troubleshooting

### Pods stuck in Pending / CrashLoopBackOff

```bash
# Check events for a specific pod
kubectl -n smartbank describe pod <pod-name>

# View logs
kubectl -n smartbank logs <pod-name>
```

### Services not discovering each other

Ensure the discovery server is fully up before other services start. With Helm, the `discovery-server` Deployment has `readinessProbe` enabled — other services will keep retrying Eureka registration until it is healthy.

### Database migration failures

Flyway migration errors usually mean the PostgreSQL pod isn't ready yet. Check:

```bash
kubectl -n smartbank logs <auth-service-pod> | grep -i flyway
kubectl -n smartbank get pods | grep postgres
```

### JWT validation errors at the gateway

Verify the `JWT_SECRET` value is identical across the gateway and all business services. A mismatch will cause 401 responses on all authenticated routes.
