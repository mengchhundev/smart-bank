package com.smartbank.transaction.service;

import com.smartbank.transaction.dto.TransactionEvent;
import com.smartbank.transaction.entity.Transaction;
import com.smartbank.transaction.entity.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventProducer {

    static final String TOPIC = "transaction-events";

    static final String EVENT_SUCCESS = "TRANSACTION_SUCCESS";
    static final String EVENT_FAILED  = "TRANSACTION_FAILED";

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    /**
     * Publishes a typed saga event. Key = referenceNumber for partition ordering.
     */
    public void publishEvent(Transaction transaction) {
        String eventType = transaction.getStatus() == TransactionStatus.COMPLETED
                ? EVENT_SUCCESS : EVENT_FAILED;

        TransactionEvent event = TransactionEvent.builder()
                .eventType(eventType)
                .referenceNumber(transaction.getReferenceNumber())
                .sourceAccount(transaction.getSourceAccount())
                .targetAccount(transaction.getTargetAccount())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus().name())
                .transactionType(transaction.getTransactionType().name())
                .failureReason(transaction.getFailureReason())
                .timestamp(LocalDateTime.now())
                .build();

        kafkaTemplate.send(TOPIC, event.getReferenceNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for {}: {}",
                                eventType, transaction.getReferenceNumber(), ex.getMessage());
                    } else {
                        log.info("Published {} for {} (partition={}, offset={})",
                                eventType, transaction.getReferenceNumber(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /** Kept for backwards-compatibility with any direct callers. */
    public void sendTransactionEvent(TransactionEvent event) {
        kafkaTemplate.send(TOPIC, event.getReferenceNumber(), event);
        log.info("Transaction event published: {}", event.getReferenceNumber());
    }
}
