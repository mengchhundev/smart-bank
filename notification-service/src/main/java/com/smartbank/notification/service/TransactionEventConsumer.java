package com.smartbank.notification.service;

import com.smartbank.notification.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;

    // ── Main consumer ─────────────────────────────────────────────────────────

    @KafkaListener(
            topics = "transaction-events",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(TransactionEvent event) {
        if (event == null) {
            log.warn("Received null event on transaction-events — skipping");
            return;
        }
        log.info("Received event type={} reference={}",
                event.getEventType(), event.getReferenceNumber());
        notificationService.processTransactionEvent(event);
    }

    // ── Dead Letter Topic consumer ────────────────────────────────────────────
    //
    // Messages land here when:
    //   - Deserialization fails (poison pill / schema mismatch)
    //   - processTransactionEvent throws after Resilience4j retries are exhausted
    //     (e.g. DB unavailable)
    //
    // Action: log for manual inspection / replay tooling. Extend with alerting
    // (PagerDuty, Slack) for production.

    @KafkaListener(
            topics = "transaction-events.DLT",
            groupId = "notification-dlt-group"
    )
    public void consumeDlt(ConsumerRecord<String, Object> record) {
        log.error("DLT message received — topic={} partition={} offset={} key={} payload={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value());
    }
}
