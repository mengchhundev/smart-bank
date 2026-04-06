# Auth Service

**Port:** 8081  
**Module:** `auth-service`  
**Package:** `com.smartbank.auth`  
**Database:** `auth_db`

## Purpose

Handles user registration, authentication, JWT issuance, token refresh, and logout. Acts as the identity authority for the platform.

## Entities

### User
| Column       | Type                       | Notes                        |
|--------------|----------------------------|------------------------------|
| id           | BIGINT PK                  | Auto-increment               |
| username     | VARCHAR(50) UNIQUE         |                              |
| email        | VARCHAR(100) UNIQUE        |                              |
| password     | VARCHAR(255)               | BCrypt strength 12           |
| full_name    | VARCHAR(100)               |                              |
| phone        | VARCHAR(20)                |                              |
| role         | ENUM(CUSTOMER, ADMIN)      | Default: CUSTOMER            |
| enabled      | BOOLEAN                    | Default: TRUE                |
| created_at   | TIMESTAMP                  |                              |
| updated_at   | TIMESTAMP                  |                              |

### RefreshToken
| Column       | Type          | Notes                              |
|--------------|---------------|------------------------------------|
| id           | BIGINT PK     |                                    |
| token        | VARCHAR(255)  | UUID, UNIQUE                       |
| user_id      | BIGINT FK     | → users(id) ON DELETE CASCADE      |
| expiry_date  | TIMESTAMP     | Now + 7 days                       |
| revoked      | BOOLEAN       | Default: FALSE                     |
| created_at   | TIMESTAMP     |                                    |

## Services

### AuthService
- `register(RegisterRequest)` — creates user with CUSTOMER role, returns token pair
- `login(LoginRequest)` — authenticates via Spring Security, returns token pair
- `refresh(RefreshTokenRequest)` — verifies refresh token, issues new access token
- `logout(LogoutRequest)` — revokes refresh token

### JwtService
- Generates HS256 access tokens with claims: `userId`, `role`
- Validates signature and expiry
- Access token TTL: **15 minutes**

### RefreshTokenService
- On `createRefreshToken()`: revokes any previous active tokens for the user before creating a new one
- `verifyRefreshToken()`: checks `revoked` flag and `expiry_date`

## Database Migrations (Flyway)

```
V1__create_users_table.sql
V2__create_refresh_tokens_table.sql
```

## Security Configuration

- BCrypt password encoder (strength 12)
- Stateless session management
- Open endpoints: `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`
- JWT filter before `UsernamePasswordAuthenticationFilter`

## API Endpoints

| Method | Path                  | Auth     | Description            |
|--------|-----------------------|----------|------------------------|
| POST   | `/api/auth/register`  | None     | Create account         |
| POST   | `/api/auth/login`     | None     | Authenticate           |
| POST   | `/api/auth/refresh`   | None     | Refresh access token   |
| POST   | `/api/auth/logout`    | Bearer   | Revoke refresh token   |
| GET    | `/api/auth/validate`  | Bearer   | Validate token (internal) |

## Key Dependencies

- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-cloud-starter-netflix-eureka-client`
- `io.jsonwebtoken:jjwt-api:0.12.5`
- `org.flywaydb:flyway-core`
- `springdoc-openapi-starter-webmvc-ui`
