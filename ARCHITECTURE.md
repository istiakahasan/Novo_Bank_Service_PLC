# System Architecture: Novo Bank Service PLC

Novo Bank is a production-grade retail and commercial banking microservices prototype built on Java 17 and Spring Boot 3.2.12. It utilizes an event-driven, ledger-centric architecture with immutable double-entry bookkeeping, multi-stage transaction settlement, and automated risk scoring.

---

## 1. High-Level Architecture Diagram

```
                                  CLIENTS & CHANNELS
              (Mobile Banking / Web Portal / Partner & ATM Integrations)
                                           |
                                           v
                             +---------------------------+
                             |     AUTH SERVICE :3008    |
                             |  (RS256 JWT, Token Refresh|
                             |   Revocation, RBAC Admin) |
                             +-------------+-------------+
                                           | Bearer JWT
                                           v
    +-----------------------------------------------------------------------------------+
    |                              CORE BANKING FABRIC                                  |
    |                                                                                   |
    |  +--------------------+       SYNC KYC        +--------------------+              |
    |  | CUSTOMER SVC :3003 |<----------------------|  LEDGER SVC :3004  |              |
    |  | Customer Profiles  |   GET /by-account     | Immutable Double-  |              |
    |  | KYC & AML Ratings  |---------------------->| Entry Balance Book |              |
    |  +--------------------+                       +---------+----------+              |
    |             |                                           |                         |
    |             | Outbox Event                              | Outbox Event            |
    |             v                                           v                         |
    |  +-----------------------------------------------------------------+              |
    |  |                     TRANSACTIONAL OUTBOX RELAY                  |              |
    |  |              Polls unpublished outbox rows every 5s             |              |
    |  +--------------------------------+--------------------------------+              |
    +-----------------------------------|-----------------------------------------------+
                                        |
                                        v
                 +----------------------------------------------+
                 |        UPSTASH QSTASH "banking-events"       |
                 |      Durable Pub/Sub & Fan-Out Broker        |
                 +----+------------------+------------------+---+
                      |                  |                  |
           +----------+                  |                  +----------+
           |                             |                             |
           v                             v                             v
+--------------------+        +--------------------+        +--------------------+
|   FRAUD SVC :3005  |        | NOTIFICATION :3006 |        |  SUPPORT SVC :3007 |
| Velocity Engine    |        | Email (Mailtrap)   |        | Auto-ticket Fraud  |
| AML Risk Rules     |        | SMS stub delivery  |        | Dispute Lifecycle  |
+--------------------+        +--------------------+        +--------------------+

    +-----------------------------------------------------------------------------------+
    |                      BUSINESS & SIMULATION MICROSERVICES                          |
    |                                                                                   |
    |  +--------------------+  +--------------------+  +--------------------+           |
    |  |   LOAN SVC :3009   |  |  TREASURY SVC :3010|  | REPORTING SVC :3011|           |
    |  | Products, Review,  |  | LCR Proxy, Net     |  | Customer PDF stmt  |           |
    |  | Amortization,      |  | Interest Margin,   |  | Regulatory CSV     |           |
    |  | Repayment Engine   |  | ALM Gap Buckets    |  | Async Worker       |           |
    |  +---------+----------+  +---------+----------+  +---------+----------+           |
    |            |                       |                       |                      |
    |            +-----------------------+-----------------------+                      |
    |            |                       |                       |                      |
    |  +---------+----------+  +---------+----------+            |                      |
    |  | PAYMENT RAIL :3012 |  |   CARD SVC :3013   |            |                      |
    |  | Simulated ACH,     |  | Virtual Debit Card |            |                      |
    |  | RTGS, SWIFT Rails  |  | Luhn, Daily Limits,|            |                      |
    |  | Worker Latency/Rev |  | 4-Stage Settlement |            |                      |
    |  +---------+----------+  +---------+----------+            |                      |
    |            |                       |                       |                      |
    |            +-----------------------+-----------------------+                      |
    |                                    | SYNC (System Key Auth)                       |
    |                                    v                                              |
    |                           LEDGER SERVICE :3004                                    |
    +-----------------------------------------------------------------------------------+

SHARED BACKING SERVICES:
- Supabase PostgreSQL: Shared relational persistence with RLS and dedicated tables.
- Upstash Redis: Distributed locking, idempotency cache, rate limiting, and tick counters.
- Upstash QStash: Scheduled webhooks (cron triggers for background workers).
```

---

## 2. Microservices Architecture Directory

### 1. Auth Service (`:3008`, `auth-server/`)
- **Role**: Identity provider and authorization authority.
- **Responsibilities**:
  - Issues RS256 signed JSON Web Tokens (15-minute lifespan).
  - Handles refresh token rotation with reuse detection (revoking compromised token families).
  - Maintains token revocation blocklist in Redis (`revoked:jti:{jti}`).
  - Manages system users and credential verification with BCrypt.

### 2. Customer Service (`:3003`, root `src/`)
- **Role**: Master customer data and compliance profile manager.
- **Responsibilities**:
  - Customer onboarding, identity documents, and status lifecycle (`unverified`, `pending`, `verified`, `rejected`).
  - AML risk rating classification (`low`, `medium`, `high`).
  - Account aggregation via synchronous Ledger lookup.

### 3. Ledger Service (`:3004`, root `src/`)
- **Role**: The authoritative financial book of record.
- **Responsibilities**:
  - Implements immutable double-entry bookkeeping (`ledger_entries`).
  - Enforces atomic balance mutation with non-negative balance checks (`balance_cents >= 0`).
  - Synchronous pre-debit KYC gate via Customer Service (`/api/customers/by-account/{id}`).
  - Distributed idempotency check (Redis + `UNIQUE(idempotency_key)` constraint).
  - Outbox publisher running on 5s intervals pushing events to QStash topic `banking-events`.
  - Account query endpoints (`GET /api/accounts/summary` for Treasury, `GET /api/accounts?accountNumber=` for Rails).

### 4. Fraud Service (`:3005`, `fraud-service/`)
- **Role**: Real-time and asynchronous fraud risk engine.
- **Responsibilities**:
  - Evaluates `transfer.completed` events against velocity rules (>10 transfers/min in Redis) and threshold rules (>$5,000).
  - Enforces onboarding risk assessment for newly registered customers.
  - Generates alerts in `fraud_alerts` and flags high-risk transactions.

### 5. Notification Service (`:3006`, `notification-service/`)
- **Role**: Asynchronous communication dispatcher.
- **Responsibilities**:
  - Consumes `transfer.completed` and `customer.created` events.
  - Sends transactional emails via Mailtrap SMTP.
  - Dispatches mock SMS alerts with sensitive data masking (`MaskingUtil`).
  - Caches customer lookup details in Redis with 600s TTL.

### 6. Support Service (`:3007`, `support-service/`)
- **Role**: Dispute management and customer support ticketing.
- **Responsibilities**:
  - Append-only ticket message thread with customer and staff roles.
  - Auto-creates dispute tickets upon ingestion of `fraud_flagged` events.
  - Publishes `ticket.created` and `ticket.status_changed` events.

### 7. Loan Service (`:3009`, `loan-service/`)
- **Role**: Credit origination, underwriting workflow, and loan repayment.
- **Responsibilities**:
  - Manages loan products (personal, auto, mortgage) with interest rates, amount limits, and tenures.
  - Credit workflow: `SUBMITTED` -> `UNDER_REVIEW` -> `APPROVED` / `REJECTED` -> `DISBURSED` -> `CLOSED`.
  - Disburses approved loans by posting transfers to the Ledger Service (`fromAccountId = LOAN_DISBURSEMENT_ACCOUNT_ID`).
  - Executes scheduled repayment deductions with interest amortization calculations.
  - QStash webhook (`POST /webhooks/loan-schedule`) marks overdue repayments.

### 8. Treasury / ALM Service (`:3010`, `treasury-service/`)
- **Role**: Balance sheet analytics, asset-liability management (ALM), and liquidity monitoring.
- **Responsibilities**:
  - Read-only analytical service querying Ledger aggregate deposits and Loan Service disbursed balances.
  - Computes Liquidity Coverage Ratio (LCR proxy):
    $$\text{LCR} = \frac{\text{Total Deposits} - \text{Total Loans}}{\text{Total Deposits}}$$
  - Computes Net Interest Margin (NIM) in basis points:
    $$\text{NIM} = \frac{\text{Loan Interest Income} - \text{Deposit Interest Expense}}{\text{Total Earning Assets}}$$
  - Computes ALM interest rate gap buckets (`<1m`, `1-3m`, `3-12m`, `1-5y`, `>5y`).
  - Generates periodic treasury snapshots in `treasury_snapshots`.

### 9. Reporting Service (`:3011`, `reporting-service/`)
- **Role**: Document generation and regulatory compliance extract generator.
- **Responsibilities**:
  - Asynchronously generates customer monthly account statements in PDF format using OpenHTMLtoPDF.
  - Generates regulatory CSV reports (`DAILY_TRANSACTION_SUMMARY`, `LOAN_PORTFOLIO`, `KYC_STATUS`).
  - Integrates with Supabase Storage for secure presigned file downloads.
  - Background report execution driven by QStash webhook `POST /webhooks/report-worker`.

### 10. Payment Rail Service (`:3012`, `payment-rail-service/`)
- **Role**: External payment network simulator (ACH, RTGS, SWIFT_SIM).
- **Responsibilities**:
  - Outbound transfers: Validates rail limits (ACH $10K, RTGS $1M, SWIFT $5M), debits customer internal account to `RAIL_SETTLEMENT_ACCOUNT_ID` via Ledger, advances to `PROCESSING`.
  - Inbound webhook: Validated via `X-Rail-Secret` or QStash signature; resolves customer account by account number and credits it from `RAIL_SETTLEMENT_ACCOUNT_ID`.
  - QStash scheduled worker (`POST /webhooks/rail-processor` every 30s):
    - Tracks latency ticks in Redis (`ACH`: 3 ticks ~90s, `RTGS`: 1 tick ~30s, `SWIFT_SIM`: 2 ticks ~60s).
    - Simulates 10% network decline rate with automated compensating reversal transfer from settlement account back to customer account (`status = RETURNED`).

### 11. Card Service (`:3013`, `card-service/`)
- **Role**: Virtual debit card issuance and four-stage clearing and settlement engine.
- **Responsibilities**:
  - Generates Luhn-valid 16-digit card numbers with 3-digit CVV and 4-year expiry.
  - Security invariant: Plaintext card numbers and CVVs are NEVER stored or returned. Only SHA-256 hashes (`card_number_hash`, `cvv_hash`) and masked strings (`****-****-****-1234`) are persisted.
  - Authorization flow (`POST /api/cards/authorize`):
    - Enforces daily spend limits ($5,000.00 default).
    - Calls Fraud Service `/webhooks/transfer-completed` synthetic check.
    - Creates Ledger hold via transfer to `CARD_SETTLEMENT_ACCOUNT_ID`.
    - Generates 6-character alphanumeric authorization code and sets stage `AUTHORIZED`.
  - Four-stage settlement state machine:
    1. `AUTHORIZED`: Hold active in settlement account.
    2. `BATCHED`: Picked up by 60s QStash cron (`/webhooks/card-batch`).
    3. `CLEARED`: Picked up by 120s QStash cron (`/webhooks/card-clear`).
    4. `FUNDED`: Picked up by 180s QStash cron (`/webhooks/card-fund`), completing the Ledger transfer to `MERCHANT_SETTLEMENT_ACCOUNT_ID`.
    - Compensating reversals: Failure at any stage moves transaction to `REVERSED` and executes a reversal transfer back to customer.

---

## 3. Data Model & Database Migrations

The database is structured in PostgreSQL with Row Level Security (RLS) enabled on all tables:

| Script | Tables Created / Modified | Purpose |
|---|---|---|
| `db/schema.sql` | `customers`, `accounts`, `transactions`, `ledger_entries`, `outbox` | Core banking, double-entry ledger, append-only rules, and outbox relay |
| `db/seed.sql` | Baseline test seed data | Test users (Alice, Bob, Charlie) and funded accounts |
| `db/002_fraud_alerts.sql` | `fraud_alerts` | Fraud detection alert records |
| `db/003_auth.sql` | `auth_users`, `refresh_tokens` | Authentication credentials and token rotation state |
| `db/005_support.sql` | `support_tickets`, `ticket_messages` | Customer support ticket threads |
| `db/006_auth_server.sql` | `auth_users`, `oauth2_registered_client` | Extended auth provider tables |
| `db/007_loans.sql` | `loan_products`, `loan_applications`, `loan_repayments` | Retail lending products, loan files, and repayment schedules |
| `db/008_treasury.sql` | `treasury_snapshots`, `alm_gap_buckets` | Treasury balance sheet snapshots and gap analyses |
| `db/009_reporting.sql` | `report_jobs` | PDF and CSV asynchronous reporting jobs |
| `db/010_payment_rails.sql` | `external_payments` | Outbound and inbound payment rail simulations |
| `db/011_cards.sql` | `cards`, `card_transactions` | Virtual card hashes and four-stage transaction records |

---

## 4. Ledger & Financial Integrity Invariants

1. **Strict Balance Conservation**: Money cannot be created or destroyed. Every transaction consists of matched pairs of debits and credits in `ledger_entries`.
2. **Append-Only Ledger**: PostgreSQL rules intercept and discard `UPDATE` and `DELETE` commands on `ledger_entries`. Corrections require compensating ledger transactions.
3. **Double-Spend Prevention**:
   - Redis distributed locking (`SET lock:{accountId} token NX EX 5`).
   - PostgreSQL unique constraints on `transactions.idempotency_key`.
   - Redis cache key (`idempotency:{key}`) returning identical cached responses on duplicate requests.
4. **Isolated Settlement Accounts**: Inter-service clearing uses dedicated system settlement accounts:
   - `CARD_SETTLEMENT_ACCOUNT_ID`: Holds authorized funds until card transaction funding.
   - `MERCHANT_SETTLEMENT_ACCOUNT_ID`: Receives cleared card funds upon final settlement.
   - `RAIL_SETTLEMENT_ACCOUNT_ID`: Absorbs external rail debit/credit positions.
   - `LOAN_DISBURSEMENT_ACCOUNT_ID`: Source account for newly disbursed customer loans.

---

## 5. Security & Inter-Service Authentication

Communication security is provided by the shared module `security-starter`:

1. **User Authentication (RBAC)**:
   - JWT RS256 token validation with local public key verification (`JWT_PUBLIC_KEY`).
   - Role matrix:
     - `ROLE_CUSTOMER`: Access limited strictly to own records via the `cid` (customerId) claim in token.
     - `ROLE_STAFF`: Full operational and back-office access.
2. **Service-to-Service Calls (System Token)**:
   - Inter-service REST calls bypass user tokens using HTTP headers:
     - `X-Service-Id`: Name of the calling service (e.g., `card-service`, `loan-service`).
     - `X-Service-Key`: Shared secret string matching `SYSTEM_TOKEN_SECRET`.
   - Populates an `AuthPrincipal` with role `ROLE_SYSTEM`.
3. **Webhook Signatures**:
   - Inbound webhook calls from QStash are verified against the cryptographic `Upstash-Signature` header (JWT HS256 containing a SHA-256 hash of the request body and timestamp validation with a 60-second skew tolerance).
