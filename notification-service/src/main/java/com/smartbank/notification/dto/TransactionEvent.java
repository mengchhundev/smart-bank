package com.smartbank.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionEvent {

    /**
     * Saga event type published by transaction-service:
     * {@code TRANSACTION_SUCCESS} or {@code TRANSACTION_FAILED}.
     */
    private String eventType;

    private String referenceNumber;
    private String sourceAccount;
    private String targetAccount;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String transactionType;
    private String failureReason;
    private LocalDateTime timestamp;

    // ── Convenience predicates ─────────────────────────────────────────────────

    public boolean isSuccess() {
        return "TRANSACTION_SUCCESS".equalsIgnoreCase(eventType);
    }

    public boolean isFailed() {
        return "TRANSACTION_FAILED".equalsIgnoreCase(eventType);
    }
}
