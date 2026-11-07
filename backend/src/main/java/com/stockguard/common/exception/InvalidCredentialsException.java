package com.stockguard.common.exception;

/**
 * Thrown when an authentication attempt fails due to invalid credentials.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
