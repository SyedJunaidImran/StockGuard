package com.stockguard.common.exception;

/**
 * Thrown when attempting to create or update a product with a SKU
 * that already exists in the catalog.
 */
public class SkuAlreadyExistsException extends RuntimeException {

    public SkuAlreadyExistsException(String message) {
        super(message);
    }
}
