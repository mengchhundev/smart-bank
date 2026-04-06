# Infrastructure & Configuration

## Docker Compose

Start all infrastructure dependencies:

```bash
docker-compose up -d
```

### Services

| Container   | Image                      | Port(s)       | Purpose                  |
|-------------|----------------------------|---------------|--------------------------|
| postgres    | postgres:16-alpine         | 5433→5432     | Primary database         |
| zookeeper   | confluentinc/cp-zookeeper:7.6.0 | 2181    | Kafka coordination       |
| kafka       | confluentinc/cp-kafka:7.6.0| 29092→29092   | Event streaming          |
| zipkin      | openzipkin/zipkin          | 9411          | Distributed tracing      |
| mailhog     | mailhog/mailhog            | 1025, 8025    | Dev SMTP + Web UI        |

### Databases

The PostgreSQL container initializes four databases via `init-db.sql`:

| Database       | Owner      | Service            |
|----------------|------------|--------------------|
| `auth_db`      | smartbank  | auth-service       |
| `account_db`   | smartbank  | account-service    |
| `transaction_db` | smartbank| transaction-service|
| `notification_db`| smartbank| notification-service|

**Credentials:**
```
host:     localhost:5433
username: smartbank
password: smartbank123
```

### Network

All containers run on `smartbank-net` (bridge). PostgreSQL data is persisted to a named volume.

---

## Config Server

**Port:** 8888  
**Profile:** `native` (reads config files from classpath)  
**Credentials:** `config / config123`

All services bootstrap from Config Server before connecting to Eureka. Config files live under:
```
config-server/src/main/resources/configurations/
```

---

## Service Discovery (Eureka)

**Port:** 8761  
**URL:** http://localhost:8761  
**Credentials:** `eureka / eureka123`

All services register themselves. Self-registration on the Eureka server itself is disabled.

---

## Key Credentials Summary

| System        | Username | Password    |
|---------------|----------|-------------|
| Config Server | config   | config123   |
| Eureka        | eureka   | eureka123   |
| PostgreSQL    | smartbank| smartbank123|

---

## JWT Configuration

| Property               | Value                                                      |
|------------------------|------------------------------------------------------------|
| Secret                 | `smartbank-jwt-secret-key-that-is-at-least-256-bits-long-for-hs256` |
| Algorithm              | HS256                                                      |
| Access token TTL       | 900,000 ms (15 min)                                        |
| Refresh token TTL      | 604,800,000 ms (7 days)                                    |

---

## Kafka

| Property         | Value              |
|------------------|--------------------|
| Bootstrap servers| localhost:29092     |
| Topic            | `transaction-events`|
| Consumer group   | `notification-group`|
| Key serializer   | StringSerializer   |
| Value serializer | JsonSerializer     |

Consumer uses `ErrorHandlingDeserializer` wrapping `JsonDeserializer`. Trusted packages: `com.smartbank.*`.

---

## Email (MailHog)

| Property  | Value                  |
|-----------|------------------------|
| SMTP host | localhost              |
| SMTP port | 1025                   |
| Web UI    | http://localhost:8025  |
| From      | noreply@smartbank.com  |
| Auth      | none (dev only)        |

Notification service uses Thymeleaf templates for HTML emails. Template: `transaction-notification`.

---

## Tracing (Zipkin)

| Property            | Value                                  |
|---------------------|----------------------------------------|
| Endpoint            | http://localhost:9411/api/v2/spans     |
| Sampling probability| 1.0 (100%)                             |
| UI                  | http://localhost:9411                  |

All services export traces. For production, lower sampling probability to ~0.1.
