#!/usr/bin/env bash
# End-to-end API scenario (docs/RUNBOOK.md Part B). Requires curl and jq.
#   MAIN=https://novo-bank.onrender.com FRAUD=... NOTIF=... scripts/e2e.sh
# Optional: PSQL_URL=postgresql://... (needs psql) to also assert DB state.
set -uo pipefail

MAIN="${MAIN:?set MAIN}"
AUTH="${AUTH:?set AUTH (auth-service URL)}"
STAFF_USER="${STAFF_USER:-staff}"
STAFF_PASSWORD="${STAFF_PASSWORD:?set STAFF_PASSWORD}"
TOKEN=""
FRAUD="${FRAUD:-}"
NOTIF="${NOTIF:-}"
ALICE_CUST="${ALICE_CUST:-11111111-1111-4111-8111-111111111111}"
ALICE_ACC="${ALICE_ACC:-aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa}"
BOB_ACC="${BOB_ACC:-bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb}"
RUN="$(date +%s)"
MOBILE="01$(printf '%09d' $((RANDOM * RANDOM % 1000000000)))"

pass=0
fail=0
CODE=""
BODY=""

check() { # name expected actual
  if [ "$2" = "$3" ]; then
    echo "PASS  $1"
    pass=$((pass + 1))
  else
    echo "FAIL  $1 (expected '$2', got '$3')"
    fail=$((fail + 1))
  fi
}

req() { # method url [json]; sends the STAFF token to the main app only
  local resp auth=()
  case "$2" in "$MAIN"*) [ -n "$TOKEN" ] && auth=(-H "Authorization: Bearer $TOKEN");; esac
  if [ -n "${3:-}" ]; then
    resp=$(curl -s --max-time 90 -w $'\n%{http_code}' -X "$1" "$2" "${auth[@]}" -H 'Content-Type: application/json' -d "$3")
  else
    resp=$(curl -s --max-time 90 -w $'\n%{http_code}' -X "$1" "$2" "${auth[@]}")
  fi
  CODE="${resp##*$'\n'}"
  BODY="${resp%$'\n'*}"
}

sql() { psql "$PSQL_URL" -tAc "$1" 2>/dev/null | tr -d ' '; }

echo "== warm up =="
for u in "$MAIN" "$FRAUD" "$NOTIF"; do
  [ -n "$u" ] && curl -s --max-time 90 -o /dev/null "$u/actuator/health"
done

echo "== health =="
req GET "$MAIN/actuator/health"; check "main health" "UP" "$(echo "$BODY" | jq -r .status)"
if [ -n "$FRAUD" ]; then req GET "$FRAUD/actuator/health"; check "fraud health" "UP" "$(echo "$BODY" | jq -r .status)"; fi
if [ -n "$NOTIF" ]; then req GET "$NOTIF/actuator/health"; check "notification health" "UP" "$(echo "$BODY" | jq -r .status)"; fi

echo "== authentication =="
req POST "$MAIN/api/transfers" '{}'; check "anonymous call is 401" "401" "$CODE"
LOGIN=$(curl -s --max-time 90 -X POST "$AUTH/auth/login" -H 'Content-Type: application/json' -d "{\"username\":\"$STAFF_USER\",\"password\":\"$STAFF_PASSWORD\"}")
TOKEN=$(echo "$LOGIN" | jq -r .accessToken)
check "staff login returns a token" "true" "$([ -n "$TOKEN" ] && [ "$TOKEN" != null ] && echo true || echo false)"

echo "== unsigned webhooks are rejected =="
if [ -n "$FRAUD" ]; then
  req POST "$FRAUD/webhooks/transfer-completed" '{}'; check "fraud rejects unsigned" "401" "$CODE"
fi
if [ -n "$NOTIF" ]; then
  req POST "$NOTIF/webhooks/customer-created" '{}'; check "notification rejects unsigned" "401" "$CODE"
fi

echo "== customer =="
CUSTOMER_JSON="{\"firstName\":\"Rahim\",\"lastName\":\"Uddin\",\"dateOfBirth\":\"1992-04-10\",\"mobileNo\":\"$MOBILE\",\"email\":\"rahim$RUN@example.com\",\"idType\":\"nid\",\"idNumber\":\"NID$RUN\",\"addressLine1\":\"House 1, Road 2, Dhaka\"}"
req POST "$MAIN/api/customers" "$CUSTOMER_JSON"
check "create customer 201" "201" "$CODE"
CUSTOMER_ID=$(echo "$BODY" | jq -r .id)
check "kyc starts pending" "pending" "$(echo "$BODY" | jq -r .kycStatus)"
check "idNumber not leaked" "null" "$(echo "$BODY" | jq -r .idNumber)"

req POST "$MAIN/api/customers" "{\"firstName\":\"Dup\",\"lastName\":\"Licate\",\"dateOfBirth\":\"1990-01-01\",\"mobileNo\":\"$MOBILE\",\"idType\":\"nid\",\"idNumber\":\"OTHER$RUN\",\"addressLine1\":\"x\"}"
check "duplicate mobile 409" "409" "$CODE"

echo "== kyc gate =="
req POST "$MAIN/api/accounts" "{\"customerId\":\"$CUSTOMER_ID\",\"accountType\":\"savings\",\"initialDepositCents\":50000}"
check "account before KYC 422" "422" "$CODE"

req PATCH "$MAIN/api/customers/$CUSTOMER_ID/kyc" '{"kycStatus":"verified","verifiedBy":"officer-1"}'
check "kyc verify 200" "200" "$CODE"
check "kyc is verified" "verified" "$(echo "$BODY" | jq -r .kycStatus)"
req PATCH "$MAIN/api/customers/$CUSTOMER_ID/kyc" '{"kycStatus":"rejected","verifiedBy":"officer-1"}'
check "kyc cannot change twice (422)" "422" "$CODE"

echo "== account =="
req POST "$MAIN/api/accounts" "{\"customerId\":\"$CUSTOMER_ID\",\"accountType\":\"savings\",\"initialDepositCents\":50000}"
check "open account 201" "201" "$CODE"
ACCOUNT_ID=$(echo "$BODY" | jq -r .id)
check "opening balance" "50000" "$(echo "$BODY" | jq -r .balanceCents)"
req GET "$MAIN/api/accounts/$ACCOUNT_ID/balance"
check "balance endpoint" "50000" "$(echo "$BODY" | jq -r .balanceCents)"
req GET "$MAIN/api/accounts/$ACCOUNT_ID/entries?limit=5"
check "opening ledger credit" "credit" "$(echo "$BODY" | jq -r '.[0].direction')"

echo "== transfers =="
KEY1="rahim-$RUN-1"
T1="{\"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$ALICE_ACC\",\"amountCents\":10000,\"idempotencyKey\":\"$KEY1\"}"
req POST "$MAIN/api/transfers" "$T1"
check "transfer \$100 201" "201" "$CODE"
TXN1=$(echo "$BODY" | jq -r .transactionId)
check "transfer completed" "completed" "$(echo "$BODY" | jq -r .status)"
req GET "$MAIN/api/accounts/$ACCOUNT_ID/balance"; check "Rahim balance 40000" "40000" "$(echo "$BODY" | jq -r .balanceCents)"

req POST "$MAIN/api/transfers" "$T1"
check "duplicate key 200 (replay)" "200" "$CODE"
check "duplicate returns same txn id" "$TXN1" "$(echo "$BODY" | jq -r .transactionId)"
req GET "$MAIN/api/accounts/$ACCOUNT_ID/balance"; check "no double debit" "40000" "$(echo "$BODY" | jq -r .balanceCents)"

req GET "$MAIN/api/transfers/$TXN1"; check "get transfer by id" "$TXN1" "$(echo "$BODY" | jq -r .transactionId)"

req POST "$MAIN/api/accounts" "{\"customerId\":\"$ALICE_CUST\",\"accountType\":\"checking\",\"initialDepositCents\":1000000}"
check "Alice second account 201" "201" "$CODE"
ALICE_ACC2=$(echo "$BODY" | jq -r .id)

req POST "$MAIN/api/transfers" "{\"fromAccount\":\"$ALICE_ACC2\",\"toAccount\":\"$BOB_ACC\",\"amountCents\":60000,\"idempotencyKey\":\"alice-$RUN-600\"}"
check "transfer \$600 201" "201" "$CODE"

req POST "$MAIN/api/transfers" "{\"fromAccount\":\"$ALICE_ACC2\",\"toAccount\":\"$BOB_ACC\",\"amountCents\":600000,\"idempotencyKey\":\"alice-$RUN-6000\"}"
check "transfer \$6000 201 (fraud is async)" "201" "$CODE"
TXN6K=$(echo "$BODY" | jq -r .transactionId)

req POST "$MAIN/api/transfers" "{\"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$BOB_ACC\",\"amountCents\":99999900,\"idempotencyKey\":\"rahim-$RUN-big\"}"
check "insufficient funds 422" "422" "$CODE"
check "insufficient funds message" "Insufficient funds" "$(echo "$BODY" | jq -r .message)"

req POST "$MAIN/api/transfers" "{\"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$ACCOUNT_ID\",\"amountCents\":1,\"idempotencyKey\":\"rahim-$RUN-same\"}"
check "same account 400" "400" "$CODE"

req POST "$MAIN/api/transfers" '{"amountCents":-5}'
check "invalid body 400" "400" "$CODE"

req GET "$MAIN/api/accounts/$ALICE_ACC2/balance"; check "Alice acc2 balance 340000" "340000" "$(echo "$BODY" | jq -r .balanceCents)"
req GET "$MAIN/api/accounts/$BOB_ACC/balance"; echo "INFO  Bob balance now $(echo "$BODY" | jq -r .balanceCents)"

if [ -n "${PSQL_URL:-}" ] && command -v psql >/dev/null 2>&1; then
  echo "== database checks (waiting 20 s for async events) =="
  sleep 20
  check "ledger entries for txn1" "2" "$(sql "select count(*) from ledger_entries where transaction_id='$TXN1'")"
  check "one row per idempotency key" "1" "$(sql "select count(*) from transactions where idempotency_key='$KEY1'")"
  check "big transfer not stored" "0" "$(sql "select count(*) from transactions where idempotency_key='rahim-$RUN-big'")"
  check "kyc_status in DB" "verified" "$(sql "select kyc_status from customers where id='$CUSTOMER_ID'")"
  check "HIGH_VALUE alert stored" "1" "$(sql "select count(*) from fraud_alerts where transaction_id='$TXN6K' and rule_fired='HIGH_VALUE'")"
  check "outbox drained" "0" "$(sql "select count(*) from outbox where published=false and created_at < now() - interval '1 minute'")"
else
  echo "INFO  PSQL_URL not set (or psql missing): skipping database checks"
fi

echo
echo "RESULT: $pass passed, $fail failed"
echo "Now grep the Render logs for: FRAUD CHECK txn=$TXN1 | FRAUD ALERT rule=HIGH_VALUE txn=$TXN6K | EMAIL sent to rahim$RUN@example.com"
[ "$fail" -eq 0 ]
