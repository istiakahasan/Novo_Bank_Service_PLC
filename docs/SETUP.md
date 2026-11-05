# Infrastructure Setup (Free Tier) — Click by Click

Dashboards change their layout from time to time. If a button label differs slightly, pick the closest one. Error messages in section "Limits" are the typical ones; confirm exact wording in your logs.

Do the services in this order. Keep a text file open (e.g. `.env` in the project root, copied from `.env.example`) and paste each credential as you go. **Never commit `.env`** (already in `.gitignore`).

---

## 1. Supabase (PostgreSQL, 500 MB)

1. Open `https://supabase.com/dashboard` in your browser.
2. Click **Continue with GitHub** (or enter email + password and click **Sign Up**). Confirm the email if asked.
3. Click **New project**. If asked, create an **Organization** first: name `novo`, type **Personal**, plan **Free**, click **Create organization**.
4. Fill the form:
   - **Name**: `novo-bank`
   - **Database Password**: click **Generate a password**, then click the copy icon. **Paste it into your `.env` as `SUPABASE_DB_PASSWORD` right now** — you cannot view it again later (only reset).
   - **Region**: choose the one closest to you (Render region should be similar).
   - **Pricing plan**: Free.
5. Click **Create new project**. Wait ~2 minutes until the green "Project is ready" state shows.
6. Run the schema: left sidebar → **SQL Editor** → **+ New query**.
7. Open `db/schema.sql` in this repo, select all (Ctrl+A), copy (Ctrl+C), paste into the editor, click **Run** (or Ctrl+Enter). Expect: `Success. No rows returned`.
8. Click **+ New query** again, paste all of `db/seed.sql`, click **Run**. Expect a table of 3 rows (see section D of README / below).
9. Get the connection string: top bar → **Connect** button.
   - Choose tab **Connection String** → **Type: JDBC** → **Method: Session pooler** (works over IPv4; direct connection is IPv6-only on free tier).
   - Copy the host (`aws-0-<region>.pooler.supabase.com`), port `5432`, and user (`postgres.<project-ref>`).

| Dashboard field | Env var | Where to paste |
|---|---|---|
| Connect → JDBC → Session pooler (host/port/db) | `SUPABASE_HOST` (host only, e.g. `aws-0-<region>.pooler.supabase.com`) | `.env`, Render env vars |
| Connect → user (`postgres.<ref>`) | `SUPABASE_DB_USER` | `.env`, Render env vars |
| Database password from step 4 (Project Settings → Database → Reset database password if lost) | `SUPABASE_DB_PASSWORD` | `.env`, Render env vars |

**Limits & failure**
| Limit | What breaks | How to detect |
|---|---|---|
| 500 MB database | Project goes **read-only** | `ERROR: cannot execute INSERT in a read-only transaction` (SQLState 25006) |
| Free projects pause after 7 days inactivity | All connections fail | Connection timeout / `FATAL: Tenant or user not found`; dashboard shows "Project paused" → click **Restore project** |
| Session pooler client limit (~15 free) | New connections refused | `FATAL: MaxClientsInSessionMode: max clients reached` — keep Hikari pool at 5 (already set) |

---

## 2. Upstash Redis (256 MB, 500K commands/month)

1. Open `https://console.upstash.com`.
2. Click **Sign up** → **Continue with GitHub** (or email). 
3. Click **Redis** in the left/top nav → **Create Database**.
4. Fill in: **Name** `novo-redis`; **Type** Regional; **Region** closest to your Render region; leave **TLS/SSL** on; plan **Free**.
5. Click **Create**.
6. On the database page scroll to the **REST API** section (or the **Connect** tab). Copy the values below.

| Dashboard field | Env var | Where to paste |
|---|---|---|
| **UPSTASH_REDIS_REST_URL** (REST API section) | `UPSTASH_REDIS_REST_URL` | `.env`, Render |
| **UPSTASH_REDIS_REST_TOKEN** (REST API section → tab `.env`) | `UPSTASH_REDIS_REST_TOKEN` | `.env`, Render |
| Connect → "Redis" TCP string `rediss://default:…@…:6379` | `REDIS_URL` | `.env`, Render |

**Limits & failure**
| Limit | What breaks | How to detect |
|---|---|---|
| 500K commands/month | All commands rejected until month resets | `ERR max daily request limit exceeded` / `ERR max monthly request limit exceeded` (HTTP 429 on REST) |
| 256 MB data | Writes rejected / eviction if enabled | `OOM command not allowed when used memory > 'maxmemory'` |
| Max request size (1 MB) | Large values rejected | `ERR max request size exceeded` |

Cost tip: use Redis only for idempotency-key cache and rate limits; set TTLs (`EX 86400`).

---

## 3. Upstash QStash (1,000 messages/day)

1. Stay in `https://console.upstash.com`. Click **QStash** in the nav (left sidebar or top).
2. Free plan is applied automatically on first open.
3. On the QStash page find the **Quickstart / Details** panel. Copy each value.

| Dashboard field | Env var | Where to paste |
|---|---|---|
| **QSTASH_URL** | `QSTASH_URL` (`https://qstash.upstash.io`) | `.env`, Render |
| **QSTASH_TOKEN** | `QSTASH_TOKEN` | `.env`, Render |
| **QSTASH_CURRENT_SIGNING_KEY** | `QSTASH_CURRENT_SIGNING_KEY` | `.env`, Render |
| **QSTASH_NEXT_SIGNING_KEY** | `QSTASH_NEXT_SIGNING_KEY` | `.env`, Render |

QStash calls your Render URL (`APP_PUBLIC_URL` + `/internal/outbox/...`) and signs requests; your app must verify the `Upstash-Signature` header with the two signing keys.

**Limits & failure**
| Limit | What breaks | How to detect |
|---|---|---|
| 1,000 messages/day | Publishes rejected until UTC midnight | HTTP `429` with body like `{"error":"daily message limit exceeded"}` |
| Render asleep | Delivery attempt times out, QStash retries | Failed attempts in QStash → **Logs**; first retry may take 30–60 s |
| Bad token | Publish fails | HTTP `401` `{"error":"invalid token"}` |

Outbox rows stay `published=false` when publishing fails, so nothing is lost — they are retried.

---

## 4. Render (512 MB RAM, sleeps after 15 min)

1. Push this repo to GitHub (already at `https://github.com/istiakahasan/Novo_Bank_Service_PLC`) after committing the new files.
2. Open `https://dashboard.render.com` → **Get Started** → **GitHub** → authorize.
3. Click **New +** → **Web Service** → **Build and deploy from a Git repository** → **Next**.
4. Click **Connect** next to `Novo_Bank_Service_PLC` (click **Configure account** first if the repo isn't listed, and grant access).
5. Settings: **Name** `novo-bank-service-plc`; **Language/Runtime** **Docker** (Render auto-detects the `Dockerfile`); **Region** same as Supabase; **Instance Type** **Free**.
6. Expand **Advanced** → **Add Environment Variable** and add every variable from `.env` (SUPABASE_HOST, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD, UPSTASH_*, REDIS_URL, QSTASH_*). Set `APP_PUBLIC_URL` after step 8.
7. Click **Create Web Service**. Watch **Logs** until you see `Started NovoBankServicePlcApplication`.
8. Copy the URL at the top (`https://novo-bank-service-plc.onrender.com`) → set `APP_PUBLIC_URL` in Render → **Environment** → **Save, rebuild, and deploy**.

| Dashboard field | Env var | Where to paste |
|---|---|---|
| Service URL (top of service page) | `APP_PUBLIC_URL` | `.env`, Render env |

**Limits & failure**
| Limit | What breaks | How to detect |
|---|---|---|
| Sleeps after 15 min idle | First request takes ~30–60 s | Browser/API timeout; Render **Events**: "Service spun down due to inactivity" |
| 512 MB RAM | App killed | Logs: `Ran out of memory (used over 512MB) while running your code` (JVM capped at 384 MB in Dockerfile) |
| 750 free instance-hours/month | Service suspended | Dashboard banner "Free instance hours used"; HTTP 503 |
| Build minutes / long Java builds | Build fails | **Events** → "Build failed" |

---

## Connection checklist

| From Service | To Service | Credential Name | Env Var Name | Where to Paste |
|---|---|---|---|---|
| Supabase | Spring Boot app | Session pooler JDBC URL | `SUPABASE_HOST` | `.env`, Render env |
| Supabase | Spring Boot app | Pooler username `postgres.<ref>` | `SUPABASE_DB_USER` | `.env`, Render env |
| Supabase | Spring Boot app | Database password | `SUPABASE_DB_PASSWORD` | `.env`, Render env |
| Upstash Redis | Spring Boot app | Endpoint (host) | `REDIS_HOST` | `.env`, Render env |
| Upstash Redis | Spring Boot app | Port (6379) | `REDIS_PORT` | `.env`, Render env |
| Upstash Redis | Spring Boot app | Password | `REDIS_PASSWORD` | `.env`, Render env |
| Upstash Redis | Spring Boot app | REST URL | `UPSTASH_REDIS_REST_URL` | `.env`, Render env |
| Upstash Redis | Spring Boot app | REST token | `UPSTASH_REDIS_REST_TOKEN` | `.env`, Render env |
| Upstash Redis | Spring Boot app | TCP `rediss://` URL | `REDIS_URL` | `.env`, Render env |
| QStash | Spring Boot app | Token | `QSTASH_TOKEN` | `.env`, Render env |
| QStash | Spring Boot app | Base URL | `QSTASH_URL` | `.env`, Render env |
| QStash | Spring Boot app | Current signing key | `QSTASH_CURRENT_SIGNING_KEY` | `.env`, Render env |
| QStash | Spring Boot app | Next signing key | `QSTASH_NEXT_SIGNING_KEY` | `.env`, Render env |
| Render | QStash (callback target) | Public service URL | `APP_PUBLIC_URL` | `.env`, Render env |

## Seed accounts (exact UUIDs, from `db/seed.sql`)

| Customer | Account UUID | Account number | Balance | Customer UUID |
|---|---|---|---|---|
| Alice | `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` | NOVO0000000001 | $1,000.00 (100000 cents) | `11111111-1111-4111-8111-111111111111` |
| Bob | `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb` | NOVO0000000002 | $500.00 (50000 cents) | `22222222-2222-4222-8222-222222222222` |
| Charlie | `cccccccc-cccc-4ccc-8ccc-cccccccccccc` | NOVO0000000003 | $200.00 (20000 cents) | `33333333-3333-4333-8333-333333333333` |

UUIDs are fixed in the seed script, so they are identical after every insert.

## Verify (run in Supabase SQL Editor)

```sql
SELECT count(*) FROM accounts;  -- expect 3
SELECT increment_balance('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 100);
SELECT decrement_balance('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 100);  -- restores balance
SELECT decrement_balance('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 99999999); -- must FAIL (check constraint)
```
