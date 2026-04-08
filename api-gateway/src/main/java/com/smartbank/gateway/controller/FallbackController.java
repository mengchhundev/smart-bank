package com.smartbank.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fallback endpoints invoked when a circuit breaker opens for a downstream service.
 *
 * <p>Returns a structured {@code 503 Service Unavailable} response so clients
 * get a consistent error format rather than a raw connection-refused error.
 *
 * <p>Each method handles ANY HTTP verb ({@code @RequestMapping} with no method
 * restriction) so fallback works for GET, POST, PUT, DELETE, etc.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        return Mono.just(unavailable("auth-service",
                "Authentication service is temporarily unavailable. Please try again shortly."));
    }

    @RequestMapping("/account")
    public Mono<ResponseEntity<Map<String, Object>>> accountFallback() {
        return Mono.just(unavailable("account-service",
                "Account service is temporarily unavailable. Please try again shortly."));
    }

    @RequestMapping("/transaction")
    public Mono<ResponseEntity<Map<String, Object>>> transactionFallback() {
        return Mono.just(unavailable("transaction-service",
                "Transaction service is temporarily unavailable. Your request was not processed."));
    }

    @RequestMapping("/notification")
    public Mono<ResponseEntity<Map<String, Object>>> notificationFallback() {
        return Mono.just(unavailable("notification-service",
                "Notification service is temporarily unavailable."));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> unavailable(String service, String message) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status",    503,
                        "error",     "Service Unavailable",
                        "service",   service,
                        "message",   message,
                        "timestamp", LocalDateTime.now().toString()
                ));
    }
}
