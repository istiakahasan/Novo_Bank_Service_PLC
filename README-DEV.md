# Local development environment

Runs the whole platform on one machine with Docker Compose. No cloud account is needed.

| Container | Port | Replaces |
|---|---|---|
| postgres (16) | 5432 | Supabase (database `banking`, schema loaded from `init-db.sql`) |
| redis (7) | 6379 | Upstash Redis |
| wiremock | 8080 | Upstash QStash (forwards events to the webhook endpoints) |
| mailhog | 1025 / 8025 | SMTP provider (UI at http://localhost:8025) |
| auth | 3008 | auth-server (RS256 JWT) |
| customer | 3003 | Customer + Ledger app |
| ledger | 3004 | forwards to the customer container (same JVM) |
| fraud / notification / support | 3005 / 3006 / 3007 | the three satellite services |

## Prerequisites
Docker with Compose v2, `openssl`, `curl`, `jq`, and a bash shell (Git Bash on Windows).
Building the services needs `GH_PACKAGES_USER` / `GH_PACKAGES_TOKEN` in your environment only if a service depends on a private GitHub Packages artifact.

## Run
```bash
./scripts/make-key.sh      # creates .env.local with a fresh RSA key pair (safe to re-run)
./scripts/start-all.sh     # builds, starts and waits until every /actuator/health is UP
./scripts/seed-data.sh     # end-to-end scenario with assertions
./scripts/stop-all.sh -v   # stop; -v also wipes the database
```

## Seed users
Password for all: `TestPass123!`

| Email | Role |
|---|---|
| alice@test.com, bob@test.com | CUSTOMER |
| staff@test.com | STAFF |

## How the pieces connect
- Customer writes an outbox event, its poller posts it to `http://wiremock:8080/qstash/topic`; WireMock calls the fraud, notification and support webhooks (`wiremock/qstash-mappings/`).
- Notification sends email to Mailhog.
- Services read `.env.local` (git-ignored). `SPRING_DATASOURCE_URL` overrides the hard-coded database URL.

## Local-only differences
- `QSTASH_VERIFY_SIGNATURES=false`: WireMock cannot compute QStash's JWT signature. Never set this in a cloud deployment.
- Ledger shares the Customer JVM, so port 3004 is a TCP forwarder to the customer container.
- Auth tables live in schema `public` locally (`AUTH_DB_SCHEMA=public`).

## Troubleshooting
- `docker compose logs -f <service>` for logs.
- Port already in use: stop the other process or edit the host side of `ports` in `docker-compose.yml`.
- Reset everything: `./scripts/stop-all.sh -v && rm .env.local`.
