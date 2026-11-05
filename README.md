# Novo Bank Service PLC

Banking prototype: Spring Boot 4 (Java 25) on Render, PostgreSQL on Supabase, Redis + QStash on Upstash — all free tier.

## Quick start

1. Follow [docs/SETUP.md](docs/SETUP.md) to create Supabase, Upstash Redis, QStash and Render.
2. In Supabase SQL Editor run `db/schema.sql`, then `db/seed.sql`.
3. `cp .env.example .env` and fill in the credentials.
4. Run locally (PowerShell):

```powershell
Get-Content .env | Where-Object { $_ -match '^[^#].*=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }
./mvnw spring-boot:run
```

## Layout

| Path | Purpose |
|---|---|
| `db/schema.sql` | Tables, indexes, append-only ledger rules, balance functions |
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
- The context-load test needs the `DATABASE_*` variables set.
