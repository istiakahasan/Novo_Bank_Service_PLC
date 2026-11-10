# Changes

## 2026-11-10 — Card Service (:3013)

- **New Microservice**: Added `card-service/` (port `:3013`, Java 17, Spring Boot 3.2.12).
- **Database Schema**: Added `db/011_cards.sql` creating `cards` and `card_transactions` with status and stage check constraints.
- **Virtual Debit Card Issuance**:
  - `POST /api/cards`: Generates 16-digit Luhn-valid card numbers, 3-digit CVVs, and 4-year expiration.
  - Security invariant: Plaintext card numbers and CVVs are NEVER stored or returned. Plaintext is hashed with SHA-256 (`card_number_hash`, `cvv_hash`) and masked (`****-****-****-1234`).
  - `GET /api/cards`: Customer sees own masked cards; Staff sees all.
  - `PATCH /api/cards/{id}/status`: Customer can toggle between `ACTIVE` and `FROZEN`; `CANCELLED` is terminal and Staff-only.
- **Card Authorization**:
  - `POST /api/cards/authorize`: Merchant terminal authorization (SYSTEM role).
  - Validates card existence, CVV hash, status (`ACTIVE`), and daily spend limit ($5,000.00 default).
  - Integrates with Fraud Service (`/webhooks/transfer-completed`) for synthetic pre-authorization screening.
  - Places a ledger hold by executing a synchronous transfer from the customer account to `CARD_SETTLEMENT_ACCOUNT_ID` (`card-auth-{cardTxnId}`).
  - Generates a 6-character alphanumeric authorization code and sets transaction stage to `AUTHORIZED`.
- **Four-Stage Settlement Simulation**:
  - `POST /webhooks/card-batch`: QStash schedule (every 60s) advances `AUTHORIZED` -> `BATCHED`.
  - `POST /webhooks/card-clear`: QStash schedule (every 120s) advances `BATCHED` -> `CLEARED`.
  - `POST /webhooks/card-fund`: QStash schedule (every 180s) moves `CLEARED` -> `FUNDED` by transferring held funds from `CARD_SETTLEMENT_ACCOUNT_ID` to `MERCHANT_SETTLEMENT_ACCOUNT_ID`.
  - Compensating reversals: Failure at any settlement stage transitions transaction to `REVERSED` and executes an automatic compensating reversal from `CARD_SETTLEMENT_ACCOUNT_ID` back to the customer's account (`card-reverse-{cardTxnId}`).
- **Deployment**: Added `services/card/Dockerfile` with multi-stage build caching `security-starter`.

---

## 2026-11-10 — Payment Rail Service (:3012)

- **New Microservice**: Added `payment-rail-service/` (port `:3012`, Java 17, Spring Boot 3.2.12).
- **Database Schema**: Added `db/010_payment_rails.sql` creating `external_payments` tracking direction, rails, settlement status, and idempotency keys.
- **Simulated Payment Rails**:
  - Outbound transfers (`POST /api/external-payments/outbound`): Enforces rail limits (`ACH`: $10,000; `RTGS`: $1,000,000; `SWIFT_SIM`: $5,000,000). Debits customer internal account into `RAIL_SETTLEMENT_ACCOUNT_ID` via Ledger Service and advances status to `PROCESSING`.
  - Inbound webhook (`POST /webhooks/inbound-payment`): Authenticated via `X-Rail-Secret` or QStash signature. Resolves destination internal account by account number and credits it from `RAIL_SETTLEMENT_ACCOUNT_ID`.
  - Idempotency: Retries with the same idempotency key safely return the existing payment without duplicate ledger movements.
- **Scheduled Rail Processing & Compensating Reversals**:
  - `POST /webhooks/rail-processor`: QStash scheduled worker (every 30s) advancing tick counter in Redis (`ACH` 3 ticks ~90s, `RTGS` 1 tick ~30s, `SWIFT_SIM` 2 ticks ~60s).
  - Simulates 10% network decline rate with automated compensating reversal from `RAIL_SETTLEMENT_ACCOUNT_ID` back to the customer's account (`ext-reverse-{paymentId}`) and marks payment `RETURNED`.
- **Ledger Service Addition**:
  - Added `GET /api/accounts?accountNumber=` to `LedgerAccountController` for inter-service account number lookups.
- **Deployment**: Added `services/payment-rail/Dockerfile`.

---

## 2026-11-10 — Reporting Service (:3011)

- **New Microservice**: Added `reporting-service/` (port `:3011`, Java 17, Spring Boot 3.2.12).
- **Database Schema**: Added `db/009_reporting.sql` creating `report_jobs` table.
- **Statement & Report Generation**:
  - Customer statement job (`POST /api/reports/statement`): HTML template populated with opening/closing balances and ledger entries, converted into PDF using OpenHTMLtoPDF.
  - Regulatory CSV extracts (`POST /api/reports/regulatory`): `DAILY_TRANSACTION_SUMMARY`, `LOAN_PORTFOLIO`, `KYC_STATUS`.
  - Cloud storage: Uploads rendered PDF and CSV files to Supabase Storage and generates presigned download URLs with 24-hour expiration.
  - Async worker (`POST /webhooks/report-worker`): Scheduled via QStash every 60s, processing one pending report per execution to stay within memory limits.
- **Deployment**: Added `services/reporting/Dockerfile`.

---

## 2026-11-10 — Treasury / ALM Service (:3010)

- **New Microservice**: Added `treasury-service/` (port `:3010`, Java 17, Spring Boot 3.2.12).
- **Database Schema**: Added `db/008_treasury.sql` creating `treasury_snapshots` and `alm_gap_buckets`.
- **Asset-Liability Management (ALM)**:
  - Liquidity coverage proxy (`GET /api/treasury/liquidity`): Queries aggregate active deposits from Ledger and disbursed loans from Loan Service.
  - Net Interest Margin (`GET /api/treasury/nim`): Computes annualized interest margin in basis points (bps).
  - Interest rate gap analysis (`GET /api/treasury/gap`): Groups assets and liabilities across 5 maturity buckets (`<1m`, `1-3m`, `3-12m`, `1-5y`, `>5y`).
  - Scheduled snapshot worker (`POST /webhooks/treasury-snapshot`): Persists daily snapshots and bucket distributions.
- **Ledger Service Addition**:
  - Added `GET /api/accounts/summary` to `LedgerAccountController` returning aggregated active deposit balances.
- **Deployment**: Added `services/treasury/Dockerfile`.

---

## 2026-11-10 — Loan Service (:3009)

- **New Microservice**: Added `loan-service/` (port `:3009`, Java 17, Spring Boot 3.2.12).
- **Database Schema**: Added `db/007_loans.sql` creating `loan_products`, `loan_applications`, and `loan_repayments`.
- **Lending Lifecycle**:
  - Product catalog (`GET /api/loans/products`): Manages personal, auto, and home loan limits and base rates.
  - Application submission & review (`POST /api/loans/apply`, `PATCH /api/loans/{id}/review`): Customer application and Staff underwriting workflow.
  - Disbursement engine: Debits `LOAN_DISBURSEMENT_ACCOUNT_ID` and credits customer account in Ledger Service.
  - Repayment & amortization: Computes monthly interest deductions; debits customer account to repayment settlement account.
  - Overdue scheduler (`POST /webhooks/loan-schedule`): QStash cron marking late repayments as `OVERDUE`.
- **Deployment**: Added `services/loan/Dockerfile`.

---

## 2026-11-05 - Local development environment (docker-compose)

- New: `docker-compose.yml` (postgres 16, redis 7, mailhog, WireMock 3.13.2 as QStash stand-in, auth, customer, ledger forwarder, fraud, notification, support), per-service `services/*/Dockerfile` (multi-stage, non-root), `init-db.sql` (schema + seed users/accounts/ticket), `wiremock/qstash-mappings/`, `.env.local.example`, `scripts/{make-key,start-all,stop-all,seed-data}.sh`, `README-DEV.md`, `.github/workflows/local-smoke.yml`.
- Local-only settings: `QSTASH_VERIFY_SIGNATURES=false`, `AUTH_DB_SCHEMA=public`, `SPRING_DATASOURCE_URL` override. Ledger (:3004) is a TCP forwarder to the Customer app, which hosts both.
- No changes to existing code or cloud deployment.

## 2026-11-05 - Customer list, accounts aggregation and /me

- Customer: `GET /api/customers` (STAFF; filters kycStatus / status / riskTier, case-insensitive; paged, default 20, max 100), `GET /api/customers/{id}/accounts` (CUSTOMER own / STAFF; aggregated from the Ledger, Redis `customer_accounts:{id}` 60 s, 503 when the Ledger is down), `GET /api/customers/me` (CUSTOMER; from the token, 403 without a customerId claim).
- New: `PaginatedCustomerResponse`, `AccountSummaryResponse`, `LedgerClient` (SYSTEM service key, 5 s timeouts; in-process when `LEDGER_SERVICE_URL` is empty), `ServiceUnavailableException` (503), `ForbiddenException` (403), `BadRequestException` (400); `CustomerRepository` extends `JpaSpecificationExecutor<Customer>`.
- Ledger: `GET /api/accounts?customerId=` (SYSTEM only) via `LedgerAccountController`, `AccountRepository.findByCustomerId`, `AccountSummaryResponse`, `TransferMapper.toAccountSummary`.
- `ServiceSecurityConfig`: rules for the four new paths (the `/api/accounts/**` user rule no longer covers `GET /api/accounts`, which is SYSTEM only).
- New config: `LEDGER_SERVICE_URL`, `CUSTOMER_SERVICE_ID`, `SYSTEM_TOKEN_SECRET`; the Ledger needs `customer-service=<secret>` in `SYSTEM_KEYS` when it is a separate service.
- Deviations from the brief: the filter uses `JpaSpecificationExecutor` (not the JPQL null-parameter query, which PostgreSQL cannot type); the existing `security-starter` is used (the `shared-security-starter` library is not adopted by this service yet); the Ledger call is skipped in favour of an in-process read when both run in one app.
- Tests: 50 pass in the main app (listing and filter rules, LedgerClient HTTP/cache/outage/headers, authorization slice for every new path).

## 2026-11-05 - shared-security-starter library

- New Maven module `shared-security-starter/` (`com.banking:shared-security-starter:1.0.0`): `JwtValidator` (RS256, issuer `banking-auth`, Redis `jwt_blocklist:{jti}`, key from `JWT_PUBLIC_KEY` or cached JWKS), `JwtAuthenticationFilter`, `QStashSignatureVerifier` + `QStashSignatureFilter` (real Upstash JWT format and `v1=` HMAC), `UnauthorizedHandler`, `ForbiddenHandler`, `RoleExtractor`, `DefaultSecurityConfig` (CORS from `CORS_ALLOWED_ORIGINS`), auto-configuration.
- Publishing: GitHub Packages (`distributionManagement`, `.github/workflows/publish-shared-security-starter.yml`) and a clone-and-install fallback; Render build commands in `docs/SHARED_SECURITY_STARTER.md`.
- 26 tests including a real Spring context with no security code of its own (401/403 JSON, @PreAuthorize, webhook signature before the controller, CORS, custom-chain override).
- Existing services and the in-repo `security-starter` are unchanged; migrating a service means removing its own security classes and adding the dependency.

## 2026-11-05 - Support Service

- New `support-service/` (port 3007, Java 17, Boot 3.2.12): entities `SupportTicket` / `TicketMessage`, repositories (Specification-based STAFF filters), DTO records, `TicketMapper`, `TicketService` (status transition validation, ownership, internal notes), `TicketEventPublisher`, `TransferCompletedWebhookHandler`, `TicketController`, `SupportWebhookController`, security config, exception handler, Dockerfile (repo-root build context).
- `db/005_support.sql`, `docs/SUPPORT_SERVICE.md` (deployment steps, 4 test commands).
- Existing four services, the auth service and the shared starter were not modified. Operational step only: add `support-service=<key>` to the Customer API's `SYSTEM_KEYS`.
- Deviations from the brief:
  - Render has no Java runtime: Docker, repo-root build context (the service needs `security-starter`).
  - The signature header is `Upstash-Signature`; `QStashSignatureVerifier` is not in the shared module (Fraud and Notification each carry a copy), so the same class was copied into this service.
  - "SYSTEM token" is the existing service-key mechanism (`SYSTEM_TOKEN_SECRET` = `X-Service-Key`, id `support-service`), not a token.
  - Extra env var `SUPABASE_DB_USER` (Supabase pooler user).
  - STAFF filters use JPA Specifications (optional UUID parameters in JPQL fail on PostgreSQL).
  - Closed-ticket customer posts return 403; empty PATCH bodies return 400.
  - **No existing service publishes `fraud_flagged`**, so auto-ticketing is wired but will not fire until the Ledger or Fraud service sends it.
  - `auth-service` also defaults to port 3007 locally.
- Verified: build + 24 tests (8 security slice, 10 ticket service, 6 webhook handler). Not run against live Supabase/Redis/QStash.

## 2026-11-05 - Customer data governance inventory

- Added `docs/DATA_GOVERNANCE.md`: master inventory (116 fields/keys: 74 implemented, 42 proposed), data-flow map for the 10 most sensitive fields, API exposure (PII leak) audit, event payload inventory with minimal payloads, retention and deletion schedule, masking rules, gap analysis, and a one-page summary for compliance.
- Added `MaskingUtil` (+ `MaskingUtilTest`, 5 tests) to `security-starter`; Notification's `SMS to ...` and `EMAIL sent to ...` log lines now log masked values (finding F7 fixed). `docs/RUNBOOK.md` and `scripts/e2e.sh` updated to the masked log format.
- Added `db/proposed/004_governance_gaps.sql`: proposed ALTER/CREATE statements (address, KYC verifier and time, audit users, PEP/sanctions, document expiry and alert timestamps, retention clock, purpose code, consent/document/PII-access tables). **Not applied automatically and not required by the app.**
- Deviations from the brief: the brief's context assumes `customer_documents` and `customer_consents` exist and that most identity, address and lifecycle fields are stored; they are not in the code, so those rows are marked NOT IMPLEMENTED/proposed. Five extra Redis keys and the auth tables were added to the inventory because they also hold customer-related data. One extra column (Status) was added to the master table.
- Findings recorded but NOT fixed in code: contact details and AML risk rating returned unmasked (F1, F2), mobile number in a URL path (F3), PII in events and Redis (F5, F6), no retention job (S7).
- Citations: FATF, GDPR and PCI DSS references are standard; Bangladesh instruments (MLPA 2012 s.25, BFIU circulars, Bangladesh Bank ICT guideline, Anti-Terrorism Act 2009, Contract Act 1872 s.11) are cited by name and must be verified by counsel; items marked `[verify]` in the document need the exact clause number.

## 2026-11-05 - Access authorization layer

- New `security-starter/` (Maven library): `JwtAuthenticationFilter`, `SystemKeyAuthenticationFilter`, `JwtVerifier` (RS256, local verification, rotation with a second public key), `RoleExtractor` (SYSTEM can never come from a user token), `UnauthorizedHandler` (401), `ForbiddenHandler` (403), `SecurityBaseline`, `SystemAuthInterceptor`, Redis revocation check, auto-configuration `SecurityConfig`.
- New `auth-service/` (port 3007): `POST /auth/login`, `/auth/refresh` (single-use rotation + reuse detection revokes all sessions), `/auth/logout` (Redis `revoked:jti:{jti}`), `POST /auth/users` (STAFF only), `GET /auth/public-key`, login rate limit (5 failures / 15 min), first-run bootstrap users. Tables in `db/003_auth.sql`.
- Main app: `ServiceSecurityConfig` (role per route, `denyAll` default), `AccessPolicy` ownership checks via `@PreAuthorize` on customer, transfer and account controllers. Fraud/notification: webhooks `permitAll` (signature is the authentication), everything else denied.
- Service-to-service: `X-Service-Id` / `X-Service-Key` with multi-key rotation (`SYSTEM_KEYS`, `SERVICE_ID`, `SERVICE_KEY`); interceptor attached only to the Customer-Service clients (fraud, notification, ledger HTTP mode).
- Dockerfiles of main app, fraud and notification now build `security-starter` first (fraud/notification must use the repo root as Docker build context on Render).
- Scripts/docs: `scripts/gen-keys.sh`, `scripts/attack-tests.sh` (10 scenarios), `docs/AUTHORIZATION.md`; `scripts/e2e.sh` and `docs/RUNBOOK.md` now authenticate as STAFF.
- Behaviour change: all main-app endpoints now return 401 without a valid token; only STAFF can create customers and verify KYC.
- Deviations from the brief:
  - Self-issued JWT chosen (Keycloak does not fit 512 MB; Supabase Auth would add a second identity store).
  - QStash verification kept as the real JWT-based check; a bare HMAC of the body would reject genuine QStash requests.
  - SYSTEM auth uses per-service shared-secret headers, not a token from the auth server, so it works while the auth server sleeps.
  - Customer and Ledger are one JVM, so there are five Render services, not four.
  - Extra endpoint rows in the matrix: `PATCH /aml-risk` and `/actuator/health`.
- Verified: all modules build; tests: starter 8, auth 9, main app 34 (13 authorization), fraud 14, notification 10. `scripts/*.sh` pass `bash -n`; generated keys checked against the Java key parser. NOT run against live Render/Supabase/Redis; Docker builds not run locally.

## 2026-11-05 - Sync KYC lookup (HTTP-capable), 503 handling, replay semantics

- Added `KycLookup` with `LocalKycLookup` (default, in-process) and `HttpKycLookup` (selected when `CUSTOMER_SERVICE_URL` is set; RestClient, 5 s timeouts; 404 -> 404, timeout/5xx -> 503).
- `ServiceUnavailableException` + 503 handler; `ledger/config/RestClientConfig` (`ledgerCustomerClient`).
- `KycGateway` now caches under `kyc:{accountId}` (transfers) and `kyc:customer:{customerId}` (account opening), TTL 600 s, verified only.
- KYC check moved to before the Redis lock and DB transaction (no HTTP call while holding a DB connection or the account lock).
- `GET /api/customers/by-account/{id}` now uses `AccountRepository.findCustomerIdByAccountId` (`SELECT a.customerId FROM Account a WHERE a.id = :accountId`) and returns 404 "Account not found" for unknown accounts; the old join query on `CustomerRepository` was removed.
- Idempotent replay now returns **200** with the original transaction (new transfers stay 201): `TransferService.transfer()` returns `TransferOutcome(response, replayed)`; `executeTransfer()` kept as a thin wrapper.
- Fraud log line now reads `FRAUD CHECK result=PASS|FLAGGED txn=...`.
- Tests: new `HttpKycLookupTest` (4), updated transfer/account tests; main app 21 tests pass, fraud/notification unchanged.
- Docs: new ASCII system diagram and KYC section in `ARCHITECTURE.md`; `docs/RUNBOOK.md` and `scripts/e2e.sh` updated for 200-on-replay, `PASS` log line, 503 behaviour and a 6-call quick check.
- Reviewed and left as is (already satisfy the request): `OutboxPublisher`/`OutboxEvent`/`OutboxRepository`, and `QStashSignatureVerifier` in both services. A bare "HMAC-SHA256 of body vs header" check was deliberately NOT used: real QStash signatures are JWTs, so that check would reject every genuine request. Verifier returns a boolean and the controllers answer 401 instead of throwing `SecurityException`.
- Not verified against live Supabase/Redis/QStash/Render.

## 2026-11-05 - Final integration and runbook

- Ledger: `KycGateway` + `KycNotVerifiedException` (422): senders must be KYC-verified; `kyc:{customerId}` cached 600 s (verified only).
- Ledger: `POST /api/accounts` (`CreateAccountRequest`, `AccountResponse`, `AccountService`), opening-deposit ledger credit via synthetic transaction, `account.created` outbox event.
- Actuator `/actuator/health` on all three services (for UptimeRobot / Render health check); mail health indicator disabled.
- Log lines for end-to-end tracing: `FRAUD CHECK`, `EMAIL sent`, `IGNORED event=...`.
- Dockerfiles use `./mvnw clean package -DskipTests`.
- Added `docs/RUNBOOK.md`, `scripts/e2e.sh`, `.github/workflows/supabase-keepalive.yml`.
- Deviations from the brief:
  - Customer + Ledger are one deployable, so the KYC lookup is an in-process call to `CustomerService` rather than HTTP to `/by-account` (the endpoint still exists for fraud/notification).
  - Render has no native Java runtime: Docker is used, so "build/start command" are the Dockerfile steps.
  - Monorepo kept (Root Directory per service) instead of separate repos.
  - Scenario step "$600 to Bob" uses a funded second Alice account, because Rahim only has $400 after the $100 transfer.
  - Keeping all three services warm 24/7 exceeds Render's 750 free hours/month; the runbook recommends warming only the main app.
- Verified: all modules build, unit tests pass (main app 16, fraud 11, notification 7); `scripts/e2e.sh` passes `bash -n`. NOT run against live infrastructure.

## 2026-11-05 — Fraud and Notification services

- New `fraud-service/` (port 3005) and `notification-service/` (port 3006): independent Maven projects, Java 17 / Boot 3.2.12, own Dockerfile and Maven wrapper.
- Fraud: `FraudRuleEngine` (HIGH_VALUE > $5,000, VELOCITY > 10 in 60 s via Redis, HIGH_RISK_CUSTOMER via Customer Service, cached 300 s), `FraudService`, `FraudAlert` entity, onboarding risk scoring (PEP / high country risk -> aml_risk_rating=high).
- Notification: `NotificationService` (email via JavaMailSender, SMS log stub), `CustomerLookupService` (Redis cache 600 s), `MailConfig` from `MAIL_*` env vars.
- Both: `QStashSignatureVerifier`, webhook controllers, `WebhookExceptionHandler`, Redis dedupe against QStash retries.
- Main app: new `GET /api/customers/by-account/{accountId}` and `PATCH /api/customers/{id}/aml-risk` (needed by the webhooks; not in the original brief).
- Main app event payloads changed to the snake_case contract with an `event` field (ledger `transfer.completed`, customer `customer.created`/`kyc.verified`; customer.created now includes `display_name` and `email`).
- Added `db/002_fraud_alerts.sql`, `docs/QSTASH_WIRING.md`, `scripts/qstash-curl.sh`, new env vars in `.env.example`.
- Deviations from the brief:
  - Signature check is JWT-based, not "HMAC of body vs header": QStash's `Upstash-Signature` is an HS256 JWT. Header name is `Upstash-Signature` (not `QStash-Signature`).
  - Topics are called URL Groups in the Upstash console; the publish URL is `https://qstash.upstash.io/v2/publish/banking-events`.
  - Webhooks ignore events that were fanned out to the wrong endpoint (returns 200 `{"ignored":true}`).
  - Alert writes are synchronous (a single insert is fast); no `@Async`.
  - The payload has no `is_pep`/`country_risk` today, so onboarding scoring only escalates if a publisher adds those fields.
- Verified: all three modules build; unit tests pass (fraud 11, notification 7, main app 11). `scripts/qstash-curl.sh` output checked independently with Python. NOT verified: running against live Supabase/Redis/QStash/Mailtrap.

## 2026-11-05 — Ledger Service

- Added `ledger` packages: entities (Account, Transaction, LedgerEntry, OutboxEvent), repositories, DTO records, mapper, `TransferService`, `OutboxPublisher` (@Scheduled 5 s), `EventPublisher`, controllers, scoped exception handlers, `RedisConfig`, `SchedulingConfig`.
- `pom.xml`: added `spring-boot-starter-data-redis`. `application.yml`: Redis settings (`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL`). `.env.example` updated.
- Deviations from the brief:
  - Same app/port as the customer service (no separate `LedgerServiceApplication`, port 3004 not used).
  - DB work uses `TransactionTemplate` so the Redis lock is released after COMMIT; the idempotency cache is written after COMMIT.
  - `decrementBalance` has `AND balance_cents >= :amt AND status='active'` so 0 rows means insufficient funds; it is a native query so `updated_at = now()` is also set.
  - Lock release is compare-and-delete (Lua); `fromAccount == toAccount` is rejected with 400.
  - Ledger exception handlers are scoped to the ledger controllers; bean names are prefixed `ledger...` to avoid clashes.
- Verified: `./mvnw package` passes, 7 ledger + 4 customer unit tests (mocked Redis/DB). NOT verified against real Postgres/Redis: Hibernate schema validation of the JSONB column and the native queries.

## 2026-11-05 — Customer Service

- Downgraded to Spring Boot 3.2.12 / Java 17 per spec; removed security, oauth2 and lombok deps.
- Added entity, repository, DTO records, mapper, service, `EventPublisher` (QStash via RestClient), controller, exception handler, `application.yml` (replaces `application.properties`), unit tests.
- `customers` table now matches the spec (display_name, date_of_birth, id_type, id_number NOT NULL; email nullable; aml_risk_rating VARCHAR(10)). If you already ran the old `db/schema.sql`, drop and recreate the tables.
- Env vars renamed: `SUPABASE_HOST`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `QSTASH_TOPIC_URL`, `QSTASH_TOKEN`, `PORT`.
- Seed mobile numbers changed to `+880170000001..3` (valid for the mobile pattern).
- Extras beyond spec: `DataIntegrityViolationException` -> 409 (race safety), publish failures are logged and do not roll back writes.
- Verified: `./mvnw package` compiles and 4 unit tests pass. Not run against a live DB.

## 2026-11-05 — Infrastructure and schema setup

Added
- `db/schema.sql`: tables `customers`, `accounts`, `transactions`, `ledger_entries`, `outbox`; indexes; append-only rules on `ledger_entries`; functions `increment_balance` / `decrement_balance`; RLS enabled on all tables.
- `db/seed.sql`: Alice ($1000), Bob ($500), Charlie ($200) with fixed UUIDs.
- `docs/SETUP.md`: click-by-click setup for Supabase, Upstash Redis, QStash, Render, with limits, failure messages and connection checklist.
- `Dockerfile` for Render (JVM capped at 384 MB).
- `.env.example`; `.env` added to `.gitignore`.
- `README.md`, `ARCHITECTURE.md`, `CHANGES.md`.

Modified
- `pom.xml`: added `org.postgresql:postgresql` (runtime).
- `src/main/resources/application.properties`: datasource from env vars, Hikari pool 5, `ddl-auto=validate`, `open-in-view=false`.

Notes
- Not built or deployed from this machine; no cloud accounts were created (that requires your own sign-ins).
- `ledger_entries.amount_cents` and `direction` also have NOT NULL / `> 0` checks beyond the spec.
- Error messages in SETUP.md are typical wording and should be confirmed in your own logs.
