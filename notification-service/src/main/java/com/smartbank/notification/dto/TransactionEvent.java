package com.smartbank.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionEvent {
    private String referenceNumber;
    private String sourceAccount;
    private String targetAccount;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String transactionType;
    private LocalDateTime timestamp;
}
