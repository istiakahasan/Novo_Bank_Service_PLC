# Novo Bank Service PLC

A full-stack, enterprise-grade banking prototype built with **Spring Boot 3.2 / Java 17**, **Next.js 14 App Router (TypeScript)**, **Supabase PostgreSQL & Realtime**, **Upstash Redis**, and **Upstash QStash**.

The platform implements an event-driven, ledger-centric architecture with immutable double-entry bookkeeping, multi-stage transaction settlement, automated fraud scoring, collateral tracking, debt collections, ALM analytics, external payment rails, virtual cards, and dual web consoles for retail customers and bank staff.

---

## Architecture at a Glance

```
                              CLIENTS / BROWSERS
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼                                               ▼
    CUSTOMER PORTAL :3100                           AGENT CONSOLE :3200
  (Next.js 14, Tailwind, Zod)                     (Next.js 14, Realtime, ALM)
              │                                               │
              └───────────────────────┬───────────────────────┘
                                      ▼
                      BFF / PROXY LAYER (/api/proxy/*)
                   (HttpOnly Cookie Auth, RS256 JWT)
                                      │
      ┌───────────────┬───────────────┼───────────────┬───────────────┐
      ▼               ▼               ▼               ▼               ▼
AUTH SVC :3008  CUSTOMER :3003  LEDGER :3004    LOAN SVC :3009  CARD SVC :3013
(RS256 JWT,     (KYC, AML,      (Double-entry,  (Origination,   (Virtual cards,
 Refresh)        Profiles)       Accounts)       Collateral,     Holds & 4-Stage
                                                 Collections)    Settlement)
                                      │
                               (Outbox Relay)
                                      ▼
                             UPSTASH QSTASH TOPIC
                               "banking-events"
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼                                               ▼
       FRAUD SVC :3005                               NOTIFICATION SVC :3006
   (Velocity, AML Scoring)                         (Email & SMS Delivery)
              │
              ▼
      SUPPORT SVC :3007
  (Tickets & Audit Logs)

ANALYTICAL & SIMULATION MICROSERVICES:
- TREASURY / ALM SERVICE :3010  (Liquidity Coverage Ratio, Net Interest Margin, ALM Gap Buckets)
- REPORTING SERVICE :3011       (OpenHTMLtoPDF Statements, CSV Regulatory Extracts)
- PAYMENT RAIL SERVICE :3012    (Simulated ACH, RTGS, SWIFT Rails with Settlement & Reversals)

SHARED PERSISTENCE & CACHE:
- Supabase PostgreSQL (Relational system of record, multi-schema, RLS)
- Supabase Realtime (WebSocket push for support tickets, ledger transactions, loan delinquency)
- Upstash Redis (Distributed locks, idempotency cache, rate limiting, settlement tick counters)
- Upstash QStash (Durable pub/sub event fan-out & scheduled cron webhooks)
```

See [architecture.mmd](architecture.mmd) for the complete Mermaid diagram and [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design specifications.

---

## Services & Applications Catalog

### Frontend Applications (Next.js 14 Monorepo)

| Application / Package | Port | Directory | Description |
|---|---|---|---|
| **Customer Portal** | `:3100` | `apps/customer-portal/` | Retail banking portal: dashboard, internal transfers, loan application with collateral, virtual card management, external payment wizard (ACH/RTGS/SWIFT), statement downloads, and real-time transaction updates. |
| **Agent Console** | `:3200` | `apps/agent-console/` | Bank staff workstation: customer KYC verification, loan underwriting and disbursement, debt collections with typed write-off actions, real-time support tickets, ALM treasury metrics, and regulatory reports. |
| **Shared Package** | N/A | `packages/shared/` | Shared TypeScript interfaces, typed API clients, session management helpers, `roleGuard` HOC, `SessionTimeoutProvider` (15m auto-logout), currency formatters, and reusable UI components. |

### Backend Microservices (Spring Boot 3.2 / Java 17)

| Microservice | Port | Directory | Description |
|---|---|---|---|
| **Customer Service** | `:3003` | root (`src/`) | Customer onboarding, identity verification (KYC), AML risk tiers (`LOW`, `MEDIUM`, `HIGH`), and account aggregation. |
| **Ledger Service** | `:3004` | root (`src/`) | Double-entry book of record, balance validation, transfer atomicity, settlement holds, and transactional outbox. |
| **Fraud Service** | `:3005` | `fraud-service/` | Async transaction scoring, velocity rules (frequency & amount limits), and high-value risk flags. |
| **Notification Service** | `:3006` | `notification-service/` | Transaction email alerts (Mailtrap/Mailhog), customer welcome notifications, and SMS stubs. |
| **Support Service** | `:3007` | `support-service/` | Customer dispute management, automated fraud-alert ticket creation, and ticket message threading. |
| **Auth Service** | `:3008` | `auth-server/` | RS256 JWT access token issuance, rotating opaque refresh tokens, BCrypt authentication, and Redis JTI revocation list. |
| **Loan Service** | `:3009` | `loan-service/` | Loan products, underwriting workflow, disbursement, repayment engine, **collateral tracking**, and **collections recovery**. |
| **Treasury Service** | `:3010` | `treasury-service/` | Asset-Liability Management (ALM): Liquidity Coverage Ratio (LCR), Net Interest Margin (NIM), and maturity gap buckets. |
| **Reporting Service** | `:3011` | `reporting-service/` | Customer PDF account statements (OpenHTMLtoPDF) and CSV regulatory extracts (transactions, portfolio, KYC). |
| **Payment Rail Service** | `:3012` | `payment-rail-service/` | Simulated external payment rails (ACH, RTGS, SWIFT) with Redis settlement ticks, network decline rates, and compensating reversals. |
| **Card Service** | `:3013` | `card-service/` | Virtual debit card issuance (Luhn-16, hashed CVV), daily spending limits, ledger holds, and 4-stage settlement simulation. |
| **Shared Security Starter** | N/A | `security-starter/` | Common library providing RS256 JWT validation, RBAC (`CUSTOMER`, `STAFF`, `SYSTEM`), service-key auth, and QStash signature verification. |

---

## Default Login Credentials

Seed test accounts configured in `init-db.sql` / `db/003_auth.sql`:

| Email | Password | Role | Target Application | Initial Funded Balance | Customer / Agent ID |
|---|---|---|---|---|---|
| `alice@test.com` | `TestPass123!` | `CUSTOMER` | Customer Portal (`http://localhost:3100`) | $1,000.00 (`NOVO0000000001`) | `11111111-1111-4111-8111-111111111111` |
| `bob@test.com` | `TestPass123!` | `CUSTOMER` | Customer Portal (`http://localhost:3100`) | $500.00 (`NOVO0000000002`) | `22222222-2222-4222-8222-222222222222` |
| `staff@test.com` | `TestPass123!` | `STAFF` | Agent Console (`http://localhost:3200`) | N/A (Bank Officer Desk) | `dddddddd-dddd-4ddd-8ddd-dddddddddddd` |

> [!TIP]
> - **Customer Portal (`:3100`)**: Log in with `alice@test.com` or `bob@test.com` to test retail banking workflows: account transfers, loan applications with collateral pledges, virtual debit card issuance and freeze/unfreeze, outbound external payment rails (ACH/RTGS/SWIFT), and account statement downloads.
> - **Agent Console (`:3200`)**: Log in with `staff@test.com` to test back-office officer workflows: customer KYC queue verification, loan underwriting and one-click disbursement, debt recovery actions (logging phone calls, demand letters, and typed write-off authorizations), ALM treasury dashboard, and regulatory report generation.

---

## Quick Start

### 1. Database Migrations

Execute the migration scripts in your Supabase SQL Editor in numerical order:

```bash
db/schema.sql                          # Core schema: customers, accounts, transactions, ledger_entries, outbox
db/seed.sql                            # Seed customers and accounts
db/002_fraud_alerts.sql                # Fraud rules and alerts
db/003_auth.sql                        # Core auth users and credentials
db/005_support.sql                     # Support tickets and messages
db/006_auth_server.sql                 # Refresh tokens and session tables
db/007_loans.sql                       # Loan products, applications, and repayments
db/008_treasury.sql                    # ALM snapshots and gap buckets
db/009_reporting.sql                   # Report jobs and generated extract URLs
db/010_payment_rails.sql               # External payments and simulated settlement tracking
db/011_cards.sql                       # Virtual debit cards and 4-stage transaction records
db/012_loan_collateral_collections.sql # Loan collateral registry and collections recovery actions
```

### 2. Environment Configuration

Copy the example environment files and configure your credentials:

```bash
# Backend environment
cp .env.example .env

# Frontend environment (Next.js monorepo)
cp .env.local.example .env.local
```

Key environment variables:
- `SUPABASE_HOST`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`: Supabase PostgreSQL credentials.
- `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`: Supabase client credentials for Realtime WebSocket subscriptions.
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`: Upstash Redis connection.
- `QSTASH_TOKEN`, `QSTASH_CURRENT_SIGNING_KEY`, `QSTASH_NEXT_SIGNING_KEY`: QStash webhook validation.
- `JWT_PUBLIC_KEY`, `JWT_PRIVATE_KEY`: RS256 key pair for auth tokens.
- `SYSTEM_TOKEN_SECRET`: Shared secret for inter-service REST calls.
- `CARD_SETTLEMENT_ACCOUNT_ID`, `MERCHANT_SETTLEMENT_ACCOUNT_ID`, `RAIL_SETTLEMENT_ACCOUNT_ID`: Internal Ledger settlement accounts.

### 3. Build & Run the Backend

```bash
# 1. Build and install the security starter
./mvnw -f security-starter/pom.xml install -DskipTests

# 2. Package all microservices
./mvnw -f pom.xml package -DskipTests
./mvnw -f auth-server/pom.xml package -DskipTests
./mvnw -f fraud-service/pom.xml package -DskipTests
./mvnw -f notification-service/pom.xml package -DskipTests
./mvnw -f support-service/pom.xml package -DskipTests
./mvnw -f loan-service/pom.xml package -DskipTests
./mvnw -f treasury-service/pom.xml package -DskipTests
./mvnw -f reporting-service/pom.xml package -DskipTests
./mvnw -f payment-rail-service/pom.xml package -DskipTests
./mvnw -f card-service/pom.xml package -DskipTests

# 3. Alternatively, launch everything with Docker Compose
docker compose up -d --build
```

### 4. Build & Run the Frontend

```bash
# 1. Install frontend dependencies
npm install

# 2. Run TypeScript type checks across all workspaces
npm run typecheck

# 3. Run shared package unit tests
npm run test

# 4. Start Customer Portal (runs on http://localhost:3100)
npm run dev:customer

# 5. Start Agent Console (runs on http://localhost:3200)
npm run dev:agent
```

---

## Security Invariants & Principles

1. **Double-Entry Invariant**: Every monetary movement consists of matched debit and credit entries with balanced net zero. Balances are validated before any debit.
2. **Token Security & Cookie BFF**: JWT access tokens are never accessible to client-side JavaScript. The Next.js BFF maintains session state in `httpOnly`, `Secure`, `SameSite=Lax` cookies.
3. **Role-Based Access Control**:
   - `CUSTOMER`: Access restricted to self-owned accounts, cards, applications, and statements.
   - `STAFF`: Access to underwriting, collections, treasury analytics, regulatory reports, and dispute resolution.
   - `SYSTEM`: Required for inter-service communication (authenticated via `X-Service-Id` and `X-Service-Key`).
4. **Card Security**: Plaintext card numbers and CVVs are never persisted or returned by APIs. Storage uses SHA-256 hashes (`card_number_hash`, `cvv_hash`) and masked display (`****-****-****-1234`).
5. **Idempotency**: All transfer and external payment endpoints require an `Idempotency-Key` header cached in Redis to prevent duplicate debit executions.

---

## Documentation Links

- Detailed Architecture & Design: [ARCHITECTURE.md](ARCHITECTURE.md)
- Complete Mermaid Diagram: [architecture.mmd](architecture.mmd)
- Full Changelog: [CHANGES.md](CHANGES.md)
