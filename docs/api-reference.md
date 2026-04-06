# API Reference

All requests go through the API Gateway at `http://localhost:8080`.  
Protected endpoints require `Authorization: Bearer <access_token>`.

---

## Auth Service `/api/v1/auth`

### Register
```
POST /api/v1/auth/register
```
**Body:**
```json
{
  "username": "john",
  "email": "john@example.com",
  "password": "secret123",
  "fullName": "John Doe",
  "phone": "+1234567890"
}
```
**Response 201:**
```json
{
  "access_token": "eyJhbGc...",
  "refresh_token": "550e8400-e29b...",
  "token_type": "Bearer",
  "expires_in": 900,
  "username": "john",
  "role": "CUSTOMER"
}
```
**Errors:** `409 Conflict` — username or email already taken

---

### Login
```
POST /api/v1/auth/login
```
**Body:**
```json
{
  "username": "john",
  "password": "secret123"
}
```
**Response 200:** same as Register  
**Errors:** `401 Unauthorized` — invalid credentials

---

### Refresh Token
```
POST /api/v1/auth/refresh
```
**Body:**
```json
{
  "refreshToken": "550e8400-e29b..."
}
```
**Response 200:** new `AuthResponse` with fresh access token  
**Errors:** `401` — token expired or revoked

---

### Logout
```
POST /api/v1/auth/logout
```
**Body:**
```json
{
  "refreshToken": "550e8400-e29b..."
}
```
**Response 204:** no content

---

### Validate Token _(internal)_
```
GET /api/v1/auth/validate
Authorization: Bearer <token>
```
**Response 200:**
```json
{
  "userId": 1,
  "username": "john",
  "role": "CUSTOMER"
}
```

---

## Account Service `/api/v1/accounts`

All endpoints require a valid JWT.

### Create Account
```
POST /api/v1/accounts
Authorization: Bearer <token>
```
**Body:**
```json
{
  "accountHolderName": "John Doe",
  "accountType": "SAVINGS",
  "currency": "USD"
}
```
`accountType`: `SAVINGS` | `CHECKING` | `FIXED_DEPOSIT` | `CURRENT`

**Response 201:**
```json
{
  "id": 1,
  "accountNumber": "SB12345678901234",
  "accountHolderName": "John Doe",
  "accountType": "SAVINGS",
  "balance": 0.0000,
  "currency": "USD",
  "status": "ACTIVE",
  "userId": 1,
  "createdAt": "2024-04-03T10:00:00"
}
```

---

### Get Account by ID
```
GET /api/v1/accounts/{id}
Authorization: Bearer <token>
```
**Response 200:** `AccountDto`  
**Errors:** `404` — account not found

---

### Get My Accounts
```
GET /api/v1/accounts/my
Authorization: Bearer <token>
```
**Response 200:** `List<AccountDto>` for the authenticated user

---

### Update Account Status _(ADMIN only)_
```
PUT /api/v1/accounts/{id}/status
Authorization: Bearer <admin_token>
```
**Body:**
```json
{
  "status": "FROZEN"
}
```
`status`: `ACTIVE` | `INACTIVE` | `CLOSED` | `FROZEN`

**Response 200:** updated `AccountDto`  
**Errors:** `403` — caller is not ADMIN

---

### Internal: Get Account by Number
```
GET /api/v1/accounts/{accountNumber}
```
Used by `transaction-service` via Feign. Not intended for direct client use.

---

### Internal: Update Balance
```
PUT /api/v1/accounts/{accountNumber}/balance
```
**Body:**
```json
{
  "amount": 500.00,
  "type": "CREDIT"
}
```
`type`: `CREDIT` | `DEBIT`

Used by `transaction-service` via Feign. Not intended for direct client use.

---

## Transaction Service `/api/v1/transactions`

### Transfer Funds
```
POST /api/v1/transactions/transfer
Authorization: Bearer <token>
```
**Body:**
```json
{
  "sourceAccount": "SB12345678901234",
  "targetAccount": "SB98765432109876",
  "amount": 250.00,
  "currency": "USD",
  "description": "Rent payment"
}
```
**Response 201:**
```json
{
  "id": 1,
  "referenceNumber": "TXN123456789012",
  "sourceAccount": "SB12345678901234",
  "targetAccount": "SB98765432109876",
  "amount": 250.0000,
  "currency": "USD",
  "status": "COMPLETED",
  "transactionType": "TRANSFER",
  "description": "Rent payment",
  "failureReason": null,
  "createdAt": "2024-04-03T10:00:00",
  "completedAt": "2024-04-03T10:00:01"
}
```
**Errors:** `400` — insufficient balance, invalid account

---

### Get Transaction by Reference
```
GET /api/v1/transactions/{referenceNumber}
Authorization: Bearer <token>
```
**Response 200:** `TransactionDto`  
**Errors:** `404` — not found

---

### Get Transactions by Account
```
GET /api/v1/transactions/account/{accountNumber}?page=0&size=20&sort=createdAt,desc
Authorization: Bearer <token>
```
**Response 200:** `Page<TransactionDto>`

---

## Notification Service `/api/v1/notifications`

### Get Notifications by Recipient
```
GET /api/v1/notifications/recipient/{recipient}
Authorization: Bearer <token>
```
**Response 200:**
```json
[
  {
    "id": 1,
    "recipient": "john@example.com",
    "subject": "Transaction Notification",
    "message": "...",
    "type": "EMAIL",
    "status": "SENT",
    "referenceNumber": "TXN123456789012",
    "createdAt": "2024-04-03T10:00:01",
    "sentAt": "2024-04-03T10:00:02"
  }
]
```

---

## Common Error Response

```json
{
  "timestamp": "2024-04-03T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient balance in account: SB12345678901234"
}
```

Validation errors (auth-service) include an additional `validationErrors` map:
```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "validationErrors": {
    "email": "must be a well-formed email address"
  }
}
```

---

## OpenAPI / Swagger

Each service exposes Swagger UI at `/swagger-ui.html` and the OpenAPI spec at `/v3/api-docs`.

| Service             | Swagger UI                               |
|---------------------|------------------------------------------|
| auth-service        | http://localhost:8081/swagger-ui.html    |
| account-service     | http://localhost:8082/swagger-ui.html    |
| transaction-service | http://localhost:8083/swagger-ui.html    |
| notification-service| http://localhost:8085/swagger-ui.html    |
