#!/usr/bin/env bash
# Browser end-to-end checks against a real server and a real database.
#
#   ./scripts/e2e/run.sh
#
# Boots the app on an in-memory Datomic, seeds a verified user, drives
# Chromium through scripts/e2e/run.js, then tears the server down.
#
# The database is datomic:mem://, which only exists inside the JVM that
# created it -- that is why dev/e2e_boot.clj starts the server AND seeds the
# user in one process rather than shelling out to `lein run -m user`.
set -uo pipefail
cd "$(dirname "$0")/../.."

PORT="${E2E_PORT:-8890}"
LOG="${E2E_LOG:-/tmp/e2e-server.log}"

if ! node -e "require('playwright')" 2>/dev/null; then
  echo "playwright is not installed. Run:"
  echo "  (cd scripts/e2e && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install)"
  exit 1
fi

if [ ! -f resources/public/js/compiled/orcpub.js ]; then
  echo "Building the ClojureScript bundle (first run only)..."
  lein fig:prod || exit 1
fi

echo "Starting server on :${PORT}..."
DATOMIC_URL="datomic:mem://orcpub-e2e" \
ORCPUB_ENV=dev \
SIGNATURE="${SIGNATURE:-e2e-test-signature}" \
PORT="$PORT" \
  setsid lein with-profile init-db run -m e2e-boot > "$LOG" 2>&1 &
SERVER_PID=$!
# lein forks a JVM, and killing only the wrapper leaves that child holding the
# port -- the next run then fails to bind and silently tests the stale server.
# setsid puts both in their own process group so the whole group goes at once.
trap 'kill -- -$SERVER_PID 2>/dev/null' EXIT

for _ in $(seq 1 90); do
  curl -sf -o /dev/null "http://localhost:${PORT}/" && break
  sleep 2
done
if ! curl -sf -o /dev/null "http://localhost:${PORT}/"; then
  echo "Server never came up. Log:"; tail -30 "$LOG"; exit 1
fi
if grep -q BindException "$LOG" 2>/dev/null; then
  echo "Port ${PORT} was already in use, so this would have tested a stale server."
  exit 1
fi

E2E_BASE="http://localhost:${PORT}" node scripts/e2e/run.js
