package com.stockguard.order.exception;

/**
 * Thrown when an order creation request collides with an active in-flight request
 * using the same idempotency key or violates key ownership.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
