package com.smartbank.transaction.controller;

import com.smartbank.transaction.dto.TransactionDto;
import com.smartbank.transaction.dto.TransferRequest;
import com.smartbank.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Fund transfers and transaction history")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    @Operation(summary = "Initiate a fund transfer",
               description = "Transfers funds between two accounts. Supply X-Idempotency-Key for safe retries.")
    public ResponseEntity<TransactionDto> transfer(
            @Valid @RequestBody TransferRequest request,
            @Parameter(description = "Client-generated UUID to deduplicate retries")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(transactionService.transfer(request, idempotencyKey));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<TransactionDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Transaction history for an account (paginated)")
    public ResponseEntity<Page<TransactionDto>> getByAccount(
            @PathVariable String accountNumber,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(transactionService.getByAccount(accountNumber, pageable));
    }
}
