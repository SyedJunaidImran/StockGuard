# StockGuard — Database Schema & Architecture Decisions

This document outlines the design decisions, relational structure, constraints, and index strategy for the PostgreSQL database underpinning **StockGuard**.

---

## 1. Relational Entity Overview

```
 [categories]
      │ 1
      │ N
  [products] ────────── 1:1 ────────── [inventory]
      │ 1                                   │ 1
      │ N                                   │ N
 [order_items]                     [inventory_movements]
      │ N                                   │ N
      │ 1                                   │ 1 (actor)
   [orders] ────────── N:1 ───────────── [users]
      │ 1                                   │ 1
      │ 0..1                                │ N
 [idempotency_records] ─────────────────────┘
```

---

## 2. Key Architecture & Schema Decisions

### A. Strict Separation of Products and Inventory
- **Decision:** Products (`products`) and stock quantities (`inventory`) are segregated into two distinct tables linked by a 1-to-1 foreign key with a `UNIQUE (product_id)` constraint.
- **Rationale:** 
  - Catalog browsing (reading product title, description, SKU, category, and images) has high read throughput and should not be impacted or blocked by row-level write locks acquired during concurrent checkouts.
  - Dedicating the `inventory` table to stock state isolates row contention (`SELECT ... FOR UPDATE` or atomic decrements) to small, dedicated rows without inflating the product cache.

### B. Defense-in-Depth Concurrency Constraint
- **Decision:** Enforce `CHECK (available_stock >= 0)` and `CHECK (reserved_stock >= 0)` at the PostgreSQL database engine level.
- **Rationale:**
  - Even if application logic experiences an unexpected edge case or transaction boundary leak, PostgreSQL will abort and roll back any transaction that would result in negative inventory.
  - This eliminates overselling at both application and database layers.

### C. Append-Only Inventory Movements Audit Trail
- **Decision:** Every modification to stock must record an entry in `inventory_movements` capturing `quantity_change`, `previous_stock`, `new_stock`, `movement_type`, `reason`, `reference_id` (such as order number), and the `actor_id`.
- **Rationale:**
  - Guarantees financial and operational auditability.
  - Enables diagnosing race conditions, verifying rollback consistency, and tracking shrinkage or manual admin overrides.

### D. Financial Snapshotting in Order Items
- **Decision:** `order_items` stores `unit_price NUMERIC(12, 2)` and `subtotal NUMERIC(12, 2)` at the exact time of order placement.
- **Rationale:**
  - If an administrator updates a product's price in `products` next month, historical orders must remain immutable. Calculating totals dynamically from the current product catalog is an anti-pattern.

### E. Precise Monetary Data Types
- **Decision:** Use `NUMERIC(12, 2)` for all monetary columns (`price`, `total_amount`, `unit_price`, `subtotal`).
- **Rationale:**
  - Floating point types (`FLOAT`, `DOUBLE PRECISION`) suffer from IEEE 754 binary rounding errors (e.g. `0.1 + 0.2 = 0.30000000000000004`), which are catastrophic in billing and inventory systems.

### F. Idempotency Key Storage
- **Decision:** `idempotency_records` table holds client-provided `idempotency_key`, request payload hash, lifecycle status (`PROCESSING`, `COMPLETED`, `FAILED`), and cached response.
- **Rationale:**
  - Protects checkout endpoints against network retries, timeout reconnections, and accidental double-submissions by returning the cached response rather than creating duplicate orders or double-deducting stock.

### G. Indexing Strategy
- `users`: B-Tree indexes on `email` (unique lookup) and `role` (admin filtering).
- `products`: B-Tree indexes on `sku` (unique), `category_id` (foreign key joins), and `is_active` (filtering active catalog items).
- `inventory`: Unique B-Tree index on `product_id`.
- `inventory_movements`: Indexes on `inventory_id`, `product_id`, `reference_id`, and `created_at` (audit log time slicing).
- `orders`: Indexes on `customer_id` (order history lookup), `order_number` (unique tracking), `status` (dashboard pipeline filters), and `created_at`.
- `order_items`: Composite/foreign key indexes on `order_id` and `product_id`.
- `idempotency_records`: Unique index on `idempotency_key` and index on `expires_at` for TTL cleanup.
