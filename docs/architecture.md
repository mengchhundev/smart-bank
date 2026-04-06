# Architecture & Design

## Principles

- **Database per service** — each service owns its schema; no shared tables across service boundaries
- **Stateless** — no HTTP sessions; JWT-based identity propagation throughout the call chain
- **Event-driven** — Kafka decouples transaction processing from notifications
- **Layered security** — JWT validated at the gateway edge _and_ re-validated inside each service
- **Optimistic concurrency** — `@Version` on `Account` prevents lost-update on concurrent balance changes

---

## Communication Patterns

### Synchronous (OpenFeign)
Used when the caller needs an immediate result:
- `transaction-service` → `account-service` (debit/credit balance)
- `account-service` → `auth-service` (token validation)

### Asynchronous (Kafka)
Used for fire-and-forget side effects:
- `transaction-service` publishes `TransactionEvent` → topic `transaction-events`
- `notification-service` consumes `transaction-events` → sends email

---

## Security Model

```
Client → [Gateway: validate JWT, inject X-User-Id / X-User-Roles headers]
       → [Service: re-validate JWT, extract principal from claims]
```

**Token lifecycle:**
| Token         | Algorithm | TTL       |
|---------------|-----------|-----------|
| Access token  | HS256     | 15 min    |
| Refresh token | UUID      | 7 days    |

Refresh tokens are stored in `auth_db.refresh_tokens`. On refresh, the old token is revoked before a new one is issued. Logout revokes the token immediately.

**Roles:** `CUSTOMER`, `ADMIN`  
ADMIN is required for account status updates (`PUT /api/accounts/{id}/status`).

---

## Data Flow: Fund Transfer

```
POST /api/v1/transactions/transfer
        │
        ▼
[API Gateway] — validate JWT → pass X-User-Id
        │
        ▼
[transaction-service]
  1. Create Transaction (status=PROCESSING)
  2. Feign → account-service: DEBIT source account
  3. Feign → account-service: CREDIT target account
  4. Update Transaction (status=COMPLETED)
  5. Publish TransactionEvent → Kafka
  6. On any failure: status=FAILED, publish event
        │
        ▼ (Kafka: transaction-events)
[notification-service]
  1. Consume TransactionEvent
  2. Send HTML email via MailHog/SMTP
  3. Persist Notification record (SENT or FAILED)
```

---

## Database Schema

### auth_db

```sql
users (
  id BIGINT PK,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,          -- BCrypt strength 12
  full_name VARCHAR(100),
  phone VARCHAR(20),
  role ENUM(CUSTOMER, ADMIN) DEFAULT CUSTOMER,
  enabled BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
)

refresh_tokens (
  id BIGINT PK,
  token VARCHAR(255) UNIQUE NOT NULL,
  user_id BIGINT FK → users(id) ON DELETE CASCADE,
  expiry_date TIMESTAMP NOT NULL,
  revoked BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL
)
```

### account_db

```sql
accounts (
  id BIGINT PK,
  account_number VARCHAR(20) UNIQUE NOT NULL,  -- Format: SB + 14 random chars
  account_holder_name VARCHAR(100) NOT NULL,
  account_type ENUM(SAVINGS, CHECKING, FIXED_DEPOSIT, CURRENT),
  balance NUMERIC(19,4) DEFAULT 0,
  currency VARCHAR(3) DEFAULT 'USD',
  status ENUM(ACTIVE, INACTIVE, CLOSED, FROZEN) DEFAULT ACTIVE,
  user_id BIGINT NOT NULL,                     -- Logical FK to auth_db.users
  version INTEGER DEFAULT 0,                   -- Optimistic locking
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
)
```

### transaction_db _(Hibernate DDL)_

```sql
transactions (
  id BIGINT PK,
  reference_number VARCHAR UNIQUE NOT NULL,    -- Format: TXN + 12 random chars
  source_account VARCHAR NOT NULL,
  target_account VARCHAR NOT NULL,
  amount NUMERIC(19,4) NOT NULL,
  currency VARCHAR(3) DEFAULT 'USD',
  status ENUM(PENDING, PROCESSING, COMPLETED, FAILED, REVERSED),
  transaction_type ENUM(TRANSFER, DEPOSIT, WITHDRAWAL),
  description VARCHAR,
  failure_reason VARCHAR,
  created_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP
)
```

### notification_db _(Hibernate DDL)_

```sql
notifications (
  id BIGINT PK,
  recipient VARCHAR NOT NULL,
  subject VARCHAR NOT NULL,
  message TEXT,
  notification_type ENUM(EMAIL, SMS),
  status ENUM(PENDING, SENT, FAILED) DEFAULT PENDING,
  reference_number VARCHAR,                    -- Links to transaction
  failure_reason VARCHAR,
  created_at TIMESTAMP NOT NULL,
  sent_at TIMESTAMP
)
```

---

## Distributed Tracing

All services instrument traces via Micrometer + Brave and export to Zipkin (`http://localhost:9411`). Sampling probability is `1.0` (100%) — tune this for production.

---

## Build System

Gradle multi-module project with:
- Parallel builds enabled (`org.gradle.parallel=true`)
- Build caching enabled (`org.gradle.caching=true`)
- JVM: `-Xmx2g -XX:+HeapDumpOnOutOfMemoryError`

Spring Boot plugin is applied `false` at root and selectively applied per module.
