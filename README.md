# Novo Bank Service PLC

Production-grade banking prototype built with Spring Boot 3.2 / Java 17, Supabase PostgreSQL, Upstash Redis, Upstash QStash, and Mailtrap.

```
                              CLIENT / API GATEWAY / WEB CONSOLE
                                              |
      +-------------------+-------------------+-------------------+-------------------+
      |                   |                   |                   |                   |
      v                   v                   v                   v                   v
AUTH SVC :3008      CUSTOMER SVC :3003  LEDGER SVC :3004    LOAN SVC :3009      CARD SVC :3013
(JWT, OAuth2,       (Profiles, KYC,     (Accounts, double-  (Origination,       (Virtual issuance,
 session/refresh)    AML rating)         entry transfers)    amortization)       holds & 4-stage)
      |                   ^                   ^                   |                   |
      |                   |    SYNC Ledger    |                   |                   |
      +-------------------+-------------------+-------------------+-------------------+
                          |                   |
                          | (Outbox Relay)    |
                          v                   |
                 +-----------------------+    |
                 | UPSTASH QSTASH TOPIC  |    |
                 |   "banking-events"    |    |
                 +-----------+-----------+    |
                             |                |
             +---------------+---------------+                |
             |                               |                |
             v                               v                v
      FRAUD SVC :3005               NOTIFICATION SVC :3006  SUPPORT SVC :3007
      (Velocity, High Value,         (Email & SMS async      (Disputes, Tickets,
       AML Rule Engine)               delivery stubs)         Audit Trail)

      ANALYTICAL & SIMULATION SERVICES:
      - TREASURY/ALM SVC :3010       (Liquidity coverage ratio, NIM, ALM gap buckets)
      - REPORTING SVC :3011          (PDF statements via OpenHTMLtoPDF, CSV regulatory extracts)
      - PAYMENT RAIL SVC :3012       (Simulated ACH, RTGS, SWIFT_SIM with settlement & reversals)

SHARED INFRASTRUCTURE:
- Supabase PostgreSQL (Single system of record, multi-schema tables, RLS enabled)
- Upstash Redis (Distributed locks, idempotency cache, rate limits, tick counters)
- Upstash QStash (Durable pub/sub event fan-out & scheduled cron webhooks)
```

## Microservices Catalog

| Service | Port | Directory | Description |
|---|---|---|---|
| **Customer Service** | `:3003` | root (`src/`) | Customer onboarding, KYC verification, AML risk classification |
| **Ledger Service** | `:3004` | root (`src/`) | Double-entry book of record, balance validation, transfer atomicity |
| **Fraud Service** | `:3005` | `fraud-service/` | Async transaction scoring, velocity rules, onboarding risk evaluation |
| **Notification Service** | `:3006` | `notification-service/` | Transaction alerts, customer onboarding emails, SMS dispatch stubs |
| **Support Service** | `:3007` | `support-service/` | Customer dispute management, ticket message threading, audit logs |
| **Auth Service** | `:3008` | `auth-server/` | RS256 JWT issuance, refresh token rotation, credential verification |
| **Loan Service** | `:3009` | `loan-service/` | Retail loan products, approval workflow, disbursement & repayments |
| **Treasury Service** | `:3010` | `treasury-service/` | ALM metrics: Liquidity Coverage Ratio (LCR), Net Interest Margin, Gap buckets |
| **Reporting Service** | `:3011` | `reporting-service/` | Customer PDF account statements & CSV regulatory summaries |
| **Payment Rail Service** | `:3012` | `payment-rail-service/` | External transfer simulation (ACH, RTGS, SWIFT) with clearing & reversals |
| **Card Service** | `:3013` | `card-service/` | Virtual debit cards (Luhn-valid, hashed), holds, 4-stage settlement |
| **Shared Security Starter** | N/A | `security-starter/` | Common library for JWT authentication, RBAC, and SYSTEM service keys |

## Quick Start

### 1. Database Migrations
Run the schema scripts in your Supabase SQL Editor in numerical order:
```bash
db/schema.sql              # Core customers, accounts, transactions, ledger_entries, outbox
db/seed.sql                # Seed customers and accounts
db/002_fraud_alerts.sql    # Fraud alert tables
db/003_auth.sql            # Auth users and refresh tokens
db/005_support.sql         # Support tickets and messages
db/006_auth_server.sql     # Auth server schemas
db/007_loans.sql           # Loan products, applications, repayments
db/008_treasury.sql        # ALM snapshots and gap buckets
db/009_reporting.sql       # Report jobs
db/010_payment_rails.sql   # External payments
db/011_cards.sql           # Virtual cards and card transactions
```

### 2. Environment Configuration
Copy `.env.example` to `.env` and provide credentials for:
- `SUPABASE_HOST`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `QSTASH_TOKEN`, `QSTASH_CURRENT_SIGNING_KEY`, `QSTASH_NEXT_SIGNING_KEY`
- `JWT_PUBLIC_KEY`, `JWT_PRIVATE_KEY`
- `SYSTEM_TOKEN_SECRET`
- `CARD_SETTLEMENT_ACCOUNT_ID`, `MERCHANT_SETTLEMENT_ACCOUNT_ID`, `RAIL_SETTLEMENT_ACCOUNT_ID`

### 3. Build All Modules
```bash
# 1. Install security-starter first
./mvnw -f security-starter/pom.xml install -DskipTests

# 2. Build the services
./mvnw -f pom.xml package -DskipTests
./mvnw -f fraud-service/pom.xml package -DskipTests
./mvnw -f notification-service/pom.xml package -DskipTests
./mvnw -f support-service/pom.xml package -DskipTests
./mvnw -f loan-service/pom.xml package -DskipTests
./mvnw -f treasury-service/pom.xml package -DskipTests
./mvnw -f reporting-service/pom.xml package -DskipTests
./mvnw -f payment-rail-service/pom.xml package -DskipTests
./mvnw -f card-service/pom.xml package -DskipTests
```

### 4. Running with Docker Compose
```bash
docker compose up -d --build
```

## Security & Architecture Overview

- **Authentication & RBAC**: Every public/internal endpoint enforces Bearer JWTs signed by Auth Service (roles `CUSTOMER` and `STAFF`).
- **Service-to-Service Communication**: Inter-service synchronous REST calls use `X-Service-Id` and `X-Service-Key` headers validated by `security-starter` (`SYSTEM` role).
- **Asynchronous Webhooks**: QStash webhooks are verified via cryptographic `Upstash-Signature` headers with clock-skew mitigation.
- **Double-Entry Ledger Integrity**: Immutable ledger entries with database-level append-only constraints and zero unbacked money movement.

Full architectural breakdown: [ARCHITECTURE.md](ARCHITECTURE.md)  
Recent system changes: [CHANGES.md](CHANGES.md)
