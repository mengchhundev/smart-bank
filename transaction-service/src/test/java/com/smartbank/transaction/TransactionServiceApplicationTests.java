package com.smartbank.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full context load test — uses Testcontainers PostgreSQL (via JDBC URL) and
 * embedded Kafka so no external infrastructure is required.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"transaction-events"})
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=",
        "eureka.client.enabled=false"
})
class TransactionServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full Spring context starts without errors
    }
}
