FROM eclipse-temurin:17-jdk-jammy AS build

ARG SERVICE_NAME

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle

COPY config-server/build.gradle ./config-server/build.gradle
COPY discovery-server/build.gradle ./discovery-server/build.gradle
COPY api-gateway/build.gradle ./api-gateway/build.gradle
COPY auth-service/build.gradle ./auth-service/build.gradle
COPY account-service/build.gradle ./account-service/build.gradle
COPY transaction-service/build.gradle ./transaction-service/build.gradle
COPY notification-service/build.gradle ./notification-service/build.gradle

RUN test -n "$SERVICE_NAME" || (echo "SERVICE_NAME build arg is required" && exit 1)
RUN chmod +x gradlew

COPY config-server/src ./config-server/src
COPY discovery-server/src ./discovery-server/src
COPY api-gateway/src ./api-gateway/src
COPY auth-service/src ./auth-service/src
COPY account-service/src ./account-service/src
COPY transaction-service/src ./transaction-service/src
COPY notification-service/src ./notification-service/src

RUN ./gradlew ":${SERVICE_NAME}:bootJar" --no-daemon
RUN find "/workspace/${SERVICE_NAME}/build/libs" -type f -name "*.jar" ! -name "*-plain.jar" -exec cp {} /workspace/app.jar \; \
    && test -f /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN useradd --system --uid 10001 --create-home smartbank

COPY --from=build /workspace/app.jar /app/app.jar

USER smartbank

EXPOSE 8080 8081 8082 8083 8085 8761 8888

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
