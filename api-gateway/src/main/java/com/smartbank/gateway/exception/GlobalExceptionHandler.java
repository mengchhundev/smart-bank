package com.smartbank.gateway.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Gateway-level exception handler.
 *
 * <p>Maps well-known exceptions to appropriate HTTP status codes:
 * <ul>
 *   <li>{@link CallNotPermittedException}  → 503 Service Unavailable (circuit open)</li>
 *   <li>{@link TimeoutException}           → 504 Gateway Timeout</li>
 *   <li>{@link NotFoundException}          → 404 Not Found (no route matched)</li>
 *   <li>{@link ResponseStatusException}    → pass-through status from exception</li>
 *   <li>Everything else                    → 500 Internal Server Error</li>
 * </ul>
 *
 * <p>Ordered at -2 so it runs before Spring's default {@code DefaultErrorWebExceptionHandler} (-1).
 */
@Slf4j
@Order(-2)
@Component
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String     message;

        if (ex instanceof CallNotPermittedException) {
            status  = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Service circuit breaker is open — request rejected";
            log.warn("Circuit breaker open: {}", ex.getMessage());

        } else if (ex instanceof TimeoutException) {
            status  = HttpStatus.GATEWAY_TIMEOUT;
            message = "Upstream service timed out";
            log.warn("Gateway timeout: {}", ex.getMessage());

        } else if (ex instanceof NotFoundException) {
            status  = HttpStatus.NOT_FOUND;
            message = "No route found for this request";
            log.warn("Route not found: {}", ex.getMessage());

        } else if (ex instanceof ResponseStatusException rse) {
            status  = HttpStatus.resolve(rse.getStatusCode().value());
            status  = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
            message = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            log.warn("ResponseStatusException {}: {}", status.value(), message);

        } else {
            status  = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected gateway error occurred";
            log.error("Unhandled gateway exception: {}", ex.getMessage(), ex);
        }

        return writeResponse(exchange, status, message);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Echo back X-Request-Id if present
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String requestIdField = requestId != null
                ? """
                  ,"requestId":"%s\"""".formatted(requestId) : "";

        String body = """
                {"status":%d,"error":"%s","message":"%s"%s}"""
                .formatted(status.value(), status.getReasonPhrase(), message, requestIdField);

        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
