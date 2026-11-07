-- ==============================================================================
-- StockGuard Database Migration: V4__init_existing_product_inventory.sql
-- PostgreSQL Dialect
--
-- Safe, idempotent initialization of inventory records for any catalog products
-- that do not have an inventory row yet.
-- Preserves existing stock quantities and never creates duplicates.
-- ==============================================================================

INSERT INTO inventory (product_id, available_stock, reserved_stock, low_stock_threshold, version, updated_at)
SELECT p.id, 0, 0, 10, 0, CURRENT_TIMESTAMP
FROM products p
WHERE NOT EXISTS (
    SELECT 1 FROM inventory i WHERE i.product_id = p.id
)
ON CONFLICT (product_id) DO NOTHING;
