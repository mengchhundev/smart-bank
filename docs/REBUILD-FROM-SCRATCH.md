# SmartBank — Rebuild From Scratch (Practice Guide)

A hands-on, step-by-step guide to rebuild the SmartBank microservices platform from an empty folder. The goal is **learning by doing** — at each step you'll understand *why* a piece exists, not just copy code.

> **Stack**: Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1, PostgreSQL, Redis, Kafka, Eureka, Spring Cloud Config, Zipkin, Prometheus, Grafana, Docker, Kubernetes/Helm.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Phase 0 — Bootstrap the multi-module Gradle project](#phase-0--bootstrap-the-multi-module-gradle-project)
3. [Phase 1 — Infrastructure services](#phase-1--infrastructure-services)
   - 1a. config-server
   - 1b. discovery-server
4. [Phase 2 — Auth service (JWT)](#phase-2--auth-service-jwt)
5. [Phase 3 — Account service](#phase-3--account-service)
6. [Phase 4 — Transaction service (saga + idempotency)](#phase-4--transaction-service-saga--idempotency)
7. [Phase 5 — Notification service (Kafka consumer)](#phase-5--notification-service-kafka-consumer)
8. [Phase 6 — API Gateway](#phase-6--api-gateway)
9. [Phase 7 — Observability](#phase-7--observability)
10. [Phase 8 — Docker Compose (local stack)](#phase-8--docker-compose-local-stack)
11. [Phase 9 — Kubernetes / Helm](#phase-9--kubernetes--helm)
12. [Phase 10 — CI/CD](#phase-10--cicd)
13. [Validation checklist](#validation-checklist)

---

## 1. Prerequisites

Install these once before starting:

| Tool | Version | Why |
|---|---|---|
| JDK | 17 (Temurin) | Spring Boot 3.x requires Java 17+ |
| Gradle | 8.x (use wrapper) | Build tool |
| Docker Desktop | latest | Local containers |
| kubectl + Helm | 1.28+ / 3.13+ | Phase 9 |
| IntelliJ IDEA / VS Code | latest | IDE |
| Git | 2.40+ | Source control |

**Sanity check:**
```bash
java -version    # should print 17.x
docker --version
helm version
```

---

## Phase 0 — Bootstrap the multi-module Gradle project

**Goal:** Empty repo → working multi-module Gradle skeleton with shared conventions.

### Step 0.1 — Create the root folder

```bash
mkdir smart-banking && cd smart-banking
git init
```

### Step 0.2 — Create `settings.gradle`

```gradle
rootProject.name = 'smart-banking'

include 'config-server'
include 'discovery-server'
include 'api-gateway'
include 'auth-service'
include 'account-service'
include 'transaction-service'
include 'notification-service'
```

### Step 0.3 — Create the root `build.gradle`

This file is the **convention plugin** for every subproject. Key points:

- `apply plugin: 'org.springframework.boot'` to all subprojects
- Pin Spring Cloud BOM version once
- Add common dependencies (Actuator, Micrometer/Zipkin tracing, Prometheus, Lombok)
- Configure JaCoCo with 70% minimum line coverage

> 💡 **Why this matters:** A convention plugin means each microservice's `build.gradle` only needs to declare *its own unique deps*. Don't repeat yourself.

Reference the existing `build.gradle` at the project root — copy its structure but type it out by hand to internalize what each line does.

### Step 0.4 — Generate the Gradle wrapper

```bash
gradle wrapper --gradle-version 8.7
```

This creates `gradlew`, `gradlew.bat`, and the `gradle/wrapper/` directory. Commit these so anyone can build without installing Gradle.

### Step 0.5 — Verify the skeleton builds

```bash
./gradlew projects
```

You should see all 7 subprojects listed. They don't exist yet — that's fine, Gradle will warn but won't fail.

### Step 0.6 — `.gitignore`

```
.gradle/
build/
out/
*.log
.idea/
*.iml
.env
logs/
```

**Commit:** `git commit -am "phase 0: gradle skeleton"`

---

## Phase 1 — Infrastructure services

### 1a — config-server

**Goal:** Centralized configuration for all services, backed by a Git repo.

#### Step 1a.1 — Folder structure
```
config-server/
├── build.gradle
└── src/main/
    ├── java/com/smartbank/configserver/ConfigServerApplication.java
    └── resources/
        ├── application.yml
        └── bootstrap.yml
```

#### Step 1a.2 — `config-server/build.gradle`
Add only:
```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-config-server'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

#### Step 1a.3 — Main class
```java
@SpringBootApplication
@EnableConfigServer
@EnableDiscoveryClient
public class ConfigServerApplication {
    public static void main(String[] args) { SpringApplication.run(ConfigServerApplication.class, args); }
}
```

#### Step 1a.4 — `application.yml`
```yaml
server:
  port: 8888
spring:
  application:
    name: config-server
  cloud:
    config:
      server:
        git:
          uri: https://github.com/your-org/smartbank-config
          default-label: main
          clone-on-start: true
management:
  endpoints.web.exposure.include: health,info,prometheus
  endpoint.health.probes.enabled: true
```

> 💡 **Concept check:** Why does config-server itself need *bootstrap* configuration? Because it must boot before reading any config — so server port and Git URI must be hardcoded or environment-injected.

#### Step 1a.5 — Test it
```bash
./gradlew :config-server:bootRun
curl http://localhost:8888/actuator/health   # → {"status":"UP"}
```

### 1b — discovery-server (Eureka)

#### Step 1b.1 — Files
```
discovery-server/
├── build.gradle
└── src/main/java/com/smartbank/discoveryserver/DiscoveryServerApplication.java
└── src/main/resources/application.yml
```

#### Step 1b.2 — `build.gradle`
```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
}
```

#### Step 1b.3 — Main class
```java
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication { ... }
```

#### Step 1b.4 — `application.yml`
```yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: true
    renewal-percent-threshold: 0.85
```

> 💡 **Why** `register-with-eureka: false`? Eureka *is* the registry — it doesn't register itself.

#### Step 1b.5 — Run both infra services
```bash
./gradlew :config-server:bootRun &
./gradlew :discovery-server:bootRun &
```
Visit http://localhost:8761 — Eureka dashboard should load.

**Commit:** `git commit -am "phase 1: infra services"`

---

## Phase 2 — Auth service (JWT)

**Goal:** Issue JWTs on login, validate them, store users in Postgres, cache sessions in Redis.

### Step 2.1 — Dependencies (`auth-service/build.gradle`)
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation "io.jsonwebtoken:jjwt-api:${jjwtVersion}"
    runtimeOnly    "io.jsonwebtoken:jjwt-impl:${jjwtVersion}"
    runtimeOnly    "io.jsonwebtoken:jjwt-jackson:${jjwtVersion}"
    runtimeOnly    'org.postgresql:postgresql'
    implementation 'org.flywaydb:flyway-core'
}
```

### Step 2.2 — Layered package structure
```
com/smartbank/authservice/
├── AuthServiceApplication.java
├── config/      # SecurityConfig, RedisConfig, JwtConfig
├── controller/  # AuthController
├── service/     # AuthService, JwtService, UserService
├── repository/  # UserRepository extends JpaRepository
├── entity/      # User
├── dto/         # LoginRequest, RegisterRequest, TokenResponse
└── exception/   # GlobalExceptionHandler
```

> 💡 **Why this layout?** It mirrors the request flow: Controller → Service → Repository → DB. Each layer has one job.

### Step 2.3 — Build the User entity
```java
@Entity
@Table(name = "users")
@Data @NoArgsConstructor @AllArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String username;
    @Column(unique = true, nullable = false)
    private String email;
    private String passwordHash;
    private String role;     // USER, ADMIN
    private Instant createdAt;
}
```

### Step 2.4 — Flyway migration
Create `src/main/resources/db/migration/V1__create_users.sql`:
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(128) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

> 💡 **Why Flyway over `ddl-auto`?** Production databases require versioned, reviewable migrations. `ddl-auto: update` is dangerous — it can drop columns silently.

### Step 2.5 — JwtService (the hardest piece)

Implement these three methods:
```java
public String generateToken(User user) { /* HS256, 1h expiry, claims: sub, role */ }
public Claims parseToken(String token)  { /* throw on invalid/expired */ }
public boolean isValid(String token)    { /* try parse; return false on exception */ }
```

> 💡 **Practice idea:** Write the unit test *first*. Force yourself to think about edge cases (expired token, tampered signature, wrong issuer).

### Step 2.6 — SecurityConfig
- Disable CSRF (stateless API)
- Permit `/api/auth/login`, `/api/auth/register`, `/actuator/**`
- Everything else requires JWT
- Add a `JwtAuthenticationFilter` that runs before `UsernamePasswordAuthenticationFilter`

### Step 2.7 — Login flow walkthrough
1. POST `/api/auth/login` with `{username, password}`
2. Controller calls `authService.login(...)`
3. Service finds user, verifies BCrypt hash
4. On success, calls `jwtService.generateToken(user)`
5. Stores `username → token` in Redis with TTL = token expiry
6. Returns `{accessToken, refreshToken, expiresIn}`

### Step 2.8 — Run it
```bash
docker run -d --name pg -e POSTGRES_PASSWORD=pass -p 5432:5432 postgres:16
docker run -d --name redis -p 6379:6379 redis:7
./gradlew :auth-service:bootRun
```

Test with curl:
```bash
curl -X POST http://localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"a@b.com","password":"secret123"}'
```

**Commit:** `git commit -am "phase 2: auth-service"`

---

## Phase 3 — Account service

**Goal:** CRUD accounts, balance management. Calls auth-service to validate JWT (or trusts the gateway).

### Step 3.1 — Domain model

```
Account
├── id (UUID)
├── userId (FK to auth users)
├── accountNumber (unique)
├── type (CHECKING, SAVINGS)
├── balance (BigDecimal — never use double for money!)
├── currency (ISO 4217: USD, EUR)
├── status (ACTIVE, FROZEN, CLOSED)
├── version (@Version for optimistic locking)
└── createdAt / updatedAt
```

> 💡 **Critical:** Use `BigDecimal` for monetary values. `double` causes rounding errors that compound across millions of transactions.

> 💡 **Why @Version?** Two concurrent withdrawals on the same account could both read balance=100 and both write balance=50 — losing 50. Optimistic locking detects this and throws.

### Step 3.2 — Endpoints
```
POST   /api/accounts               # Create account
GET    /api/accounts/{id}          # Get one
GET    /api/accounts/user/{userId} # List by user
PATCH  /api/accounts/{id}/freeze   # Admin only
GET    /api/accounts/{id}/balance  # Balance check
```

### Step 3.3 — JWT filter (shared pattern)
Copy the same `JwtAuthenticationFilter` shape from auth-service. **The JWT secret must come from config-server**, not be hardcoded — both services must agree on the signing key.

### Step 3.4 — Test it
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login -d '{...}' | jq -r .accessToken)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/accounts/user/1
```

**Commit:** `git commit -am "phase 3: account-service"`

---

## Phase 4 — Transaction service (saga + idempotency)

**This is the hardest service.** Take your time.

**Goal:** Process money transfers across accounts safely, even when failures happen mid-flow.

### Step 4.1 — Why a saga?

A transfer is two operations:
1. Debit source account
2. Credit destination account

If step 2 fails (network, crash), step 1 must be undone. **A two-phase commit isn't possible** across separate services without distributed locks. Instead, use a **saga**: a sequence of local transactions, each with a compensating action.

### Step 4.2 — Idempotency

Clients **will** retry. If a network blip causes a duplicate POST `/transfer`, you must NOT debit twice.

Solution: every request includes an `Idempotency-Key` header. Store `(key, response)` in a table; on duplicate, return the original response.

```sql
CREATE TABLE idempotency_keys (
    key VARCHAR(64) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    response_body JSONB,
    status_code INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Step 4.3 — Saga state machine

```
PENDING → DEBITED → CREDITED → COMPLETED
                      ↓
                    FAILED → COMPENSATING → REVERSED
```

Persist this state in a `transactions` table. A scheduled job picks up stuck `COMPENSATING` rows and retries the rollback.

### Step 4.4 — Kafka events

Publish to topic `transaction-events` after each state change. The notification-service consumes these. **Use idempotent producer:**

```yaml
spring:
  kafka:
    producer:
      acks: all
      enable-idempotence: true
      properties:
        max.in.flight.requests.per.connection: 5
```

> 💡 **Why idempotent producer?** Without it, a producer retry on a slow ACK can write the same message twice. Idempotent producers attach a sequence number so the broker dedupes.

### Step 4.5 — Build it incrementally

1. Start with a synchronous "happy path" `transfer` endpoint
2. Add idempotency keys
3. Add the state machine + DB persistence
4. Add Kafka event publishing
5. Add a `@Scheduled` compensation worker
6. Inject failures with a feature flag and verify rollback works

**Commit after each step.** This is the most rewardable part of the project to test thoroughly.

---

## Phase 5 — Notification service (Kafka consumer)

**Goal:** Listen to `transaction-events`, send email/SMS notifications.

### Step 5.1 — Consumer config
```yaml
spring:
  kafka:
    consumer:
      group-id: notification-service
      auto-offset-reset: earliest
      enable-auto-commit: false   # manual commit after success
      properties:
        isolation.level: read_committed
```

> 💡 **`read_committed`** ensures the consumer only sees messages from committed transactional producers — important when the producer uses transactions.

### Step 5.2 — At-least-once handling
Use `@KafkaListener` with manual ack. If sending email fails, **don't commit** — the message will be redelivered.

But what if the email *did* send and only the commit failed? You'd send twice. **Make the handler idempotent**: store `messageId → sentAt` in Redis with a TTL.

### Step 5.3 — Channels
- Email via Spring Mail (SMTP)
- SMS via a stub adapter (e.g., Twilio interface — actual implementation can be a no-op for practice)

---

## Phase 6 — API Gateway

**Goal:** Single entry point. Routes by path, validates JWTs, applies rate limits.

### Step 6.1 — Why Spring Cloud Gateway?
Reactive (Netty), built-in filters, integrates with Eureka for service discovery, supports rate limiting via Redis.

### Step 6.2 — Routes
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth
          uri: lb://auth-service
          predicates: [Path=/api/auth/**]
        - id: account
          uri: lb://account-service
          predicates: [Path=/api/accounts/**]
          filters:
            - JwtValidation
        - id: transaction
          uri: lb://transaction-service
          predicates: [Path=/api/transactions/**]
          filters:
            - JwtValidation
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
```

### Step 6.3 — Custom JwtValidation filter
A `GatewayFilter` that:
1. Reads `Authorization: Bearer <token>`
2. Validates with the shared JWT secret
3. Adds `X-User-Id` header to the downstream request
4. Rejects with 401 if invalid

Now downstream services can **trust** the `X-User-Id` header (no need to re-validate JWT). Reduces CPU and centralizes auth logic.

---

## Phase 7 — Observability

### Step 7.1 — Three pillars
| Pillar | Tool | What you see |
|---|---|---|
| Metrics | Prometheus + Micrometer | Request rate, latency, JVM heap |
| Tracing | Zipkin + Brave | End-to-end span across services |
| Logs | Logstash encoder → JSON | Searchable structured logs |

### Step 7.2 — Per-service config
Already added via root `build.gradle`. Each service exposes:
- `/actuator/prometheus` for scraping
- W3C trace context propagation (automatic with Brave)
- JSON logs to stdout

### Step 7.3 — Stand up the stack
Add to `docker-compose.yml`:
```yaml
prometheus:
  image: prom/prometheus:latest
  volumes: [./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml]
  ports: ["9090:9090"]

grafana:
  image: grafana/grafana:latest
  ports: ["3000:3000"]
  environment: { GF_SECURITY_ADMIN_PASSWORD: admin }

zipkin:
  image: openzipkin/zipkin:latest
  ports: ["9411:9411"]
```

### Step 7.4 — Validate
1. Make a request through the gateway
2. Open Zipkin → see the trace span 4 services
3. Open Grafana → import dashboard 4701 (JVM Micrometer)
4. Open Prometheus → query `http_server_requests_seconds_count`

---

## Phase 8 — Docker Compose (local stack)

**Goal:** One command spins up the entire system.

### Step 8.1 — Multi-stage Dockerfile (per service)
```dockerfile
FROM gradle:8-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :auth-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/auth-service/build/libs/*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

> 💡 **Why multi-stage?** Build image is ~800MB; runtime image is ~200MB. Faster pulls, smaller attack surface.

> 💡 **Why non-root user?** Defense in depth. If an attacker exploits a vulnerability, they don't get root.

### Step 8.2 — `docker-compose.yml`
Reference the existing one. Order of `depends_on`:
1. postgres, redis, kafka, zookeeper
2. config-server, discovery-server
3. auth, account, transaction, notification
4. api-gateway

### Step 8.3 — Run it
```bash
docker compose up --build
```

Hit `http://localhost:8080/api/auth/register` to verify end-to-end.

---

## Phase 9 — Kubernetes / Helm

**Goal:** Take the same stack to a real K8s cluster via Helm.

You already have a complete Helm chart at `helm/smartbank/`. Rather than rewriting it, **read it line by line** and answer these questions:

1. Why is `config-server` in `smartbank-infra` and not `smartbank-apps`?
2. What does `podAntiAffinity: requiredDuringScheduling` guarantee?
3. Why does the `apps/deployment.yaml` use a `range` loop?
4. What's the role of the `initContainer` in app deployments?
5. Why do app pods have `readOnlyRootFilesystem: true` AND an `emptyDir` for `/tmp`?
6. What does the `default-deny-all` NetworkPolicy do, and why is it the first thing applied?
7. Why is the deploy script ordered: data → infra → obs → apps?

If you can answer all 7, you understand the chart.

### Hands-on K8s practice
```bash
# Use kind for a local cluster
kind create cluster --name smartbank
cd helm
./deploy.sh --dry-run    # render templates
./deploy.sh              # actual install
kubectl get pods -A
```

---

## Phase 10 — CI/CD

Read `docs/cicd-setup-guide.md` and implement these pipelines:

1. **PR pipeline:** lint → build → test → SonarCloud scan → fail on coverage <70%
2. **Main branch:** build → test → push Docker image → update Helm values → ArgoCD sync (or `helm upgrade`)
3. **Tag pipeline:** semantic version → release notes → deploy to prod

---

## Validation checklist

When you finish each phase, verify:

### Phase 0–1
- [ ] `./gradlew build` succeeds
- [ ] Eureka dashboard shows 0 services initially

### Phase 2–6
- [ ] All services register with Eureka
- [ ] You can register a user, log in, create an account, transfer money — through the gateway
- [ ] An invalid JWT returns 401, a valid one returns 200

### Phase 4 (transactions)
- [ ] Two concurrent transfers from the same account never go negative
- [ ] Killing transaction-service mid-transfer leaves the system consistent (no orphaned debits)
- [ ] Replaying the same `Idempotency-Key` returns the original response, not a duplicate transfer

### Phase 7
- [ ] One request shows up as a multi-service trace in Zipkin
- [ ] Prometheus has metrics from all services
- [ ] Grafana dashboards render

### Phase 8–9
- [ ] `docker compose up` works on a clean machine
- [ ] `helm install` succeeds on a kind cluster
- [ ] All NetworkPolicies are enforced (test by exec'ing into a pod and trying to reach a forbidden namespace)

---

## Tips for learning

1. **Type, don't paste.** Copying code is the fastest way to forget it. Typing forces you to read every line.
2. **Break things on purpose.** Delete the JWT secret. Stop Postgres mid-transfer. Network-partition Kafka. Watching how the system fails teaches more than watching it succeed.
3. **Read the existing code only when stuck.** This repo is your reference manual, not a tutorial to follow blindly.
4. **Commit every working step.** When you break something later, `git diff` is your best friend.
5. **Write tests as you go.** A microservices system without tests is unmaintainable.

---

## Estimated phase difficulty

| Phase | Difficulty | Key new concept |
|---|---|---|
| 0 — Gradle | ⭐ | Convention plugins |
| 1 — Infra | ⭐⭐ | Service discovery, externalized config |
| 2 — Auth | ⭐⭐⭐ | JWT, Spring Security filter chain |
| 3 — Account | ⭐⭐ | Optimistic locking, BigDecimal |
| 4 — Transaction | ⭐⭐⭐⭐⭐ | Saga, idempotency, compensation |
| 5 — Notification | ⭐⭐⭐ | Kafka consumer semantics |
| 6 — Gateway | ⭐⭐⭐ | Reactive routing, filter chain |
| 7 — Observability | ⭐⭐ | Three pillars, instrumentation |
| 8 — Docker | ⭐⭐ | Multi-stage builds, compose ordering |
| 9 — K8s/Helm | ⭐⭐⭐⭐ | Templates, network policies, dependency order |
| 10 — CI/CD | ⭐⭐⭐ | Pipeline as code |

Good luck. This is a real production-shape codebase — finishing it end-to-end will materially level you up as a backend engineer.
