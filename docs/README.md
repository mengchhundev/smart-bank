# SmartBank Microservices Platform

## Overview

SmartBank is a production-grade banking platform built on a microservices architecture. It handles user authentication, account management, fund transfers, and event-driven notifications.

**Stack:** Java 17 · Spring Boot 3.2.5 · Spring Cloud 2023.0.1 · PostgreSQL · Apache Kafka · Redis · Kubernetes · Helm

---

## Architecture Diagram

```
                     ┌────────────────────────────────────────┐
                     │         NGINX Ingress Controller       │
                     │          smartbank.local                │
                     └──────────────┬─────────────────────────┘
                                    │
                          ┌─────────▼─────────┐
                          │    API Gateway     │ :8080
                          │  JWT · Rate Limit  │
                          │  Circuit Breaker   │
                          └────────┬──────────┘
                                   │
         ┌───────────────┬─────────┴────────┬──────────────────┐
         ▼               ▼                  ▼                  ▼
  ┌─────────────┐ ┌─────────────┐ ┌───────────────┐ ┌──────────────────┐
  │auth-service │ │account-svc  │ │transaction-svc│ │notification-svc  │
  │   :8081     │ │   :8082     │ │    :8083      │ │     :8085        │
  └──────┬──────┘ └──────┬──────┘ └──────┬────────┘ └────────┬─────────┘
         │               │        Feign ↕│                   │
         │               │◄──────────────┘   Kafka ──────────┘
         │               │                     │
   ┌─────▼─────┐  ┌──────▼──────┐  ┌──────────▼──────────┐
   │  auth_db  │  │ account_db  │  │  transaction_db     │
   └───────────┘  └─────────────┘  └─────────────────────┘

   Infrastructure: PostgreSQL · Kafka · Redis · Zipkin · Prometheus · Grafana
```

---

## Services

| Service              | Port | Purpose                                      |
|----------------------|------|----------------------------------------------|
| `config-server`      | 8888 | Centralized Spring Cloud Config              |
| `discovery-server`   | 8761 | Eureka service registry                      |
| `api-gateway`        | 8080 | Edge routing, JWT validation, rate limiting  |
| `auth-service`       | 8081 | Registration, login, JWT issuance            |
| `account-service`    | 8082 | Account CRUD, balance operations             |
| `transaction-service`| 8083 | Fund transfers, Kafka event producer         |
| `notification-service`| 8085| Kafka consumer, email notifications          |

---

## Infrastructure

| Component          | Dev Port      | K8s Service Name             | Purpose                    |
|--------------------|---------------|------------------------------|----------------------------|
| PostgreSQL         | 5433          | smartbank-postgresql         | Primary datastore (4 DBs)  |
| Apache Kafka       | 29092         | smartbank-kafka              | Event streaming            |
| Redis              | 6379          | smartbank-redis-master       | Rate limiting & caching    |
| Zipkin             | 9411          | zipkin                       | Distributed tracing        |
| Prometheus         | 9090          | (kube-prometheus-stack)      | Metrics collection         |
| Grafana            | 3000          | (kube-prometheus-stack)      | Dashboards & alerting      |
| MailHog            | 1025 / 8025   | —                            | SMTP dev server / Web UI   |

---

## Quick Start

### Docker Compose (local development)

```bash
docker-compose up -d
./gradlew build
# Start services in order: config-server → discovery-server → rest
```

### Kubernetes (Helm)

```bash
helm dependency build helm/smartbank
helm upgrade --install smartbank helm/smartbank \
  --namespace smartbank --create-namespace \
  --set global.imageRegistry=docker.io/YOUR_USER \
  --set global.imageTag=latest
```

See [Startup Guide](startup-guide.md) for detailed step-by-step instructions.

---

## Documentation Index

### Architecture & Design
- [Architecture & Design](architecture.md) — principles, data flow, security model, K8s topology

### Infrastructure & Configuration
- [Infrastructure & Configuration](infrastructure.md) — Docker Compose & K8s infrastructure
- [Kubernetes Deployment](kubernetes-deployment.md) — complete K8s + Helm deployment guide

### API & Development
- [API Reference](api-reference.md) — REST endpoints for all services
- [Development Guide](development.md) — local setup, testing, code conventions
- [Startup Guide](startup-guide.md) — step-by-step startup (Docker & K8s)

### CI/CD
- [CI/CD Setup Guide](cicd-setup-guide.md) — GitHub Actions, Helm deploy, secrets, environments

### Services
- [Config Server](services/config-server.md)
- [Discovery Server](services/discovery-server.md)
- [API Gateway](services/api-gateway.md)
- [Auth Service](services/auth-service.md)
- [Account Service](services/account-service.md)
- [Transaction Service](services/transaction-service.md)
- [Notification Service](services/notification-service.md)
