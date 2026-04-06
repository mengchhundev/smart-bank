package com.smartbank.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionEvent {

    /**
     * Saga event type:
     * <ul>
     *   <li>{@code TRANSACTION_SUCCESS} — transfer completed; downstream services may proceed.</li>
     *   <li>{@code TRANSACTION_FAILED}  — transfer failed; compensation already applied.</li>
     * </ul>
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
}
