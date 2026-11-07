## Description
<!-- Provide a brief summary of the changes introduced in this PR. -->

## Type of Change
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] New feature (non-breaking change adding functionality)
- [ ] Concurrency / Performance optimization
- [ ] Documentation update
- [ ] CI / Build configuration

## Architectural Checklist
- [ ] **Zero Overselling**: Preserves `PESSIMISTIC_WRITE` row locks and database check constraints (`CHECK (available_stock >= 0)`).
- [ ] **Deadlock Free**: Ensures multi-item inventory locks are acquired in sorted product ID order.
- [ ] **Idempotency Preserved**: Order operations maintain client idempotency validation and terminal status immunity.
- [ ] **Immutable Audit Ledger**: All inventory quantity modifications append corresponding rows to `inventory_movements`.
- [ ] **Historical Financial Snapshots**: Past order line items capture unit prices with `NUMERIC(12, 2)` precision without mutating existing records.
- [ ] **Security Boundaries**: Enforces Spring Security `@PreAuthorize` guards; no raw credentials or JWT tokens exposed.
- [ ] **Database Integrity**: Any schema modifications are managed exclusively via versioned Flyway migrations.

## Verification & Testing
- [ ] Backend compiles cleanly (`mvnw test-compile`).
- [ ] Frontend builds with zero TypeScript errors (`npm run build`).
- [ ] Unit & slice test suite passes (`mvnw test -Dtest="!PostgresIntegrationTest"`).
- [ ] (If applicable) PostgreSQL integration test verified against PostgreSQL 16.

