package com.smartbank.transaction.controller;

import com.smartbank.transaction.dto.TransactionDto;
import com.smartbank.transaction.dto.TransferRequest;
import com.smartbank.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/transfer")
    public ResponseEntity<TransactionDto> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.transfer(request));
    }

    @GetMapping("/{referenceNumber}")
    public ResponseEntity<TransactionDto> getTransaction(@PathVariable String referenceNumber) {
        return ResponseEntity.ok(transactionService.getByReference(referenceNumber));
    }

    @GetMapping("/account/{accountNumber}")
    public ResponseEntity<Page<TransactionDto>> getByAccount(
            @PathVariable String accountNumber, Pageable pageable) {
        return ResponseEntity.ok(transactionService.getByAccount(accountNumber, pageable));
    }
}
