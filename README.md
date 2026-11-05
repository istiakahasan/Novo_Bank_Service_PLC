# Novo Bank Service PLC

Banking prototype: Spring Boot 3.2 (Java 17) on Render, PostgreSQL on Supabase, Redis + QStash on Upstash — all free tier.

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
- Tests are plain unit tests (Mockito); no database needed. Build: `./mvnw package`.

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
