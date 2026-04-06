package com.smartbank.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_number", nullable = false, unique = true, length = 20)
    private String referenceNumber;

    @Column(name = "source_account", nullable = false, length = 50)
    private String sourceAccount;

    @Column(name = "target_account", nullable = false, length = 50)
    private String targetAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(length = 500)
    private String description;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    /** Stores the X-Idempotency-Key header value for deduplication. */
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
