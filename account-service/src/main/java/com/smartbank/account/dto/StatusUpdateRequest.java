package com.smartbank.account.dto;

import com.smartbank.account.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StatusUpdateRequest {

    @NotNull(message = "Status is required")
    private AccountStatus status;
}
