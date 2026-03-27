package com.smartbank.transaction.dto;

import com.smartbank.transaction.entity.TransactionStatus;
import com.smartbank.transaction.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionDto {
    private Long id;
    private String referenceNumber;
    private String sourceAccount;
    private String targetAccount;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private TransactionType transactionType;
    private String description;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
