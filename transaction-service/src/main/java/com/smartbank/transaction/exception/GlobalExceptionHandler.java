package com.smartbank.transaction.exception;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(TransactionNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransaction(TransactionException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DuplicateIdempotencyKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateKey(DuplicateIdempotencyKeyException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeign(FeignException ex) {
        HttpStatus status = ex.status() >= 400 && ex.status() < 600
                ? HttpStatus.resolve(ex.status())
                : HttpStatus.BAD_GATEWAY;
        if (status == null) status = HttpStatus.BAD_GATEWAY;

        String message = ex.contentUTF8().isBlank()
                ? "Upstream service error: " + ex.getMessage()
                : ex.contentUTF8();

        log.warn("Feign error {}: {}", ex.status(), message);
        return buildResponse(status, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
