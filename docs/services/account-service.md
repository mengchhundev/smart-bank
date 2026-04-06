# Account Service

**Port:** 8082  
**Module:** `account-service`  
**Package:** `com.smartbank.account`  
**Database:** `account_db`

## Purpose

Manages bank accounts. Exposes public APIs for account lifecycle operations and internal APIs consumed by `transaction-service` for balance updates.

## Entity: Account

| Column               | Type                                           | Notes                          |
|----------------------|------------------------------------------------|--------------------------------|
| id                   | BIGINT PK                                      |                                |
| account_number       | VARCHAR(20) UNIQUE                             | Format: `SB` + 14 random chars |
| account_holder_name  | VARCHAR(100)                                   |                                |
| account_type         | ENUM(SAVINGS, CHECKING, FIXED_DEPOSIT, CURRENT)|                                |
| balance              | NUMERIC(19,4)                                  | Default: 0                     |
| currency             | VARCHAR(3)                                     | Default: USD                   |
| status               | ENUM(ACTIVE, INACTIVE, CLOSED, FROZEN)         | Default: ACTIVE                |
| user_id              | BIGINT                                         | Logical FK to auth_db.users    |
| version              | INTEGER                                        | Optimistic locking             |
| created_at           | TIMESTAMP                                      |                                |
| updated_at           | TIMESTAMP                                      |                                |

**Indexes:** `idx_accounts_user_id`, `idx_accounts_account_number`, `idx_accounts_status`

## Optimistic Locking

`version` field prevents concurrent balance updates from overwriting each other. On conflict, a `ObjectOptimisticLockingFailureException` is thrown — the `transaction-service` handles this by marking the transaction as FAILED.

## AccountService Methods

| Method              | Description                                         |
|---------------------|-----------------------------------------------------|
| `createAccount()`   | Generates account number, persists with ACTIVE status |
| `getAccountById()`  | Retrieves by PK                                     |
| `getAccountsByUserId()` | Returns all accounts for a user                 |
| `updateStatus()`    | ADMIN-only status change                            |
| `getAccountByNumber()` | Internal — lookup by account number             |
| `updateBalance()`   | CREDIT or DEBIT with balance validation             |

## Feign Client: AuthServiceClient

Calls `auth-service` `/api/auth/validate` to validate tokens for internal endpoints.

## Mapper

MapStruct `AccountMapper` handles `Account ↔ AccountDto` conversion. Implementation is compile-time generated.

## API Endpoints

### Public (JWT required)

| Method | Path                          | Role     | Description              |
|--------|-------------------------------|----------|--------------------------|
| POST   | `/api/accounts`               | Any      | Create account           |
| GET    | `/api/accounts/{id}`          | Any      | Get account by ID        |
| GET    | `/api/accounts/my`            | Any      | Get authenticated user's accounts |
| PUT    | `/api/accounts/{id}/status`   | ADMIN    | Update account status    |

### Internal (service-to-service)

| Method | Path                                      | Description              |
|--------|-------------------------------------------|--------------------------|
| GET    | `/api/v1/accounts/{accountNumber}`        | Get account by number    |
| GET    | `/api/v1/accounts/user/{userId}`          | Get accounts by user ID  |
| PUT    | `/api/v1/accounts/{accountNumber}/balance`| Debit or credit balance  |

## Database Migrations (Flyway)

```
V1__create_accounts_table.sql
```

## Security Configuration

- Open: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- `/api/accounts/{id}/status` — requires `ADMIN` role
- All other `/api/accounts/**` — requires authentication
- Internal `/api/v1/accounts/**` — requires authentication

## Key Dependencies

- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-cloud-starter-openfeign`
- `spring-cloud-starter-netflix-eureka-client`
- `org.mapstruct:mapstruct:1.5.5.Final`
- `org.flywaydb:flyway-core`
- `springdoc-openapi-starter-webmvc-ui`

## Tests

`AccountServiceTest` — unit tests with Mockito.  
Covers: create, retrieve, status update, balance CREDIT/DEBIT, insufficient balance guard, optimistic locking.
