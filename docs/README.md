# SmartBank Microservices Platform

## Overview

SmartBank is a production-grade banking platform built on a microservices architecture. It handles user authentication, account management, fund transfers, and event-driven notifications.

**Stack:** Java 17 · Spring Boot 3.2.5 · Spring Cloud 2023.0.1 · PostgreSQL · Apache Kafka · Docker

---

## Architecture Diagram

```
                          ┌─────────────────┐
                          │   API Gateway   │ :8080
                          │  (JWT Validate) │
                          └────────┬────────┘
                                   │
           ┌───────────────┬───────┴────────┬─────────────────┐
           ▼               ▼                ▼                 ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌──────────────────┐
    │ auth-service│ │account-svc  │ │transaction  │ │notification-svc  │
    │   :8081     │ │   :8082     │ │   svc :8083 │ │     :8085        │
    └─────────────┘ └─────────────┘ └──────┬──────┘ └────────┬─────────┘
                           ▲               │  Feign          │
                           └───────────────┘    Kafka ───────┘
                                                  │
                                         ┌────────▼────────┐
                                         │  Kafka :29092   │
                                         │  topic:         │
                                         │  transaction-   │
                                         │  events         │
                                         └─────────────────┘

    ┌──────────────────┐   ┌──────────────────┐
    │  Config Server   │   │ Discovery Server │
    │     :8888        │   │  (Eureka) :8761  │
    └──────────────────┘   └──────────────────┘
```

---

## Services

| Service              | Port | Purpose                                      |
|----------------------|------|----------------------------------------------|
| `config-server`      | 8888 | Centralized Spring Cloud Config              |
| `discovery-server`   | 8761 | Eureka service registry                      |
| `api-gateway`        | 8080 | Edge routing, JWT validation, CORS           |
| `auth-service`       | 8081 | Registration, login, JWT issuance            |
| `account-service`    | 8082 | Account CRUD, balance operations             |
| `transaction-service`| 8083 | Fund transfers, Kafka event producer         |
| `notification-service`| 8085| Kafka consumer, email notifications          |

---

## Infrastructure

| Component   | Port(s)       | Purpose                        |
|-------------|---------------|--------------------------------|
| PostgreSQL  | 5433          | Primary datastore (4 databases)|
| Apache Kafka| 29092         | Event streaming                |
| Zookeeper   | 2181          | Kafka coordination             |
| Zipkin      | 9411          | Distributed tracing            |
| MailHog     | 1025 / 8025   | SMTP dev server / Web UI       |

---

## Quick Start

```bash
# Start infrastructure
docker-compose up -d

# Build all services
./gradlew build

# Start services in order:
# 1. config-server
# 2. discovery-server
# 3. api-gateway
# 4. auth-service, account-service, transaction-service, notification-service
```

---

## Documentation Index

- [Architecture & Design](architecture.md)
- [Infrastructure & Configuration](infrastructure.md)
- [API Reference](api-reference.md)
- [Development Guide](development.md)
- **Services**
  - [Config Server](services/config-server.md)
  - [Discovery Server](services/discovery-server.md)
  - [API Gateway](services/api-gateway.md)
  - [Auth Service](services/auth-service.md)
  - [Account Service](services/account-service.md)
  - [Transaction Service](services/transaction-service.md)
  - [Notification Service](services/notification-service.md)
