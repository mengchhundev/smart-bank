package com.smartbank.transaction.service;

import com.smartbank.transaction.client.AccountClient;
import com.smartbank.transaction.dto.*;
import com.smartbank.transaction.entity.Transaction;
import com.smartbank.transaction.entity.TransactionStatus;
import com.smartbank.transaction.entity.TransactionType;
import com.smartbank.transaction.exception.TransactionException;
import com.smartbank.transaction.exception.TransactionNotFoundException;
import com.smartbank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final TransactionEventProducer eventProducer;

    @Transactional
    public TransactionDto transfer(TransferRequest request) {
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
                .status(TransactionStatus.PROCESSING)
                .build();

        transaction = transactionRepository.save(transaction);

        try {
            // Debit source
            accountClient.updateBalance(request.getSourceAccount(),
                    BalanceUpdateRequest.builder()
                            .amount(request.getAmount())
                            .type("DEBIT")
                            .build());

            // Credit target
            accountClient.updateBalance(request.getTargetAccount(),
                    BalanceUpdateRequest.builder()
                            .amount(request.getAmount())
                            .type("CREDIT")
                            .build());

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.setCompletedAt(LocalDateTime.now());
            log.info("Transfer completed: {}", transaction.getReferenceNumber());

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            log.error("Transfer failed: {} — {}", transaction.getReferenceNumber(), e.getMessage());
        }

        transaction = transactionRepository.save(transaction);

        // Publish event to Kafka
        eventProducer.sendTransactionEvent(toEvent(transaction));

        return toDto(transaction);
    }

    public TransactionDto getByReference(String referenceNumber) {
        return toDto(transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + referenceNumber)));
    }

    public Page<TransactionDto> getByAccount(String accountNumber, Pageable pageable) {
        return transactionRepository
                .findBySourceAccountOrTargetAccount(accountNumber, accountNumber, pageable)
                .map(this::toDto);
    }

    private String generateReference() {
        return "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private TransactionDto toDto(Transaction t) {
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

    private TransactionEvent toEvent(Transaction t) {
        return TransactionEvent.builder()
                .referenceNumber(t.getReferenceNumber())
                .sourceAccount(t.getSourceAccount())
                .targetAccount(t.getTargetAccount())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .status(t.getStatus().name())
                .transactionType(t.getTransactionType().name())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
