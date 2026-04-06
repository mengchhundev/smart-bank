# Discovery Server

**Port:** 8761  
**Module:** `discovery-server`  
**Package:** `com.smartbank.discovery`

## Purpose

Eureka Server for service registration and discovery. All microservices register here and use `lb://` load-balanced URIs for inter-service calls.

## Key Details

- Security: HTTP Basic (`eureka / eureka123`)
- Self-registration: disabled
- Fetch-registry: disabled (server doesn't need its own registry)
- Bootstraps config from Config Server

## Dashboard

http://localhost:8761 — shows all registered services and their instance status.

## Startup Dependency

Must start **after** Config Server, **before** all business services.

## Dependencies

- `spring-cloud-starter-netflix-eureka-server`
- `spring-boot-starter-security`
- `spring-cloud-starter-config`
