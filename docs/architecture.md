# Architecture

## System Overview

SmartBank follows a **microservices architecture** where each business domain owns its own service, database, and deployment unit. Services communicate synchronously via HTTP (Feign) for queries and asynchronously via Kafka for events.

---

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            CLIENT LAYER                                 │
│              Web App · Mobile App · Third-party API consumers           │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ HTTPS
┌──────────────────────────────▼──────────────────────────────────────────┐
│                          API GATEWAY  :8080                             │
│  • JWT validation           • CORS                                      │
│  • Rate limiting (Redis)    • Swagger aggregation                       │
│  • Circuit breaker (Resilience4j)                                       │
└──────┬───────────────────┬───────────────────┬──────────────────────────┘
       │                   │                   │
┌──────▼──────┐   ┌────────▼──────┐   ┌────────▼──────────┐
│ Auth Service│   │Account Service│   │Transaction Service│
│   :8081     │   │   :8082       │   │      :8083        │
│             │   │               │   │                   │
│ PostgreSQL  │   │  PostgreSQL   │   │  PostgreSQL       │
│  (auth_db)  │   │ (account_db)  │   │(transaction_db)   │
└─────────────┘   └───────┬───────┘   └────────┬──────────┘
                          │                    │
                          │ Feign (sync)       │ Kafka event
                          └────────────────────┘
                                               │
                                    ┌──────────▼───────────┐
                                    │ Notification Service │
                                    │       :8085          │
                                    │                      │
                                    │    PostgreSQL        │
                                    │ (notification_db)    │
                                    │  Email · Telegram    │
                                    └──────────────────────┘

┌─────────────────────┐   ┌─────────────────────┐
│  Discovery Server   │   │   Config Server     │
│  Eureka  :8761      │◄──│     :8888           │
│  (all services      │   │  (native classpath  │
│   register here)    │   │   configs)          │
└─────────────────────┘   └─────────────────────┘

┌──────────────────────────────────────────────────────────┐
│                  OBSERVABILITY LAYER                     │
│   Prometheus (:9090) · Grafana (:3000) · Zipkin (:9411)  │
└──────────────────────────────────────────────────────────┘
```

---

## Request Flow: Authenticated API Call

```
Client
  │
  ├─ POST /api/auth/login ──────────────────────────► Auth Service
  │                                                      │ returns JWT
  │◄──────────────────────────────────────────────────── │
  │
  ├─ GET /api/accounts (Bearer: <token>)
  │     │
  │   API Gateway
  │     ├─ Validates JWT signature & expiry
  │     ├─ Checks rate limit bucket in Redis
  │     ├─ Routes to Account Service via Eureka
  │     │
  │   Account Service
  │     ├─ Re-validates JWT claims
  │     └─ Returns account data
  │◄─────────────────────────────────────────────────────
```

## Request Flow: Transaction with Notification

```
Client ──► API Gateway ──► Transaction Service
                                  │
                                  ├─ Checks idempotency table (prevent duplicates)
                                  ├─ Calls Account Service (Feign) to debit/credit
                                  ├─ Persists transaction record
                                  └─ Publishes Kafka event: transaction.completed
                                                    │
                                         Notification Service (Kafka consumer)
                                                    │
                                                    ├─ Persists notification record
                                                    ├─ Sends email (SMTP/Thymeleaf)
                                                    └─ Sends Telegram (optional)
```

---

## Key Design Decisions

### Database per Service (Polyglot Persistence)
Each service owns its PostgreSQL database. No cross-service joins. Eventual consistency is maintained through Kafka events. Schema evolution is managed per-service with Flyway migrations.

### Idempotency in Transaction Service
Every transaction request carries a client-supplied idempotency key. The service checks a dedicated `idempotency_records` table before processing, making retries safe without double-charging.

### JWT at the Edge
The API Gateway performs JWT signature validation and rejects invalid tokens before a request reaches any downstream service. Individual services also validate claims as a second layer of defense.

### Asynchronous Notifications
Notifications are decoupled via Kafka. If the Notification Service is unavailable, transactions complete successfully. Kafka retains the events; the consumer catches up when it recovers. Resilience4j retry with exponential backoff handles transient SMTP failures.

### Circuit Breakers at the Gateway
Resilience4j circuit breakers wrap each upstream route at the gateway. When a service degrades, the circuit opens and returns a fast fallback rather than cascading timeouts to the client.

---

## Infrastructure Services

### Eureka Discovery Server (:8761)
All services register on startup with a heartbeat interval. The API Gateway resolves service hostnames through Eureka before forwarding requests. Credentials: `eureka:eureka123` (override in production via Config Server).

### Config Server (:8888)
Uses the `native` profile to serve `application.yml` from the classpath. Switch to the `git` profile in production to back configuration in a private Git repository. Credentials: `config:config123`.

### Redis
Used exclusively by the API Gateway for token-bucket rate limiting. Each route has an independent bucket:

| Route | Replenish Rate | Burst Capacity |
|---|---|---|
| `/api/accounts/**` | 10 req/s | 20 |
| `/api/transactions/**` | 5 req/s | 10 |
| `/api/notifications/**` | 10 req/s | 20 |
| `/api/auth/**` | no limit | — |

---

## Deployment Topology (Kubernetes)

```
Namespace: smartbank
│
├── Platform Tier
│   ├── discovery-server   (1 replica)
│   ├── config-server      (1 replica)
│   └── api-gateway        (2 replicas)
│
├── Business Tier (2 replicas each)
│   ├── auth-service
│   ├── account-service
│   ├── transaction-service
│   └── notification-service
│
├── Data Tier
│   ├── postgres-auth        (2Gi PVC)
│   ├── postgres-account     (2Gi PVC)
│   ├── postgres-transaction (5Gi PVC — larger for tx history)
│   ├── postgres-notification(2Gi PVC)
│   ├── redis                (1 replica)
│   └── kafka                (1 replica, 5Gi PVC)
│
└── Observability Tier
    └── zipkin               (1 replica)
```

Production overrides (via Helm values):
- PostgreSQL PVCs: 50Gi
- Redis: replication mode
- Kafka: 3 replicas
- Prometheus: 30-day retention
