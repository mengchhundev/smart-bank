package com.smartbank.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbank.transaction.config.JwtAuthenticationFilter;
import com.smartbank.transaction.dto.TransactionDto;
import com.smartbank.transaction.dto.TransferRequest;
import com.smartbank.transaction.entity.TransactionStatus;
import com.smartbank.transaction.entity.TransactionType;
import com.smartbank.transaction.exception.GlobalExceptionHandler;
import com.smartbank.transaction.exception.TransactionNotFoundException;
import com.smartbank.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TransactionService          transactionService;
    @MockBean JwtAuthenticationFilter     jwtAuthenticationFilter;

    private TransactionDto sampleDto;
    private TransferRequest validRequest;

    @BeforeEach
    void setUp() {
        sampleDto = TransactionDto.builder()
                .id(1L)
                .referenceNumber("TXN000000000001")
                .sourceAccount("SB-SOURCE-001")
                .targetAccount("SB-TARGET-002")
                .amount(BigDecimal.valueOf(250.00))
                .currency("USD")
                .status(TransactionStatus.COMPLETED)
                .transactionType(TransactionType.TRANSFER)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        validRequest = TransferRequest.builder()
                .sourceAccount("SB-SOURCE-001")
                .targetAccount("SB-TARGET-002")
                .amount(BigDecimal.valueOf(250.00))
                .build();
    }

    // ─── POST /api/transactions/transfer ──────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("POST /api/transactions/transfer → 201 with transaction DTO")
    void transfer_success_returns201() throws Exception {
        when(transactionService.transfer(any(TransferRequest.class), isNull()))
                .thenReturn(sampleDto);

        mockMvc.perform(post("/api/transactions/transfer")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.referenceNumber").value("TXN000000000001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/transactions/transfer with idempotency key → forwarded to service")
    void transfer_withIdempotencyKey() throws Exception {
        when(transactionService.transfer(any(), eq("idem-key-123")))
                .thenReturn(sampleDto);

        mockMvc.perform(post("/api/transactions/transfer")
                        .with(csrf())
                        .header("X-Idempotency-Key", "idem-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceNumber").value("TXN000000000001"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/transactions/transfer with invalid body → 400")
    void transfer_invalidBody_returns400() throws Exception {
        TransferRequest invalid = TransferRequest.builder()
                .sourceAccount("")          // blank — invalid
                .targetAccount("SB-TARGET-002")
                .amount(BigDecimal.valueOf(-10))  // negative — invalid
                .build();

        mockMvc.perform(post("/api/transactions/transfer")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("POST /api/transactions/transfer without auth → 401")
    void transfer_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/transactions/{id} ───────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /api/transactions/1 → 200 with DTO")
    void getById_found_returns200() throws Exception {
        when(transactionService.getById(1L)).thenReturn(sampleDto);

        mockMvc.perform(get("/api/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sourceAccount").value("SB-SOURCE-001"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/transactions/999 → 404")
    void getById_notFound_returns404() throws Exception {
        when(transactionService.getById(999L))
                .thenThrow(new TransactionNotFoundException("Transaction not found: id=999"));

        mockMvc.perform(get("/api/transactions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found: id=999"));
    }

    // ─── GET /api/transactions/account/{accountNumber} ────────────────────────

    @Test
    @WithMockUser
    @DisplayName("GET /api/transactions/account/SB-SOURCE-001 → 200 paginated")
    void getByAccount_returns200() throws Exception {
        when(transactionService.getByAccount(eq("SB-SOURCE-001"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleDto)));

        mockMvc.perform(get("/api/transactions/account/SB-SOURCE-001")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceAccount").value("SB-SOURCE-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/transactions/account/UNKNOWN → empty page")
    void getByAccount_noResults_returnsEmptyPage() throws Exception {
        when(transactionService.getByAccount(eq("UNKNOWN"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/transactions/account/UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
