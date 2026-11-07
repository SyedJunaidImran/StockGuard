# StockGuard — Production Cloud Deployment Guide

This guide provides end-to-end instructions for deploying StockGuard into production using:
* **Database**: Hosted Supabase PostgreSQL 16 (Flyway managed)
* **Backend**: Render Web Service (Docker / Spring Boot 3 / Java 21)
* **Frontend**: Vercel (React 18 + Vite SPA)

---

## 1. Architecture & Deployment Prerequisites

### Deployment Topology
```
┌─────────────────────────────────┐
│  Frontend (Vercel CDN)          │
│  React 18 + Vite SPA            │
└──────────────┬──────────────────┘
               │ HTTPS (REST API / JSON)
               ▼
┌─────────────────────────────────┐
│  Backend (Render Web Service)   │
│  Spring Boot 3 / Java 21 Docker │
└──────────────┬──────────────────┘
               │ JDBC over SSL (Port 5432)
               ▼
┌─────────────────────────────────┐
│  Database (Supabase PostgreSQL) │
│  Direct / Session Pooler        │
└─────────────────────────────────┘
```

### Accounts Required
- [Supabase](https://supabase.com) account & PostgreSQL 16 project
- [Render](https://render.com) account connected to your GitHub repository
- [Vercel](https://vercel.com) account connected to your GitHub repository

---

## 2. Supabase Connection Requirements

StockGuard relies on database-level pessimistic write row locking (`SELECT ... FOR UPDATE`), transaction-scoped idempotency reservations, and Flyway schema migration locks. These features require stateful, session-scoped PostgreSQL connections.

### Choosing the Correct Supabase Connection Type
In the Supabase Dashboard (**Project Settings** &rarr; **Database** &rarr; **Connection string** &rarr; **URI**):

| Connection Type | Port | Supported? | Rationale |
| :--- | :---: | :---: | :--- |
| **Direct Connection** | `5432` | **YES (Recommended)** | Direct stateful TCP connection to PostgreSQL; supports all locking, prepared statements, and DDL migrations. |
| **Session Pooler (Supavisor)** | `5432` | **YES** | Connects through Supavisor in session mode. Each client gets a dedicated backend connection while active; supports row locks and Flyway. |
| **Transaction Pooler (PgBouncer)** | `6543` | ❌ **DO NOT USE** | Connections are pooled at the single-statement transaction level. Does **not** support session-level locks, prepared statements, or Flyway migration locking. |

### JDBC Connection String Format
Configure your JDBC connection string using your project's reference and password:

* **Direct Connection (Port 5432)**:
  ```text
  jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require
  ```
* **Session Pooler (Port 5432)**:
  ```text
  jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
  ```

> [!NOTE]
> When connecting via the Supavisor pooler, the database username format is `postgres.<project-ref>`, whereas direct connections use plain `postgres`. Always verify the username format displayed in your Supabase connection modal.

---

## 3. Flyway Production Migration Considerations

StockGuard manages schema evolution through versioned Flyway scripts (`V1__initial_schema.sql` through `V4__init_existing_product_inventory.sql`).

### Understanding `SPRING_FLYWAY_BASELINE_ON_MIGRATE`
- **Fresh Supabase Database (Default & Recommended)**:
  Set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false`.
  Flyway will start from version `0`, detect that no schema history table exists, create `flyway_schema_history`, and execute all migrations (`V1` &rarr; `V2` &rarr; `V3` &rarr; `V4`) sequentially.
- **When is `baseline-on-migrate=true` Safe?**:
  Only enable `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` if your Supabase database **already contains tables** created outside of Flyway (e.g. manually imported via the SQL editor).
  > [!WARNING]
  > Enabling `baseline-on-migrate: true` on an existing database tells Flyway to baseline at version 1, causing Flyway to **skip `V1__initial_schema.sql`**. If tables are missing, skipping V1 will cause runtime errors.

---

## 4. Render Backend Deployment

StockGuard provides a multi-stage `backend/Dockerfile` using Eclipse Temurin 21 with non-root security and JVM container memory flags.

### Memory Optimization for Render Free Tier (512MB RAM)
The Dockerfile configures:
- `-XX:+UseContainerSupport`: Instructs the JVM to respect cgroup memory limits.
- `-XX:MaxRAMPercentage=60.0`: Limits heap allocation to ~307MB on a 512MB container, reserving ~205MB for Metaspace, CodeCache, thread stacks, and OS cgroup buffers.
- `-Xss512k`: Reduces per-thread stack size from default 1024k to 512k.
- `-XX:+ExitOnOutOfMemoryError`: Halts the JVM immediately on OOM so Render can perform a clean container restart.

### Render Configuration Options

#### Option A: Automated Blueprint Deployment (`render.yaml`)
1. In the Render Dashboard, click **New +** &rarr; **Blueprint**.
2. Select your `StockGuard` repository.
3. Render reads [`render.yaml`](../render.yaml) automatically and provisions the `stockguard-backend` service.
4. Supply the secret database values when prompted.

#### Option B: Manual Web Service Setup
1. In the Render Dashboard, click **New +** &rarr; **Web Service**.
2. Connect your GitHub repository.
3. Configure the service settings based on your chosen Root Directory:
   - **Name**: `stockguard-backend`
   - **Region**: Choose closest to your Supabase region (e.g. `Oregon (US West)` or `Frankfurt (EU)`)
   - **Branch**: `main`
   - **Runtime**: `Docker`
   - **Plan**: `Free`
   - **Path Configuration**:
     - *If Root Directory is left as repository root (`.`)*:
       - **Dockerfile Path**: `backend/Dockerfile`
       - **Docker Context**: `backend`
     - *If Root Directory is set to `backend`*:
       - **Dockerfile Path**: `Dockerfile`
       - **Docker Context**: `.`
4. Expand **Advanced Settings**:
   - **Health Check Path**: `/api/v1/health`
5. Inject the required Environment Variables:

| Environment Variable | Value / Instructions |
| :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<supabase-host>:5432/postgres?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` *(or `postgres.<project-ref>` for pooler)* |
| `SPRING_DATASOURCE_PASSWORD` | `<your-supabase-db-password>` |
| `SPRING_FLYWAY_ENABLED` | `true` |
| `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | `false` *(set `true` only if pre-existing tables exist)* |
| `JWT_SECRET` | *(64-char hex string — see generator command below)* |
| `JWT_EXPIRATION_MS` | `86400000` *(24 hours)* |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` *(update with Vercel URL after Step 5)* |
| `JAVA_TOOL_OPTIONS` | `-XX:+UseContainerSupport -XX:MaxRAMPercentage=60.0 -Xss512k -XX:+ExitOnOutOfMemoryError` |
| `STOCKGUARD_ADMIN_EMAIL` | *(Optional)* Dedicated admin email address to provision or synchronize (e.g. `admin@yourdomain.com`) |
| `STOCKGUARD_ADMIN_PASSWORD` | *(Optional)* Secure admin password (sets or rotates the admin password safely on startup without database edits) |

### Generating a Secure 256-Bit JWT Secret
Run one of the following commands in your local terminal to generate a secure secret:

* **Linux / macOS / Git Bash**:
  ```bash
  openssl rand -hex 32
  ```
* **PowerShell**:
  ```powershell
  -join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })
  ```

---

## 5. Vercel Frontend Deployment

1. Open your [Vercel Dashboard](https://vercel.com/dashboard) and click **Add New...** &rarr; **Project**.
2. Import your `StockGuard` GitHub repository.
3. Configure the project settings:
   - **Framework Preset**: `Vite`
   - **Root Directory**: Select `frontend`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
   - **Install Command**: `npm install`
4. Expand **Environment Variables**:
   - **Key**: `VITE_API_BASE_URL`
   - **Value**: `https://<your-render-backend-name>.onrender.com/api/v1`
5. Click **Deploy**.
6. The `frontend/vercel.json` rewrite configuration ensures all client routes resolve correctly without 404s.

---

## 6. Finalizing CORS & Deployment Order

Due to the mutual dependency between the backend (which needs the frontend's origin for CORS) and the frontend (which needs the backend's URL for API requests):

1. **Deploy Backend first**: Set `CORS_ALLOWED_ORIGINS=http://localhost:5173`.
2. **Deploy Frontend on Vercel**: Inject `VITE_API_BASE_URL=https://<your-render-app>.onrender.com/api/v1`.
3. **Update Backend CORS on Render**:
   - In Render Dashboard &rarr; `stockguard-backend` &rarr; **Environment**.
   - Update `CORS_ALLOWED_ORIGINS` to:
     ```text
     https://<your-vercel-app>.vercel.app,http://localhost:5173
     ```
   - Render will perform a rolling deployment.

---

## 7. Post-Deployment Verification Checklist

Verify the live deployment using these sequential checks:

### 1. Health Probe Verification
```bash
curl -s https://<your-render-backend>.onrender.com/api/v1/health
```
**Expected Response**:
```json
{
  "success": true,
  "message": "Service is operational",
  "data": {
    "status": "UP",
    "service": "StockGuard Backend",
    "version": "1.0.0",
    "environment": "production",
    "database": "CONNECTED",
    "uptimeSeconds": 30
  }
}
```

### 2. Frontend Catalog & Live Inventory
- Open `https://<your-vercel-app>.vercel.app`.
- Verify the product catalogue loads with live emerald ("In Stock") or amber ("Low Stock") badges.

### 3. Customer Registration & JWT Flow
- Register a new customer account via the UI.
- Verify navigation bar reflects the authenticated session.

### 4. Idempotent Order Placement
- Add products to cart and proceed through checkout.
- Verify that order totals match authoritative server-side calculation.
- Verify product stock decrements immediately upon return to the catalog.

### 5. Administrative Console & Audit Ledger
- Log in with your configured administrator credentials (provisioned via `STOCKGUARD_ADMIN_EMAIL` and `STOCKGUARD_ADMIN_PASSWORD` in Render).
- Access the **Inventory Management** console.
- Open the **Inventory Ledger** drawer and verify the append-only movement record reflects the order purchase with previous stock, quantity change, new stock, and customer actor ID.
- Perform a manual stock adjustment (+Restock or -Write-off) with an audit reason and verify immediate ledger propagation.
