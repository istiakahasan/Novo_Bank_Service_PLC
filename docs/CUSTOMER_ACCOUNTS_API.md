# Customer Service: list, accounts and /me endpoints

Added to the Customer Service (the Customer + Ledger app on port 3003). Frontend `/kyc-queue`, the dashboard and the customer portal use them.

| Method | Path | Roles | Result |
|---|---|---|---|
| GET | `/api/customers?kycStatus=&status=&riskTier=&page=0&size=20` | STAFF | `{content:[CustomerResponse], page, size, totalElements, totalPages}` |
| GET | `/api/customers/{id}/accounts` | CUSTOMER (own id only, else 403), STAFF | `[{accountId, accountNumber, accountType, balanceCents, status, createdAt}]` |
| GET | `/api/customers/me` | CUSTOMER | the caller's own `CustomerResponse`, id from the token's `customerId`/`cid` claim; 403 if the token has none |
| GET | `/api/accounts?customerId={id}` (Ledger) | SYSTEM only | the same account list; used by the Customer Service |

- **Filters** are case-insensitive: `kycStatus` PENDING / VERIFIED / REJECTED / FLAGGED, `status` active / dormant / suspended / closed, `riskTier` LOW / MEDIUM / HIGH (matched against `aml_risk_rating`). Any other value is a 400. `size` defaults to 20 and is capped at 100; results are newest first.
- **Implementation:** `CustomerRepository` now extends `JpaSpecificationExecutor<Customer>`. A JPQL `(:p IS NULL OR c.x = :p)` query is not used because PostgreSQL cannot infer the type of a null parameter in that pattern.
- **Accounts aggregation:** `LedgerClient` calls the Ledger's `GET /api/accounts?customerId=` with the SYSTEM service key (5 s timeouts) and caches the answer in Redis under `customer_accounts:{customerId}` for 60 s. A timeout, connection error or any non-2xx answer becomes `ServiceUnavailableException` -> HTTP 503 and nothing is cached. The call is made outside a database transaction so a slow Ledger never holds a connection. A Redis problem never fails the request.
- **Same-app mode:** Customer and Ledger currently run as one app, so with `LEDGER_SERVICE_URL` empty the accounts are read in-process (same cache, no HTTP hop). Set `LEDGER_SERVICE_URL` when the Ledger is a separate service.

## Configuration

```yaml
ledger:
  service:
    url: ${LEDGER_SERVICE_URL:}            # empty = same app, in-process
system:
  service-id: ${CUSTOMER_SERVICE_ID:customer-service}
  token:
    secret: ${SYSTEM_TOKEN_SECRET:}        # X-Service-Key sent to the Ledger
```
On the Ledger side add `customer-service=<SYSTEM_TOKEN_SECRET>` to `SYSTEM_KEYS` (generate with `openssl rand -hex 32`). The security rules for the new paths are in `ServiceSecurityConfig` (`GET /api/customers` STAFF, `/api/customers/me` CUSTOMER, `/api/customers/*/accounts` CUSTOMER or STAFF, `GET /api/accounts` SYSTEM).

## How to test

```bash
export CUSTOMER=https://novo-bank.onrender.com     # Customer + Ledger app
export STAFF=<staff access token>   ALICE=<alice access token>   OTHER_ID=<another customer's id>
export ALICE_ID=11111111-1111-4111-8111-111111111111 LEDGER_KEY=<SYSTEM key of a caller listed in SYSTEM_KEYS>
```

```bash
# 1. STAFF lists pending KYC -> 200 {"content":[...],"page":0,"size":20,"totalElements":N,"totalPages":M}
curl -s -w '\n%{http_code}\n' "$CUSTOMER/api/customers?kycStatus=PENDING&page=0&size=20" -H "Authorization: Bearer $STAFF"
```
```bash
# 2. CUSTOMER gets own accounts -> 200 [{"accountId":"...","accountNumber":"ACC-...","accountType":"savings","balanceCents":50000,"status":"active","createdAt":"..."}]
curl -s -w '\n%{http_code}\n' "$CUSTOMER/api/customers/$ALICE_ID/accounts" -H "Authorization: Bearer $ALICE"
```
```bash
# 3. CUSTOMER asks for another customer's accounts -> 403 {"status":403,"message":"Access denied",...}
curl -s -w '\n%{http_code}\n' "$CUSTOMER/api/customers/$OTHER_ID/accounts" -H "Authorization: Bearer $ALICE"
```
```bash
# 4. Ledger lookup as SYSTEM -> 200 (same list); with a user token it is 403
curl -s -w '\n%{http_code}\n' "$CUSTOMER/api/accounts?customerId=$ALICE_ID" -H "X-Service-Id: customer-service" -H "X-Service-Key: $LEDGER_KEY"
```
Also: `curl "$CUSTOMER/api/customers/me" -H "Authorization: Bearer $ALICE"` returns Alice's profile (200); with a STAFF token it is 403.
