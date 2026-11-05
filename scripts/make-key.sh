#!/usr/bin/env bash
# Generates a local RSA-2048 key pair and writes it into .env.local. Safe to run again: it does nothing if both keys exist.
#   JWT_PRIVATE_KEY = base64 of the PEM private key   (the auth service accepts PKCS#1 or PKCS#8)
#   JWT_PUBLIC_KEY  = base64 of the DER public key     (the form the other services parse)
set -euo pipefail

cd "$(dirname "$0")/.."
ENV_FILE=".env.local"

[ -f "$ENV_FILE" ] || cp .env.local.example "$ENV_FILE"

if grep -Eq '^JWT_PRIVATE_KEY=.+' "$ENV_FILE" && grep -Eq '^JWT_PUBLIC_KEY=.+' "$ENV_FILE"; then
  echo "JWT keys already present in $ENV_FILE: nothing to do"
  exit 0
fi

command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

openssl genrsa -out "$tmp/private.pem" 2048 2>/dev/null
PRIVATE_B64="$(base64 < "$tmp/private.pem" | tr -d '\n')"
PUBLIC_B64="$(openssl rsa -in "$tmp/private.pem" -pubout -outform DER 2>/dev/null | base64 | tr -d '\n')"

# replace "NAME=..." in place, or append it when the line is missing
set_var() {
  local name="$1" value="$2"
  if grep -q "^${name}=" "$ENV_FILE"; then
    awk -v n="$name" -v v="$value" -F= 'BEGIN{OFS="="} $1==n {print n "=" v; next} {print}' "$ENV_FILE" > "$tmp/env"
    cat "$tmp/env" > "$ENV_FILE"
  else
    printf '%s=%s\n' "$name" "$value" >> "$ENV_FILE"
  fi
}

set_var JWT_PRIVATE_KEY "$PRIVATE_B64"
set_var JWT_PUBLIC_KEY "$PUBLIC_B64"
echo "Wrote a new local JWT key pair to $ENV_FILE (never commit this file)"
