# Changes

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
