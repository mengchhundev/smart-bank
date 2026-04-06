package com.smartbank.transaction.service;

import com.smartbank.transaction.client.AccountClient;
import com.smartbank.transaction.dto.AccountDto;
import com.smartbank.transaction.dto.BalanceUpdateRequest;
import com.smartbank.transaction.dto.TransactionDto;
import com.smartbank.transaction.dto.TransferRequest;
import com.smartbank.transaction.entity.IdempotencyRecord;
import com.smartbank.transaction.entity.Transaction;
import com.smartbank.transaction.entity.TransactionStatus;
import com.smartbank.transaction.entity.TransactionType;
import com.smartbank.transaction.exception.TransactionException;
import com.smartbank.transaction.exception.TransactionNotFoundException;
import com.smartbank.transaction.repository.IdempotencyRecordRepository;
import com.smartbank.transaction.repository.TransactionRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository       transactionRepository;
    @Mock IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock AccountClient               accountClient;
    @Mock TransactionEventProducer    eventProducer;

    @InjectMocks TransactionService transactionService;

    private TransferRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = TransferRequest.builder()
                .sourceAccount("SB-SOURCE-001")
                .targetAccount("SB-TARGET-002")
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .description("Test transfer")
                .build();
    }

    // ─── Transfer ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("transfer()")
    class TransferTests {

        @Test
        @DisplayName("successful transfer → COMPLETED status and SUCCESS event")
        void transfer_success() {
            Transaction saved = buildTransaction(TransactionStatus.PROCESSING);
            Transaction completed = buildTransaction(TransactionStatus.COMPLETED);
            completed.setCompletedAt(LocalDateTime.now());

            when(transactionRepository.save(any())).thenReturn(saved, completed);
            when(accountClient.updateBalance(any(), any()))
                    .thenReturn(ResponseEntity.ok(new AccountDto()));
            when(idempotencyRecordRepository.findByIdempotencyKey(any()))
                    .thenReturn(Optional.empty());

            TransactionDto result = transactionService.transfer(validRequest, null);

            assertThat(result).isNotNull();
            verify(accountClient, times(2)).updateBalance(any(), any());
            verify(eventProducer).publishEvent(any(Transaction.class));
        }

        @Test
        @DisplayName("same source and target → TransactionException")
        void transfer_sameAccount_throws() {
            validRequest.setTargetAccount(validRequest.getSourceAccount());

            assertThatThrownBy(() -> transactionService.transfer(validRequest, null))
                    .isInstanceOf(TransactionException.class)
                    .hasMessageContaining("cannot be the same");

            verifyNoInteractions(accountClient, eventProducer);
        }

        @Test
        @DisplayName("debit fails → FAILED status, no compensation needed")
        void transfer_debitFails_markedFailed() {
            Transaction saved = buildTransaction(TransactionStatus.PROCESSING);
            when(transactionRepository.save(any())).thenReturn(saved, saved);
            when(idempotencyRecordRepository.findByIdempotencyKey(any()))
                    .thenReturn(Optional.empty());

            FeignException feignEx = buildFeignException(422);
            when(accountClient.updateBalance(eq("SB-SOURCE-001"), any()))
                    .thenThrow(feignEx);

            TransactionDto result = transactionService.transfer(validRequest, null);

            // Debit failed → verify credit was never attempted
            verify(accountClient, times(1)).updateBalance(any(), any());
            // Event still published (FAILED)
            verify(eventProducer).publishEvent(any(Transaction.class));
        }

        @Test
        @DisplayName("credit fails → compensation credit applied to source, FAILED status")
        void transfer_creditFails_compensationApplied() {
            Transaction saved = buildTransaction(TransactionStatus.PROCESSING);
            when(transactionRepository.save(any())).thenReturn(saved, saved);
            when(idempotencyRecordRepository.findByIdempotencyKey(any()))
                    .thenReturn(Optional.empty());

            // Debit succeeds, credit throws generic exception
            when(accountClient.updateBalance(eq("SB-SOURCE-001"), any()))
                    .thenReturn(ResponseEntity.ok(new AccountDto()));
            when(accountClient.updateBalance(eq("SB-TARGET-002"), any()))
                    .thenThrow(new RuntimeException("network error"));

            transactionService.transfer(validRequest, null);

            // Compensation: credit back source (3 calls total: debit, credit attempt, compensation)
            ArgumentCaptor<BalanceUpdateRequest> captor =
                    ArgumentCaptor.forClass(BalanceUpdateRequest.class);
            verify(accountClient, times(3)).updateBalance(any(), captor.capture());

            List<BalanceUpdateRequest> calls = captor.getAllValues();
            assertThat(calls.get(0).getType()).isEqualTo("DEBIT");   // original debit
            assertThat(calls.get(1).getType()).isEqualTo("CREDIT");  // credit target (fails)
            assertThat(calls.get(2).getType()).isEqualTo("CREDIT");  // compensation
        }

        @Test
        @DisplayName("duplicate idempotency key → returns existing transaction without processing")
        void transfer_duplicateIdempotencyKey_returnsExisting() {
            IdempotencyRecord existingRecord = IdempotencyRecord.builder()
                    .idempotencyKey("key-abc")
                    .referenceNumber("TXNABC123")
                    .build();
            Transaction existingTx = buildTransaction(TransactionStatus.COMPLETED);

            when(idempotencyRecordRepository.findByIdempotencyKey("key-abc"))
                    .thenReturn(Optional.of(existingRecord));
            when(transactionRepository.findByReferenceNumber("TXNABC123"))
                    .thenReturn(Optional.of(existingTx));

            transactionService.transfer(validRequest, "key-abc");

            verifyNoInteractions(accountClient, eventProducer);
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("new idempotency key → saves idempotency record after transfer")
        void transfer_newIdempotencyKey_savesRecord() {
            Transaction saved = buildTransaction(TransactionStatus.PROCESSING);
            Transaction completed = buildTransaction(TransactionStatus.COMPLETED);

            when(transactionRepository.save(any())).thenReturn(saved, completed);
            when(accountClient.updateBalance(any(), any()))
                    .thenReturn(ResponseEntity.ok(new AccountDto()));
            when(idempotencyRecordRepository.findByIdempotencyKey("new-key"))
                    .thenReturn(Optional.empty());

            transactionService.transfer(validRequest, "new-key");

            verify(idempotencyRecordRepository).save(argThat(r ->
                    "new-key".equals(r.getIdempotencyKey())));
        }
    }

    // ─── getById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("existing id → returns DTO")
        void getById_found() {
            Transaction tx = buildTransaction(TransactionStatus.COMPLETED);
            tx.setId(1L);
            when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));

            TransactionDto result = transactionService.getById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }

        @Test
        @DisplayName("unknown id → TransactionNotFoundException")
        void getById_notFound() {
            when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.getById(99L))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("id=99");
        }
    }

    // ─── getByAccount ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getByAccount()")
    class GetByAccountTests {

        @Test
        @DisplayName("returns paginated results mapped to DTOs")
        void getByAccount_returnsMappedPage() {
            Transaction tx = buildTransaction(TransactionStatus.COMPLETED);
            Page<Transaction> page = new PageImpl<>(List.of(tx));
            PageRequest pageable = PageRequest.of(0, 20);

            when(transactionRepository.findBySourceAccountOrTargetAccount(
                    "SB-SOURCE-001", "SB-SOURCE-001", pageable))
                    .thenReturn(page);

            Page<TransactionDto> result =
                    transactionService.getByAccount("SB-SOURCE-001", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSourceAccount()).isEqualTo("SB-SOURCE-001");
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Transaction buildTransaction(TransactionStatus status) {
        return Transaction.builder()
                .id(1L)
                .referenceNumber("TXN000000000001")
                .sourceAccount("SB-SOURCE-001")
                .targetAccount("SB-TARGET-002")
                .amount(BigDecimal.valueOf(100.00))
                .currency("USD")
                .transactionType(TransactionType.TRANSFER)
                .status(status)
                .build();
    }

    private FeignException buildFeignException(int status) {
        return FeignException.errorStatus("updateBalance",
                feign.Response.builder()
                        .status(status)
                        .reason("Unprocessable")
                        .request(Request.create(Request.HttpMethod.PUT, "/test",
                                Map.of(), null, null, null))
                        .build());
    }
}
