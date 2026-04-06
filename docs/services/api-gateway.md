# API Gateway

**Port:** 8080  
**Module:** `api-gateway`  
**Package:** `com.smartbank.gateway`

## Purpose

Single entry point for all client traffic. Handles JWT validation, CORS, and request routing to downstream services.

## Routing Table

| Path Prefix                    | Target Service       | Load Balanced |
|--------------------------------|----------------------|---------------|
| `/api/v1/auth/**`              | auth-service         | Yes           |
| `/api/v1/accounts/**`          | account-service      | Yes           |
| `/api/v1/transactions/**`      | transaction-service  | Yes           |
| `/api/v1/notifications/**`     | notification-service | Yes           |

## JWT Authentication Filter

`JwtAuthenticationFilter` — global filter applied to all routes.

**Open (no auth required):**
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `/actuator/**`

**For authenticated requests:**
1. Extracts `Authorization: Bearer <token>`
2. Validates signature and expiry using the shared JWT secret
3. Injects downstream headers:
   - `X-User-Id`: user's ID from JWT claims
   - `X-User-Roles`: comma-separated roles

## CORS

Configured to allow all origins (`*`). Tighten for production.

## Circuit Breaker

Resilience4j circuit breaker configured via Spring Cloud Gateway. Handles downstream failures gracefully.

## Key Dependencies

- `spring-cloud-starter-gateway`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-cloud-starter-circuitbreaker-resilience4j`
- `spring-boot-starter-oauth2-resource-server`
- `io.jsonwebtoken:jjwt-api:0.12.5`
