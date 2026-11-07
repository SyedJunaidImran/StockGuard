-- ==============================================================================
-- StockGuard Database Migration: V3__seed_admin_user.sql
-- Idempotent seed for default ADMIN user account
-- Credentials:
--   Email:    admin@stockguard.io
--   Password: Admin123!
-- ==============================================================================

INSERT INTO users (email, password_hash, first_name, last_name, role, is_active, created_at, updated_at)
VALUES (
    'admin@stockguard.io',
    '$2a$10$gT0ikm34qEiRgATuwtESPOY6CD4TY3E8AOKpShTH8EP94Xw5GPseG',
    'System',
    'Admin',
    'ADMIN',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO UPDATE SET role = 'ADMIN', is_active = TRUE;
