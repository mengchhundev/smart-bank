# ════════════════════════════════════════════════════════════════
# SmartBank – parameterised multi-stage Dockerfile
#
# Build args:
#   SERVICE_NAME  – subproject name (e.g. auth-service)   [required]
#   SERVICE_PORT  – port the service listens on            [default: 8080]
#
# Example:
#   docker build --build-arg SERVICE_NAME=auth-service \
#                --build-arg SERVICE_PORT=8081 \
#                -t smartbank/auth-service:latest .
# ════════════════════════════════════════════════════════════════

# ── Stage 1: Dependency cache ────────────────────────────────────
# Separate layer so `gradle dependencies` is only re-run when
# the wrapper / build scripts change, not on every source edit.
FROM eclipse-temurin:17-jdk-alpine AS deps
WORKDIR /workspace

COPY gradle/            gradle/
COPY gradlew            gradlew
COPY gradlew.bat        gradlew.bat
COPY build.gradle       build.gradle
COPY settings.gradle    settings.gradle
COPY gradle.properties  gradle.properties

# Sub-project build scripts (no source yet)
COPY config-server/build.gradle       config-server/build.gradle
COPY discovery-server/build.gradle    discovery-server/build.gradle
COPY api-gateway/build.gradle         api-gateway/build.gradle
COPY auth-service/build.gradle        auth-service/build.gradle
COPY account-service/build.gradle     account-service/build.gradle
COPY transaction-service/build.gradle transaction-service/build.gradle
COPY notification-service/build.gradle notification-service/build.gradle

RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon --quiet 2>/dev/null || true

# ── Stage 2: Build ───────────────────────────────────────────────
FROM deps AS builder

ARG SERVICE_NAME
COPY . .

RUN ./gradlew ${SERVICE_NAME}:bootJar -x test --no-daemon && \
    # Rename to a version-independent filename for easy COPY below
    find ${SERVICE_NAME}/build/libs -name "*.jar" ! -name "*-plain.jar" \
         -exec cp {} /app.jar \;

# ── Stage 3: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S smartbank && adduser -S smartbank -G smartbank

WORKDIR /app

ARG SERVICE_PORT=8080
ENV SERVER_PORT=${SERVICE_PORT}
ENV SPRING_PROFILES_ACTIVE=prod

COPY --from=builder --chown=smartbank:smartbank /app.jar app.jar

USER smartbank

EXPOSE ${SERVICE_PORT}

# Container-aware JVM flags:
#   UseContainerSupport  – honour cgroup CPU/memory limits
#   MaxRAMPercentage     – use 75 % of container RAM for the heap
#   TieredStopAtLevel=1 – faster startup (skip C2 JIT warmup)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:TieredStopAtLevel=1", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider \
      http://localhost:${SERVER_PORT}/actuator/health || exit 1
