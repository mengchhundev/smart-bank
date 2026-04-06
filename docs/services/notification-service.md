# Notification Service

**Port:** 8085  
**Module:** `notification-service`  
**Package:** `com.smartbank.notification`  
**Database:** `notification_db` (Hibernate DDL auto)

## Purpose

Consumes `TransactionEvent` messages from Kafka and sends HTML email notifications via MailHog (dev) or a real SMTP server (prod). Persists every notification attempt.

## Entity: Notification

| Column            | Type                           | Notes                          |
|-------------------|--------------------------------|--------------------------------|
| id                | BIGINT PK                      |                                |
| recipient         | VARCHAR                        | Email address                  |
| subject           | VARCHAR                        |                                |
| message           | TEXT                           |                                |
| notification_type | ENUM(EMAIL, SMS)               |                                |
| status            | ENUM(PENDING, SENT, FAILED)    | Default: PENDING               |
| reference_number  | VARCHAR                        | Links to transaction           |
| failure_reason    | VARCHAR                        | Populated on FAILED            |
| created_at        | TIMESTAMP                      |                                |
| sent_at           | TIMESTAMP                      |                                |

## Kafka Consumer: TransactionEventConsumer

- Topic: `transaction-events`
- Consumer group: `notification-group`
- Uses `ErrorHandlingDeserializer` wrapping `JsonDeserializer`
- Trusted packages: `com.smartbank.*`

On message receipt → calls `NotificationService.processTransactionEvent()`.

## Processing Flow

```
TransactionEventConsumer.consume(TransactionEvent)
  → NotificationService.processTransactionEvent()
    1. Build Notification entity (status=PENDING)
    2. EmailService.sendEmail() → JavaMailSender + Thymeleaf template
    3. On success: status=SENT, sentAt=now()
    4. On failure: status=FAILED, failureReason=exception message
    5. Persist Notification to database
```

## EmailService

- Uses `JavaMailSender` and Thymeleaf `SpringTemplateEngine`
- Template: `transaction-notification` (HTML)
- Template location: `classpath:/templates/`

## Mail Configuration (dev)

```yaml
host: localhost
port: 1025         # MailHog SMTP
auth: false
from: noreply@smartbank.com
```

View sent emails at http://localhost:8025.

## API Endpoints

| Method | Path                                          | Description                     |
|--------|-----------------------------------------------|---------------------------------|
| GET    | `/api/v1/notifications/recipient/{recipient}` | Get notifications by recipient  |

## Key Dependencies

- `spring-kafka`
- `spring-boot-starter-mail`
- `spring-boot-starter-thymeleaf`
- `spring-boot-starter-data-jpa`
- `spring-cloud-starter-netflix-eureka-client`
