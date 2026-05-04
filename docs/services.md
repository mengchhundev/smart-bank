# Services Reference

Detailed per-service information: ports, API routes, configuration, and database schema.

---

## API Gateway

**Port:** 8080  
**Module:** `api-gateway`

The single entry point for all client traffic. Handles JWT validation, rate limiting, CORS, circuit breaking, and Swagger UI aggregation.

### Routes

| Path Prefix | Upstream Service | Rate Limit |
|---|---|---|
| `/api/auth/**` | auth-service | None |
| `/api/accounts/**` | account-service | 10 req/s, burst 20 |
| `/api/transactions/**` | transaction-service | 5 req/s, burst 10 |
| `/api/notifications/**` | notification-service | 10 req/s, burst 20 |

### Key Config Properties

```yaml
spring.cloud.gateway.routes       # Route definitions
resilience4j.circuitbreaker       # Per-route circuit breaker settings
spring.data.redis.host            # Redis for rate limiting
```

### Circuit Breaker Defaults

- Sliding window: COUNT_BASED
- Failure rate threshold: 50%
- Wait duration in open state: 10–20s (per route)
- Each route has a `/fallback` URI configured

---

## Auth Service

**Port:** 8081  
**Module:** `auth-service`  
**Database:** `auth_db` (PostgreSQL)

Handles user registration, authentication, and token lifecycle.

### API Endpoints

| Method | Path | Auth Required | Description |
|---|---|---|---|
| POST | `/api/auth/register` | No | Register a new user |
| POST | `/api/auth/login` | No | Authenticate and receive tokens |
| POST | `/api/auth/refresh` | No | Exchange refresh token for new access token |
| POST | `/api/auth/logout` | Yes | Invalidate refresh token |

### Token Details

| Token | Expiry | Storage |
|---|---|---|
| Access token (JWT) | 15 minutes | Client-side (Authorization header) |
| Refresh token | 7 days | Database (`refresh_tokens` table) |

JWT claims include: `sub` (user ID), `iat`, `exp`.

### Database Schema

```sql
-- users
id          BIGSERIAL PRIMARY KEY
username    VARCHAR UNIQUE NOT NULL
email       VARCHAR UNIQUE NOT NULL
password    VARCHAR NOT NULL          -- BCrypt hashed
role        VARCHAR NOT NULL
created_at  TIMESTAMP

-- refresh_tokens
id          BIGSERIAL PRIMARY KEY
token       VARCHAR UNIQUE NOT NULL
user_id     BIGINT REFERENCES users(id)
expires_at  TIMESTAMP
revoked     BOOLEAN DEFAULT FALSE
```

---

## Account Service

**Port:** 8082  
**Module:** `account-service`  
**Database:** `account_db` (PostgreSQL)

Manages bank account creation, status transitions, and balance tracking.

### API Endpoints

| Method | Path | Auth Required | Description |
|---|---|---|---|
| POST | `/api/accounts` | Yes | Open a new account |
| GET | `/api/accounts/{id}` | Yes | Get account details |
| GET | `/api/accounts/user/{userId}` | Yes | List accounts for a user |
| PATCH | `/api/accounts/{id}/status` | Yes (ADMIN) | Update account status |
| GET | `/api/accounts/{id}/balance` | Yes | Get current balance |

**Internal endpoint** (used by Transaction Service via Feign):

| Method | Path | Description |
|---|---|---|
| PUT | `/api/accounts/internal/balance` | Debit or credit an account |

### Account Types

`CHECKING` · `SAVINGS` · `FIXED_DEPOSIT` · `BUSINESS`

### Account Statuses

`ACTIVE` · `FROZEN` · `CLOSED`

### Kafka Events Published

- Topic: `account.notifications` — fires on account creation

### Database Schema

```sql
-- accounts
id            BIGSERIAL PRIMARY KEY
account_number VARCHAR UNIQUE NOT NULL
user_id        BIGINT NOT NULL
type           VARCHAR NOT NULL
status         VARCHAR NOT NULL DEFAULT 'ACTIVE'
balance        DECIMAL(19,4) NOT NULL DEFAULT 0
currency       VARCHAR(3) NOT NULL DEFAULT 'USD'
created_at     TIMESTAMP
```

---

## Transaction Service

**Port:** 8083  
**Module:** `transaction-service`  
**Database:** `transaction_db` (PostgreSQL)

Processes fund transfers between accounts. Guarantees exactly-once execution via idempotency records.

### API Endpoints

| Method | Path | Auth Required | Description |
|---|---|---|---|
| POST | `/api/transactions` | Yes | Initiate a transaction |
| GET | `/api/transactions/{id}` | Yes | Get transaction details |
| GET | `/api/transactions/account/{accountId}` | Yes | List transactions for an account |

### Request Body (POST /api/transactions)

```json
{
  "idempotencyKey": "uuid-v4-generated-by-client",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 250.00,
  "currency": "USD",
  "description": "Rent payment"
}
```

The `idempotencyKey` must be unique per distinct transaction. Replaying the same key returns the original result without re-processing.

### Kafka Events Published

- Topic: `transaction.completed` — consumed by Notification Service

### Database Schema

```sql
-- transactions
id                   BIGSERIAL PRIMARY KEY
idempotency_key      VARCHAR UNIQUE NOT NULL
source_account_id    BIGINT NOT NULL
destination_account_id BIGINT NOT NULL
amount               DECIMAL(19,4) NOT NULL
currency             VARCHAR(3) NOT NULL
status               VARCHAR NOT NULL   -- PENDING, COMPLETED, FAILED
description          VARCHAR
created_at           TIMESTAMP

-- idempotency_records
id              BIGSERIAL PRIMARY KEY
idempotency_key VARCHAR UNIQUE NOT NULL
response_body   TEXT
created_at      TIMESTAMP
```

---

## Notification Service

**Port:** 8085  
**Module:** `notification-service`  
**Database:** `notification_db` (PostgreSQL)

Consumes Kafka events from Transaction Service and Account Service, then delivers notifications via email and optionally Telegram.

### Kafka Topics Consumed

| Topic | Source | Action |
|---|---|---|
| `transaction.completed` | transaction-service | Send transaction receipt email |
| `account.notifications` | account-service | Send account opening confirmation |

### Channels

| Channel | Status | Config |
|---|---|---|
| Email (SMTP) | Always enabled | `spring.mail.*` properties |
| Telegram | Disabled by default | Set `TELEGRAM_BOT_TOKEN` + `TELEGRAM_CHAT_ID` |

Email templates are rendered via **Thymeleaf**.

### Resilience Configuration

- Retry: 3 attempts with exponential backoff (initial 1s, multiplier 2)
- Applies to email and Telegram delivery

### Database Schema

```sql
-- notifications
id           BIGSERIAL PRIMARY KEY
recipient    VARCHAR NOT NULL
channel      VARCHAR NOT NULL      -- EMAIL, TELEGRAM
subject      VARCHAR
content      TEXT
status       VARCHAR NOT NULL      -- SENT, FAILED
event_type   VARCHAR NOT NULL
reference_id VARCHAR
sent_at      TIMESTAMP
created_at   TIMESTAMP
```

---

## Discovery Server (Eureka)

**Port:** 8761  
**Module:** `discovery-server`  
**URL:** `http://eureka:eureka123@<host>:8761/eureka/`

Standard Spring Cloud Netflix Eureka Server. All services register on startup and renew heartbeats every 30 seconds.

Dashboard available at `http://localhost:8761` in local environments.

---

## Config Server

**Port:** 8888  
**Module:** `config-server`  
**Profile:** `native` (classpath-backed)

Serves `application.yml` and per-service YAML files to all services on startup. Services fetch config at boot via `spring.config.import=configserver:http://config:config123@<host>:8888`.

To switch to Git-backed config, change the active profile to `git` and set `spring.cloud.config.server.git.uri`.
