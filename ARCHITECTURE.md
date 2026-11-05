# Architecture

## System diagram

```
                       CLIENT / curl
                            |
              +-------------+--------------+
              v                            v
 +----------------------------------------------------+
 | NOVO-BANK (one Spring Boot app / one Render service) |
 |  +-------------------+     +--------------------+  |
 |  | CUSTOMER  :3003   |<----| LEDGER  (same app) |  |
 |  | /api/customers    |     | /api/transfers     |  |
 |  | /by-account/{id}  |     | /api/accounts      |  |
 |  +---------+---------+     +----+----------+----+  |
 |            |   SYNC KYC check   |          |       |
 |            |   (in-process, or HTTP GET    |       |
 |            |    /by-account if             |       |
 |            |    CUSTOMER_SERVICE_URL set;  |       |
 |            |    blocks transfer, 5 s, 503) |       |
 +------------+----------------------+--------+-------+
              |                      |        |
   ASYNC POST event            ASYNC POST event  JDBC / Redis
   (customer.created,          (transfer.completed,   |
    kyc.verified)               account.created,      |
              |                 via outbox + 5 s poll)|
              v                      v                |
        +-------------------------------------+       |
        |  UPSTASH QSTASH  URL group          |       |
        |  "banking-events"  (4 endpoints)    |       |
        +----------+---------------+----------+       |
                   |               |                  |
         ASYNC fan-out      ASYNC fan-out             |
         (signed, retried)  (signed, retried)         |
                   v               v                  |
        +----------------+  +--------------------+    |
        | FRAUD   :3005  |  | NOTIFICATION :3006 |    |
        | /webhooks/     |  | /webhooks/         |    |
        |  transfer-     |  |  transfer-         |    |
        |  completed     |  |  completed         |    |
        |  customer-     |  |  customer-         |    |
        |  created       |  |  created           |    |
        +-------+--------+  +---------+----------+    |
                |                     |               |
                |  SYNC GET /by-account (Redis-cached 300 s / 600 s;
                |  does not block any transfer)
                +----------+----------+
                           v
                  NOVO-BANK (Customer API)

 SHARED: Supabase PostgreSQL (app + fraud_alerts) | Upstash Redis (idempotency, locks, kyc and customer caches)
 Mailtrap SMTP <- notification-service
```

Which arrows block a transfer: only the Ledger -> Customer KYC check (and Redis lock/idempotency). Everything below QStash is asynchronous and happens after the transfer has committed.

## Deployment shape

```
Client ──HTTPS──▶ Render (Spring Boot, free, sleeps 15 min)
                    │  ├─ JDBC (session pooler) ─▶ Supabase PostgreSQL (500 MB)
                    │  ├─ Redis (rediss://)     ─▶ Upstash Redis (idempotency cache, rate limit)
                    │  └─ publish (HTTPS)       ─▶ Upstash QStash ──signed callback──▶ Render
```

## Authorization layer

```
 user --login--> AUTH-SERVICE :3007 --(RS256 JWT, 15 min + rotating refresh token)--> user
                  | identity: Supabase auth_users, refresh_tokens
                  | logout: Redis revoked:jti:{jti} (TTL = token life)
 user --Bearer JWT--> NOVO-BANK (Customer + Ledger)   verifies locally with JWT_PUBLIC_KEY (no call to auth-service)
 fraud / notification --X-Service-Id + X-Service-Key--> NOVO-BANK Customer API   (ROLE_SYSTEM, read-only + aml-risk)
 QStash --Upstash-Signature (JWT HS256)--> fraud / notification webhooks         (no user token involved)
```

- Roles: CUSTOMER (own data only, via the `cid` claim), STAFF (all bank operations), SYSTEM (service key only; customer lookups and AML-risk update). A user token can never carry SYSTEM.
- Two layers: route rules in each `ServiceSecurityConfig` (role per path, `denyAll` default) and `@PreAuthorize` ownership checks (`AccessPolicy`) on the controllers.
- Shared code lives in `security-starter` (Maven library): filters, verifier, handlers, service-key interceptor.
- Full matrix and rotation procedures: `docs/AUTHORIZATION.md`.

## Data governance

Full inventory, lineage, PII leak audit, retention schedule and gap analysis: `docs/DATA_GOVERNANCE.md`.
- Customer PII lives only in `customers` (and short-lived Redis caches in Notification); the ledger holds account ids and amounts only, so anonymizing `customers` makes ledger history non-attributable while the append-only rule stays intact.
- `MaskingUtil` (in `security-starter`) is the single place for masking; Notification's logs already use it.
- Known gaps (details in the document): contact details and the AML risk rating are returned unmasked by the Customer API, `customer.created` events carry PII, and no retention/deletion job exists yet.

## Components

| Component | Role | Free limit |
|---|---|---|
| Render web service | REST API, transfer logic, outbox relay endpoint | 512 MB RAM, sleeps after 15 min |
| Supabase PostgreSQL | System of record | 500 MB |
| Upstash Redis | Fast idempotency-key lookup, rate limiting (with TTL) | 500K cmds/month |
| Upstash QStash | Reliable async delivery of outbox events, retries, wakes Render | 1,000 msgs/day |

## Data model

`customers 1─* accounts 1─* ledger_entries *─1 transactions`; `transactions` reference `from_account` / `to_account`; `outbox` holds events for async publishing.

- All money is `BIGINT` cents; `accounts.balance_cents >= 0` is enforced by a CHECK constraint.
- `ledger_entries` is append-only (rules turn UPDATE/DELETE into no-ops). Each transfer writes one debit and one credit.
- `transactions.idempotency_key` is UNIQUE so a retried request cannot double-spend.

## Transfer flow (single DB transaction)

1. Client sends `Idempotency-Key`. App checks Redis; on hit, returns the stored result.
2. `BEGIN`; insert `transactions` (`pending`). Unique violation → duplicate, return the existing one.
3. Lock both accounts in ascending UUID order (`SELECT … FOR UPDATE`) to avoid deadlocks.
4. `decrement_balance(from, amt)` (fails on overdraft), `increment_balance(to, amt)`.
5. Insert debit + credit `ledger_entries`; set transaction `completed`; insert `outbox` row `transfer.completed`.
6. `COMMIT`; cache result in Redis (TTL 24 h).

## Transactional outbox

A relay (called by a QStash schedule or after commit) reads `outbox WHERE published=false`, publishes to QStash, then marks `published=true`. Delivery is at-least-once, so consumers must be idempotent. QStash callbacks are authenticated by verifying `Upstash-Signature` with the current/next signing keys.

## Security

- RLS enabled on all tables with no policies: Supabase's public REST API cannot read them; only the backend's direct DB connection can.
- Secrets live only in `.env` (git-ignored) and Render environment variables.

## Free-tier constraints and mitigations

| Constraint | Mitigation |
|---|---|
| Cold start after sleep | QStash schedule ping every ≤14 min (costs ~100 msgs/day) or accept the delay |
| 1,000 QStash msgs/day | Batch outbox events per message |
| Supabase pauses after 7 days idle | Scheduled ping hitting the DB |
| 512 MB RAM | `-Xmx384m`, SerialGC, Hikari pool of 5 |

## Ledger service implementation notes

- Packages: `...Novo_Bank_Service_PLC.ledger.*` (transfers/accounts) next to the customer packages; one Spring Boot app, one port, one Render service (free tier allows one always-on-ish instance anyway).
- Transfer order: Redis idempotency check -> `SET lock:{from} token NX EX 5` -> DB transaction (validate accounts, balance check, insert transaction, debit + credit ledger rows, atomic `UPDATE ... WHERE balance_cents >= :amt`, outbox row) -> COMMIT -> cache result in Redis (TTL 3600 s) -> release lock.
- Lock is released with a compare-and-delete Lua script so an expired lock re-acquired by another request is never deleted.
- The DB transaction uses `TransactionTemplate`, not `@Transactional` on the method, so the lock is held until after COMMIT.
- Idempotency is enforced twice: Redis cache and `UNIQUE(idempotency_key)`; on a DB duplicate the original transaction is returned.
- `OutboxPublisher` polls every 5 s (`fixedDelay`), POSTs to QStash with `Upstash-Deduplication-Id: outbox-{id}`, marks `published=true` only on success.

## Event consumers

```
Customer/Ledger (Render) --publish--> QStash URL group "banking-events"
        |--> fraud-service        /webhooks/transfer-completed   (rules 1-3, fraud_alerts)
        |--> notification-service /webhooks/transfer-completed   (email + SMS stub)
        |--> fraud-service        /webhooks/customer-created     (onboarding risk score)
        |--> notification-service /webhooks/customer-created     (welcome email)
```

- Wire format is snake_case JSON with an `event` field (`transfer.completed`, `customer.created`, `kyc.verified`).
- QStash fans out every message to every endpoint, so each webhook ignores foreign `event` values with a 200.
- Delivery is at-least-once. Fraud/notification dedupe with Redis keys (`fraud_done:`, `notif_done:`, `welcome_done:`).
- Fraud rule 3 and the notification email need customer data: they call `GET /api/customers/by-account/{id}` (cached 300 s / 600 s). Rule 3 fails open if the customer service is down; notifications propagate the error so QStash retries.
- Requests are authenticated with the `Upstash-Signature` JWT (HS256, current/next signing key, body-hash claim).
- Each service is an independent Maven project (own Dockerfile, Render root directory); they share the Supabase DB (fraud only) and Upstash Redis.

## KYC gate and account opening

- Transfer path: after the idempotency check and **before** the Redis lock and the DB transaction, `KycGateway.requireVerifiedAccount(fromAccount)` runs. It reads Redis `kyc:{accountId}` (TTL 600 s) and otherwise asks a `KycLookup`:
  - `LocalKycLookup` (default): calls `CustomerService.getByAccountId` in-process (Customer and Ledger share a JVM).
  - `HttpKycLookup` (when `CUSTOMER_SERVICE_URL` is set): `GET /api/customers/by-account/{id}` with 5 s connect/read timeouts. 404 -> 404, timeout / connection error / 5xx -> **503 `ServiceUnavailableException`**, and the transfer is not attempted.
- `kyc_status != 'verified'` -> `KycNotVerifiedException` -> **422**. Only `verified` is cached, so an approval applies immediately and a pending customer is never "stuck" for 10 minutes.
- The check runs outside the DB transaction so a slow Customer Service never holds a database connection or the account lock.
- `GET /api/customers/by-account/{id}` resolves the customer with `AccountRepository.findCustomerIdByAccountId` (`SELECT a.customerId FROM Account a WHERE a.id = :accountId`), then reuses `getById`. Unknown account -> 404.
- Idempotent replay (Redis hit or DB unique-key hit) returns the original transfer with **200**; a new transfer returns **201**.
- `AccountService.createAccount` uses `requireVerifiedCustomer` (cache key `kyc:customer:{id}`) and writes account + opening credit (synthetic transaction, from = to = new account) + `account.created` outbox row atomically.

## Outbox and webhook verification (unchanged, re-verified)

- `OutboxPublisher` `@Scheduled(fixedDelay = 5000)` reads `published = false` rows oldest-first, POSTs to the QStash URL group with `Authorization: Bearer`, marks `published = true` only on success, and stops the cycle on the first failure (warning logged, retried next tick). The outbox row is written in the same DB transaction as the ledger entries.
- Fraud and Notification verify `Upstash-Signature`. QStash signs with a JWT (HS256), not a bare HMAC of the body, so the verifier checks the HMAC over `header.payload` with the current or next signing key, the issuer, the time window and the SHA-256 body hash claim. Invalid -> 401.

## Status

Infrastructure and schema are defined. Customer and ledger services are implemented and unit-tested; not yet run against live Supabase/Upstash.
