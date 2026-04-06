package com.smartbank.account.dto;

import com.smartbank.account.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateAccountRequest {

    @NotBlank(message = "Account holder name is required")
    private String accountHolderName;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    private String currency;
}
