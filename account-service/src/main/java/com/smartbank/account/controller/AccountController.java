package com.smartbank.account.controller;

import com.smartbank.account.dto.AccountDto;
import com.smartbank.account.dto.BalanceUpdateRequest;
import com.smartbank.account.dto.CreateAccountRequest;
import com.smartbank.account.dto.StatusUpdateRequest;
import com.smartbank.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // ── Public API (JWT-protected) ──────────────────────────────

    @PostMapping("/api/accounts")
    public ResponseEntity<AccountDto> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(request, userId));
    }

    @GetMapping("/api/accounts/{id}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @GetMapping("/api/accounts/my")
    public ResponseEntity<List<AccountDto>> getMyAccounts(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(accountService.getAccountsByUserId(userId));
    }

    @PutMapping("/api/accounts/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(accountService.updateStatus(id, request.getStatus()));
    }

    // ── Internal API (for transaction-service via Feign) ────────

    @GetMapping("/api/v1/accounts/{accountNumber}")
    public ResponseEntity<AccountDto> getAccountByNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccountByNumber(accountNumber));
    }

    @GetMapping("/api/v1/accounts/user/{userId}")
    public ResponseEntity<List<AccountDto>> getAccountsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(accountService.getAccountsByUserId(userId));
    }

    @PutMapping("/api/v1/accounts/{accountNumber}/balance")
    public ResponseEntity<AccountDto> updateBalance(
            @PathVariable String accountNumber,
            @Valid @RequestBody BalanceUpdateRequest request) {
        return ResponseEntity.ok(accountService.updateBalance(accountNumber, request));
    }
}
