package com.smartbank.transaction.client;

import com.smartbank.transaction.dto.AccountDto;
import com.smartbank.transaction.dto.BalanceUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "account-service", path = "/api/v1/accounts")
public interface AccountClient {

    @GetMapping("/{accountNumber}")
    ResponseEntity<AccountDto> getAccount(@PathVariable String accountNumber);

    @PutMapping("/{accountNumber}/balance")
    ResponseEntity<AccountDto> updateBalance(
            @PathVariable String accountNumber,
            @RequestBody BalanceUpdateRequest request);
}
