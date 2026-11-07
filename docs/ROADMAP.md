# StockGuard — Engineering Roadmap & Implementation Milestones

This document preserves the comprehensive multi-phase engineering roadmap for **StockGuard**, detailing completed capabilities, current verification statuses, and planned future milestones.

---

## Roadmap Overview

```
[Phase 1] Foundations & Monorepo Setup (Completed)
   │
[Phase 2] Stateless JWT Auth & RBAC Boundaries (Completed)
   │
[Phase 3] Product Catalog, Dynamic Search & Admin CRUD (Completed)
   │
[Phase 4] Inventory Engine, Pessimistic Row Locking & Audit Ledger (Completed)
   │
[Phase 5] High-Concurrency Order Placement & Price Snapshots (Completed)
   │
[Phase 6] PostgreSQL Testcontainers, RFC 4122 Idempotency & Transaction Gap Resolution (Implementation Completed)
   │
[Phase 7] Remote GitHub Actions CI Verification with Live PostgreSQL 16 (Pending CI Execution)
   │
[Phase 8] Production Cloud Deployment: Supabase & Render / Railway (Planned)
   │
[Phase 9] Production Smoke Verification, Live Telemetry & Portfolio Finalization (Planned)
```

---

## Detailed Milestone Breakdown

### Phase 1: Foundations & Architecture
* **Status:** `COMPLETED`
* **Focus:** Monorepo architecture, foundational backend/frontend scaffolds, automated database migrations, and CI skeleton.
* **Deliverables:**
  - Standardized monorepo hierarchy: `backend/` (Spring Boot 3.3.4, Java 21 LTS) and `frontend/` (React 18, TypeScript 5.5, Vite 5.4).
  - Version-controlled PostgreSQL schema evolution via **Flyway** (`V1__initial_schema.sql`).
  - Base GitHub Actions workflow (`.github/workflows/ci.yml`) compiling Java and building frontend assets.
  - Initial system health probe endpoint (`GET /api/v1/health`) measuring database connection latency and JVM memory.

---

### Phase 2: Authentication, Security & Role-Based Access Control (RBAC)
* **Status:** `COMPLETED`
* **Focus:** Stateless authentication, password hashing, JWT token lifecycle, and role security boundaries.
* **Deliverables:**
  - BCrypt credential hashing with configurable cost factor.
  - Stateless JSON Web Token issuance and parsing using `io.jsonwebtoken` (JJWT 0.12.6).
  - Spring Security 6 filter chain with `OncePerRequestFilter` parsing Bearer tokens into authenticated `UsernamePasswordAuthenticationToken` principals.
  - Granular role guards (`ROLE_CUSTOMER`, `ROLE_ADMIN`) with `@PreAuthorize` method security.
  - Customer registration (`POST /api/v1/auth/register`) and authentication (`POST /api/v1/auth/login`).
  - Strict credentials isolation: raw cryptographic tokens remain in memory and are never exposed in DOM or application logs.

---

### Phase 3: Product Catalog & Administrative Management
* **Status:** `COMPLETED`
* **Focus:** High-throughput catalog discovery, dynamic search, multi-faceted filtering, and admin product mutations.
* **Deliverables:**
  - Public product browsing with dynamic multi-field search (product title, SKU, description) and category filtering.
  - Allowlisted database sorting (`createdAt`, `price`, `name` in `ASC`/`DESC`) preventing SQL injection.
  - Spring Data JPA pagination returning metadata (`page`, `size`, `totalElements`, `totalPages`).
  - Administrative product management: create product (`POST /api/v1/admin/products`), update metadata (`PUT /api/v1/admin/products/{id}`), and toggle active/inactive status (`PATCH /api/v1/admin/products/{id}/status`).
  - Reusable hardware SVG thumbnail fallback system rendering custom silicon circuit dies when product image URLs are absent or broken.

---

### Phase 4: Inventory Engine & Immutable Movement Ledger
* **Status:** `COMPLETED`
* **Focus:** Stock isolation, pessimistic database write locking, administrative adjustments, and append-only audit tracking.
* **Deliverables:**
  - Decoupled `inventory` table isolating physical stock writes from public catalog read traffic.
  - Database-level defense-in-depth: PostgreSQL hard check constraint `CHECK (available_stock >= 0)` preventing negative balances at the database engine level.
  - Explicit row-level locking via `LockModeType.PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`).
  - Administrative stock adjustments (`POST /api/v1/admin/inventory/{id}/adjustments`) supporting `ADD` (restock, intake) and `REMOVE` (damaged write-off, shrink) with mandatory business reason logging.
  - Overdraft prevention: stock removals exceeding available quantity are rejected with HTTP 409 Conflict before touching database rows.
  - Append-only immutable audit movement ledger (`inventory_movements`) recording previous stock, new stock, delta pills, movement type, actor ID, and business rationale.

---

### Phase 5: Concurrency-Safe Order Processing & Price Snapshots
* **Status:** `COMPLETED`
* **Focus:** Multi-threaded checkout concurrency, deadlock prevention, cart session management, and immutable historical price ledger.
* **Deliverables:**
  - Concurrency-safe customer checkout (`POST /api/v1/orders`).
  - **Deadlock-Free Sorted Lock Acquisition:** Product IDs in multi-item orders are sorted in ascending numerical order ($P_1 < P_2 < \dots < P_n$) before acquiring pessimistic row locks, mathematically breaking circular wait lock deadlocks.
  - Authoritative server-side price computation: client checkout payloads submit only product IDs and requested quantities; all pricing and totals are calculated authoritatively under row lock.
  - Immutable purchase unit price snapshots: `order_items` stores historical unit prices with `NUMERIC(12, 2)` precision at checkout time, decoupling past orders from future catalog price alterations.
  - Customer ownership boundary enforcement: customer tokens can only retrieve orders they personally created.

---

### Phase 6: PostgreSQL Integration Harness & RFC 4122 Idempotency
* **Status:** `IMPLEMENTATION COMPLETED` *(Local Compilation Clean; CI Verification Pending)*
* **Focus:** RFC 4122 v4 idempotency deduplication, transaction-gap race condition elimination, stalled/failed recovery, and Testcontainers integration test harness.
* **Deliverables:**
  - Idempotent checkout engine using client-supplied `Idempotency-Key` headers.
  - Request payload SHA-256 fingerprinting: mismatched payloads submitting a previously used key are immediately rejected with HTTP 422 Unprocessable Entity.
  - **Transaction-Gap Race Condition Resolution:** Eliminates the subtle window between committing an initial `PROCESSING` idempotency record and acquiring inventory row locks in `executeOrderPlacement()`. Delayed threads lock the idempotency record and detect `COMPLETED` status early, returning the cached response before touching inventory rows or creating duplicate orders.
  - Terminal status immunity: `markFailed()` and recovery logic cannot overwrite or downgrade an already completed order.
  - Stalled request recovery: requests stalled in `PROCESSING` status beyond the 2-minute threshold can be safely recovered by retries, updating `createdAt` to reset timeout windows.
  - PostgreSQL 16 Testcontainers integration test suite (`PostgresIntegrationTest.java`) defining 7 high-concurrency race condition scenarios.

---

### Phase 7: PostgreSQL CI Automation & Verification
* **Status:** `PENDING CI EXECUTION`
* **Focus:** Automated validation of all 7 PostgreSQL concurrency scenarios in GitHub Actions.
* **Deliverables:**
  - Dedicated GitHub Actions CI service container running real `postgres:16-alpine`.
  - CI environment flag `STOCKGUARD_ENFORCE_POSTGRES_TESTS=true` to guarantee execution without fallback skips.
  - Automated execution of all 7 integration test scenarios:
    1. 10 concurrent orders competing for 3 units (zero overselling).
    2. 5 simultaneous requests with identical idempotency key (deduplication).
    3. Multi-item transaction rollback atomicity.
    4. Inventory movement ledger 1:1 consistency.
    5. Security, RBAC & cross-customer ownership isolation.
    6. Stalled `PROCESSING` and `FAILED` recovery after timeout.
    7. Transaction-gap concurrency race elimination.
  - Automated pull request gating ensuring zero regressions to concurrency safeguards.

---

### Phase 8: Production Cloud Deployment
* **Status:** `PLANNED`
* **Focus:** Cloud infrastructure provisioning, managed relational database setup, production secrets management, and SSL termination.
* **Deliverables:**
  - Managed PostgreSQL instance on Supabase or Render Postgres with connection pooling (HikariCP / Supavisor).
  - Backend Spring Boot 3 container deployment on Render or Railway with health check probes (`/api/v1/health`).
  - Frontend React 18 production build deployment on Vercel, Netlify, or Cloudflare Pages with edge CDN caching.
  - Production secrets management: secure generation of 256-bit HMAC-SHA JWT secret keys and database credentials via cloud environment variables.
  - Cross-Origin Resource Sharing (CORS) hardening restricted to production domain origins.

---

### Phase 9: Live Testing & Portfolio Finalization
* **Status:** `PLANNED`
* **Focus:** End-to-end smoke verification, public telemetry validation, and demonstration walkthrough.
* **Deliverables:**
  - Live production smoke tests: user registration, catalog search, order checkout under concurrency protection, and admin inventory adjustments.
  - Live operational telemetry verification via `/api/v1/health`.
  - Finalized public repository presentation with production URL badges, architecture walkthrough, and recruitment documentation.

