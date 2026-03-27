package com.smartbank.account.dto;

import com.smartbank.account.entity.AccountStatus;
import com.smartbank.account.entity.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountDto {
    private Long id;
    private String accountNumber;
    private String accountHolderName;
    private AccountType accountType;
    private BigDecimal balance;
    private String currency;
    private AccountStatus status;
    private Long userId;
    private LocalDateTime createdAt;
}
