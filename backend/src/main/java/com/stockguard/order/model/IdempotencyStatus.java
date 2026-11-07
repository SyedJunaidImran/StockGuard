package com.stockguard.order.model;

/**
 * Lifecycle status of an idempotency record.
 * Maps to the Flyway V1 check constraint chk_idempotency_status:
 * ('PROCESSING', 'COMPLETED', 'FAILED')
 */
public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
