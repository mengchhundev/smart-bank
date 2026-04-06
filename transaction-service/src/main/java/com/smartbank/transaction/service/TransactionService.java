package com.smartbank.transaction.service;

import com.smartbank.transaction.client.AccountClient;
import com.smartbank.transaction.dto.*;
import com.smartbank.transaction.entity.IdempotencyRecord;
import com.smartbank.transaction.entity.Transaction;
import com.smartbank.transaction.entity.TransactionStatus;
import com.smartbank.transaction.entity.TransactionType;
import com.smartbank.transaction.exception.DuplicateIdempotencyKeyException;
import com.smartbank.transaction.exception.TransactionException;
import com.smartbank.transaction.exception.TransactionNotFoundException;
import com.smartbank.transaction.repository.IdempotencyRecordRepository;
import com.smartbank.transaction.repository.TransactionRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository       transactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AccountClient               accountClient;
    private final TransactionEventProducer    eventProducer;

    // ─── Transfer ─────────────────────────────────────────────────────────────

    /**
     * Fund transfer between two accounts.
     *
     * <p>Saga choreography:
     * <ol>
     *   <li>Debit source account via Feign → account-service</li>
     *   <li>Credit target account via Feign → account-service</li>
     *   <li>On credit failure: compensating CREDIT back to source, mark FAILED</li>
     *   <li>Publish {@code TransactionSuccessEvent} or {@code TransactionFailedEvent}</li>
     * </ol>
     *
     * @param request        transfer details
     * @param idempotencyKey value of X-Idempotency-Key header (may be {@code null})
     * @return the resulting transaction DTO
     */
    @Transactional
    public TransactionDto transfer(TransferRequest request, String idempotencyKey) {

        // ── Idempotency guard ──────────────────────────────────────────────────
        if (idempotencyKey != null) {
            return idempotencyRecordRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .map(record -> transactionRepository
                            .findByReferenceNumber(record.getReferenceNumber())
                            .map(this::toDto)
                            .orElseThrow(() -> new TransactionNotFoundException(
                                    "Idempotency record found but transaction missing: "
                                            + record.getReferenceNumber())))
                    .orElseGet(() -> processTransfer(request, idempotencyKey));
        }

        return processTransfer(request, null);
    }

    private TransactionDto processTransfer(TransferRequest request, String idempotencyKey) {

        if (request.getSourceAccount().equals(request.getTargetAccount())) {
            throw new TransactionException("Source and target accounts cannot be the same");
        }

        Transaction transaction = Transaction.builder()
                .referenceNumber(generateReference())
                .sourceAccount(request.getSourceAccount())
                .targetAccount(request.getTargetAccount())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .transactionType(TransactionType.TRANSFER)
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.PROCESSING)
                .build();

        transaction = transactionRepository.save(transaction);

        boolean debitApplied = false;

        try {
            // ── Step 1: Debit source ───────────────────────────────────────────
            accountClient.updateBalance(request.getSourceAccount(),
                    BalanceUpdateRequest.builder()
                            .amount(request.getAmount())
                            .type("DEBIT")
                            .build());
            debitApplied = true;

            // ── Step 2: Credit target ──────────────────────────────────────────
            accountClient.updateBalance(request.getTargetAccount(),
                    BalanceUpdateRequest.builder()
                            .amount(request.getAmount())
                            .type("CREDIT")
                            .build());

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            log.info("Transfer completed: {}", transaction.getReferenceNumber());

        } catch (FeignException.UnprocessableEntity | FeignException.BadRequest e) {
            // Business-rule failure (e.g. insufficient funds) — propagate immediately
            String msg = extractFeignMessage(e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(msg);
            log.warn("Transfer rejected ({}): {} — {}",
                    transaction.getReferenceNumber(), e.status(), msg);

        } catch (Exception e) {
            // Infrastructure or unexpected failure
            String reason = e.getMessage();
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(reason);
            log.error("Transfer failed ({}): {}", transaction.getReferenceNumber(), reason, e);

            // ── Saga compensation: reverse the debit if it was already applied ──
            if (debitApplied) {
                applyCompensation(transaction, request);
            }
        }

        transaction = transactionRepository.save(transaction);

        // ── Persist idempotency record ─────────────────────────────────────────
        if (idempotencyKey != null) {
            try {
                idempotencyRecordRepository.save(
                        IdempotencyRecord.builder()
                                .idempotencyKey(idempotencyKey)
                                .referenceNumber(transaction.getReferenceNumber())
                                .build());
            } catch (DataIntegrityViolationException ex) {
                // Concurrent duplicate request — return the first-stored result
                log.warn("Idempotency key race condition on key={}", idempotencyKey);
                return idempotencyRecordRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .flatMap(r -> transactionRepository.findByReferenceNumber(r.getReferenceNumber()))
                        .map(this::toDto)
                        .orElse(toDto(transaction));
            }
        }

        // ── Publish saga event (fire-and-forget; logged on failure) ───────────
        eventProducer.publishEvent(transaction);

        return toDto(transaction);
    }

    /**
     * Compensating action: credit the source account back to reverse a partial debit.
     * Errors here are logged but do not alter the FAILED status (a reconciliation
     * job handles any lingering inconsistencies).
     */
    private void applyCompensation(Transaction transaction, TransferRequest original) {
        log.warn("Applying compensation for failed transfer: {}", transaction.getReferenceNumber());
        try {
            accountClient.updateBalance(original.getSourceAccount(),
                    BalanceUpdateRequest.builder()
                            .amount(original.getAmount())
                            .type("CREDIT")
                            .build());
            log.info("Compensation credit applied successfully for: {}", transaction.getReferenceNumber());
        } catch (Exception ex) {
            // Compensation failed — flag in failure reason for reconciliation
            String compensationError = "COMPENSATION_FAILED: " + ex.getMessage();
            transaction.setFailureReason(transaction.getFailureReason()
                    + " | " + compensationError);
            log.error("Compensation failed for {}: {}", transaction.getReferenceNumber(), ex.getMessage(), ex);
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    public TransactionDto getById(Long id) {
        return toDto(transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: id=" + id)));
    }

    public TransactionDto getByReference(String referenceNumber) {
        return toDto(transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + referenceNumber)));
    }

    public Page<TransactionDto> getByAccount(String accountNumber, Pageable pageable) {
        return transactionRepository
                .findBySourceAccountOrTargetAccount(accountNumber, accountNumber, pageable)
                .map(this::toDto);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String generateReference() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
    }

    private String extractFeignMessage(FeignException e) {
        // FeignException body contains the upstream error JSON; return raw message for simplicity
        return e.contentUTF8().isBlank() ? e.getMessage() : e.contentUTF8();
    }

    TransactionDto toDto(Transaction t) {
        return TransactionDto.builder()
                .id(t.getId())
                .referenceNumber(t.getReferenceNumber())
                .sourceAccount(t.getSourceAccount())
                .targetAccount(t.getTargetAccount())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .status(t.getStatus())
                .transactionType(t.getTransactionType())
                .description(t.getDescription())
                .failureReason(t.getFailureReason())
                .createdAt(t.getCreatedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}
