-- ==============================================================================
-- StockGuard Database Migration: V2__seed_categories.sql
-- PostgreSQL Dialect
--
-- Inserts foundational product categories for the catalog.
-- Idempotent: uses ON CONFLICT (slug) DO NOTHING to prevent duplicate errors.
-- ==============================================================================

INSERT INTO categories (name, slug, description) VALUES
    ('Hardware & Components', 'hardware-components', 'CPUs, GPUs, motherboards, and core system components'),
    ('Networking & Servers', 'networking-servers', 'Enterprise switches, routers, and rackmount server gear'),
    ('Storage & Memory', 'storage-memory', 'NVMe SSDs, enterprise HDDs, and ECC memory modules'),
    ('Peripherals & Accessories', 'peripherals-accessories', 'Monitors, mechanical keyboards, and precision peripherals')
ON CONFLICT (slug) DO NOTHING;
