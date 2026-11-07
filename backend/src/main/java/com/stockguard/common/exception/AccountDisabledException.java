package com.stockguard.common.exception;

/**
 * Thrown when an authentication attempt is made for a disabled or suspended account.
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException(String message) {
        super(message);
    }
}
