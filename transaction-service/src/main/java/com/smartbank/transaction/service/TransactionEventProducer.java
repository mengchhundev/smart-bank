package com.smartbank.transaction.service;

import com.smartbank.transaction.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventProducer {

    private static final String TOPIC = "transaction-events";

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    public void sendTransactionEvent(TransactionEvent event) {
        kafkaTemplate.send(TOPIC, event.getReferenceNumber(), event);
        log.info("Transaction event published: {}", event.getReferenceNumber());
    }
}
