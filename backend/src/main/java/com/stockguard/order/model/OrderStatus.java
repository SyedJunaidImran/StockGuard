package com.stockguard.order.model;

/**
 * Status lifecycle of a customer order in StockGuard.
 * Strictly adheres to the database check constraint in Flyway V1:
 * ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'FAILED')
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED
}
