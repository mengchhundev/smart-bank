package com.smartbank.notification.service;

import com.smartbank.notification.dto.TransactionEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock NotificationService notificationService;

    @InjectMocks TransactionEventConsumer consumer;

    // ── Main consumer ─────────────────────────────────────────────────────────

    @Test
    void consume_validEvent_delegatesToNotificationService() {
        TransactionEvent event = validSuccessEvent();

        consumer.consume(event);

        verify(notificationService).processTransactionEvent(event);
    }

    @Test
    void consume_failedEvent_delegatesToNotificationService() {
        TransactionEvent event = validFailedEvent();

        consumer.consume(event);

        verify(notificationService).processTransactionEvent(event);
    }

    @Test
    void consume_nullEvent_doesNotDelegateToNotificationService() {
        consumer.consume(null);

        verifyNoInteractions(notificationService);
    }

    // ── DLT consumer ──────────────────────────────────────────────────────────

    @Test
    void consumeDlt_anyRecord_doesNotThrow() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "transaction-events.DLT", 0, 42L, "TXN001", "poison-payload"
        );

        // Must not throw — DLT handler only logs
        consumer.consumeDlt(record);

        verifyNoInteractions(notificationService);
    }

    @Test
    void consumeDlt_nullValue_doesNotThrow() {
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "transaction-events.DLT", 0, 1L, "key", null
        );

        consumer.consumeDlt(record);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private TransactionEvent validSuccessEvent() {
        return TransactionEvent.builder()
                .eventType("TRANSACTION_SUCCESS")
                .referenceNumber("TXN001")
                .sourceAccount("ACC001")
                .targetAccount("ACC002")
                .amount(new BigDecimal("500"))
                .currency("USD")
                .status("COMPLETED")
                .timestamp(LocalDateTime.now())
                .build();
    }

    private TransactionEvent validFailedEvent() {
        return TransactionEvent.builder()
                .eventType("TRANSACTION_FAILED")
                .referenceNumber("TXN002")
                .sourceAccount("ACC001")
                .targetAccount("ACC002")
                .amount(new BigDecimal("500"))
                .currency("USD")
                .status("FAILED")
                .failureReason("Insufficient funds")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
