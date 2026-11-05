#!/usr/bin/env bash
# Runs the 10 authorization attack scenarios (docs/AUTHORIZATION.md, Deliverable 6). Requires curl, jq, openssl.
#
#   AUTH=https://auth-service.onrender.com MAIN=https://novo-bank.onrender.com FRAUD=https://fraud-service.onrender.com \
#   STAFF_PASSWORD=... DEMO_PASSWORD=... LEDGER_KEY=<ledger service key from SYSTEM_KEYS> \
#   PRIVATE_PEM=keys/private.pem scripts/attack-tests.sh
#
# Needs the demo users (AUTH_BOOTSTRAP_DEMO=true): staff, alice (customer 1111..), bob (customer 2222..).
# PRIVATE_PEM is only used to forge an *expired but correctly signed* token (scenario 6); skipped if unset.
set -uo pipefail

AUTH="${AUTH:?set AUTH}"; MAIN="${MAIN:?set MAIN}"; FRAUD="${FRAUD:?set FRAUD}"
STAFF_PASSWORD="${STAFF_PASSWORD:?}"; DEMO_PASSWORD="${DEMO_PASSWORD:?}"; LEDGER_KEY="${LEDGER_KEY:?}"
ALICE_ACC="${ALICE_ACC:-aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa}"
BOB_ACC="${BOB_ACC:-bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb}"
RUN="$(date +%s)"
pass=0; fail=0

expect() { # name expected_code actual_code
  if [ "$2" = "$3" ]; then echo "PASS  $1 -> $3"; pass=$((pass+1)); else echo "FAIL  $1 (expected $2, got $3)"; fail=$((fail+1)); fi
}
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 90 "$@"; }
login() { curl -s --max-time 90 -X POST "$AUTH/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" | jq -r .accessToken; }
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
b64url_dec() { local s="$1"; s="${s//-/+}"; s="${s//_//}"; case $((${#s} % 4)) in 2) s="$s==";; 3) s="$s=";; esac; printf '%s' "$s" | base64 -d 2>/dev/null; }

STAFF=$(login staff "$STAFF_PASSWORD"); ALICE=$(login alice "$DEMO_PASSWORD")
[ -n "$STAFF" ] && [ "$STAFF" != null ] && [ -n "$ALICE" ] && [ "$ALICE" != null ] || { echo "login failed: check AUTH / passwords / demo users"; exit 2; }

JSON='Content-Type: application/json'
TRANSFER="{\"fromAccount\":\"$ALICE_ACC\",\"toAccount\":\"$BOB_ACC\",\"amountCents\":100,\"idempotencyKey\":\"atk-$RUN\"}"

# 1 anonymous transfer -> 401
expect "1  anonymous POST /api/transfers" 401 "$(code -X POST "$MAIN/api/transfers" -H "$JSON" -d "$TRANSFER")"

# 2 CUSTOMER creates a customer -> 403
expect "2  CUSTOMER POST /api/customers" 403 "$(code -X POST "$MAIN/api/customers" -H "Authorization: Bearer $ALICE" -H "$JSON" -d '{}')"

# 3 CUSTOMER A reads CUSTOMER B's balance -> 403
expect "3  alice GET bob's balance" 403 "$(code "$MAIN/api/accounts/$BOB_ACC/balance" -H "Authorization: Bearer $ALICE")"
expect "3b alice GET own balance" 200 "$(code "$MAIN/api/accounts/$ALICE_ACC/balance" -H "Authorization: Bearer $ALICE")"

# 4 CUSTOMER patches KYC -> 403   /   5 STAFF patches KYC -> 200 (fresh pending customer)
MOBILE="01$(printf '%09d' $((RANDOM * RANDOM % 1000000000)))"
NEW=$(curl -s --max-time 90 -X POST "$MAIN/api/customers" -H "Authorization: Bearer $STAFF" -H "$JSON" -d "{\"firstName\":\"Atk\",\"lastName\":\"Test\",\"dateOfBirth\":\"1990-01-01\",\"mobileNo\":\"$MOBILE\",\"idType\":\"nid\",\"idNumber\":\"ATK$RUN\",\"addressLine1\":\"x\"}" | jq -r .id)
KYC='{"kycStatus":"verified","verifiedBy":"officer-1"}'
expect "4  CUSTOMER PATCH /kyc" 403 "$(code -X PATCH "$MAIN/api/customers/$NEW/kyc" -H "Authorization: Bearer $ALICE" -H "$JSON" -d "$KYC")"
expect "5  STAFF PATCH /kyc" 200 "$(code -X PATCH "$MAIN/api/customers/$NEW/kyc" -H "Authorization: Bearer $STAFF" -H "$JSON" -d "$KYC")"

# 6 expired (correctly signed) JWT -> 401
if [ -n "${PRIVATE_PEM:-}" ] && [ -f "$PRIVATE_PEM" ]; then
  H=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
  NOW=$(date +%s)
  P=$(printf '{"iss":"novo-auth","sub":"expired-test","roles":["STAFF"],"iat":%d,"exp":%d,"jti":"expired-%s"}' $((NOW-7200)) $((NOW-3600)) "$RUN" | b64url)
  S=$(printf '%s' "$H.$P" | openssl dgst -sha256 -sign "$PRIVATE_PEM" | b64url)
  expect "6  expired JWT" 401 "$(code "$MAIN/api/accounts/$ALICE_ACC/balance" -H "Authorization: Bearer $H.$P.$S")"
else
  echo "SKIP  6  expired JWT (set PRIVATE_PEM=keys/private.pem)"
fi

# 7 tampered role claim -> 401
IFS=. read -r TH TP TS <<< "$ALICE"
FORGED=$(b64url_dec "$TP" | jq -c '.roles=["STAFF"]' | b64url)
expect "7  tampered role claim" 401 "$(code -X POST "$MAIN/api/customers" -H "Authorization: Bearer $TH.$FORGED.$TS" -H "$JSON" -d '{}')"

# 8 SYSTEM key on /by-account -> 200   /   9 CUSTOMER token on someone else's /by-account -> 403
expect "8  SYSTEM key GET /by-account" 200 "$(code "$MAIN/api/customers/by-account/$ALICE_ACC" -H 'X-Service-Id: ledger' -H "X-Service-Key: $LEDGER_KEY")"
expect "9  CUSTOMER token GET bob /by-account" 403 "$(code "$MAIN/api/customers/by-account/$BOB_ACC" -H "Authorization: Bearer $ALICE")"
expect "9b wrong service key" 401 "$(code "$MAIN/api/customers/by-account/$ALICE_ACC" -H 'X-Service-Id: ledger' -H 'X-Service-Key: wrong')"

# 10 QStash webhook with invalid / missing Upstash-Signature -> 401
expect "10 webhook invalid signature" 401 "$(code -X POST "$FRAUD/webhooks/transfer-completed" -H "$JSON" -H 'Upstash-Signature: aaa.bbb.ccc' -d '{"event":"transfer.completed"}')"
expect "10b webhook missing signature" 401 "$(code -X POST "$FRAUD/webhooks/transfer-completed" -H "$JSON" -d '{"event":"transfer.completed"}')"

echo; echo "RESULT: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
