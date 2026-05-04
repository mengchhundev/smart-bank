# SmartBank

A production-ready, cloud-native banking platform built on **Spring Boot 3.2.5** and **Spring Cloud 2023.0.1**. The system is composed of seven microservices, containerized with Docker, orchestrated on Kubernetes via Helm, and shipped through a fully automated GitHub Actions CI/CD pipeline.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Services at a Glance](#services-at-a-glance)
- [Tech Stack](#tech-stack)
- [Quick Start (Local)](#quick-start-local)
- [Documentation](#documentation)
- [CI/CD](#cicd)
- [Project Structure](#project-structure)

---

## Architecture Overview

```
                          ┌──────────────┐
           Clients ──────►│  API Gateway  │◄── JWT validation, Rate limiting, Circuit breakers
                          └──────┬───────┘
                                 │  Spring Cloud Gateway (port 8080)
          ┌──────────────────────┼────────────────────────┐
          │                      │                         │
   ┌──────▼──────┐      ┌────────▼──────┐      ┌─────────▼──────────┐
   │ Auth Service│      │Account Service│      │Transaction Service  │
   │  (port 8081)│      │  (port 8082)  │      │    (port 8083)      │
   └─────────────┘      └───────────────┘      └────────────────────┘
                                                          │
                                                     Kafka Event
                                                          │
                                               ┌──────────▼──────────┐
                                               │ Notification Service │
                                               │     (port 8085)      │
                                               └─────────────────────┘

   ┌──────────────────┐   ┌──────────────────┐
   │ Discovery Server │   │  Config Server   │
   │  Eureka (8761)   │   │   (port 8888)    │
   └──────────────────┘   └──────────────────┘
```

All services register with **Eureka** for service discovery and pull configuration from the **Config Server**. Inter-service calls use **Spring Cloud OpenFeign**. Async events flow through **Apache Kafka**. Distributed traces are exported to **Zipkin**.

---

## Services at a Glance

| Service | Port | Role |
|---|---|---|
| api-gateway | 8080 | Edge routing, JWT auth, rate limiting, circuit breaking |
| auth-service | 8081 | User registration, login, JWT + refresh tokens |
| account-service | 8082 | Account lifecycle, balance management |
| transaction-service | 8083 | Transaction processing, idempotency |
| notification-service | 8085 | Email / Telegram alerts via Kafka events |
| discovery-server | 8761 | Eureka service registry |
| config-server | 8888 | Centralized configuration |

---

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 · Spring Cloud 2023.0.1 |
| Build | Gradle 8 |
| Database | PostgreSQL 16 (per-service isolation) |
| Cache | Redis 7 |
| Messaging | Apache Kafka 3.7 |
| Container | Docker (multi-stage, non-root) |
| Orchestration | Kubernetes · Helm 3 · Kustomize |
| Observability | Prometheus · Grafana · Zipkin · Micrometer |
| Resilience | Resilience4j (circuit breaker, retry, rate limiter) |
| Security | Spring Security · JJWT 0.12.5 · Trivy |
| Code Quality | SonarCloud · JaCoCo (≥70% coverage) |
| CI/CD | GitHub Actions |

---

## Quick Start (Local)

### Prerequisites

- Java 17+
- Docker 24+
- Kubernetes cluster (minikube / kind / cloud)
- Helm 3+
- `kubectl` configured

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env — set DB passwords, JWT secret, SMTP credentials, etc.
```

### 2. Build all service images

```bash
scripts/build-images.sh --registry <your-dockerhub-username> --tag latest --push
```

Build a subset only:

```bash
scripts/build-images.sh --registry <your-dockerhub-username> --tag latest auth-service api-gateway
```

### 3. Deploy with Helm

```bash
helm upgrade --install smartbank helm/smartbank-chart \
  --namespace smartbank --create-namespace \
  --set global.imageRegistry=<your-dockerhub-username> \
  --set secrets.jwtSecret=<your-256bit-secret> \
  --set secrets.postgresPassword=<db-password>
```

### 4. Access the API

```bash
kubectl -n smartbank port-forward svc/api-gateway 8080:8080
open http://localhost:8080/swagger-ui.html
```

---

## Documentation

Detailed guides live in the [`docs/`](docs/) directory:

| Document | Description |
|---|---|
| [Architecture](docs/architecture.md) | Component diagram, data flows, design decisions |
| [Services Reference](docs/services.md) | Per-service API routes, config, and DB schema |
| [Getting Started](docs/getting-started.md) | Full local setup walkthrough |
| [Deployment Guide](docs/deployment.md) | Kubernetes, Helm, and CI/CD pipeline reference |
| [Monitoring & Observability](docs/monitoring.md) | Prometheus, Grafana, Zipkin setup |

---

## CI/CD

| Workflow | Trigger | Jobs |
|---|---|---|
| `ci.yml` | Push / PR → `main`, `develop` | Build & test → SonarCloud → Docker build (matrix) → Trivy security scan → Helm lint |
| `cd.yml` | Push → `main` | Deploy staging → Smoke tests → Deploy production (manual approval) |
| `qodana_code_quality.yml` | Scheduled | JetBrains Qodana analysis |

---

## Project Structure

```
smart-bank/
├── api-gateway/             # Spring Cloud Gateway service
├── auth-service/            # Authentication & JWT service
├── account-service/         # Account management service
├── transaction-service/     # Transaction processing service
├── notification-service/    # Email/Telegram notification service
├── discovery-server/        # Eureka service registry
├── config-server/           # Centralized config server
├── k8s/                     # Kustomize Kubernetes manifests
├── helm/smartbank-chart/    # Helm chart for full-stack deployment
├── prometheus/              # Prometheus scrape configuration
├── grafana/                 # Grafana dashboards & provisioning
├── scripts/                 # build-images.sh helper
├── .github/workflows/       # CI/CD pipelines
├── docs/                    # Extended documentation
├── Dockerfile               # Multi-stage build (all services via ARG)
├── build.gradle             # Root Gradle configuration
├── settings.gradle          # Multi-project Gradle setup
└── .env.example             # Environment variable template
```
