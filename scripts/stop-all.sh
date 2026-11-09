#!/usr/bin/env bash
# Stops the local stack. Add -v to also delete the database volume (full reset: init-db.sql runs again on the next start).
set -euo pipefail

cd "$(dirname "$0")/.."

case "${1:-}" in
  -v|--volumes)
    docker compose down -v
    echo "Stopped and removed volumes (database wiped)."
    ;;
  "")
    docker compose down
    echo "Stopped. Data is kept in the pgdata volume (use -v to wipe it)."
    ;;
  *)
    echo "usage: $0 [-v|--volumes]" >&2
    exit 2
    ;;
esac
