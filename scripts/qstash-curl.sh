#!/usr/bin/env bash
# Simulates QStash calling a webhook, including a valid Upstash-Signature JWT.
# Usage:  QSTASH_CURRENT_SIGNING_KEY=sig_xxx scripts/qstash-curl.sh <url> '<json body>'
#         add --print as a 3rd arg to only print the signature header.
set -euo pipefail

URL="${1:?url required}"
BODY="${2:?json body required}"
KEY="${QSTASH_CURRENT_SIGNING_KEY:?set QSTASH_CURRENT_SIGNING_KEY}"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

HEADER=$(printf '{"alg":"HS256","typ":"JWT"}' | b64url)
BODY_HASH=$(printf '%s' "$BODY" | openssl dgst -sha256 -binary | b64url)
NOW=$(date +%s)
EXP=$((NOW + 300))
CLAIMS=$(printf '{"iss":"Upstash","sub":"%s","exp":%d,"nbf":%d,"iat":%d,"jti":"local-%s","body":"%s"}' \
  "$URL" "$EXP" "$NOW" "$NOW" "$NOW" "$BODY_HASH" | b64url)
SIG=$(printf '%s' "$HEADER.$CLAIMS" | openssl dgst -sha256 -hmac "$KEY" -binary | b64url)
TOKEN="$HEADER.$CLAIMS.$SIG"

if [ "${3:-}" = "--print" ]; then
  echo "$TOKEN"
  exit 0
fi

curl -i -X POST "$URL" -H "Content-Type: application/json" -H "Upstash-Signature: $TOKEN" --data-raw "$BODY"
