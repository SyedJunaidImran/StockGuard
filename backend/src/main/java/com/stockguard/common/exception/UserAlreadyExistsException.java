package com.stockguard.common.exception;

/**
 * Thrown when an account registration attempt is made with an email
 * that is already registered in the system.
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
