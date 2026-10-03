#!/usr/bin/env bash
# The e2e server: the app on an in-memory Datomic with a seeded verified user.
#
#   scripts/e2e/server.sh start [profiles]   default init-db; returns once the app answers
#   scripts/e2e/server.sh stop
#
# run.sh starts and stops one per suite; run-all.sh starts one per batch and shares it. The caller's
# environment reaches the server, so CSP_POLICY set for a development bundle applies.
#
# The database is datomic:mem://, which only exists inside the JVM that created it -- that is why
# dev/e2e_boot.clj starts the server AND seeds the user in one process.
set -uo pipefail
cd "$(dirname "$0")/../.."

PORT="${E2E_PORT:-8890}"
LOG="${E2E_LOG:-/tmp/e2e-server.log}"
PIDFILE="/tmp/e2e-server-${PORT}.pid"
BASE="http://localhost:${PORT}"

stop() {
  [ -f "$PIDFILE" ] || return 0
  # lein forks a JVM, and killing only the wrapper leaves that child holding the port -- the next
  # run then binds nothing and silently tests the stale server. The whole process group goes.
  kill -- -"$(cat "$PIDFILE")" 2>/dev/null
  rm -f "$PIDFILE"
  for _ in $(seq 1 20); do curl -sf -o /dev/null "$BASE/" || return 0; sleep 1; done
  echo "server.sh: the server on :${PORT} did not stop"; return 1
}

case "${1:-}" in
  start)
    PROFILES="${2:-init-db}"
    if curl -sf -o /dev/null "$BASE/"; then
      echo "server.sh: something already answers on :${PORT}; a suite would test it instead of this code"
      exit 1
    fi
    echo "Starting server on :${PORT} (profiles: $PROFILES)..."
    DATOMIC_URL="datomic:mem://orcpub-e2e" \
    ORCPUB_ENV=dev \
    SIGNATURE="${SIGNATURE:-e2e-test-signature}" \
    PORT="$PORT" \
      setsid lein with-profile "$PROFILES" run -m e2e-boot > "$LOG" 2>&1 < /dev/null &
    echo $! > "$PIDFILE"
    for _ in $(seq 1 90); do
      curl -sf -o /dev/null "$BASE/" && break
      sleep 2
    done
    if ! curl -sf -o /dev/null "$BASE/"; then
      tail -30 "$LOG"; stop; echo "server.sh: the server never came up (log above)"; exit 1
    fi
    if grep -q BindException "$LOG" 2>/dev/null; then
      stop; echo "server.sh: port ${PORT} was already in use, so this would have tested a stale server"; exit 1
    fi
    ;;
  stop) stop ;;
  *) echo "usage: scripts/e2e/server.sh start [profiles] | stop"; exit 2 ;;
esac
