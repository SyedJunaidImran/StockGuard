# StockGuard — Testing Strategy & Verification Guide

This document provides a comprehensive guide to StockGuard's multi-tiered testing strategy, detailing the in-memory H2 unit/slice test suite, the real PostgreSQL 16 concurrency and idempotency test suite, GitHub Actions CI automation, and execution commands.

---

## 1. Testing Philosophy: Defense-in-Depth

StockGuard employs a two-tier testing methodology:
1. **Tier 1 (Fast Unit & Mock Slice Tests):** Validates controller HTTP status codes, request validation, service business logic, and security filters using Spring Boot MockMvc, Mockito, and in-memory H2.
2. **Tier 2 (Real PostgreSQL Concurrency & Transactional Integration Tests):** Validates transactional row locks (`PESSIMISTIC_WRITE`), schema-level check constraints (`CHECK (available_stock >= 0)`), multi-threaded race conditions, idempotency deduplication, and ledger consistency against **real PostgreSQL 16**.

> [!IMPORTANT]
> **Verification Status Distinction:**
> - Unit tests and H2 slice tests run and pass in local builds.
> - PostgreSQL 16 integration tests (`PostgresIntegrationTest.java`) are **fully authored, configured with Testcontainers, and wired into GitHub Actions CI**.
> - Formal verification of all 7 PostgreSQL concurrency scenarios remains **pending a remote GitHub Actions workflow execution** with an active PostgreSQL 16 service container.

---

## 2. In-Memory (H2) Test Suite

The fast unit test suite runs against in-memory H2 (`SPRING_PROFILES_ACTIVE=test`) and executes in seconds without requiring external database dependencies.

### Core Test Suites
- `AuthControllerTest`: Validates registration, duplicate email rejection (409), login credential verification, and JWT generation.
- `RbacTestControllerTest`: Validates that `@PreAuthorize` correctly permits `ROLE_ADMIN` and rejects `ROLE_CUSTOMER` (403 Forbidden).
- `ProductControllerTest` & `AdminProductControllerTest`: Validates pagination, allowlisted sorting, search queries, and admin catalog mutations.
- `OrderServiceTest`: Validates order entity mappings, total computation, and idempotency key reservation logic.
- `HealthControllerTest`: Validates `/api/v1/health` status codes, memory telemetry, and latency reporting.

### Command to Run Tier 1 Tests
```bash
cd backend
./mvnw test -Dtest="!PostgresIntegrationTest"
```

---

## 3. PostgreSQL 16 Real Database Integration Suite

File: `backend/src/test/java/com/stockguard/integration/PostgresIntegrationTest.java`

The integration suite tests real database behavior that in-memory databases (like H2) cannot authentically replicate:
- Real PostgreSQL row-level locks (`SELECT ... FOR UPDATE`).
- Real PostgreSQL engine check constraints (`CHECK (available_stock >= 0)`).
- Real multi-connection concurrency and transaction isolation.

### Dynamic Database Resolution Strategy
`PostgresIntegrationTest` uses a 3-step environment discovery mechanism:
1. **Dedicated Database:** If `STOCKGUARD_TEST_POSTGRES_URL` is set, connects directly to that PostgreSQL instance.
2. **Testcontainers Docker:** If Docker is running locally, spins up a dynamic `postgres:16-alpine` container.
3. **Graceful Skip:** If neither Docker nor external PostgreSQL is available on the host machine, gracefully skips execution via JUnit 5 `Assumptions.assumeTrue(...)`, allowing local builds on machines without Docker to succeed. In CI, `STOCKGUARD_ENFORCE_POSTGRES_TESTS=true` fails the build if PostgreSQL is missing.

---

## 4. The 7 PostgreSQL Integration Scenarios

The integration test suite specifically tests 7 high-concurrency failure modes:

### Scenario 1: 10 Concurrent Orders Competing for 3 Units (Zero Overselling)
* **Objective:** Verify that when 10 threads concurrently attempt to purchase 1 unit of a product with only 3 available in stock, overselling is physically impossible.
* **Mechanism:**
  - 10 threads submit simultaneous requests through MockMvc using separate customer accounts and unique idempotency keys.
  - An `ExecutorService` with 10 threads releases all requests simultaneously via a `CountDownLatch`.
* **Expected Outcome:**
  - Exactly 3 orders succeed with HTTP 201 Created.
  - Exactly 7 orders are rejected with HTTP 409 Conflict ("Insufficient stock").
  - Final `available_stock` in PostgreSQL is exactly `0` (never negative).
  - Database check constraint `chk_inventory_available_stock_non_negative` is never violated.

### Scenario 2: 5 Simultaneous Requests with the Same Idempotency-Key
* **Objective:** Verify that when a client rapidly double-clicks or experiences network retries with the identical `Idempotency-Key`, only 1 order and 1 inventory deduction occur.
* **Mechanism:**
  - 5 concurrent threads submit identical order requests with the identical `Idempotency-Key`.
* **Expected Outcome:**
  - Exactly 1 order is created in PostgreSQL.
  - Exactly 1 stock deduction occurs.
  - All 5 requests receive the identical `OrderResponse` (matching `orderNumber` and `totalAmount`).
  - Exactly 1 `ORDER_PURCHASE` ledger entry is written.

### Scenario 3: Multi-Item Transaction Rollback Atomicity
* **Objective:** Verify atomic transaction rollback when an order has multiple line items and an error occurs on item 2.
* **Mechanism:**
  - Order requests Item A (in stock) and Item B (insufficient stock).
* **Expected Outcome:**
  - Transaction rolls back completely.
  - Item A stock is NOT deducted.
  - Zero order records are created.
  - Zero partial `inventory_movements` rows are written.

### Scenario 4: Inventory Movement Ledger Consistency
* **Objective:** Verify that every stock alteration matches the append-only ledger deltas 1:1.
* **Mechanism:**
  - Performs an administrative restock (`+10`), an order purchase (`-3`), and a manual adjustment (`-2`).
* **Expected Outcome:**
  - `inventory_movements` contains exactly 3 entries.
  - Sum of `quantity_change` equals the net difference in `inventory.available_stock`.
  - Previous and new stock columns accurately track state transitions.

### Scenario 5: Security, RBAC & Customer Ownership Isolation
* **Objective:** Verify that customer tokens cannot access other customers' orders or administrative endpoints.
* **Mechanism:**
  - Customer A places an order.
  - Customer B attempts to retrieve Customer A's order (`GET /api/v1/orders/{id}`).
  - Customer A attempts to access `/api/v1/admin/inventory`.
* **Expected Outcome:**
  - Cross-customer access returns HTTP 404 / 403 (zero data leakage).
  - Customer accessing admin routes returns HTTP 403 Forbidden.
  - Unauthenticated access returns HTTP 401 Unauthorized.

### Scenario 6: Stalled PROCESSING and FAILED State Recovery
* **Objective:** Verify that if an order process crashes mid-flight and leaves an idempotency record in `PROCESSING` status, a client retry after the 2-minute timeout can safely recover.
* **Mechanism:**
  - Seeds an `idempotency_records` row in `PROCESSING` status with `created_at` backdated 3 minutes.
  - Submits a retry with the identical key and payload.
* **Expected Outcome:**
  - Stalled lock is recognized as timed out.
  - Retry successfully places the order and updates status to `COMPLETED`.

### Scenario 7: Transaction-Gap Concurrency Race Elimination
* **Objective:** Verify that a delayed original request that was superseded by a timeout retry does not execute a second stock deduction when it wakes up.
* **Mechanism:**
  - Request 1 reserves the key (`PROCESSING`) and is delayed before `executeOrderPlacement()`.
  - Request 2 enters after the timeout, acquires locks, deducts stock, and commits `COMPLETED`.
  - Request 1 resumes and calls `lockActiveRecord()`.
* **Expected Outcome:**
  - Request 1 detects that the record was already completed by another thread.
  - Returns the cached completed order immediately without executing a second stock deduction.
  - Zero duplicate inventory deductions occur.

---

## 5. GitHub Actions CI/CD Pipeline

File: `.github/workflows/ci.yml`

The CI pipeline runs automatically on all pushes and pull requests to `main` and `develop`:

```yaml
jobs:
  backend-unit-test:
    name: Backend Unit & Slice Tests (H2)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
      - run: chmod +x backend/mvnw
      - run: ./mvnw -B test -Dtest="!PostgresIntegrationTest" --file pom.xml
        working-directory: backend
        env:
          SPRING_PROFILES_ACTIVE: test

  postgres-integration-test:
    name: PostgreSQL 16 Integration Tests (Real Database)
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: stockguard_test
          POSTGRES_USER: testuser
          POSTGRES_PASSWORD: testpassword
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: 'maven'
      - run: chmod +x backend/mvnw
      - run: ./mvnw -B test -Dtest=PostgresIntegrationTest --file pom.xml
        working-directory: backend
        env:
          SPRING_PROFILES_ACTIVE: test
          STOCKGUARD_TEST_POSTGRES_URL: jdbc:postgresql://localhost:5432/stockguard_test
          STOCKGUARD_TEST_POSTGRES_USER: testuser
          STOCKGUARD_TEST_POSTGRES_PASSWORD: testpassword
          STOCKGUARD_ENFORCE_POSTGRES_TESTS: "true"

  frontend-build:
    name: Frontend Build & Type Check (Node 22)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: frontend/package.json
      - run: npm install
        working-directory: frontend
      - run: npm run build
        working-directory: frontend
```

---

## 6. Local Testing Execution Reference

### Running Backend Unit & Slice Tests (H2)
```bash
# Windows
cd backend
.\mvnw.cmd test -Dtest="!PostgresIntegrationTest"

# Linux / macOS
cd backend
./mvnw test -Dtest="!PostgresIntegrationTest"
```

### Running Real PostgreSQL Integration Tests Locally
If you have local Docker running:
```bash
cd backend
./mvnw test -Dtest=PostgresIntegrationTest
```
Or with an external PostgreSQL test database:
```bash
cd backend
$env:STOCKGUARD_TEST_POSTGRES_URL="jdbc:postgresql://localhost:5432/stockguard_test"
$env:STOCKGUARD_TEST_POSTGRES_USER="postgres"
$env:STOCKGUARD_TEST_POSTGRES_PASSWORD="your-password"
.\mvnw.cmd test -Dtest=PostgresIntegrationTest
```

### Running Frontend Type-Check & Build
```bash
cd frontend
npm run build
```
Ensures zero TypeScript compilation errors and builds the production bundle with Vite.

