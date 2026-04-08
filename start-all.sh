#!/usr/bin/env bash
# =============================================================
#  SmartBank — Start All Services
#  Usage: bash start-all.sh
#  Requires: Docker, Java 17+, curl, Git Bash (Windows)
# =============================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOGS="$ROOT/logs"
mkdir -p "$LOGS"

# Windows path for PowerShell (e.g. D:/smart-banking)
WIN_ROOT="$(cd "$ROOT" && pwd -W)"

# ── Colours ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; MAGENTA='\033[0;35m'; WHITE='\033[1;37m'; NC='\033[0m'

# ── wait_for_health <name> <url> [timeout_sec] ─────────────────────────────────
wait_for_health() {
  local name="$1" url="$2" timeout="${3:-60}"
  local deadline=$(( $(date +%s) + timeout ))

  echo -e "${YELLOW}  Waiting for $name to be healthy...${NC}"
  while [[ $(date +%s) -lt $deadline ]]; do
    if curl -sf --max-time 2 "$url" 2>/dev/null | grep -q '"status":"UP"'; then
      echo -e "${GREEN}  $name is UP${NC}"
      return 0
    fi
    sleep 3
  done
  echo -e "${RED}  WARNING: $name did not become healthy within ${timeout}s — continuing anyway${NC}"
}

# ── start_service <name> ───────────────────────────────────────────────────────
# Opens a new PowerShell window for each service (same behaviour as start-all.ps1)
start_service() {
  local name="$1"
  echo -e "${CYAN}>> Starting $name${NC}"
  powershell.exe -Command "
    Start-Process powershell -ArgumentList \`
      '-NoExit', \`
      '-Command', \`
      \"cd '$WIN_ROOT'; Write-Host '[$name]' -ForegroundColor Cyan; ./gradlew :$name:bootRun\"
  "
}

# ==============================================================================
#  Step 1: Infrastructure
# ==============================================================================
echo ""
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}  SmartBank — Starting Infrastructure${NC}"
echo -e "${MAGENTA}============================================${NC}"

cd "$ROOT" || exit 1
docker compose up -d

echo -e "${YELLOW}  Waiting 10s for Docker services to stabilise...${NC}"
sleep 10

# ==============================================================================
#  Step 2: Config Server
# ==============================================================================
echo ""
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}  Step 1/4 — Config Server${NC}"
echo -e "${MAGENTA}============================================${NC}"

start_service "config-server"
wait_for_health "config-server" "http://localhost:8888/actuator/health" 60

# ==============================================================================
#  Step 3: Discovery Server
# ==============================================================================
echo ""
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}  Step 2/4 — Discovery Server (Eureka)${NC}"
echo -e "${MAGENTA}============================================${NC}"

start_service "discovery-server"
wait_for_health "discovery-server" "http://localhost:8761/actuator/health" 60

# ==============================================================================
#  Step 4: Core Services
# ==============================================================================
echo ""
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}  Step 3/4 — Core Services${NC}"
echo -e "${MAGENTA}============================================${NC}"

for svc in auth-service account-service transaction-service notification-service; do
  start_service "$svc"
  sleep 3
done

wait_for_health "auth-service"         "http://localhost:8081/actuator/health" 90
wait_for_health "account-service"      "http://localhost:8082/actuator/health" 90
wait_for_health "transaction-service"  "http://localhost:8083/actuator/health" 90
wait_for_health "notification-service" "http://localhost:8085/actuator/health" 90

# ==============================================================================
#  Step 5: API Gateway
# ==============================================================================
echo ""
echo -e "${MAGENTA}============================================${NC}"
echo -e "${MAGENTA}  Step 4/4 — API Gateway${NC}"
echo -e "${MAGENTA}============================================${NC}"

echo -e "${YELLOW}  Waiting 10s for services to register in Eureka...${NC}"
sleep 10

start_service "api-gateway"
wait_for_health "api-gateway" "http://localhost:8080/actuator/health" 60

# ==============================================================================
#  Done
# ==============================================================================
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  SmartBank is UP${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo -e "${WHITE}  API Gateway:   http://localhost:8080${NC}"
echo -e "${WHITE}  Swagger UI:    http://localhost:8080/swagger-ui.html${NC}"
echo -e "${WHITE}  Eureka:        http://localhost:8761${NC}"
echo -e "${WHITE}  MailHog:       http://localhost:8025${NC}"
echo -e "${WHITE}  Zipkin:        http://localhost:9411${NC}"
echo -e "${WHITE}  Prometheus:    http://localhost:9090${NC}"
echo -e "${WHITE}  Grafana:       http://localhost:3000  (admin / admin)${NC}"
echo ""
