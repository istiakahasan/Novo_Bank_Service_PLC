# Authorization design

Scope: only authorization was added. Services were not redesigned. Customer + Ledger are one deployable (`novo-bank`), so there are **five** Render services: `auth-service` (new), `novo-bank`, `fraud-service`, `notification-service` (the diagram's Customer :3003 and Ledger :3004 are the same JVM).

Everything below is implemented; file paths are in "Where the code is" at the end. Verified by unit/slice tests (starter 8, auth 9, main app 13 security tests, fraud 3, notification 3). Not run against live Render/Supabase/Redis.

---

## Deliverable 1: authorization matrix

ALLOW = allowed, DENY = 403 (or 401 if unauthenticated), CONDITIONAL = allowed only if the condition holds, otherwise 403.

| Service | Endpoint | CUSTOMER | STAFF | SYSTEM | ANONYMOUS |
|---|---|---|---|---|---|
| Customer | `POST /api/customers` | DENY | ALLOW | DENY | DENY |
| Customer | `GET /api/customers/{id}` | CONDITIONAL: `{id}` equals the `cid` in the token | ALLOW | ALLOW | DENY |
| Customer | `GET /api/customers/by-mobile/{mobile}` | DENY (prevents enumerating people by phone) | ALLOW | ALLOW | DENY |
| Customer | `GET /api/customers/by-account/{accountId}` | CONDITIONAL: the account belongs to the token's `cid` | ALLOW | ALLOW | DENY |
| Customer | `PATCH /api/customers/{id}/kyc` | DENY | ALLOW | DENY | DENY |
| Customer | `PATCH /api/customers/{id}/aml-risk` (extra) | DENY | ALLOW | ALLOW (fraud-service) | DENY |
| Ledger | `POST /api/transfers` | CONDITIONAL: `fromAccount` belongs to the token's `cid` | ALLOW | DENY | DENY |
| Ledger | `GET /api/transfers/{id}` | CONDITIONAL: the debited or credited account belongs to `cid` | ALLOW | DENY | DENY |
| Ledger | `GET /api/accounts/{id}/balance` | CONDITIONAL: account belongs to `cid` | ALLOW | DENY | DENY |
| Ledger | `GET /api/accounts/{id}/entries` | CONDITIONAL: account belongs to `cid` | ALLOW | DENY | DENY |
| Ledger | `POST /api/accounts` | CONDITIONAL: body `customerId` equals `cid` (and the customer must be KYC-verified) | ALLOW (customer must be KYC-verified) | DENY | DENY |
| Fraud | `POST /webhooks/transfer-completed` | DENY (a user token does not help) | DENY | DENY | CONDITIONAL: valid `Upstash-Signature` (QStash only) |
| Fraud | `POST /webhooks/customer-created` | DENY | DENY | DENY | CONDITIONAL: valid `Upstash-Signature` |
| Notification | `POST /webhooks/transfer-completed` | DENY | DENY | DENY | CONDITIONAL: valid `Upstash-Signature` |
| Notification | `POST /webhooks/customer-created` | DENY | DENY | DENY | CONDITIONAL: valid `Upstash-Signature` |
| All | `GET /actuator/health` | ALLOW | ALLOW | ALLOW | ALLOW |
| All other paths | | DENY | DENY | DENY | DENY (`denyAll`) |

Design rules behind the matrix:
- SYSTEM can only read customers and update AML risk; it can never move money or read ledgers.
- SYSTEM is granted only by a valid service key. A user JWT claiming `SYSTEM` is ignored.
- Webhooks are open at the Spring Security layer (`permitAll`) because QStash cannot send a JWT; the signature check in the controller is their authentication. Sending a user JWT does not bypass it.
- Ownership failures return 403 for unknown ids too, so other people's records are not revealed.

---

## Deliverable 2: token strategy

**Chosen: self-issued RS256 JWT (jjwt) from a small Spring Boot `auth-service`.**

1. **Fits the free tier:** Keycloak needs roughly 700 MB-1 GB RAM and 1-2 minutes to start; it will **not** fit Render's 512 MB. The auth service runs with a 300 MB heap.
2. **No extra paid or capped dependency:** Supabase Auth is free (50K MAU) but would make a second identity store and tie token verification to Supabase's JWKS/settings; the identity data already lives in our Supabase Postgres.
3. **Verification needs no network:** services hold the RSA **public** key, so an auth-service cold start (30-60 s on Render free) only affects login and refresh, never normal API calls.
4. **Asymmetric on purpose:** only `auth-service` holds the private key; a compromised fraud or notification service cannot mint tokens (a shared HS256 secret would allow that).
5. **Solo-developer sized:** ~400 lines of Java, same stack, same deploy path as the other services.

**Identity data:** Supabase PostgreSQL, tables `auth_users` and `refresh_tokens` (`db/003_auth.sql`). Passwords: BCrypt. Refresh tokens: only their SHA-256 hash is stored.

**Issuing tokens** (`auth-service`, port 3007):

```
POST /auth/login
{"username":"alice","password":"..."}
-> 200 {"accessToken":"<jwt>","refreshToken":"<opaque>","tokenType":"Bearer","expiresIn":900}
```
Access token claims: `iss=novo-auth`, `sub=<user id>`, `jti`, `iat`, `exp`, `roles:["CUSTOMER"|"STAFF"]`, `cid:<customers.id>` (customers only), `username`.

**Lifetime / refresh:** access token 15 minutes; refresh token 7 days, opaque, **single use** (rotation). `POST /auth/refresh {"refreshToken":"..."}` returns a new pair and revokes the old refresh token. Presenting an already-used refresh token is treated as theft: every refresh token of that user is revoked. Login is rate limited: 5 failures per username per 15 minutes -> 429 (Redis `login_fail:{username}`).

**Verification without calling the auth server:** `JWT_PUBLIC_KEY` (base64 X.509 DER) is an env var on each service that accepts user tokens (`novo-bank`). The starter verifies signature, issuer, expiry locally. Key rotation: set `JWT_PUBLIC_KEY_NEXT` on the services first, then switch the auth-service private key, then drop the old public key. (`GET /auth/public-key` returns the current public key for convenience.)

---

## Deliverable 3: service-to-service authentication

**Mechanism: per-caller shared secret headers** (no auth-server call, works while anything sleeps).

```
X-Service-Id:  fraud-service
X-Service-Key: <64 hex chars>
```

| Where | Env var | Value |
|---|---|---|
| Customer API (`novo-bank`) | `SYSTEM_KEYS` | `ledger=KEY_L;fraud-service=KEY_F;notification-service=KEY_N` |
| fraud-service | `SERVICE_ID`, `SERVICE_KEY` | `fraud-service`, `KEY_F` |
| notification-service | `SERVICE_ID`, `SERVICE_KEY` | `notification-service`, `KEY_N` |
| Ledger, only if deployed separately from Customer | `SERVICE_ID`, `SERVICE_KEY`, `CUSTOMER_SERVICE_URL` | `ledger`, `KEY_L` |

Generate with `scripts/gen-keys.sh` (`openssl rand -hex 32`). When Customer and Ledger share a JVM (current deployment) the Ledger's KYC lookup is in-process and needs no key.

**Rotate without downtime** (each id accepts several keys, comma separated):
1. On `novo-bank` set `SYSTEM_KEYS=...;fraud-service=OLD,NEW;...` and redeploy (both work).
2. On the caller set `SERVICE_KEY=NEW` and redeploy.
3. On `novo-bank` remove `OLD` and redeploy.

Code:
- Outbound: `security-starter/.../SystemAuthInterceptor.java`, attached by `RestClientConfig` only to the Customer-Service client (never the QStash client, so the secret cannot leak to a third party).
- Inbound: `security-starter/.../SystemKeyAuthenticationFilter.java` (separate from the JWT filter): constant-time comparison, wrong pair -> 401, valid pair -> `ROLE_SYSTEM` with the service id as principal.

---

## Deliverable 4: implementation (where the code is)

| Item | Path |
|---|---|
| A) Auth server | `auth-service/` (`pom.xml`, `application.yml`, `AuthController`, `AuthService`, `TokenService`, entities, repositories, `BootstrapRunner`, `Dockerfile`) |
| B) Shared starter | `security-starter/` (Maven library `com.Bank_website:security-starter:1.0.0`): `JwtAuthenticationFilter`, `SecurityConfig` (auto-configuration), `RoleExtractor`, `UnauthorizedHandler`, `ForbiddenHandler`, plus `JwtVerifier`, `SystemKeyAuthenticationFilter`, `SystemAuthInterceptor`, `SecurityBaseline`, `RedisRevocationChecker` |
| C) Per-service rules | `ServiceSecurityConfig.java` in `novo-bank` (`.../security/`), `fraud-service` and `notification-service` (`.../config/`); ownership rules in `AccessPolicy.java` + `@PreAuthorize` on the controllers |
| D) Refresh + rotation | `AuthService.refresh` (`POST /auth/refresh`) |
| E) Logout / revocation | `AuthService.logout` (`POST /auth/logout`), checked by `RedisRevocationChecker` |

Add the starter to a service:

```xml
<dependency>
  <groupId>com.Bank_website</groupId>
  <artifactId>security-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```
Locally install it once: `./mvnw -f security-starter/pom.xml install`. The Dockerfiles do this for you, which is why `novo-bank`, `fraud-service` and `notification-service` must be built with the **repository root** as Docker context (see below).

Route rules (main app):

| Route | Rule |
|---|---|
| `GET /actuator/health` | public |
| `POST /api/customers`, `PATCH .../kyc` | STAFF |
| `PATCH .../aml-risk`, `GET .../by-mobile/*` | STAFF or SYSTEM |
| `GET /api/customers/*`, `.../by-account/*` | any authenticated role, then ownership via `@PreAuthorize` |
| `/api/transfers/**`, `/api/accounts/**` | CUSTOMER or STAFF, then ownership; SYSTEM denied |
| everything else | denied |

**Logout / revocation (Redis blocklist):**
- Key `revoked:jti:{jti}`, value `1`, TTL = seconds left until the access token's `exp` (so the list cleans itself).
- `POST /auth/logout` with `Authorization: Bearer <access>` and body `{"refreshToken":"..."}` -> 204; blocklists the access token's `jti` and revokes the refresh token.
- Every service checks the key on each authenticated request (1 Redis `EXISTS`). If Redis is unreachable the token is rejected (fail-closed); set `JWT_REVOCATION_FAIL_OPEN=true` to accept tokens instead during a Redis outage.

---

## Deliverable 5: QStash webhook authentication

QStash signs each delivery with header `Upstash-Signature`. **It is not a bare HMAC of the body**: it is an HS256 JWT. A check that HMACs the body and compares it to the header would reject every real QStash request, so the implemented check is:

1. Split the header into `header.payload.signature`; require `alg = HS256`.
2. `HMAC-SHA256(header + "." + payload, signingKey)` compared (constant time) with `signature`, trying `QSTASH_CURRENT_SIGNING_KEY` then `QSTASH_NEXT_SIGNING_KEY`.
3. `iss == "Upstash"`, `exp` / `nbf` within 60 s skew.
4. The `body` claim equals `base64url(SHA256(raw body))`, binding the signature to this exact payload.

Code: `fraud-service/.../security/QStashSignatureVerifier.java` and the identical copy in `notification-service`. Keys live in each webhook service's Render env: `QSTASH_CURRENT_SIGNING_KEY`, `QSTASH_NEXT_SIGNING_KEY` (copy from the QStash console). Missing header -> 401, invalid header -> 401.

Local test (builds a valid signature for any payload):

```bash
export QSTASH_CURRENT_SIGNING_KEY=sig_local
QSTASH_VERIFY_SIGNATURES=true ./mvnw spring-boot:run     # inside fraud-service, with the same key
scripts/qstash-curl.sh http://localhost:3005/webhooks/transfer-completed '{"event":"transfer.completed","transaction_id":"7b0c1d2e-0000-4000-8000-000000000001","from_account":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","to_account":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","amount_cents":600000}'
```

---

## Deliverable 6: attack scenarios

`scripts/attack-tests.sh` runs all of these and prints PASS/FAIL. Variables: `AUTH`, `MAIN`, `FRAUD` are the three Render URLs; users `staff`, `alice`, `bob` come from `AUTH_BOOTSTRAP_DEMO=true`.

```bash
# set up
login() { curl -s -X POST $AUTH/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" | jq -r .accessToken; }
export STAFF=$(login staff "$STAFF_PASSWORD") ALICE=$(login alice "$DEMO_PASSWORD")
export ALICE_ACC=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa BOB_ACC=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb
```

```bash
# 1. expect 401  (anonymous transfer)
curl -s -o /dev/null -w '%{http_code}\n' -X POST $MAIN/api/transfers -H 'Content-Type: application/json' \
  -d "{\"fromAccount\":\"$ALICE_ACC\",\"toAccount\":\"$BOB_ACC\",\"amountCents\":100,\"idempotencyKey\":\"atk-1\"}"
```
```bash
# 2. expect 403  (CUSTOMER creates a customer)
curl -s -o /dev/null -w '%{http_code}\n' -X POST $MAIN/api/customers -H "Authorization: Bearer $ALICE" -H 'Content-Type: application/json' -d '{}'
```
```bash
# 3. expect 403  (alice reads bob's balance; her own returns 200)
curl -s -o /dev/null -w '%{http_code}\n' $MAIN/api/accounts/$BOB_ACC/balance -H "Authorization: Bearer $ALICE"
```
```bash
# 4. expect 403  (CUSTOMER patches KYC)
export TARGET=<id of a pending customer>
curl -s -o /dev/null -w '%{http_code}\n' -X PATCH $MAIN/api/customers/$TARGET/kyc -H "Authorization: Bearer $ALICE" \
  -H 'Content-Type: application/json' -d '{"kycStatus":"verified","verifiedBy":"x"}'
```
```bash
# 5. expect 200  (STAFF patches KYC of a pending customer)
curl -s -o /dev/null -w '%{http_code}\n' -X PATCH $MAIN/api/customers/$TARGET/kyc -H "Authorization: Bearer $STAFF" \
  -H 'Content-Type: application/json' -d '{"kycStatus":"verified","verifiedBy":"officer-1"}'
```
```bash
# 6. expect 401  (expired but correctly signed JWT; needs keys/private.pem from scripts/gen-keys.sh)
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
H=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url); NOW=$(date +%s)
P=$(printf '{"iss":"novo-auth","sub":"x","roles":["STAFF"],"iat":%d,"exp":%d,"jti":"exp1"}' $((NOW-7200)) $((NOW-3600)) | b64url)
S=$(printf '%s' "$H.$P" | openssl dgst -sha256 -sign keys/private.pem | b64url)
curl -s -o /dev/null -w '%{http_code}\n' $MAIN/api/accounts/$ALICE_ACC/balance -H "Authorization: Bearer $H.$P.$S"
```
```bash
# 7. expect 401  (alice's token with the role claim changed to STAFF, original signature kept)
IFS=. read -r TH TP TS <<< "$ALICE"
dec() { s="${1//-/+}"; s="${s//_//}"; case $((${#s}%4)) in 2) s="$s==";; 3) s="$s=";; esac; printf '%s' "$s" | base64 -d; }
FORGED=$(dec "$TP" | jq -c '.roles=["STAFF"]' | b64url)
curl -s -o /dev/null -w '%{http_code}\n' -X POST $MAIN/api/customers -H "Authorization: Bearer $TH.$FORGED.$TS" -H 'Content-Type: application/json' -d '{}'
```
```bash
# 8. expect 200  (SYSTEM service key on /by-account)
curl -s -o /dev/null -w '%{http_code}\n' $MAIN/api/customers/by-account/$ALICE_ACC -H 'X-Service-Id: ledger' -H "X-Service-Key: $LEDGER_KEY"
```
```bash
# 9. expect 403  (a CUSTOMER token on another customer's /by-account; an own account would be 200 by the matrix)
curl -s -o /dev/null -w '%{http_code}\n' $MAIN/api/customers/by-account/$BOB_ACC -H "Authorization: Bearer $ALICE"
```
```bash
# 10. expect 401  (QStash webhook with an invalid signature)
curl -s -o /dev/null -w '%{http_code}\n' -X POST $FRAUD/webhooks/transfer-completed -H 'Content-Type: application/json' \
  -H 'Upstash-Signature: aaa.bbb.ccc' -d '{"event":"transfer.completed"}'
```

Also covered by automated tests: a token with `roles:["SYSTEM"]` gets no power; HMAC/`alg:none` tokens are rejected; a refresh token replay revokes all sessions; wrong service key -> 401; SYSTEM key on `/api/transfers` -> 403.

---

## Deliverable 7: free-tier impact

- **Redis (Upstash, 500K commands/month):** +1 `EXISTS` per authenticated request on `novo-bank`; login costs ~3-5 commands, logout 1. Error when exhausted (wording may vary): `ERR max daily request limit exceeded` / `ERR max monthly request limit exceeded`; with fail-closed revocation every authenticated call then returns 401. Mitigation: set `JWT_REVOCATION_FAIL_OPEN=true` (or skip the blocklist) while over quota and rely on the 15-minute token lifetime.
- **Render (750 free instance-hours/month for the whole workspace, 512 MB RAM each):** `auth-service` is an extra free service (300 MB heap cap). Error: service suspended / `503`, banner "Free instance hours used". Mitigation: keep only `novo-bank` warm; let `auth-service` sleep (only login/refresh wait ~30-60 s, token verification stays local).
- **Supabase (500 MB database):** `auth_users` + `refresh_tokens` are tiny, but expired refresh tokens accumulate. Error when full: `cannot execute INSERT in a read-only transaction`. Mitigation: periodically `DELETE FROM refresh_tokens WHERE expires_at < now() - interval '1 day'` (statement is in `db/003_auth.sql`).
- **Supabase Auth MAU (50K):** not used, no consumption.

---

## Setup order (new env vars only)

1. `scripts/gen-keys.sh` and copy the printed values.
2. Supabase SQL Editor: run `db/003_auth.sql`.
3. `auth-service` (Render, Docker, Root Directory `auth-service`): `SUPABASE_*`, `REDIS_*`, `JWT_PRIVATE_KEY`, `AUTH_BOOTSTRAP_PASSWORD`, optionally `AUTH_BOOTSTRAP_DEMO=true` (demo only; remove afterwards).
4. `novo-bank`: add `JWT_PUBLIC_KEY`, `SYSTEM_KEYS`.
5. `fraud-service` / `notification-service`: add `SERVICE_ID`, `SERVICE_KEY` (and the existing QStash keys).
6. **Docker build context:** `novo-bank` builds from the repo root as before. For `fraud-service` and `notification-service`: Root Directory **blank**, **Dockerfile Path** `./fraud-service/Dockerfile` (or `./notification-service/Dockerfile`), **Docker Build Context Directory** `.` — they now need the sibling `security-starter` module. `auth-service` keeps Root Directory `auth-service`.
7. Run `scripts/attack-tests.sh`.

Known limits: customers cannot self-register (STAFF creates customers and users via `POST /auth/users`); no password reset flow; no MFA; the SYSTEM keys are static secrets (rotate on a schedule).
