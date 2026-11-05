# Architecture

```
Client ──HTTPS──▶ Render (Spring Boot, free, sleeps 15 min)
                    │  ├─ JDBC (session pooler) ─▶ Supabase PostgreSQL (500 MB)
                    │  ├─ Redis (rediss://)     ─▶ Upstash Redis (idempotency cache, rate limit)
                    │  └─ publish (HTTPS)       ─▶ Upstash QStash ──signed callback──▶ Render
```

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

## Status

Infrastructure and schema are defined. Customer and ledger services are implemented and unit-tested; not yet run against live Supabase/Upstash.
