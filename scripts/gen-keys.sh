#!/usr/bin/env bash
# Generates the RS256 key pair for the auth server and random service-to-service keys.
# Usage: scripts/gen-keys.sh [output-dir]      (default: ./keys, git-ignored)
set -euo pipefail

OUT="${1:-keys}"
mkdir -p "$OUT"

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$OUT/private.pem" 2>/dev/null
openssl pkcs8 -topk8 -nocrypt -in "$OUT/private.pem" -outform DER | openssl base64 -A > "$OUT/private.b64"
openssl rsa -in "$OUT/private.pem" -pubout -outform DER 2>/dev/null | openssl base64 -A > "$OUT/public.b64"

LEDGER_KEY=$(openssl rand -hex 32)
FRAUD_KEY=$(openssl rand -hex 32)
NOTIF_KEY=$(openssl rand -hex 32)

cat <<EOF
Files written to $OUT/ (never commit them).

== auth-service (Render env) ==
JWT_PRIVATE_KEY=$(cat "$OUT/private.b64")

== main app / any service that accepts user tokens (Render env) ==
JWT_PUBLIC_KEY=$(cat "$OUT/public.b64")

== main app: inbound service keys (Customer API) ==
SYSTEM_KEYS=ledger=$LEDGER_KEY;fraud-service=$FRAUD_KEY;notification-service=$NOTIF_KEY

== fraud-service ==        SERVICE_ID=fraud-service         SERVICE_KEY=$FRAUD_KEY
== notification-service == SERVICE_ID=notification-service  SERVICE_KEY=$NOTIF_KEY
== main app, ONLY if Customer and Ledger are deployed separately ==
SERVICE_ID=ledger   SERVICE_KEY=$LEDGER_KEY
EOF
