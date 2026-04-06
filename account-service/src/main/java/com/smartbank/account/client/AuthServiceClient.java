package com.smartbank.account.client;

import com.smartbank.account.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "auth-service", path = "/api/auth")
public interface AuthServiceClient {

    @GetMapping("/validate")
    UserResponse validateToken(@RequestHeader("Authorization") String bearerToken);
}
