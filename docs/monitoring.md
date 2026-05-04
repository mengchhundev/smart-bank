# Monitoring & Observability

SmartBank ships with a three-pillar observability stack: **Prometheus** (metrics), **Grafana** (dashboards), and **Zipkin** (distributed traces).

---

## Metrics — Prometheus

**Config:** `prometheus/prometheus.yml`  
**Default port:** 9090

All services expose a Prometheus-compatible metrics endpoint via Spring Boot Actuator:

```
GET /actuator/prometheus
```

Prometheus scrapes every **15 seconds**.

### Scrape Targets

| Service | Address |
|---|---|
| api-gateway | `api-gateway:8080` |
| auth-service | `auth-service:8081` |
| account-service | `account-service:8082` |
| transaction-service | `transaction-service:8083` |
| notification-service | `notification-service:8085` |
| discovery-server | `discovery-server:8761` |
| config-server | `config-server:8888` |

### Key Metrics to Watch

| Metric | Description |
|---|---|
| `http_server_requests_seconds` | Request latency per endpoint |
| `resilience4j_circuitbreaker_state` | Circuit breaker open/closed/half-open |
| `resilience4j_ratelimiter_available_permissions` | Remaining rate-limit capacity |
| `jvm_memory_used_bytes` | JVM heap and non-heap usage |
| `hikaricp_connections_active` | Active DB connection pool usage |
| `spring_kafka_consumer_records_consumed_total` | Kafka consumer throughput |
| `process_cpu_usage` | CPU utilization per service |

---

## Dashboards — Grafana

**Config:** `grafana/`  
**Default port:** 3000  
**Admin credentials:** `admin` / `${GRAFANA_PASSWORD}` (set in `.env`)

### Pre-provisioned Dashboard

`grafana/dashboards/smartbank.json` is automatically loaded on startup via the provisioning configuration in `grafana/provisioning/`.

The dashboard includes panels for:

- Request rate and error rate per service
- P99 response latency
- JVM memory and GC metrics
- Circuit breaker state transitions
- Kafka consumer lag
- Database connection pool utilization

### Datasource

Grafana is pre-configured with a Prometheus datasource pointing to `http://prometheus:9090`.

### Access (local port-forward)

```bash
kubectl -n smartbank port-forward svc/grafana 3000:3000
open http://localhost:3000
```

---

## Distributed Tracing — Zipkin

**Port:** 9411  
**Kubernetes manifest:** `k8s/09-zipkin.yaml`

All services export traces to Zipkin using **Micrometer Tracing** with the **Brave** bridge.

### Sampling

```yaml
management.tracing.sampling.probability: 1.0   # 100% — good for development
```

Reduce to `0.1` (10%) in production to limit overhead.

### What Gets Traced

- Incoming HTTP requests (via Spring Cloud Gateway)
- Feign client calls between services (Account ↔ Transaction)
- Kafka producer and consumer operations

### Viewing Traces

```bash
kubectl -n smartbank port-forward svc/zipkin 9411:9411
open http://localhost:9411
```

Filter by service name to follow a transaction across all hops:

```
client → api-gateway → transaction-service → account-service → notification-service (async)
```

---

## Health Checks — Actuator Endpoints

All services expose the following Actuator endpoints:

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Health status with component details |
| `GET /actuator/prometheus` | Prometheus metrics |
| `GET /actuator/info` | Application name, version, git commit |
| `GET /actuator/flyway` | Database migration status and history |
| `GET /actuator/metrics` | All available metric names |

### Gateway-specific endpoints

| Endpoint | Description |
|---|---|
| `GET /actuator/gateway/routes` | Active route definitions |
| `GET /actuator/circuitbreakers` | Circuit breaker states |
| `GET /actuator/ratelimiters` | Rate limiter statistics |

### Kubernetes Probes

Helm templates configure liveness and readiness probes against `/actuator/health`:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: <service-port>
  initialDelaySeconds: 60
  periodSeconds: 30

readinessProbe:
  httpGet:
    path: /actuator/health
    port: <service-port>
  initialDelaySeconds: 30
  periodSeconds: 10
```

---

## Logging

All services use **Logstash Logback Encoder** to emit structured JSON logs.

Log format fields: `timestamp`, `level`, `service`, `traceId`, `spanId`, `message`, `exception`.

The `traceId` field correlates log lines with Zipkin traces — paste the trace ID into Zipkin search to jump directly to the distributed trace.

### Viewing Logs (Kubernetes)

```bash
# Stream logs from a single pod
kubectl -n smartbank logs -f deployment/transaction-service

# Follow logs from all replicas of a service
kubectl -n smartbank logs -f -l app=transaction-service

# Filter for errors
kubectl -n smartbank logs deployment/api-gateway | jq 'select(.level == "ERROR")'
```

---

## Alerting (Recommended Setup)

The project does not ship with pre-built alerting rules. Recommended rules to add in Prometheus:

```yaml
groups:
  - name: smartbank
    rules:
      - alert: ServiceDown
        expr: up{job=~"smartbank.*"} == 0
        for: 1m

      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 2m

      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state{state="open"} == 1
        for: 30s

      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.85
        for: 5m
```

Add these to `prometheus/prometheus.yml` under `rule_files` and create a corresponding `alertmanager.yml` for notification routing (email, Slack, PagerDuty).
