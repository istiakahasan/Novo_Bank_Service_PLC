# Novo Bank Service PLC

Banking prototype (customer service + double-entry ledger service in one deployable): Spring Boot 3.2 (Java 17) on Render, PostgreSQL on Supabase, Redis + QStash on Upstash — all free tier.

## Quick start

1. Follow [docs/SETUP.md](docs/SETUP.md) to create Supabase, Upstash Redis, QStash and Render.
2. In Supabase SQL Editor run `db/schema.sql`, then `db/seed.sql`.
3. `cp .env.example .env` and fill in the credentials.
4. Run locally (PowerShell):

```powershell
Get-Content .env | Where-Object { $_ -match '^[^#].*=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }
./mvnw spring-boot:run
```

## Services

| Service | Folder | Port | Role |
|---|---|---|---|
| Customer + Ledger | repo root | 3003 | REST API, publishes events to QStash |
| Fraud | `fraud-service/` | 3005 | QStash webhooks: HIGH_VALUE / VELOCITY / HIGH_RISK_CUSTOMER rules, onboarding risk scoring |
| Notification | `notification-service/` | 3006 | QStash webhooks: transfer + welcome emails (Mailtrap), SMS stub |

Event flow: main app -> QStash URL group `banking-events` -> 4 webhook endpoints. Setup, retry/DLQ notes, signature verification and 4 test curls: [docs/QSTASH_WIRING.md](docs/QSTASH_WIRING.md). Build each service with `./mvnw package` inside its folder.

New endpoints on the main app for the webhooks: `GET /api/customers/by-account/{accountId}`, `PATCH /api/customers/{id}/aml-risk` (`{"amlRiskRating":"low|medium|high"}`).

## Layout

| Path | Purpose |
|---|---|
| `db/schema.sql` | Tables, indexes, append-only ledger rules, balance functions |
| `db/002_fraud_alerts.sql` | `fraud_alerts` table (run after schema.sql) |
| `scripts/qstash-curl.sh` | Sends a correctly signed fake QStash request |
| `db/seed.sql` | Alice $1000, Bob $500, Charlie $200 with fixed UUIDs |
| `docs/SETUP.md` | Click-by-click infrastructure guide |
| `Dockerfile` | Render build (JVM capped at 384 MB) |
| `.env.example` | All environment variables |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Design |
| [CHANGES.md](CHANGES.md) | Change log |

## Seed accounts

| Name | Account UUID | Balance |
|---|---|---|
| Alice | `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` | $1,000 |
| Bob | `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb` | $500 |
| Charlie | `cccccccc-cccc-4ccc-8ccc-cccccccccccc` | $200 |

## Notes

- Money is stored as integer cents (`BIGINT`).
- `ddl-auto=validate`: the app never changes the schema; `db/schema.sql` is the source of truth.
- Tests are plain unit tests (Mockito); no database needed. Build: `./mvnw package`.

## Ledger API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/transfers` | 201; 400 invalid/same account, 404 account, 409 lock busy, 422 insufficient funds |
| GET | `/api/transfers/{id}` | 404 if missing |
| GET | `/api/accounts/{id}/balance` | `{accountId, balanceCents, currency}` |
| GET | `/api/accounts/{id}/entries?limit=20` | newest first, max 100 |

Replace the host below with `http://localhost:3003` or your Render URL.

```bash
BASE=http://localhost:3003
ALICE=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa
BOB=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb

# 1. Successful transfer ($100.00 Alice -> Bob)
curl -i -X POST $BASE/api/transfers -H "Content-Type: application/json"   -d "{\"fromAccount\":\"$ALICE\",\"toAccount\":\"$BOB\",\"amountCents\":10000,\"idempotencyKey\":\"demo-001\"}"

# 2. Duplicate: same key again -> same transactionId, balance moves only once
curl -i -X POST $BASE/api/transfers -H "Content-Type: application/json"   -d "{\"fromAccount\":\"$ALICE\",\"toAccount\":\"$BOB\",\"amountCents\":10000,\"idempotencyKey\":\"demo-001\"}"

# 3. Insufficient funds -> 422
curl -i -X POST $BASE/api/transfers -H "Content-Type: application/json"   -d "{\"fromAccount\":\"$ALICE\",\"toAccount\":\"$BOB\",\"amountCents\":99999999,\"idempotencyKey\":\"demo-002\"}"

curl $BASE/api/accounts/$ALICE/balance
curl "$BASE/api/accounts/$ALICE/entries?limit=5"
```

### Verify the outbox when QStash is down

1. Set `QSTASH_TOKEN` to a wrong value (Render -> Environment) or `QSTASH_TOPIC_URL` to an unreachable URL, redeploy.
2. Make a transfer (it still returns 201). In Supabase SQL Editor:
   `SELECT id, event_type, published FROM outbox ORDER BY id DESC LIMIT 5;` -> newest row has `published = false`; logs show `Outbox event N not delivered, will retry`.
3. Restore the correct values and redeploy. Within ~5 seconds the poller delivers the event and the row flips to `published = true`. The ledger and balances were correct the whole time.

## Customer API (port 3003)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/customers` | 201; 400 validation, 409 duplicate mobile/ID |
| GET | `/api/customers/{id}` | 404 if missing |
| GET | `/api/customers/by-mobile/{mobileNo}` | 404 if missing |
| PATCH | `/api/customers/{id}/kyc` | `verified`/`rejected`; 422 unless current status is `pending` |

```bash
curl -i -X POST http://localhost:3003/api/customers -H "Content-Type: application/json" -d '{"firstName":"Rahim","lastName":"Uddin","dateOfBirth":"1992-04-10","mobileNo":"01712345678","email":"rahim@example.com","idType":"nid","idNumber":"1990123456789","addressLine1":"House 1, Road 2, Dhaka"}'
```

`addressLine1` is validated but not stored: the `customers` table has no address column.
