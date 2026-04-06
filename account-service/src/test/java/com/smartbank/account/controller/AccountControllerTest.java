package com.smartbank.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbank.account.config.JwtAuthenticationFilter;
import com.smartbank.account.config.SecurityConfig;
import com.smartbank.account.dto.AccountDto;
import com.smartbank.account.dto.BalanceUpdateRequest;
import com.smartbank.account.dto.CreateAccountRequest;
import com.smartbank.account.dto.StatusUpdateRequest;
import com.smartbank.account.entity.AccountStatus;
import com.smartbank.account.entity.AccountType;
import com.smartbank.account.exception.AccountNotFoundException;
import com.smartbank.account.exception.GlobalExceptionHandler;
import com.smartbank.account.exception.InsufficientBalanceException;
import com.smartbank.account.service.AccountService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=smartbank-jwt-secret-key-that-is-at-least-256-bits-long-for-hs256",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class AccountControllerTest {

    private static final String JWT_SECRET =
            "smartbank-jwt-secret-key-that-is-at-least-256-bits-long-for-hs256";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    private String customerToken;
    private String adminToken;
    private AccountDto accountDto;

    @BeforeEach
    void setUp() {
        customerToken = generateToken(1L, "john", "CUSTOMER");
        adminToken   = generateToken(2L, "admin", "ADMIN");

        accountDto = AccountDto.builder()
                .id(1L)
                .accountNumber("SB1234567890ABCD")
                .accountHolderName("John Doe")
                .accountType(AccountType.SAVINGS)
                .balance(BigDecimal.valueOf(1000))
                .currency("USD")
                .status(AccountStatus.ACTIVE)
                .userId(1L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String generateToken(Long userId, String username, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(key)
                .compact();
    }

    // ── POST /api/accounts ──────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/accounts")
    class CreateAccountTests {

        @Test
        @DisplayName("should create account and return 201")
        void shouldCreateAccount() throws Exception {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountHolderName("John Doe")
                    .accountType(AccountType.SAVINGS)
                    .currency("USD")
                    .build();

            when(accountService.createAccount(any(CreateAccountRequest.class), eq(1L)))
                    .thenReturn(accountDto);

            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.account_number").value("SB1234567890ABCD"))
                    .andExpect(jsonPath("$.account_holder_name").value("John Doe"))
                    .andExpect(jsonPath("$.account_type").value("SAVINGS"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("should return 400 when accountHolderName is blank")
        void shouldRejectBlankHolderName() throws Exception {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountHolderName("")
                    .accountType(AccountType.SAVINGS)
                    .build();

            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.accountHolderName").exists());
        }

        @Test
        @DisplayName("should return 400 when accountType is null")
        void shouldRejectNullAccountType() throws Exception {
            String body = "{\"accountHolderName\":\"John Doe\"}";

            mockMvc.perform(post("/api/accounts")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.accountType").exists());
        }

        @Test
        @DisplayName("should return 401 when no token is provided")
        void shouldReturn401WhenUnauthenticated() throws Exception {
            CreateAccountRequest request = CreateAccountRequest.builder()
                    .accountHolderName("John Doe")
                    .accountType(AccountType.SAVINGS)
                    .build();

            mockMvc.perform(post("/api/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /api/accounts/{id} ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/accounts/{id}")
    class GetAccountByIdTests {

        @Test
        @DisplayName("should return account when found")
        void shouldReturnAccount() throws Exception {
            when(accountService.getAccountById(1L)).thenReturn(accountDto);

            mockMvc.perform(get("/api/accounts/1")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.account_number").value("SB1234567890ABCD"));
        }

        @Test
        @DisplayName("should return 404 when account not found")
        void shouldReturn404() throws Exception {
            when(accountService.getAccountById(999L))
                    .thenThrow(new AccountNotFoundException("Account not found with id: 999"));

            mockMvc.perform(get("/api/accounts/999")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Account not found with id: 999"));
        }

        @Test
        @DisplayName("should return 401 when unauthenticated")
        void shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/accounts/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /api/accounts/my ────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/accounts/my")
    class GetMyAccountsTests {

        @Test
        @DisplayName("should return accounts for authenticated user")
        void shouldReturnMyAccounts() throws Exception {
            when(accountService.getAccountsByUserId(1L)).thenReturn(List.of(accountDto));

            mockMvc.perform(get("/api/accounts/my")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].user_id").value(1));
        }

        @Test
        @DisplayName("should return empty list when user has no accounts")
        void shouldReturnEmptyList() throws Exception {
            when(accountService.getAccountsByUserId(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/accounts/my")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ── PUT /api/accounts/{id}/status ───────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/accounts/{id}/status")
    class UpdateStatusTests {

        @Test
        @DisplayName("should update status when caller is ADMIN")
        void shouldUpdateStatusAsAdmin() throws Exception {
            AccountDto frozenDto = AccountDto.builder()
                    .id(1L)
                    .accountNumber("SB1234567890ABCD")
                    .status(AccountStatus.FROZEN)
                    .build();

            when(accountService.updateStatus(1L, AccountStatus.FROZEN)).thenReturn(frozenDto);

            StatusUpdateRequest request = StatusUpdateRequest.builder()
                    .status(AccountStatus.FROZEN)
                    .build();

            mockMvc.perform(put("/api/accounts/1/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FROZEN"));
        }

        @Test
        @DisplayName("should return 403 when caller is CUSTOMER")
        void shouldReturn403ForCustomer() throws Exception {
            StatusUpdateRequest request = StatusUpdateRequest.builder()
                    .status(AccountStatus.FROZEN)
                    .build();

            mockMvc.perform(put("/api/accounts/1/status")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should return 400 when status is null")
        void shouldReturn400WhenStatusNull() throws Exception {
            String body = "{}";

            mockMvc.perform(put("/api/accounts/1/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.status").exists());
        }
    }

    // ── GET /api/v1/accounts/{accountNumber} (internal) ────────────────────

    @Nested
    @DisplayName("GET /api/v1/accounts/{accountNumber}")
    class GetAccountByNumberTests {

        @Test
        @DisplayName("should return account by number")
        void shouldReturnByNumber() throws Exception {
            when(accountService.getAccountByNumber("SB1234567890ABCD")).thenReturn(accountDto);

            mockMvc.perform(get("/api/v1/accounts/SB1234567890ABCD")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account_number").value("SB1234567890ABCD"));
        }

        @Test
        @DisplayName("should return 404 when account number not found")
        void shouldReturn404() throws Exception {
            when(accountService.getAccountByNumber("INVALID"))
                    .thenThrow(new AccountNotFoundException("Account not found: INVALID"));

            mockMvc.perform(get("/api/v1/accounts/INVALID")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Account not found: INVALID"));
        }
    }

    // ── PUT /api/v1/accounts/{accountNumber}/balance (internal) ────────────

    @Nested
    @DisplayName("PUT /api/v1/accounts/{accountNumber}/balance")
    class UpdateBalanceTests {

        @Test
        @DisplayName("should credit balance")
        void shouldCreditBalance() throws Exception {
            AccountDto credited = AccountDto.builder()
                    .balance(BigDecimal.valueOf(1500))
                    .build();

            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(500))
                    .type(BalanceUpdateRequest.TransactionType.CREDIT)
                    .build();

            when(accountService.updateBalance(eq("SB1234567890ABCD"), any(BalanceUpdateRequest.class)))
                    .thenReturn(credited);

            mockMvc.perform(put("/api/v1/accounts/SB1234567890ABCD/balance")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(1500));
        }

        @Test
        @DisplayName("should return 400 on insufficient balance")
        void shouldReturn400OnInsufficientBalance() throws Exception {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(9999))
                    .type(BalanceUpdateRequest.TransactionType.DEBIT)
                    .build();

            when(accountService.updateBalance(eq("SB1234567890ABCD"), any(BalanceUpdateRequest.class)))
                    .thenThrow(new InsufficientBalanceException("Insufficient balance in account: SB1234567890ABCD"));

            mockMvc.perform(put("/api/v1/accounts/SB1234567890ABCD/balance")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Insufficient balance in account: SB1234567890ABCD"));
        }

        @Test
        @DisplayName("should return 409 when account is FROZEN")
        void shouldReturn409WhenAccountFrozen() throws Exception {
            BalanceUpdateRequest request = BalanceUpdateRequest.builder()
                    .amount(BigDecimal.valueOf(100))
                    .type(BalanceUpdateRequest.TransactionType.DEBIT)
                    .build();

            when(accountService.updateBalance(eq("SB1234567890ABCD"), any(BalanceUpdateRequest.class)))
                    .thenThrow(new IllegalStateException("Cannot perform balance operation on account with status: FROZEN"));

            mockMvc.perform(put("/api/v1/accounts/SB1234567890ABCD/balance")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Cannot perform balance operation on account with status: FROZEN"));
        }

        @Test
        @DisplayName("should return 400 when amount is zero or negative")
        void shouldReturn400WhenAmountInvalid() throws Exception {
            String body = "{\"amount\": -100, \"type\": \"DEBIT\"}";

            mockMvc.perform(put("/api/v1/accounts/SB1234567890ABCD/balance")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists());
        }

        @Test
        @DisplayName("should return 400 when type is missing")
        void shouldReturn400WhenTypeMissing() throws Exception {
            String body = "{\"amount\": 100}";

            mockMvc.perform(put("/api/v1/accounts/SB1234567890ABCD/balance")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.type").exists());
        }
    }
}
