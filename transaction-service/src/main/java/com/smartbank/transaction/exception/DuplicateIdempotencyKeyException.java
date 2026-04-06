package com.smartbank.transaction.exception;

public class DuplicateIdempotencyKeyException extends RuntimeException {
    public DuplicateIdempotencyKeyException(String message) {
        super(message);
    }
}
