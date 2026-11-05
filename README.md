# Novo Bank Service PLC

Banking prototype on free tiers: Spring Boot 3.2 / Java 17, Supabase PostgreSQL, Upstash Redis + QStash, Render, Mailtrap.

```
                 CLIENT / curl
                       |
          +------------+------------+
          v                         v
   CUSTOMER SVC  :3003        LEDGER SVC  :3004      (one app in this repo)
   POST /api/customers        POST /api/transfers
   GET  /api/customers/...    POST /api/accounts
          |                   GET  /api/accounts/{id}/balance
          |                         |
          |   (1) SYNC: KYC check before debit
          |<------------------------+
          |
          | (2) ASYNC: events -> QStash URL group "banking-events"
          v
   +------------------------------------------+
   |   UPSTASH QSTASH  "banking-events"       |
   |   fan-out, independent retries per URL   |
   +---------+------------------+-------------+
             v                  v
      FRAUD SVC :3005    NOTIFICATION SVC :3006
      /webhooks/transfer-completed
      /webhooks/customer-created
             |                  |
             +--- SYNC: GET customer (Redis-cached) ---> CUSTOMER SVC

SHARED: Supabase PostgreSQL | Upstash Redis (idempotency, locks, cache) | Upstash QStash
```

Note: Customer and Ledger run as **one Spring Boot app / one Render service**, so the KYC check is in-process by default. Set `CUSTOMER_SERVICE_URL` on the ledger to switch to HTTP (`GET /api/customers/by-account/{id}`, 5 s timeout, 503 if the Customer Service is down; a transfer is never attempted without a KYC answer). Unverified customer -> 422. Replaying an idempotency key -> 200 with the original transaction. Fraud and Notification are separate services that call the main app over HTTP. Full diagram: [ARCHITECTURE.md](ARCHITECTURE.md).

## Quick start

1. Create the infrastructure: [docs/SETUP.md](docs/SETUP.md)
2. Supabase SQL Editor: run `db/schema.sql`, `db/seed.sql`, `db/002_fraud_alerts.sql`, `db/003_auth.sql`, `db/005_support.sql`
3. Copy `.env.example` to `.env` and fill it in
4. Build and test: `./mvnw -f security-starter/pom.xml install` once, then `./mvnw package` (also inside `fraud-service/`, `notification-service/`, `auth-service/`)
5. Wire QStash: [docs/QSTASH_WIRING.md](docs/QSTASH_WIRING.md)
6. Deploy and verify: [docs/RUNBOOK.md](docs/RUNBOOK.md), then `MAIN=https://... scripts/e2e.sh`

## Services

| Folder | Port | Role |
|---|---|---|
| repo root | 3003 | Customer + Ledger REST API, outbox relay to QStash |
| `fraud-service/` | 3005 | QStash webhooks: fraud rules, onboarding risk |
| `notification-service/` | 3006 | QStash webhooks: email + SMS stub |
| `support-service/` | 3007 | Support tickets + message thread; auto-creates a dispute ticket from QStash fraud events |
| `auth-service/` | 3007 | Login, refresh rotation, logout; issues RS256 JWTs |
| `security-starter/` | - | Shared Maven library: JWT + service-key authorization |

## Docs

| File | Contents |
|---|---|
| [docs/API.md](docs/API.md) | Endpoints, curl examples, outbox test |
| [docs/RUNBOOK.md](docs/RUNBOOK.md) | End-to-end test, Render deploy, monitoring, troubleshooting, checklist |
| [docs/DATA_GOVERNANCE.md](docs/DATA_GOVERNANCE.md) | Customer data inventory, flows, PII leak audit, event payloads, retention, masking, gaps |
| [docs/SUPPORT_SERVICE.md](docs/SUPPORT_SERVICE.md) | Support service endpoints, deployment, test commands |
| [docs/AUTHORIZATION.md](docs/AUTHORIZATION.md) | Roles, endpoint matrix, tokens, service keys, attack tests |
| [docs/SETUP.md](docs/SETUP.md) | Click-by-click account setup |
| [docs/QSTASH_WIRING.md](docs/QSTASH_WIRING.md) | URL group, retries/DLQ, signature verification |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Design notes |
| [CHANGES.md](CHANGES.md) | Change log |

**Security:** every API call needs `Authorization: Bearer <JWT>` from `auth-service` (roles CUSTOMER / STAFF); service-to-service calls use `X-Service-Id` + `X-Service-Key` (SYSTEM); QStash webhooks are authenticated by `Upstash-Signature`. Matrix, token strategy and attack tests: [docs/AUTHORIZATION.md](docs/AUTHORIZATION.md). First-time setup: `scripts/gen-keys.sh`, then `db/003_auth.sql`.

Prototype only: shared database, unauthenticated internal endpoints. Do not use with real data.
