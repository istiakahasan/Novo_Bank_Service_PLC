# Auth Service (:3008)

Folder `auth-server/`, SQL `db/006_auth_server.sql`. Local email + password login, RS256 access tokens (15 minutes), opaque rotating refresh tokens (7 days), Redis jti blocklist, JWKS.

## SQL (Supabase SQL Editor)

Run the whole file `db/006_auth_server.sql`. It creates `auth_users` and `refresh_tokens` exactly as specified, but inside a schema called `auth_v2`, so they cannot collide with the `auth_users` / `refresh_tokens` tables of the older `auth-service` (`db/003_auth.sql`). The service reads that schema through `AUTH_DB_SCHEMA` (default `auth_v2`). To use the `public` schema instead, remove the `auth_v2.` prefixes in the file and set `AUTH_DB_SCHEMA=public`.

## Endpoints

| Method | Path | Who | Result |
|---|---|---|---|
| POST | `/api/auth/register` | STAFF bearer; or no token for the very first STAFF with `X-Bootstrap-Secret` | 201 `{userId,email,role}`; 409 duplicate email; 400 weak password |
| POST | `/api/auth/login` | public | 200 `{accessToken,refreshToken,role,customerId,agentId,expiresIn:900}`; 401 `Invalid credentials`; 423 + `Retry-After`; 403 disabled; 429 + `Retry-After` |
| POST | `/api/auth/refresh` | public (refresh token) | same shape, token rotated; 401 if unknown, revoked or expired |
| POST | `/api/auth/logout` | Bearer + `{refreshToken}` | 204; refresh token revoked, access token jti blocklisted |
| GET | `/api/auth/.well-known/jwks.json` (also `/.well-known/jwks.json`) | public | `{keys:[{kty,kid:"v1",use,alg,n,e}]}` |
| POST | `/api/auth/admin/unlock/{userId}` | STAFF | 200; clears lock and failed attempts |

Rules implemented: passwords BCrypt strength 12 (never logged or returned), password 12-72 characters with upper, lower, digit, symbol; emails stored lower-case; five wrong passwords lock the account for 15 minutes (the fifth wrong attempt already returns 423); lock check happens before the password check; unknown email and wrong password give the same 401 and take the same time; 10 login attempts per IP per minute (Redis `login_rate:{ip}`, uses the last `X-Forwarded-For` entry, which is the one Render appends); every error is `{status,message,timestamp}`; request bodies are never logged.

Token claims: `sub`=userId, `role`, `customerId` (customers) or `agentId` (staff), `iat`, `exp`=iat+900, `jti`, `iss`=`banking-auth`, header `kid`=`v1`. `roles` (list) and `cid` repeat `role` / `customerId` because the shared security-starter reads those names. Logout writes `jwt_blocklist:{jti}` and also `revoked:jti:{jti}` (the key the shared starter checks), both with TTL = remaining token life.

## Key generation

```bash
openssl genrsa -out jwt_private.pem 2048
openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem

# values to paste into Render (single line, no spaces)
JWT_PRIVATE_KEY=$(base64 jwt_private.pem | tr -d '\n')                                   # base64 of the PEM
JWT_PUBLIC_KEY=$(openssl rsa -in jwt_private.pem -pubout -outform DER | base64 | tr -d '\n')   # base64 of the DER
echo "$JWT_PRIVATE_KEY"; echo "$JWT_PUBLIC_KEY"
AUTH_BOOTSTRAP_SECRET=$(openssl rand -hex 24); echo "$AUTH_BOOTSTRAP_SECRET"
```
The auth service accepts the private key as PEM, base64 of the PEM, or base64 of the DER, in PKCS#1 or PKCS#8. The public key is written as base64 DER because that is the form the other services parse; the auth service refuses to start if `JWT_PUBLIC_KEY` does not belong to `JWT_PRIVATE_KEY`.

## Render deployment

1. Render dashboard -> **New +** -> **Web Service** -> **Build and deploy from a Git repository** -> select `Novo_Bank_Service_PLC` -> **Connect**.
2. **Name** `auth-service` (use a different name if the older auth-service is deployed); **Branch** `main`; **Runtime: Docker** (Render has no native Java runtime, so "mvn clean package -DskipTests" and "java -jar target/*.jar" are the Dockerfile's build and start steps); **Root Directory** `auth-server`; **Instance Type** Free.
3. **Advanced** -> **Health Check Path** `/actuator/health`.
4. **Advanced -> Add Environment Variable**:
```
SUPABASE_HOST=aws-0-<region>.pooler.supabase.com
SUPABASE_DB_USER=postgres.<project-ref>
SUPABASE_DB_PASSWORD=<db password>
REDIS_HOST=<name>.upstash.io
REDIS_PORT=6379
REDIS_PASSWORD=<upstash password>
JWT_PRIVATE_KEY=<value from the key generation step>
JWT_PUBLIC_KEY=<value from the key generation step>
AUTH_BOOTSTRAP_SECRET=<value from the key generation step>
PORT=3008
```
5. **Create Web Service**; wait for `Started AuthServiceApplication`.
6. UptimeRobot: **+ New monitor** -> HTTP(s) -> `https://auth-service.onrender.com/actuator/health` -> every 5 minutes (stay within Render's shared 750 free hours).
7. On every service that verifies tokens set `JWT_PUBLIC_KEY` (same value) and `JWT_ISSUER=banking-auth`.
8. To log in from the Next.js apps, set `NEXT_PUBLIC_AUTH_URL` to this service and `AUTH_LOGIN_PATH=/api/auth/login`, `AUTH_REFRESH_PATH=/api/auth/refresh`, `AUTH_LOGOUT_PATH=/api/auth/logout` (the login body field may be called `email` or `username`).
9. Seed the first STAFF user (needs the bootstrap secret, works only while no STAFF exists):

```bash
curl -s -X POST https://auth-service.onrender.com/api/auth/register -H 'Content-Type: application/json' \
  -H "X-Bootstrap-Secret: $AUTH_BOOTSTRAP_SECRET" \
  -d '{"email":"staff@novobank.example","password":"Staff-Pass-12345!","role":"STAFF"}'
```
Every later user, including more STAFF, is created with `POST /api/auth/register` by a logged-in STAFF user (bearer token). There is no admin seed endpoint; you can remove `AUTH_BOOTSTRAP_SECRET` afterwards.

## Six curl commands

```bash
export AUTH=https://auth-service.onrender.com
```

```bash
# 1. register the first STAFF user -> 201 {"userId":"<uuid>","email":"staff@novobank.example","role":"STAFF"}
curl -s -w '\n%{http_code}\n' -X POST $AUTH/api/auth/register -H 'Content-Type: application/json' \
  -H "X-Bootstrap-Secret: $AUTH_BOOTSTRAP_SECRET" \
  -d '{"email":"staff@novobank.example","password":"Staff-Pass-12345!","role":"STAFF"}'
```
```bash
# 2. register a CUSTOMER (done by STAFF) -> 201 {"userId":"<uuid>","email":"rahim@example.com","role":"CUSTOMER"}
STAFF_TOKEN=$(curl -s -X POST $AUTH/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"staff@novobank.example","password":"Staff-Pass-12345!"}' | jq -r .accessToken)
curl -s -w '\n%{http_code}\n' -X POST $AUTH/api/auth/register -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $STAFF_TOKEN" \
  -d '{"email":"rahim@example.com","password":"Rahim-Secure-2024!","role":"CUSTOMER","customerId":"11111111-1111-4111-8111-111111111111"}'
```
```bash
# 3. login success -> 200 {"accessToken":"<jwt>","refreshToken":"<43 chars>","role":"CUSTOMER","customerId":"1111...","agentId":null,"expiresIn":900}
curl -s -w '\n%{http_code}\n' -X POST $AUTH/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"rahim@example.com","password":"Rahim-Secure-2024!"}'
```
```bash
# 4. wrong password: attempts 1-4 -> 401 {"status":401,"message":"Invalid credentials",...}; attempt 5 -> 423 with "Retry-After: 900"; the correct password is refused while locked
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -D - -w 'attempt '$i' -> %{http_code}\n' -X POST $AUTH/api/auth/login -H 'Content-Type: application/json' \
    -d '{"email":"rahim@example.com","password":"Wrong-Password-1!"}' | grep -iE "^HTTP|retry-after|attempt"
done
# unlock with: curl -X POST $AUTH/api/auth/admin/unlock/<userId> -H "Authorization: Bearer $STAFF_TOKEN"   -> 200
```
```bash
# 5. refresh -> 200 new accessToken + new refreshToken; using the OLD refresh token again -> 401 (and every session of that user is revoked)
LOGIN=$(curl -s -X POST $AUTH/api/auth/login -H 'Content-Type: application/json' -d '{"email":"staff@novobank.example","password":"Staff-Pass-12345!"}')
RT=$(echo "$LOGIN" | jq -r .refreshToken)
curl -s -w '\n%{http_code}\n' -X POST $AUTH/api/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$RT\"}"
curl -s -w '\n%{http_code}\n' -X POST $AUTH/api/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$RT\"}"
```
```bash
# 6. logout -> 204; refresh with the same refreshToken afterwards -> 401; the access token is blocklisted (jwt_blocklist:{jti}) until it would have expired
LOGIN=$(curl -s -X POST $AUTH/api/auth/login -H 'Content-Type: application/json' -d '{"email":"staff@novobank.example","password":"Staff-Pass-12345!"}')
AT=$(echo "$LOGIN" | jq -r .accessToken); RT=$(echo "$LOGIN" | jq -r .refreshToken)
curl -s -o /dev/null -w '%{http_code}\n' -X POST $AUTH/api/auth/logout -H "Authorization: Bearer $AT" -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$RT\"}"
curl -s -w '\n%{http_code}\n' -X POST $AUTH/api/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$RT\"}"
```
Also: `curl $AUTH/api/auth/.well-known/jwks.json` returns `{"keys":[{"kty":"RSA","kid":"v1","use":"sig","alg":"RS256","n":"...","e":"AQAB"}]}`.

## Differences from the written brief

- **Registration is not open to anonymous callers.** With `permit /api/auth/**` and a role in the body, anyone could create a STAFF or SYSTEM login (or a CUSTOMER login linked to someone else's `customerId`). Only a STAFF bearer can register users; the first STAFF is created once with `X-Bootstrap-Secret`.
- **The locking attempt returns 423** (the brief's flow said 401 for that attempt, its test steps said 423). Attempts 1-4 return 401.
- **Tables are in schema `auth_v2`** to avoid colliding with the older auth-service tables (see above).
- **Docker runtime on Render**, not a native Java runtime.
- **Key formats:** the private key may be PKCS#1 or PKCS#8; the public key for other services is base64 DER, because that is what their parser reads.
- **Extra token claims** (`roles`, `cid`) and a second blocklist key (`revoked:jti:{jti}`) exist for the shared security-starter. Tokens use issuer `banking-auth`; set `JWT_ISSUER=banking-auth` on the services that verify them.
- Security chain: only the endpoints listed above are public, not all of `/api/auth/**`; `/api/auth/admin/**` needs a STAFF JWT. The service validates bearer tokens itself (it does not depend on the shared starter).
- Extras beyond the listed exceptions: `ForbiddenException`, `UnauthorizedException`, `UserNotFoundException` (404), `RateLimitExceededException` (429).
