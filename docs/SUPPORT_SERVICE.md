# Support Service (:3007)

Tickets with a message thread, plus automatic ticket creation from QStash events. Folder: `support-service/`. SQL: `db/005_support.sql`. Code is complete in the repo; this page is the deployment and test guide.

## 1. SQL (Supabase SQL Editor)

Run the whole file `db/005_support.sql` (same two tables, indexes and append-only rules as specified, plus `IF NOT EXISTS` and row-level security like the other tables).

## 2. Endpoints

| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/tickets` | CUSTOMER | `customer_id` from the JWT `cid` claim; first SYSTEM message "Ticket created"; publishes `ticket.created` -> 201 |
| GET | `/api/tickets` | CUSTOMER (own), STAFF (all, filters `status`, `priority`, `assignedAgentId`) | `?page=0&size=20` (size max 100), newest first |
| GET | `/api/tickets/{id}` | CUSTOMER (own, else 403), STAFF | ticket + messages; internal notes hidden from customers |
| PATCH | `/api/tickets/{id}` | STAFF | `{status, assignedAgentId, priority}`; one step forward only: OPEN -> IN_PROGRESS -> PENDING_CUSTOMER -> RESOLVED -> CLOSED, else 422; `resolved_at` set on RESOLVED; SYSTEM message + `ticket.status_changed` per transition |
| POST | `/api/tickets/{id}/messages` | CUSTOMER (own, not CLOSED; `isInternalNote` forced false), STAFF (may set `isInternalNote`) | 201 |
| POST | `/webhooks/transfer-completed` | QStash only | `Upstash-Signature` verified first; no JWT |

Webhook behaviour: only events with `fraud_flagged = true` create a ticket (TRANSACTION_DISPUTE, HIGH, subject `Fraud flag on transaction {id}`, customer resolved with `GET /api/customers/by-account/{from_account}`). Anything else (other event types fanned out by QStash, unflagged transfers) returns 200 `{"status":"ignored"}`. If a ticket with the same `related_transaction_id` exists (or another delivery holds the Redis lock `support:txn-lock:{id}`), it returns 200 `{"status":"duplicate"}` without creating one.

## 3. Render deployment

1. Render dashboard -> **New +** -> **Web Service** -> **Build and deploy from a Git repository** -> select `Novo_Bank_Service_PLC` -> **Connect**.
2. **Name** `support-service`; **Branch** `initial` (or `main` after merge); **Region** same as Supabase.
3. **Runtime: Docker** (Render has no native Java runtime, so `mvn clean package -DskipTests` and `java -jar target/*.jar` run inside the Dockerfile). **Root Directory**: leave blank. **Dockerfile Path**: `./support-service/Dockerfile`. **Docker Build Context Directory**: `.` (the service builds the shared `security-starter` first). **Instance Type**: Free.
4. **Advanced** -> **Health Check Path** `/actuator/health`.
5. **Advanced -> Add Environment Variable**:

```
SUPABASE_HOST=aws-0-<region>.pooler.supabase.com
SUPABASE_DB_USER=postgres.<project-ref>
SUPABASE_DB_PASSWORD=<db password>
REDIS_HOST=<name>.upstash.io
REDIS_PORT=6379
REDIS_PASSWORD=<upstash password>
QSTASH_TOPIC_URL=https://qstash.upstash.io/v2/publish/banking-events
QSTASH_TOKEN=<qstash token>
QSTASH_CURRENT_SIGNING_KEY=<sig_...>
QSTASH_NEXT_SIGNING_KEY=<sig_...>
CUSTOMER_SERVICE_URL=https://novo-bank.onrender.com
SYSTEM_TOKEN_SECRET=<64 hex chars, e.g. openssl rand -hex 32>
JWT_PUBLIC_KEY=<same value as on novo-bank>
PORT=3007
```
   (`SUPABASE_DB_USER` is needed because the Supabase pooler user is `postgres.<project-ref>`. `SYSTEM_TOKEN_SECRET` is this service's `X-Service-Key`; its id is `support-service`.)
6. **Create Web Service**; wait for `Started SupportServiceApplication`.
7. On **`novo-bank`** (Customer API) append `;support-service=<SYSTEM_TOKEN_SECRET>` to `SYSTEM_KEYS` and redeploy. Without it the webhook's customer lookup returns 401.
8. QStash console -> **URL Groups** -> `banking-events` -> **Add Endpoint**: `https://support-service.onrender.com/webhooks/transfer-completed`.
9. UptimeRobot: **+ New monitor** -> HTTP(s) -> `https://support-service.onrender.com/actuator/health` -> interval 5 minutes. (Warming a fourth always-on service exceeds Render's shared 750 free hours per month.)

## 4. Four test commands

```bash
export SUPPORT=https://support-service.onrender.com
export AUTH=https://auth-service.onrender.com
export ALICE=$(curl -s -X POST $AUTH/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"alice\",\"password\":\"$DEMO_PASSWORD\"}" | jq -r .accessToken)
export STAFF=$(curl -s -X POST $AUTH/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"staff\",\"password\":\"$STAFF_PASSWORD\"}" | jq -r .accessToken)
```

```bash
# 1. create ticket as CUSTOMER -> 201; body has ticketId, status OPEN, messages[0] = SYSTEM "Ticket created"
curl -s -w '\n%{http_code}\n' -X POST $SUPPORT/api/tickets -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' \
  -d '{"category":"GENERAL_INQUIRY","priority":"LOW","subject":"Question about my statement"}' | tee /tmp/ticket.json
export TICKET_ID=$(head -n -1 /tmp/ticket.json | jq -r .ticketId)
```
```bash
# 2. list tickets as CUSTOMER -> 200; only Alice's tickets, {content:[...],page:0,size:20,totalElements,totalPages}
curl -s -w '\n%{http_code}\n' "$SUPPORT/api/tickets?page=0&size=20" -H "Authorization: Bearer $ALICE"
```
```bash
# 3. STAFF moves the ticket OPEN -> IN_PROGRESS -> 200 (a second call asking for RESOLVED returns 422)
curl -s -w '\n%{http_code}\n' -X PATCH $SUPPORT/api/tickets/$TICKET_ID -H "Authorization: Bearer $STAFF" -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS"}'
```
```bash
# 4. webhook idempotency: the same signed fraud_flagged event twice -> first {"status":"created",...}, second {"status":"duplicate",...}, both 200
export QSTASH_CURRENT_SIGNING_KEY=<same as on support-service>
BODY='{"event":"transfer.completed","transaction_id":"7b0c1d2e-0000-4000-8000-0000000000aa","from_account":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","to_account":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","amount_cents":600000,"fraud_flagged":true}'
scripts/qstash-curl.sh $SUPPORT/webhooks/transfer-completed "$BODY"
scripts/qstash-curl.sh $SUPPORT/webhooks/transfer-completed "$BODY"
```

Verify in Supabase: `SELECT count(*) FROM support_tickets WHERE related_transaction_id = '7b0c1d2e-0000-4000-8000-0000000000aa';` returns 1.

## 5. Things to know

- **Nothing emits `fraud_flagged` yet.** The Ledger publishes `transfer.completed` without it and the Fraud service does not publish its verdict, so with the four existing services unchanged the webhook accepts the event but no ticket is auto-created until a publisher sends `fraud_flagged: true`.
- The header is `Upstash-Signature` (QStash's real header name); the verifier is the same JWT-based check used by Fraud and Notification, copied into this service (it is not part of the shared starter).
- `ticket.created` / `ticket.status_changed` are published best-effort after the database commit; a QStash outage is logged, not retried. Payloads carry ids only. QStash fans them out to every endpoint of the topic, and every webhook ignores events that are not its own.
- `SYSTEM_TOKEN_SECRET` is a service key (`X-Service-Id: support-service`, `X-Service-Key`), not a token.
- Locally, `auth-service` and this service both default to port 3007; set `PORT` on one of them.
