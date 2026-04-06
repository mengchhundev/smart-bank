# Transaction Service

**Port:** 8083  
**Module:** `transaction-service`  
**Package:** `com.smartbank.transaction`  
**Database:** `transaction_db` (Hibernate DDL auto)

## Purpose

Orchestrates fund transfers between accounts. Publishes `TransactionEvent` to Kafka on completion or failure.

## Entity: Transaction

| Column            | Type                                                        | Notes                           |
|-------------------|-------------------------------------------------------------|---------------------------------|
| id                | BIGINT PK                                                   |                                 |
| reference_number  | VARCHAR UNIQUE                                              | Format: `TXN` + 12 random chars |
| source_account    | VARCHAR                                                     |                                 |
| target_account    | VARCHAR                                                     |                                 |
| amount            | NUMERIC(19,4)                                               |                                 |
| currency          | VARCHAR(3)                                                  | Default: USD                    |
| status            | ENUM(PENDING, PROCESSING, COMPLETED, FAILED, REVERSED)      | Default: PENDING                |
| transaction_type  | ENUM(TRANSFER, DEPOSIT, WITHDRAWAL)                         |                                 |
| description       | VARCHAR                                                     |                                 |
| failure_reason    | VARCHAR                                                     | Populated on FAILED             |
| created_at        | TIMESTAMP                                                   |                                 |
| completed_at      | TIMESTAMP                                                   |                                 |

## Transfer Orchestration Flow

```
TransactionService.transfer()
  1. Create Transaction record (status=PROCESSING)
  2. AccountClient.updateBalance(sourceAccount, DEBIT)    ← Feign
  3. AccountClient.updateBalance(targetAccount, CREDIT)   ← Feign
  4. Update status → COMPLETED, set completedAt
  5. Publish TransactionEvent (status=COMPLETED)          ← Kafka
  
  On any exception:
  → Update status → FAILED, set failureReason
  → Publish TransactionEvent (status=FAILED)              ← Kafka
```

**Note:** No distributed transaction (saga/2PC). A failure after step 2 but before step 3 will leave source debited but target not credited. Compensating transaction (REVERSED status) needs to be implemented for production.

## Feign Client: AccountClient

Target: `lb://account-service`

| Method | Path                                      | Description        |
|--------|-------------------------------------------|--------------------|
| GET    | `/api/v1/accounts/{accountNumber}`        | Verify account     |
| PUT    | `/api/v1/accounts/{accountNumber}/balance`| Debit/credit       |

## Kafka Producer: TransactionEventProducer

- Topic: `transaction-events`
- Key: `referenceNumber`
- Value: `TransactionEvent` (JSON serialized)

```java
TransactionEvent {
  referenceNumber, sourceAccount, targetAccount,
  amount, currency, status, transactionType, timestamp
}
```

## API Endpoints

| Method | Path                                           | Description                  |
|--------|------------------------------------------------|------------------------------|
| POST   | `/api/v1/transactions/transfer`                | Initiate fund transfer       |
| GET    | `/api/v1/transactions/{referenceNumber}`       | Get by reference             |
| GET    | `/api/v1/transactions/account/{accountNumber}` | Paginated account history    |

## Kafka Configuration

```yaml
bootstrap-servers: localhost:29092
key-serializer: StringSerializer
value-serializer: JsonSerializer
```

## Key Dependencies

- `spring-cloud-starter-openfeign`
- `spring-kafka`
- `spring-boot-starter-data-jpa`
- `spring-cloud-starter-netflix-eureka-client`
