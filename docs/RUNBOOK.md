# Final Runbook: wiring, testing, deployment

**Read this first (how the repo really maps to your 4 services):** Customer and Ledger are ONE Spring Boot app (`/` of the repo, one JVM, one `PORT`). So there are **three** Render services: `novo-bank` (Customer + Ledger), `fraud-service`, `notification-service`. Because they share a JVM, the Ledger's KYC check calls `CustomerService` in-process (the same code behind `GET /api/customers/{id}`) instead of an HTTP hop. Part A lists exactly what was added.

Shell variables used throughout (set once; `jq` required):

```bash
export MAIN=https://novo-bank.onrender.com          # Customer + Ledger
export FRAUD=https://fraud-service.onrender.com
export NOTIF=https://notification-service.onrender.com
export ALICE_CUST=11111111-1111-4111-8111-111111111111
export ALICE_ACC=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa
export BOB_ACC=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb
export RUN=$(date +%s)                               # makes every re-run unique
export PSQL_URL='postgresql://postgres.<ref>:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres'
```

Automated version of Part B: `MAIN=... FRAUD=... NOTIF=... scripts/e2e.sh` (prints PASS/FAIL per check; set `PSQL_URL` to also run the SQL checks).

---

## A) Integration code (all committed; file -> change)

| File | Change |
|---|---|
| `controller/CustomerController.java` | `GET /api/customers/by-account/{accountId}` (added in the Fraud/Notification step) |
| `repository/CustomerRepository.java` | `findByAccountId`: `select c from Customer c where c.id = (select a.customerId from Account a where a.id = :accountId)` (accounts -> customers join) |
| `service/CustomerService.java` | `getByAccountId(UUID)` |
| `ledger/service/KycGateway.java`, `KycLookup`, `LocalKycLookup`, `HttpKycLookup` (new) | `requireVerifiedAccount(accountId)`: Redis `kyc:{accountId}` (TTL 600 s) else lookup (in-process, or HTTP `GET /api/customers/by-account/{id}` with a 5 s timeout when `CUSTOMER_SERVICE_URL` is set); not verified -> 422; customer service unreachable -> 503. **Only `verified` is cached** |
| `ledger/service/TransferService.java` | `kycGateway.requireVerifiedAccount(fromAccount)` after the idempotency check and **before** the lock, DB transaction and balance check; replays return 200 via `TransferOutcome` |
| `ledger/exception/KycNotVerifiedException.java`, `ServiceUnavailableException.java` (new) + `GlobalExceptionHandler` | **422** and **503** |
| `ledger/controller/TransferController.java` | no change (exception bubbles) |
| `ledger/dto/CreateAccountRequest.java` (new) | `customerId @NotNull`, `accountType @Pattern(^(savings\|checking)$)`, `initialDepositCents @NotNull @PositiveOrZero` |
| `ledger/dto/AccountResponse.java` (new), `TransferMapper.toAccountResponse` | response shape |
| `ledger/service/AccountService.java` (new) | `createAccount`: KYC check -> `ACC-%010d` from `count()+1` -> insert account (`balance_cents = initialDepositCents`) -> if deposit > 0: synthetic transaction (`from = to = new account`, key `open-{accountId}`) + `credit` ledger entry -> `account.created` outbox row. One DB transaction |
| `ledger/controller/AccountController.java` | `POST /api/accounts` -> 201 |
| `ledger/event/EventPublisher.java` | `accountCreatedPayload(...)`, `ACCOUNT_CREATED` |
| all 3 poms + `application.yml` | `spring-boot-starter-actuator`, only `/actuator/health` exposed (needed for UptimeRobot) |
| fraud/notification | `FRAUD CHECK ...`, `EMAIL sent ...`, `IGNORED event=...` log lines so every event is greppable |

Notes: `count()+1` can collide under concurrency (one request gets 409, retry). A zero deposit creates no ledger entry (`ledger_entries.amount_cents` must be > 0). `account.created` events fan out to all 4 QStash endpoints and are ignored by them.

---

## B) Full test scenario

Fresh seed assumed: Alice account = 100000 cents, Bob = 50000. Numbers (`CIF-000004`, `ACC-0000000004`) are `existing count + 1`, so they differ if you have created rows before. `Wait ~15 s` after steps that emit events (QStash + Render cold start).

Where to look: Render -> service -> **Logs** (search box). Fraud/Notification logs show only after the event arrives.

### Step 0: health

```bash
for u in $MAIN $FRAUD $NOTIF; do curl -s --max-time 90 $u/actuator/health; echo; done
```
Expected (each): `{"status":"UP"}` (first call may take 30-60 s on free tier).

### Step 1: create customer Rahim -> 201

```bash
MOBILE="01$(printf '%09d' $((RANDOM*RANDOM % 1000000000)))"
RESP=$(curl -s -w '\n%{http_code}' -X POST $MAIN/api/customers -H 'Content-Type: application/json' -d "{
 \"firstName\":\"Rahim\",\"lastName\":\"Uddin\",\"dateOfBirth\":\"1992-04-10\",
 \"mobileNo\":\"$MOBILE\",\"email\":\"rahim$RUN@example.com\",\"idType\":\"nid\",
 \"idNumber\":\"NID$RUN\",\"addressLine1\":\"House 1, Road 2, Dhaka\"}")
echo "${RESP##*$'\n'}"; echo "${RESP%$'\n'*}" | jq .
export CUSTOMER_ID=$(echo "${RESP%$'\n'*}" | jq -r .id)
```
Status `201`. Body:
```json
{"id":"<uuid>","customerNumber":"CIF-000004","displayName":"Rahim Uddin","mobileNo":"01...","email":"rahim<RUN>@example.com","kycStatus":"pending","amlRiskRating":"low","status":"active","createdAt":"2026-...Z"}
```
Note: `idNumber` is never returned.
DB:
```sql
SELECT customer_number, kyc_status, aml_risk_rating, status FROM customers WHERE id = '<CUSTOMER_ID>';
-- CIF-000004 | pending | low | active
```
Fraud log: `Customer <CUSTOMER_ID> scored low risk`
Notification log: `EMAIL sent to rahim<RUN>@example.com subject='Welcome to Novo Bank'`

### Step 2: duplicate mobile -> 409

```bash
curl -s -w '\n%{http_code}\n' -X POST $MAIN/api/customers -H 'Content-Type: application/json' -d "{
 \"firstName\":\"Dup\",\"lastName\":\"Licate\",\"dateOfBirth\":\"1990-01-01\",\"mobileNo\":\"$MOBILE\",
 \"idType\":\"nid\",\"idNumber\":\"OTHER$RUN\",\"addressLine1\":\"x\"}"
```
Status `409`. Body: `{"status":409,"message":"Mobile number already registered","timestamp":"..."}`
DB: `SELECT count(*) FROM customers WHERE mobile_no = '<MOBILE>';` -> `1`. No fraud/notification log (no event).

### Step 3: account before KYC -> 422 (proves the gate)

```bash
curl -s -w '\n%{http_code}\n' -X POST $MAIN/api/accounts -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"$CUSTOMER_ID\",\"accountType\":\"savings\",\"initialDepositCents\":50000}"
```
Status `422`. Body: `{"status":422,"message":"Customer KYC is not verified (status: pending)","timestamp":"..."}`
DB: `SELECT count(*) FROM accounts WHERE customer_id = '<CUSTOMER_ID>';` -> `0`.

### Step 4: verify KYC -> 200

```bash
curl -s -w '\n%{http_code}\n' -X PATCH $MAIN/api/customers/$CUSTOMER_ID/kyc \
  -H 'Content-Type: application/json' -d '{"kycStatus":"verified","verifiedBy":"officer-1"}'
```
Status `200`; body is the customer with `"kycStatus":"verified"`.
DB: `SELECT kyc_status, updated_at > created_at AS touched FROM customers WHERE id='<CUSTOMER_ID>';` -> `verified | t`.
Logs: `kyc.verified` fans out to both `customer-created` endpoints: Fraud and Notification each log `IGNORED event=kyc.verified at /webhooks/customer-created`.

### Step 5: open account with $500 -> 201

```bash
RESP=$(curl -s -w '\n%{http_code}' -X POST $MAIN/api/accounts -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"$CUSTOMER_ID\",\"accountType\":\"savings\",\"initialDepositCents\":50000}")
echo "${RESP##*$'\n'}"; echo "${RESP%$'\n'*}" | jq .
export ACCOUNT_ID=$(echo "${RESP%$'\n'*}" | jq -r .id)
```
Status `201`. Body:
```json
{"id":"<uuid>","customerId":"<CUSTOMER_ID>","accountNumber":"ACC-0000000004","accountType":"savings","balanceCents":50000,"status":"active","createdAt":"..."}
```
DB:
```sql
SELECT a.account_number, a.balance_cents, l.direction, l.amount_cents
FROM accounts a JOIN ledger_entries l ON l.account_id = a.id WHERE a.id = '<ACCOUNT_ID>';
-- ACC-0000000004 | 50000 | credit | 50000
SELECT event_type, published FROM outbox ORDER BY id DESC LIMIT 1;   -- account.created | f then t within ~5 s
```
Logs: both services `IGNORED event=account.created ...` (twice each, one per endpoint).

### Step 6: check balance -> 200

```bash
curl -s $MAIN/api/accounts/$ACCOUNT_ID/balance | jq .
curl -s "$MAIN/api/accounts/$ACCOUNT_ID/entries?limit=5" | jq .
```
Expected: `{"accountId":"<ACCOUNT_ID>","balanceCents":50000,"currency":"USD"}` and one entry `{"id":..,"transactionId":"<uuid>","accountId":"<ACCOUNT_ID>","direction":"credit","amountCents":50000,"createdAt":"..."}`.
DB: `SELECT balance_cents FROM accounts WHERE id='<ACCOUNT_ID>';` -> `50000`.

### Step 7: send $100 to Alice -> 201

```bash
RESP=$(curl -s -w '\n%{http_code}' -X POST $MAIN/api/transfers -H 'Content-Type: application/json' -d "{
 \"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$ALICE_ACC\",\"amountCents\":10000,\"idempotencyKey\":\"rahim-$RUN-1\"}")
echo "${RESP##*$'\n'}"; echo "${RESP%$'\n'*}" | jq .
export TXN1=$(echo "${RESP%$'\n'*}" | jq -r .transactionId)
```
Status `201`. Body: `{"transactionId":"<uuid>","status":"completed","amountCents":10000,"createdAt":"..."}`
DB:
```sql
SELECT direction, account_id, amount_cents FROM ledger_entries WHERE transaction_id = '<TXN1>' ORDER BY direction;
-- credit | <ALICE_ACC> | 10000
-- debit  | <ACCOUNT_ID> | 10000
```
Fraud log: `FRAUD CHECK result=PASS txn=<TXN1> account=<ACCOUNT_ID> amountCents=10000 rules=[]`
Notification log: `EMAIL sent to rahim<RUN>@example.com subject='Transfer completed'` and `SMS to <MOBILE>: Txn <TXN1> of $100.00 completed`

### Step 8: both balances

```bash
curl -s $MAIN/api/accounts/$ACCOUNT_ID/balance | jq -c .balanceCents   # 40000
curl -s $MAIN/api/accounts/$ALICE_ACC/balance  | jq -c .balanceCents   # 110000
```
```sql
SELECT id, balance_cents FROM accounts WHERE id IN ('<ACCOUNT_ID>','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');  -- 40000 / 110000
```

### Step 9: duplicate idempotency key -> same transaction, no double debit

```bash
curl -s -w '\n%{http_code}\n' -X POST $MAIN/api/transfers -H 'Content-Type: application/json' -d "{
 \"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$ALICE_ACC\",\"amountCents\":10000,\"idempotencyKey\":\"rahim-$RUN-1\"}"
curl -s $MAIN/api/accounts/$ACCOUNT_ID/balance | jq -c .balanceCents
```
Status **`200`** (a new transfer is 201, an idempotent replay is 200), **same** `transactionId` as `$TXN1`; balance still `40000`.
```sql
SELECT count(*) FROM transactions WHERE idempotency_key = 'rahim-<RUN>-1';        -- 1
SELECT count(*) FROM ledger_entries WHERE transaction_id = '<TXN1>';              -- 2
```
Redis (Upstash console -> CLI): `GET idem:rahim-<RUN>-1` returns the cached JSON, `TTL idem:rahim-<RUN>-1` <= 3600. No new fraud/notification log for this call (no new event).

### Step 10: fund Alice a second account ($10,000) for the bigger transfers

Rahim only has $400 left, so the $600 and $6000 transfers need a richer sender. Using the real API (not SQL) keeps the ledger reconcilable.

```bash
RESP=$(curl -s -X POST $MAIN/api/accounts -H 'Content-Type: application/json' \
  -d "{\"customerId\":\"$ALICE_CUST\",\"accountType\":\"checking\",\"initialDepositCents\":1000000}")
echo "$RESP" | jq .; export ALICE_ACC2=$(echo "$RESP" | jq -r .id)
```
Status `201`, `balanceCents: 1000000`, `accountNumber: ACC-0000000005`. (Seed customers are `verified`.)

### Step 11: $600 to Bob -> 201

```bash
curl -s -w '\n%{http_code}\n' -X POST $MAIN/api/transfers -H 'Content-Type: application/json' -d "{
 \"fromAccount\":\"$ALICE_ACC2\",\"toAccount\":\"$BOB_ACC\",\"amountCents\":60000,\"idempotencyKey\":\"alice-$RUN-600\"}"
```
Status `201`, `amountCents: 60000`. Balances: Alice acc2 `940000`, Bob `110000`.
Fraud log: `FRAUD CHECK result=PASS txn=<id> ... amountCents=60000 rules=[]`. Notification: `EMAIL sent to alice@example.com subject='Transfer completed'`.

### Step 12: $6000 -> succeeds, fraud flags it

```bash
RESP=$(curl -s -X POST $MAIN/api/transfers -H 'Content-Type: application/json' -d "{
 \"fromAccount\":\"$ALICE_ACC2\",\"toAccount\":\"$BOB_ACC\",\"amountCents\":600000,\"idempotencyKey\":\"alice-$RUN-6000\"}")
echo "$RESP" | jq .; export TXN6K=$(echo "$RESP" | jq -r .transactionId)
```
Status `201` (fraud is detected asynchronously and does **not** block the transfer). After ~15 s:
```sql
SELECT rule_fired, severity, details FROM fraud_alerts WHERE transaction_id = '<TXN6K>';
-- HIGH_VALUE | medium | {"amount_cents":600000,"to_account":"bbbb...","event_created_at":"..."}
```
Fraud logs: `FRAUD ALERT rule=HIGH_VALUE txn=<TXN6K> account=<ALICE_ACC2> amountCents=600000` then `FRAUD CHECK result=FLAGGED txn=<TXN6K> ... rules=[HIGH_VALUE]`.
Balances: Alice acc2 `340000`, Bob `710000`.

### Step 13: $999,999 -> 422 insufficient funds

```bash
curl -s -w '\n%{http_code}\n' -X POST $MAIN/api/transfers -H 'Content-Type: application/json' -d "{
 \"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$BOB_ACC\",\"amountCents\":99999900,\"idempotencyKey\":\"rahim-$RUN-big\"}"
```
Status `422`. Body: `{"status":422,"message":"Insufficient funds","timestamp":"..."}`
DB: `SELECT count(*) FROM transactions WHERE idempotency_key = 'rahim-<RUN>-big';` -> `0` (rolled back / never written); Rahim still `40000`. No fraud/notification log.

### Step 14: reconcile everything

```sql
-- every outbox row delivered (allow ~5 s after the last call)
SELECT event_type, published, count(*) FROM outbox GROUP BY 1,2 ORDER BY 1,2;      -- no published = f rows
-- ledger == balance for accounts opened through the API (net of credits and debits)
SELECT a.account_number, a.balance_cents,
       COALESCE(SUM(CASE l.direction WHEN 'credit' THEN l.amount_cents ELSE -l.amount_cents END),0) AS ledger_net
FROM accounts a LEFT JOIN ledger_entries l ON l.account_id = a.id
WHERE a.account_number LIKE 'ACC-%' GROUP BY 1,2;                                    -- balance_cents = ledger_net
-- money conserved: seed 170000 + Rahim 50000 + Alice acc2 1000000 per fresh run
SELECT sum(balance_cents) FROM accounts;                                             -- 1220000 on a fresh DB
```

---

### Quick 6-call check (minimal path)

Uses the variables from the top (`MAIN`, `ALICE_ACC`). Field names are camelCase (`kycStatus`), not `kyc_status`.

```bash
# 1. create customer -> 201
RESP=$(curl -s -X POST $MAIN/api/customers -H 'Content-Type: application/json' -d "{\"firstName\":\"Q\",\"lastName\":\"Check\",\"dateOfBirth\":\"1990-01-01\",\"mobileNo\":\"01$(printf '%09d' $((RANDOM*RANDOM % 1000000000)))\",\"idType\":\"nid\",\"idNumber\":\"Q$RUN\",\"addressLine1\":\"x\"}")
export CUSTOMER_ID=$(echo "$RESP" | jq -r .id)
curl -s -X PATCH $MAIN/api/customers/$CUSTOMER_ID/kyc -H 'Content-Type: application/json' -d '{"kycStatus":"verified","verifiedBy":"officer-1"}' | jq -r .kycStatus   # verified

# 3. create account (needed before step 2 can use it) -> 201
RESP=$(curl -s -X POST $MAIN/api/accounts -H 'Content-Type: application/json' -d "{\"customerId\":\"$CUSTOMER_ID\",\"accountType\":\"savings\",\"initialDepositCents\":50000}")
export ACCOUNT_ID=$(echo "$RESP" | jq -r .id)

# 2. customer by account -> 200, body has "kycStatus":"verified"
curl -s -w '\n%{http_code}\n' $MAIN/api/customers/by-account/$ACCOUNT_ID | jq .

# 4. transfer $100 to Alice -> 201; then grep logs: Fraud "FRAUD CHECK result=PASS txn=<id>", Notification "EMAIL sent to"
curl -s -w '\n%{http_code}\n' -X POST $MAIN/api/transfers -H 'Content-Type: application/json' -d "{\"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$ALICE_ACC\",\"amountCents\":10000,\"idempotencyKey\":\"q-$RUN-1\"}"

# 6. same key again -> 200, SAME transactionId, ledger_entries count unchanged (2 for that transaction)
curl -s -w '\n%{http_code}\n' -X POST $MAIN/api/transfers -H 'Content-Type: application/json' -d "{\"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$ALICE_ACC\",\"amountCents\":10000,\"idempotencyKey\":\"q-$RUN-1\"}"
#    SQL: SELECT count(*) FROM ledger_entries WHERE account_id = '<ACCOUNT_ID>' AND direction = 'debit';   -- 1

# 5. unverified customer -> 422. In the Supabase SQL Editor:
#    UPDATE customers SET kyc_status = 'pending' WHERE id = '<CUSTOMER_ID>';
#    then the Redis cache (kyc:<ACCOUNT_ID>, up to 600 s) must be cleared or expired first: Upstash CLI  DEL kyc:<ACCOUNT_ID>
curl -s -w '\n%{http_code}\n' -X POST $MAIN/api/transfers -H 'Content-Type: application/json' -d "{\"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$ALICE_ACC\",\"amountCents\":100,\"idempotencyKey\":\"q-$RUN-2\"}"
#    -> 422 {"status":422,"message":"Customer KYC is not verified (status: pending)","timestamp":"..."}
```

If `CUSTOMER_SERVICE_URL` is set on the ledger and the Customer Service is asleep or down, step 4/5 return `503 {"status":503,"message":"Customer service unavailable, try again shortly",...}` and nothing is written.

---

## C) Render deployment

**Repos:** keep the **monorepo** (`istiakahasan/Novo_Bank_Service_PLC`, branch `initial`/`main`) and give each service a Root Directory. Separate repos would force copy/paste of the Maven wrapper and the shared Customer/Ledger code, and Render free has no benefit from splitting.

**Runtime:** Render has no native Java runtime, only Docker. So there is no "build command / start command" box: use **Runtime = Docker**. The Dockerfiles run exactly the commands you asked for (`./mvnw clean package -DskipTests` at build, `java -jar app.jar` at start with `-Xmx384m`).

For each service: Render dashboard -> **New +** -> **Web Service** -> **Build and deploy from a Git repository** -> select the repo -> **Connect**, then:

| Field | `novo-bank` | `fraud-service` | `notification-service` |
|---|---|---|---|
| Name | `novo-bank` | `fraud-service` | `notification-service` |
| Branch | `initial` | `initial` | `initial` |
| Root Directory | *(blank)* | `fraud-service` | `notification-service` |
| Runtime | Docker | Docker | Docker |
| Instance Type | Free | Free | Free |
| Health Check Path (Advanced) | `/actuator/health` | `/actuator/health` | `/actuator/health` |

Click **Advanced -> Add Environment Variable**, paste the pairs, then **Create Web Service**. (`PORT` is set by Render; do not add it.)

### Env vars: `novo-bank` (Customer + Ledger)
```
SUPABASE_HOST=aws-0-<region>.pooler.supabase.com
SUPABASE_DB_USER=postgres.<project-ref>
SUPABASE_DB_PASSWORD=<db password>
REDIS_HOST=<name>.upstash.io
REDIS_PORT=6379
REDIS_PASSWORD=<upstash password>
REDIS_SSL=true
QSTASH_TOPIC_URL=https://qstash.upstash.io/v2/publish/banking-events
QSTASH_TOKEN=<qstash token>
```
### Env vars: `fraud-service`
```
SUPABASE_HOST=...            SUPABASE_DB_USER=...           SUPABASE_DB_PASSWORD=...
REDIS_HOST=...  REDIS_PORT=6379  REDIS_PASSWORD=...  REDIS_SSL=true
CUSTOMER_SERVICE_URL=https://novo-bank.onrender.com
QSTASH_CURRENT_SIGNING_KEY=<sig_...>
QSTASH_NEXT_SIGNING_KEY=<sig_...>
```
### Env vars: `notification-service`
```
REDIS_HOST=...  REDIS_PORT=6379  REDIS_PASSWORD=...  REDIS_SSL=true
CUSTOMER_SERVICE_URL=https://novo-bank.onrender.com
QSTASH_CURRENT_SIGNING_KEY=<sig_...>
QSTASH_NEXT_SIGNING_KEY=<sig_...>
MAIL_HOST=smtp.mailtrap.io   MAIL_PORT=2525
MAIL_USERNAME=<mailtrap user>   MAIL_PASSWORD=<mailtrap pass>   MAIL_FROM=noreply@novobank.test
```
Notification has no database. Fraud's `CUSTOMER_SERVICE_URL` is needed for rule 3 and onboarding scoring.

### Order of deployment
1. Supabase: run `db/schema.sql`, `db/seed.sql`, `db/002_fraud_alerts.sql`.
2. Deploy `novo-bank`, wait for `Started NovoBankServicePlcApplication`.
3. Deploy `fraud-service` and `notification-service`.
4. Create the QStash URL group `banking-events` with the 4 endpoints (see `docs/QSTASH_WIRING.md`), put its token/URL into `novo-bank`.

### What a missing env var looks like
Logs show a startup failure like:
```
Caused by: java.lang.IllegalArgumentException: Could not resolve placeholder 'SUPABASE_HOST' in value "jdbc:postgresql://${SUPABASE_HOST}:5432/postgres"
...
APPLICATION FAILED TO START / Error creating bean with name 'dataSource'
```
Same message with `REDIS_HOST`, `QSTASH_TOPIC_URL`, `CUSTOMER_SERVICE_URL` etc. Wrong DB password instead gives `FATAL: password authentication failed for user "postgres.<ref>"`; wrong host gives `UnknownHostException`. Schema drift gives `Schema-validation: missing table [fraud_alerts]` (forgot `002_fraud_alerts.sql`) or `wrong column type ... payload` (see Troubleshooting).

### Reading logs of a failed start
Render -> the service -> **Logs** (live) -> scroll to the first `ERROR` / `APPLICATION FAILED TO START` block; **Events** tab shows `Deploy failed` / `Exited with status 1` and the build log. Build failures (compile, wrong Root Directory = `Could not find mvnw`) appear in the build log before the service ever starts.

### Keeping the free tier warm (read the math)
Render free = **750 instance-hours per month for the whole workspace**. One service awake 24/7 is ~720 h. **Three services awake 24/7 would need ~2,160 h and the quota runs out around day 10** (then they are suspended). So:
- Keep only `novo-bank` warm 24/7 with UptimeRobot. Let `fraud-service` and `notification-service` sleep: QStash retries wake them (first attempt times out, a retry succeeds), at the cost of delayed alerts/emails.
- If you must keep all three warm, use one per month or upgrade.

UptimeRobot (free, `https://uptimerobot.com`): **+ New monitor** -> Monitor Type **HTTP(s)** -> Friendly Name `novo-bank` -> URL `https://novo-bank.onrender.com/actuator/health` -> Monitoring Interval **5 minutes** (free minimum; anything < 15 min keeps the service awake) -> Alert contacts: your email -> **Create Monitor**. Add the same for the other two only if you accept the hours cost.

---

## D) Free-tier monitoring

**Supabase**
- Size: `SELECT pg_size_pretty(pg_database_size('postgres'));` (limit 500 MB; read-only above it: `cannot execute INSERT in a read-only transaction`). Per table: `SELECT relname, pg_size_pretty(pg_total_relation_size(relid)) FROM pg_catalog.pg_statio_user_tables ORDER BY pg_total_relation_size(relid) DESC;`
- Dashboard: Project -> Reports -> Database.
- Avoid the 7-day pause: GitHub Action `.github/workflows/supabase-keepalive.yml` (committed) runs `psql ... select now()` daily. Setup: GitHub repo -> Settings -> Secrets and variables -> Actions -> **New repository secret** `SUPABASE_DB_URL` = `postgresql://postgres.<ref>:<URL-encoded password>@aws-0-<region>.pooler.supabase.com:5432/postgres`. Run once from the Actions tab (**Run workflow**) to confirm green. GitHub disables scheduled workflows after 60 days with no repo activity; re-enable it in the Actions tab.
- If paused anyway: dashboard shows "Project paused" -> **Restore project**.

**Upstash Redis**
- Console -> Redis -> your DB -> **Usage / Details**: *Commands* (monthly, limit 500K), *Data size* (limit 256 MB). CLI tab: `INFO memory`, `DBSIZE`, `TTL idem:<key>`.
- Cost control: every transfer uses ~6 commands; idempotency/KYC/cache keys all have TTLs.
- When exceeded: errors such as `ERR max daily request limit exceeded` (message wording may vary); transfers then fail with a 5xx because Redis is on the hot path.

**Upstash QStash**
- Console -> QStash -> **Usage** (daily messages, limit 1,000). Each event is delivered to 4 endpoints, so budget about 250 events/day (verify against the Usage graph).
- When exceeded, QStash answers the publish call with **HTTP 429**. In the app log (`novo-bank`): `Outbox event <id> not delivered, will retry: 429 Too Many Requests: ...` (body text depends on QStash; confirm in your log). Rows stay `published=false` and are retried every 5 s, so nothing is lost; they flush after the daily reset.
- Failed deliveries to your endpoints (after retries) land in QStash -> **DLQ**.

**Render**
- Hibernation = first request after 15 min idle takes 30-60 s. Detect: `curl -s -o /dev/null -w '%{time_total}\n' $MAIN/actuator/health` (> 30 = it was asleep) or Render **Events**: `Service spun down due to inactivity`.
- Hours used: Dashboard -> Account/Workspace -> **Billing / Usage** (free instance hours).
- Fix: UptimeRobot monitor (Part C), within the 750-hour limit.

---

## E) Troubleshooting

| Symptom | Likely cause | Exact fix |
|---|---|---|
| 502 Bad Gateway on Render | Service still booting/waking, crashed, or not listening on `$PORT` | Render -> Logs; wait 60 s and retry; if `APPLICATION FAILED TO START`, fix env vars (Part C). Dockerfile already binds `$PORT` |
| First request takes 30-60 s | Free-tier hibernation | UptimeRobot monitor on `/actuator/health` |
| Build fails `mvnw: not found` / `Could not find or load main class` | Wrong Root Directory | Settings -> Root Directory = blank (main) / `fraud-service` / `notification-service` -> Manual Deploy |
| QStash webhook returns 401 | Signing keys missing/wrong, or body modified by a proxy | Copy `QSTASH_CURRENT_SIGNING_KEY` and `QSTASH_NEXT_SIGNING_KEY` from QStash console (no spaces) to Render env; test with `scripts/qstash-curl.sh`; never set `QSTASH_VERIFY_SIGNATURES=false` in prod |
| QStash message in DLQ / retries failing | Target asleep (timeouts) or 5xx | QStash -> DLQ -> **Retry**; check service Logs for the stack trace |
| Customer Service returns 404 for valid UUID | UUID is an *account* id (use `/api/accounts/{id}/balance`) or a customer in another DB/project | `SELECT id FROM customers WHERE id='<uuid>';` and compare `SUPABASE_HOST` |
| `by-account` returns 404 | Account has no customer row | `SELECT customer_id FROM accounts WHERE id='<uuid>';` then check that customer exists |
| Transfer succeeds but no fraud alert | Rule not hit (alerts only on HIGH_VALUE > $5,000, VELOCITY > 10/min, high-risk customer), or event never delivered | Check `outbox.published`; Fraud logs for `FRAUD CHECK txn=<id>`; QStash -> Logs |
| No `FRAUD CHECK` log at all | Fraud service asleep and retries pending, or endpoint URL wrong in URL group | QStash -> URL Groups -> `banking-events` -> check 4 URLs; `curl $FRAUD/actuator/health` |
| Duplicate transfer creates two ledger entries | Different `idempotencyKey` used (a new key is a new transfer) | Reuse the same key. Verify: `SELECT idempotency_key,count(*) FROM transactions GROUP BY 1 HAVING count(*)>1;` must return 0 rows (UNIQUE constraint) |
| Transfer returns 409 "Account is busy" | Redis lock `lock:{from}` held (concurrent request, 5 s TTL) | Retry after 1-5 s. Stuck key: Upstash CLI `DEL lock:<account>` |
| Redis connection refused / `RedisConnectionFailureException` | Wrong `REDIS_HOST/PORT`, SSL off, or monthly quota exhausted | Use Upstash host, port 6379, `REDIS_SSL=true`; check Usage tab |
| Supabase connection pool exhausted (`MaxClientsInSessionMode` / `remaining connection slots`) | Too many connections: 3 services x Hikari pool | Pool sizes are 5/3; switch `SUPABASE_HOST` to the transaction pooler port 6543 (`jdbc:...:6543`) or lower `spring.datasource.hikari.maximum-pool-size` |
| `FATAL: password authentication failed` | Wrong `SUPABASE_DB_USER`/password | User must be `postgres.<project-ref>` on the pooler; Supabase -> Project Settings -> Database -> **Reset database password**, update Render |
| KYC verified but ledger still rejects | Stale answer or wrong customer: only `verified` is cached, so this means the **account's** `customer_id` differs from the customer you verified | `SELECT a.customer_id, c.kyc_status FROM accounts a JOIN customers c ON c.id=a.customer_id WHERE a.id='<acc>';` |
| Outbox rows stuck `published=false` | QStash token/URL wrong (401/404), daily limit (429), or app asleep | Log line `Outbox event N not delivered, will retry: <status>`; fix `QSTASH_TOPIC_URL`/`QSTASH_TOKEN`; rows flush automatically within 5 s |
| `Schema-validation: wrong column type ... payload/details` | JSONB mapping mismatch | Confirm column is `jsonb` (`\d outbox`); the entities use `@JdbcTypeCode(SqlTypes.JSON)`; report the full error |
| `Schema-validation: missing table [fraud_alerts]` | `db/002_fraud_alerts.sql` not run | Run it in Supabase SQL Editor and redeploy fraud |
| No emails in Mailtrap | Wrong SMTP creds, or customer has no email | Notification logs `EMAIL sent ...` or `Customer ... has no email`; `535 Authentication failed` -> recheck `MAIL_USERNAME/PASSWORD` |
| Supabase project paused | 7 days inactive | Dashboard -> **Restore project**; enable the keep-alive workflow |
| Insufficient funds when balance looks fine | Concurrent debit changed the balance | Expected 422 `Insufficient funds (balance changed concurrently)`; retry |

---

## F) Final verification checklist

Run after setting the variables at the top. Each line is pass/fail. `psql "$PSQL_URL" -tAc` prints one value.

1. [ ] Main app up: `curl -s $MAIN/actuator/health | jq -e '.status=="UP"'`
2. [ ] Fraud up: `curl -s $FRAUD/actuator/health | jq -e '.status=="UP"'`
3. [ ] Notification up: `curl -s $NOTIF/actuator/health | jq -e '.status=="UP"'`
4. [ ] Six tables exist: `psql "$PSQL_URL" -tAc "select count(*) from information_schema.tables where table_schema='public' and table_name in ('customers','accounts','transactions','ledger_entries','outbox','fraud_alerts')"` -> `6`
5. [ ] Seed accounts: `psql "$PSQL_URL" -tAc "select count(*) from accounts where id in ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','cccccccc-cccc-4ccc-8ccc-cccccccccccc')"` -> `3`
6. [ ] Ledger append-only rules: `psql "$PSQL_URL" -tAc "select count(*) from pg_rules where tablename='ledger_entries'"` -> `2`
7. [ ] Balance functions: `psql "$PSQL_URL" -tAc "select count(*) from pg_proc where proname in ('increment_balance','decrement_balance')"` -> `2`
8. [ ] RLS on: `psql "$PSQL_URL" -tAc "select count(*) from pg_tables where schemaname='public' and rowsecurity"` -> `6` or more
9. [ ] Redis reachable: `curl -s -H "Authorization: Bearer $UPSTASH_REDIS_REST_TOKEN" $UPSTASH_REDIS_REST_URL/ping | jq -e '.result=="PONG"'`
10. [ ] QStash group has 4 endpoints: `curl -s -H "Authorization: Bearer $QSTASH_TOKEN" https://qstash.upstash.io/v2/topics | jq -e '[.[]|select(.name=="banking-events")|.endpoints|length]==[4]'` (if 404, the console's "URL Groups" API path is `/v2/urlGroups`)
11. [ ] Unsigned webhook rejected (fraud): `test "$(curl -s -o /dev/null -w '%{http_code}' -X POST $FRAUD/webhooks/transfer-completed -H 'Content-Type: application/json' -d '{}')" = 401`
12. [ ] Unsigned webhook rejected (notification): same with `$NOTIF/webhooks/customer-created` -> `401`
13. [ ] Customer create 201 (Step 1): `test "$CODE" = 201` from the e2e script / Step 1 output
14. [ ] Duplicate mobile 409 (Step 2)
15. [ ] Account before KYC 422 (Step 3)
16. [ ] KYC verify 200 and DB shows verified: `psql "$PSQL_URL" -tAc "select kyc_status from customers where id='$CUSTOMER_ID'"` -> `verified`
17. [ ] Account open 201 with balance 50000: `curl -s $MAIN/api/accounts/$ACCOUNT_ID/balance | jq -e '.balanceCents==50000'`
18. [ ] Transfer 201: `curl -s -X POST $MAIN/api/transfers ... | jq -e '.status=="completed"'`
19. [ ] Double entry: `psql "$PSQL_URL" -tAc "select count(*) from ledger_entries where transaction_id='$TXN1'"` -> `2`
20. [ ] Balances after $100: `curl -s $MAIN/api/accounts/$ACCOUNT_ID/balance | jq -e '.balanceCents==40000'`
21. [ ] Idempotent replay: second POST returns HTTP `200`, `.transactionId == $TXN1`, and Rahim balance still `40000`
22. [ ] One transaction row per key: `psql "$PSQL_URL" -tAc "select count(*) from transactions where idempotency_key='rahim-$RUN-1'"` -> `1`
23. [ ] Redis idempotency cache: `curl -s -H "Authorization: Bearer $UPSTASH_REDIS_REST_TOKEN" $UPSTASH_REDIS_REST_URL/get/idem:rahim-$RUN-1 | jq -e '.result!=null'`
24. [ ] Insufficient funds 422 and nothing written: `psql "$PSQL_URL" -tAc "select count(*) from transactions where idempotency_key='rahim-$RUN-big'"` -> `0`
25. [ ] $6000 transfer 201 and fraud alert row (wait 15 s): `psql "$PSQL_URL" -tAc "select count(*) from fraud_alerts where transaction_id='$TXN6K' and rule_fired='HIGH_VALUE'"` -> `1`
26. [ ] Outbox drained: `psql "$PSQL_URL" -tAc "select count(*) from outbox where published=false and created_at < now() - interval '1 minute'"` -> `0`
27. [ ] Ledger reconciles with balances (Step 14 query returns no row where `balance_cents <> ledger_net`): `psql "$PSQL_URL" -tAc "select count(*) from (select a.balance_cents, coalesce(sum(case l.direction when 'credit' then l.amount_cents else -l.amount_cents end),0) n from accounts a left join ledger_entries l on l.account_id=a.id where a.account_number like 'ACC-%' group by a.id,a.balance_cents) t where balance_cents<>n"` -> `0`
28. [ ] Fraud saw the event: Render fraud Logs contain `FRAUD CHECK result=PASS txn=<TXN1>`
29. [ ] Notification sent the email: Notification Logs contain `EMAIL sent to rahim<RUN>@example.com subject='Transfer completed'` and Mailtrap inbox shows it
30. [ ] Keep-alive workflow green: `gh run list --workflow=supabase-keepalive.yml -L 1 --json conclusion -q '.[0].conclusion'` -> `success`
31. [ ] All unit tests green in CI/locally: `./mvnw -q package && (cd fraud-service && ./mvnw -q package) && (cd notification-service && ./mvnw -q package)` exits 0
