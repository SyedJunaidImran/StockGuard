package com.stockguard.inventory.exception;

/**
 * Exception thrown when a stock deduction exceeds current available stock.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
