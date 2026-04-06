package com.smartbank.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("account_holder_name")
    private String accountHolderName;

    @JsonProperty("account_type")
    private AccountType accountType;

    private BigDecimal balance;
    private String currency;
    private AccountStatus status;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
