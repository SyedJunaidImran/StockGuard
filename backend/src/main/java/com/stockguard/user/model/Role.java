package com.stockguard.user.model;

/**
 * User roles within StockGuard.
 * Matches the Flyway V1 schema CHECK constraint: chk_users_role CHECK (role IN ('CUSTOMER', 'ADMIN')).
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
