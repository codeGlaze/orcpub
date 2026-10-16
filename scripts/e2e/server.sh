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
# run-all.sh's lock is fd 9; the JVM below outlives this script and must not keep it held.
exec 9>&-

PORT="${E2E_PORT:-8890}"
LOG="${E2E_LOG:-/tmp/e2e-server-${PORT}.log}"
PIDFILE="/tmp/e2e-server-${PORT}.pid"
BASE="http://localhost:${PORT}"

stop() {
  [ -f "$PIDFILE" ] || return 0
  local pgid; pgid="$(cat "$PIDFILE")"; rm -f "$PIDFILE"
  # A pidfile left by a killed run can name a group id the system has since reused; signal it only
  # while it still holds this server.
  pgrep -g "$pgid" -f e2e-boot > /dev/null || return 0
  # lein forks a JVM, and killing only the wrapper leaves that child holding the port -- the next
  # run then binds nothing and silently tests the stale server. The whole process group goes.
  kill -- -"$pgid" 2>/dev/null
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
    # Mail goes to scripts/e2e/lib/mail-sink.js, which auth-flows.js runs on E2E_MAIL_PORT: without
    # a mail server registration fails outright, and the verify and reset keys exist only in mail.
    EMAIL_SERVER_URL=127.0.0.1 \
    EMAIL_SERVER_PORT="${E2E_MAIL_PORT:-2525}" \
    EMAIL_FROM_ADDRESS="${EMAIL_FROM_ADDRESS:-e2e@orcpub.invalid}" \
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
    # It answers before e2e-boot has finished seeding the users and characters.
    for _ in $(seq 1 60); do grep -q E2E-READY "$LOG" && break; sleep 1; done
    grep -q E2E-READY "$LOG" || { stop; echo "server.sh: the server never finished seeding (no E2E-READY in $LOG)"; exit 1; }
    if grep -q BindException "$LOG" 2>/dev/null; then
      stop; echo "server.sh: port ${PORT} was already in use, so this would have tested a stale server"; exit 1
    fi
    ;;
  stop) stop ;;
  *) echo "usage: scripts/e2e/server.sh start [profiles] | stop"; exit 2 ;;
esac
