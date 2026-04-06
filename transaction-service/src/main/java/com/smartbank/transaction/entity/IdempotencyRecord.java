package com.smartbank.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persists idempotency keys so that duplicate requests with the same
 * X-Idempotency-Key return the original transaction without re-processing.
 */
@Entity
@Table(name = "idempotency_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "reference_number", nullable = false, length = 20)
    private String referenceNumber;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
