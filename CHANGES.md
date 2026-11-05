# Changes

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
