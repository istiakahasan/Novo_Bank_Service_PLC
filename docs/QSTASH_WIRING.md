# QStash Wiring: banking-events -> Fraud + Notification

Upstash renamed "Topics" to **URL Groups** in the console. Same thing: one name, many endpoint URLs, each message fanned out to all of them.

## 0. One-time database step

Supabase -> SQL Editor -> New query -> paste `db/002_fraud_alerts.sql` -> Run.

## 1. Deploy the two webhook services first (you need their URLs)

Render -> New + -> Web Service -> same GitHub repo, branch `initial`, then per service:

| Setting | Fraud | Notification |
|---|---|---|
| Name | `fraud-service` | `notification-service` |
| Runtime | Docker | Docker |
| Root Directory | *(blank)* | *(blank)* |
| Dockerfile Path | `./fraud-service/Dockerfile` | `./notification-service/Dockerfile` |
| Docker Build Context Directory | `.` | `.` |
| Instance type | Free | Free |

Environment variables (Render -> Environment):

| Variable | Fraud | Notification | Value |
|---|---|---|---|
| `SUPABASE_HOST`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` | yes | no | same as the customer service |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | yes | yes | Upstash Redis (same DB is fine) |
| `CUSTOMER_SERVICE_URL` | yes | yes | Render URL of the main app, e.g. `https://novo-bank-service-plc.onrender.com` |
| `QSTASH_CURRENT_SIGNING_KEY`, `QSTASH_NEXT_SIGNING_KEY` | yes | yes | QStash console -> Signing keys |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | no | yes | Mailtrap (see below) |

Mailtrap: sign up at `https://mailtrap.io` -> Email Testing -> Inboxes -> My Inbox -> SMTP Settings. Copy Host, Port, Username, Password. Legacy host `smtp.mailtrap.io:2525` is the default here; newer inboxes show `sandbox.smtp.mailtrap.io` - use whatever your inbox shows.

## 2. Create the URL group and endpoints

1. Open `https://console.upstash.com` -> **QStash** (left menu).
2. Click **URL Groups** (formerly **Topics**) -> **Create URL Group**. Name: `banking-events`. Click **Create**.
3. Open `banking-events` -> **Add Endpoint** and add these four, one at a time (replace the hostnames with your real Render URLs):

| # | Endpoint URL |
|---|---|
| 1 | `https://fraud-service.onrender.com/webhooks/transfer-completed` |
| 2 | `https://notification-service.onrender.com/webhooks/transfer-completed` |
| 3 | `https://fraud-service.onrender.com/webhooks/customer-created` |
| 4 | `https://notification-service.onrender.com/webhooks/customer-created` |

4. Copy the token: QStash page -> **QSTASH_TOKEN**.
5. The publish URL for a group is `https://qstash.upstash.io/v2/publish/banking-events`. Set on the main app (customer + ledger) in Render:

| Env var | Value |
|---|---|
| `QSTASH_TOPIC_URL` | `https://qstash.upstash.io/v2/publish/banking-events` |
| `QSTASH_TOKEN` | the token from step 4 |

6. Redeploy the main app.

### Fan-out gotcha (already handled in code)

QStash sends **every** message to **all four** endpoints. So `transfer.completed` also arrives at `/webhooks/customer-created`, `customer.created` and `kyc.verified` arrive at `/webhooks/transfer-completed`, etc. Each webhook checks the `event` field and answers `200 {"ignored":true}` for events that are not its own, so QStash does not retry them.

Cost: each endpoint delivery likely counts as a message against the free 1,000/day, so one event ~ 4 messages (~250 events/day). Check **QStash -> Usage** after a few tests. To cut this to 2 per event, point each service at a single `/webhooks/...` endpoint and dispatch on `event` inside it.

## 3. Retry policy and dead-letter queue

Check the current numbers in the Upstash docs / your plan; typical behaviour:

- Any non-2xx response or timeout counts as a failure. The default is a few retries (3) with exponential backoff. Override per publish with the `Upstash-Retries` header (maximum depends on plan).
- After the last retry the message is moved to the **DLQ** automatically: console -> QStash -> **DLQ**. There you can inspect the response, **Retry** or **Delete** each message. There is nothing to configure to get a DLQ.
- To be notified instead of polling, publish with a failure callback (`Upstash-Failure-Callback: https://your-url/alerts`); QStash POSTs the failed message details there.
- Render free services sleep. The first delivery to a sleeping service usually times out; the retry lands once it is awake. This is expected.
- Our webhooks are idempotent (Redis `fraud_done:{txn}`, `notif_done:{txn}`, `welcome_done:{customer}`), so retries never double-alert or double-email.

## 4. Signature verification

QStash signs each request with header `Upstash-Signature`. It is **a JWT (HS256), not a plain HMAC of the body**. The services verify:

1. `HMAC-SHA256(header + "." + payload)` with `QSTASH_CURRENT_SIGNING_KEY`, else with `QSTASH_NEXT_SIGNING_KEY` (keys rotate), compared in constant time.
2. `iss == "Upstash"`, `exp` / `nbf` within a 60 s skew.
3. The `body` claim equals `base64url(SHA-256(raw body))`, so the payload cannot be swapped.

Code: `fraud-service/.../security/QStashSignatureVerifier.java` and the identical copy in `notification-service`. Unsigned or invalid requests get `401`. For local-only testing set `QSTASH_VERIFY_SIGNATURES=false`.

## 5. Four test calls (no real QStash needed)

Local run (PowerShell, from each service folder; set the variables from `.env.example` first):

```powershell
$env:QSTASH_VERIFY_SIGNATURES="false"; ./mvnw spring-boot:run     # fraud-service on :3005, notification-service on :3006
```

Use real account/customer IDs from your database (seed Alice: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`).

```bash
# 1. Fraud: transfer-completed ($6,000 -> HIGH_VALUE)
curl -i -X POST http://localhost:3005/webhooks/transfer-completed -H "Content-Type: application/json" \
  -d '{"event":"transfer.completed","transaction_id":"7b0c1d2e-0000-4000-8000-000000000001","from_account":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","to_account":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","amount_cents":600000,"created_at":"2026-01-01T10:00:00Z"}'

# 2. Fraud: customer-created (PEP -> escalates aml_risk_rating to high)
curl -i -X POST http://localhost:3005/webhooks/customer-created -H "Content-Type: application/json" \
  -d '{"event":"customer.created","customer_id":"11111111-1111-4111-8111-111111111111","customer_number":"CUST000001","display_name":"Alice Smith","email":"alice@example.com","mobile_no":"+880170000001","is_pep":true}'

# 3. Notification: transfer-completed (emails Alice via Mailtrap)
curl -i -X POST http://localhost:3006/webhooks/transfer-completed -H "Content-Type: application/json" \
  -d '{"event":"transfer.completed","transaction_id":"7b0c1d2e-0000-4000-8000-000000000002","from_account":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","to_account":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","amount_cents":12345,"created_at":"2026-01-01T10:00:00Z"}'

# 4. Notification: customer-created (welcome email)
curl -i -X POST http://localhost:3006/webhooks/customer-created -H "Content-Type: application/json" \
  -d '{"event":"customer.created","customer_id":"11111111-1111-4111-8111-111111111111","customer_number":"CUST000001","display_name":"Alice Smith","email":"alice@example.com","mobile_no":"+880170000001"}'
```

Expected: 1 -> `{"transaction_id":"...","flagged":true,"rules":["HIGH_VALUE"]}`; 2 -> `{"customer_id":"...","aml_risk_rating":"high","escalated":true}`; 3 -> `{"reference_id":"...","email_sent":true,"sms_logged":true,"duplicate":false}`; 4 -> `{"reference_id":"...","email_sent":true,"sms_logged":false,"duplicate":false}`. Re-sending 3 returns `duplicate:true`.

Signed variant (verification ON, e.g. against Render): the script builds a valid `Upstash-Signature` for you.

```bash
export QSTASH_CURRENT_SIGNING_KEY=sig_xxx   # same value as on the service
scripts/qstash-curl.sh https://fraud-service.onrender.com/webhooks/transfer-completed '{"event":"transfer.completed","transaction_id":"7b0c1d2e-0000-4000-8000-000000000003","from_account":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","to_account":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","amount_cents":600000,"created_at":"2026-01-01T10:00:00Z"}'
```

Test 1 and 3 need the main app reachable at `CUSTOMER_SERVICE_URL` (`/api/customers/by-account/{id}`); test 2 needs it for `PATCH /api/customers/{id}/aml-risk`. Note the PATCH and by-account endpoints are unauthenticated in this prototype; do not expose the main app publicly with real data.
