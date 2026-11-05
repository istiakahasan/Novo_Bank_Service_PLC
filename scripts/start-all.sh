#!/usr/bin/env bash
# Builds and starts the whole local stack in the background, then waits until every service reports UP.
set -euo pipefail

cd "$(dirname "$0")/.."
TIMEOUT="${START_TIMEOUT:-300}"

./scripts/make-key.sh

docker compose up --build -d

declare -A SERVICES=(
  [auth]=3008 [customer]=3003 [ledger]=3004 [fraud]=3005 [notification]=3006 [support]=3007
)

echo "Waiting for /actuator/health = UP (every 2 s, up to ${TIMEOUT} s)..."
deadline=$((SECONDS + TIMEOUT))
for name in auth customer ledger fraud notification support; do
  port="${SERVICES[$name]}"
  until curl -fsS "http://localhost:${port}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "TIMEOUT: ${name} (port ${port}) is not UP. Last logs:" >&2
      docker compose logs --tail 40 "$name" >&2 || true
      exit 1
    fi
    sleep 2
  done
  echo "  UP  ${name} :${port}"
done

cat <<'URLS'

Stack is up.
  auth          http://localhost:3008        (POST /api/auth/login, GET /api/auth/.well-known/jwks.json)
  customer      http://localhost:3003
  ledger        http://localhost:3004        (same app as customer)
  fraud         http://localhost:3005
  notification  http://localhost:3006
  support       http://localhost:3007
  mailhog UI    http://localhost:8025
  wiremock      http://localhost:8080/__admin   (requests: /__admin/requests)
  postgres      localhost:5432  db=banking user=banking password=localdev

Seed users (password TestPass123!): alice@test.com, bob@test.com (CUSTOMER), staff@test.com (STAFF)
Next: ./scripts/seed-data.sh        Logs: docker compose logs -f        Stop: ./scripts/stop-all.sh [-v]
URLS
