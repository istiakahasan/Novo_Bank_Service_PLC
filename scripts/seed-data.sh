#!/usr/bin/env bash
# Plays a complete scenario against the local stack and asserts the result of every step. Needs curl and jq.
#   staff logs in -> creates + KYC-verifies a customer -> registers that customer's login -> customer logs in
#   -> opens an account -> sends $100 to Alice -> raises a ticket -> staff moves it forward -> email arrives in Mailhog
# Re-runnable: every run creates a new customer.
set -uo pipefail

AUTH="${AUTH_URL:-http://localhost:3008}"
CUSTOMER="${CUSTOMER_URL:-http://localhost:3003}"
SUPPORT="${SUPPORT_URL:-http://localhost:3007}"
MAILHOG="${MAILHOG_URL:-http://localhost:8025}"
PASSWORD="TestPass123!"
ALICE_ACCOUNT="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
RUN="$(date +%s)$RANDOM"
MOBILE="01$(printf '%09d' $(( (RANDOM * 32768 + RANDOM) % 1000000000 )))"
EMAIL="demo${RUN}@test.com"

command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 2; }

fail=0
CODE=""; BODY=""

# call METHOD URL [TOKEN] [JSON]  -> sets CODE and BODY
call() {
  local method="$1" url="$2" token="${3:-}" data="${4:-}" resp args=(-s --max-time 60 -w $'\n%{http_code}' -X "$method" "$url" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-d "$data")
  resp="$(curl "${args[@]}")"
  CODE="${resp##*$'\n'}"
  BODY="${resp%$'\n'*}"
}

expect() { # name expected_code
  if [ "$CODE" = "$2" ]; then echo "PASS  $1 -> $CODE"; else echo "FAIL  $1 -> $CODE (expected $2): $BODY"; fail=1; fi
}

echo "== health"
for entry in "auth:$AUTH" "customer:$CUSTOMER" "support:$SUPPORT"; do
  call GET "${entry#*:}/actuator/health"
  status="$(echo "$BODY" | jq -r .status 2>/dev/null)"
  if [ "$status" = "UP" ]; then echo "PASS  ${entry%%:*} /actuator/health -> UP"; else echo "FAIL  ${entry%%:*} /actuator/health -> $CODE $BODY"; fail=1; fi
done

echo "== 1. staff logs in"
call POST "$AUTH/api/auth/login" "" "{\"email\":\"staff@test.com\",\"password\":\"$PASSWORD\"}"
expect "staff login" 200
STAFF="$(echo "$BODY" | jq -r .accessToken)"
[ -n "$STAFF" ] && [ "$STAFF" != null ] || { echo "cannot continue without a staff token"; exit 1; }

echo "== 2. create the customer and verify KYC"
call POST "$CUSTOMER/api/customers" "$STAFF" "{\"firstName\":\"Demo\",\"lastName\":\"User$RUN\",\"dateOfBirth\":\"1995-05-05\",\"mobileNo\":\"$MOBILE\",\"email\":\"$EMAIL\",\"idType\":\"nid\",\"idNumber\":\"NID$RUN\",\"addressLine1\":\"1 Local Road, Dhaka\"}"
expect "customer created" 201
CUSTOMER_ID="$(echo "$BODY" | jq -r .id)"
call PATCH "$CUSTOMER/api/customers/$CUSTOMER_ID/kyc" "$STAFF" '{"kycStatus":"verified","verifiedBy":"staff@test.com"}'
expect "kyc verified" 200

echo "== 3. register the customer's login and sign in"
call POST "$AUTH/api/auth/register" "$STAFF" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\",\"customerId\":\"$CUSTOMER_ID\"}"
expect "customer login registered" 201
call POST "$AUTH/api/auth/login" "" "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}"
expect "customer login" 200
TOKEN="$(echo "$BODY" | jq -r .accessToken)"

echo "== 4. open an account with \$500"
call POST "$CUSTOMER/api/accounts" "$TOKEN" "{\"customerId\":\"$CUSTOMER_ID\",\"accountType\":\"savings\",\"initialDepositCents\":50000}"
expect "account opened" 201
ACCOUNT_ID="$(echo "$BODY" | jq -r .id)"

echo "== 5. transfer \$100 to Alice"
call POST "$CUSTOMER/api/transfers" "$TOKEN" "{\"fromAccount\":\"$ACCOUNT_ID\",\"toAccount\":\"$ALICE_ACCOUNT\",\"amountCents\":10000,\"idempotencyKey\":\"seed-$RUN\"}"
expect "transfer" 201
TXN="$(echo "$BODY" | jq -r .transactionId)"
call GET "$CUSTOMER/api/accounts/$ACCOUNT_ID/balance" "$TOKEN"
expect "balance" 200
if [ "$(echo "$BODY" | jq -r .balanceCents)" = "40000" ]; then echo "PASS  balance is 40000 cents after the transfer"; else echo "FAIL  balance: $BODY"; fail=1; fi

echo "== 6. raise a ticket and let staff move it forward"
call POST "$SUPPORT/api/tickets" "$TOKEN" "{\"category\":\"TRANSACTION_DISPUTE\",\"priority\":\"MEDIUM\",\"subject\":\"Question about transfer $TXN\",\"relatedTransactionId\":\"$TXN\"}"
expect "ticket created" 201
TICKET="$(echo "$BODY" | jq -r .ticketId)"
call PATCH "$SUPPORT/api/tickets/$TICKET" "$STAFF" '{"status":"IN_PROGRESS"}'
expect "ticket OPEN -> IN_PROGRESS" 200

echo "== 7. the welcome / transfer email reaches Mailhog (events: customer -> WireMock -> notification -> SMTP)"
found=0
for _ in $(seq 1 30); do
  count="$(curl -fsS "$MAILHOG/api/v2/search?kind=to&query=$EMAIL" 2>/dev/null | jq -r '.total' 2>/dev/null || echo 0)"
  if [ "${count:-0}" -ge 1 ] 2>/dev/null; then found=1; break; fi
  sleep 2
done
if [ "$found" = 1 ]; then echo "PASS  email for $EMAIL is in Mailhog ($MAILHOG)"; else echo "FAIL  no email for $EMAIL after 60 s"; fail=1; fi

echo
if [ "$fail" = 0 ]; then echo "SEED SCENARIO OK (customer $CUSTOMER_ID, account $ACCOUNT_ID, ticket $TICKET)"; else echo "SEED SCENARIO FAILED"; fi
exit "$fail"
