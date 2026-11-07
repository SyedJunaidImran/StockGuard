package com.stockguard.order.exception;

/**
 * Thrown when an existing idempotency key is reused with a different request payload
 * (different items, quantities, or notes).
 */
public class IdempotencyPayloadMismatchException extends RuntimeException {

    public IdempotencyPayloadMismatchException(String message) {
        super(message);
    }
}
