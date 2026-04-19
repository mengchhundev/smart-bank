# SmartBank Kubernetes Manifests

This directory contains a Kustomize-ready Kubernetes base for the SmartBank Spring microservices.

## Included

- `config-server`, `discovery-server`, `api-gateway`
- `auth-service`, `account-service`, `transaction-service`, `notification-service`
- PostgreSQL with four databases initialized on first boot
- Redis for API Gateway rate limiting
- Single-node Kafka for transaction notifications
- Zipkin for tracing
- NGINX Ingress routing `smartbank.local` to the API Gateway

## Before Deploying

1. Build and push service images with the root `Dockerfile`, then update `k8s/kustomization.yaml`.

   ```bash
   scripts/build-images.sh --registry your-dockerhub-username --tag latest --push
   ```

   To build only one or two services:

   ```bash
   scripts/build-images.sh --registry your-dockerhub-username --tag latest auth-service api-gateway
   ```

   The script builds these `SERVICE_NAME` and image pairs:

   - `config-server` -> `smartbank-config-server`
   - `discovery-server` -> `smartbank-discovery-server`
   - `api-gateway` -> `smartbank-api-gateway`
   - `auth-service` -> `smartbank-auth-service`
   - `account-service` -> `smartbank-account-service`
   - `transaction-service` -> `smartbank-transaction-service`
   - `notification-service` -> `smartbank-notification-service`

2. Replace placeholder values in `k8s/02-secrets.yaml`.

   For production, prefer sealed secrets, External Secrets Operator, SOPS, or your cloud secret manager instead of committing literal secret values.

3. Update `k8s/08-ingress.yaml`.

   Replace `smartbank.local` with your real host and add TLS if this is not a local cluster.

## Deploy

```bash
kubectl apply -k k8s
kubectl -n smartbank get pods
kubectl -n smartbank get svc
```

For local testing without Ingress:

```bash
kubectl -n smartbank port-forward svc/api-gateway 8080:8080
```

Then open:

```text
http://localhost:8080/swagger-ui.html
```

## Notes

- The application source currently defaults many dependencies to `localhost`. These manifests override those settings with Kubernetes service names via environment variables.
- The notification service has Telegram disabled by default in `02-secrets.yaml`; enable it only after supplying real secret values through a secure mechanism.
- Prometheus/Grafana are not included in this base because the checked-in monitoring config is currently Docker-host oriented. Add them as an overlay or Helm chart once scrape discovery and dashboards are adapted to Kubernetes.
- The bundled PostgreSQL, Redis, and Kafka manifests are suitable for development or small internal environments. For production, use managed services or hardened Helm charts with backups, anti-affinity, monitoring, and storage classes tuned for your cluster.
