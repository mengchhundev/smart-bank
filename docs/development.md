# Development Guide

## Prerequisites

- Java 17+
- Docker & Docker Compose
- Gradle (wrapper included)

---

## Local Setup

### 1. Start Infrastructure

```bash
docker-compose up -d
```

Verify everything is up:
```bash
docker-compose ps
```

### 2. Build All Services

```bash
./gradlew build
```

To skip tests:
```bash
./gradlew build -x test
```

### 3. Start Services

Services must start in dependency order:

```bash
# Terminal 1
./gradlew :config-server:bootRun

# Terminal 2 (after Config Server is up)
./gradlew :discovery-server:bootRun

# Terminal 3+ (after Discovery Server is up)
./gradlew :api-gateway:bootRun
./gradlew :auth-service:bootRun
./gradlew :account-service:bootRun
./gradlew :transaction-service:bootRun
./gradlew :notification-service:bootRun
```

---

## Service URLs

| Service              | URL                          |
|----------------------|------------------------------|
| API Gateway          | http://localhost:8080        |
| Auth Service         | http://localhost:8081        |
| Account Service      | http://localhost:8082        |
| Transaction Service  | http://localhost:8083        |
| Notification Service | http://localhost:8085        |
| Config Server        | http://localhost:8888        |
| Eureka Dashboard     | http://localhost:8761        |
| Zipkin UI            | http://localhost:9411        |
| MailHog UI           | http://localhost:8025        |

---

## Running Tests

```bash
# All tests
./gradlew test

# Single service
./gradlew :account-service:test

# Single test class
./gradlew :account-service:test --tests "com.smartbank.account.service.AccountServiceTest"
```

---

## Database Migrations

**auth-service** and **account-service** use Flyway. Migrations run automatically on startup.

Migration files:
```
auth-service/src/main/resources/db/migration/
  V1__create_users_table.sql
  V2__create_refresh_tokens_table.sql

account-service/src/main/resources/db/
  migration/V1__create_accounts_table.sql
```

`transaction-service` and `notification-service` use `spring.jpa.hibernate.ddl-auto: update` — schema is auto-managed by Hibernate.

---

## Typical Development Flow

### Adding a new API endpoint

1. Define the DTO in `dto/`
2. Add method to the service interface/implementation
3. Add endpoint to the controller
4. Update security config if a new path pattern is needed
5. Add a test in `src/test/`

### Adding a new Kafka event type

1. Define the event DTO (must be in a package matching `com.smartbank.*`)
2. Publish via `KafkaTemplate` in the producer service
3. Add `@KafkaListener` in the consumer service
4. Register the new type in the consumer's `KafkaConfig` trusted packages (already covered by `com.smartbank.*`)

### Adding a new Feign client

1. Create interface in `client/` with `@FeignClient(name = "service-name")`
2. Use `lb://service-name` URL — service discovery handles routing
3. Annotate with Spring MVC `@GetMapping` / `@PostMapping` etc.
4. Enable with `@EnableFeignClients` on the main application class (already present)

---

## Common Issues

### Services fail to start — "Config Server not available"
Config Server must be fully started before any other service. Wait for the banner to show `Started ConfigServerApplication`.

### JWT 401 errors
- Check the `jwt.secret` is identical across gateway and all services
- Verify token is sent as `Authorization: Bearer <token>` (space after Bearer)
- Check token expiry (15-minute TTL on access tokens)

### Feign client returns 500 / connection refused
- Confirm the target service is registered in Eureka
- Check `lb://` prefix in Feign client `url` attribute — it must match the `spring.application.name` of the target service exactly

### Kafka consumer not receiving messages
- Confirm bootstrap server is `localhost:29092` (Docker maps to this port)
- Verify consumer group ID matches `notification-group`
- Check deserializer config — `ErrorHandlingDeserializer` wraps `JsonDeserializer`

### Optimistic locking exception on account balance
Expected behavior — `transaction-service` will mark the transaction as FAILED and publish a FAILED event. Retry logic should be implemented at the caller if needed.

---

## Code Conventions

- **Entities:** JPA `@Entity` with Lombok `@Data` / `@Builder`
- **DTOs:** Record classes or Lombok `@Data`; validated with `jakarta.validation`
- **Mapping:** MapStruct mappers in `mapper/` package; compile-time generated
- **Exceptions:** Custom exception classes per domain, caught by `@RestControllerAdvice`
- **Security:** Extract user identity from JWT claims, never from the request body
- **Internal APIs:** Prefixed `/api/v1/` (service-to-service); public APIs use the same prefix but are routed through the gateway

---

## Kubernetes Development Workflow

For developing and testing against a local K8s cluster.

### Setup

```bash
# Start minikube
minikube start --cpus=4 --memory=8192
minikube addons enable ingress
minikube addons enable metrics-server

# Build images in minikube's Docker
eval $(minikube docker-env)
```

### Build & Deploy

```bash
# Build a single service image
docker build --build-arg SERVICE_NAME=auth-service --build-arg SERVICE_PORT=8081 \
  -t smartbank-auth-service:dev .

# Deploy with Helm
helm dependency build helm/smartbank
helm upgrade --install smartbank helm/smartbank \
  --namespace smartbank --create-namespace \
  --set global.imageTag=dev \
  --set global.imagePullPolicy=Never    # use local images
```

### Iterating on a Single Service

```bash
# Rebuild and restart just one service
docker build --build-arg SERVICE_NAME=auth-service --build-arg SERVICE_PORT=8081 \
  -t smartbank-auth-service:dev .
kubectl rollout restart deployment/auth-service -n smartbank
```

### Debugging in K8s

```bash
# Tail logs
kubectl logs -f deployment/auth-service -n smartbank

# Exec into pod
kubectl exec -it deployment/auth-service -n smartbank -- /bin/sh

# Port-forward for direct access
kubectl port-forward svc/auth-service 8081:8081 -n smartbank

# Check events
kubectl get events -n smartbank --sort-by='.lastTimestamp'
```

### Common K8s Issues

**Pod stuck in CrashLoopBackOff:** Check logs with `kubectl logs <pod> -n smartbank --previous`. Usually a config/connection issue.

**ImagePullBackOff:** If using local images with minikube, set `imagePullPolicy: Never` or run `eval $(minikube docker-env)` before building.

**Pods pending:** Insufficient cluster resources — check `kubectl top nodes` and increase minikube resources.

See [Kubernetes Deployment Guide](kubernetes-deployment.md) for comprehensive troubleshooting.
