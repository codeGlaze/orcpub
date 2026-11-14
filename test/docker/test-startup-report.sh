#!/usr/bin/env bash
# Tests scripts/startup-report.sh with a fake docker: ./run shows the app's latest startup report,
# or why it would not start, and gives up with directions when the app says nothing.
# Runs without Docker. Usage: bash test/docker/test-startup-report.sh
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
TESTS=0
FAILURES=0
info() { echo "INFO $*"; }
warn() { echo "WARN $*"; }
# shellcheck source=../../scripts/startup-report.sh
. "$PROJECT_ROOT/scripts/startup-report.sh"
sleep() { :; }

# A fake docker printing $WORK/log for any logs command.
mkdir -p "$WORK/bin"
printf '#!/usr/bin/env bash\ncat "%s/log"\n' "$WORK" > "$WORK/bin/docker"
chmod +x "$WORK/bin/docker"
PATH="$WORK/bin:$PATH"

# check NAME OUTPUT CONDITION: CONDITION is evaluated with $OUT set to OUTPUT.
check() {
  TESTS=$((TESTS + 1))
  OUT="$2"
  if eval "$3"; then echo "PASS  $1"; else echo "FAIL  $1"; echo "$OUT" | sed 's/^/      /'; FAILURES=$((FAILURES + 1)); fi
}

RULE="------------------------------"
cat > "$WORK/log" <<EOF
datomic: connecting
$RULE
  orcpub started
$RULE
  OLD REPORT
  This report is saved in /var/log/orcpub/startup-report.txt on the host
$RULE
restart
$RULE
  orcpub started
$RULE
  SETTING   VALUE
  NEW REPORT
  This report is saved in /var/log/orcpub/startup-report.txt on the host
$RULE
a request line after the report
EOF
OUT=$(show_startup_report compose 2026-10-24T00:00:00Z 2)
check "it shows the latest report, from its top rule to its bottom rule" "$OUT" \
  '[[ "$OUT" == *"NEW REPORT"* && "$OUT" != *"OLD REPORT"* && "$OUT" == *"This report is saved in"* ]]'
check "and nothing logged after it" "$OUT" '[[ "$OUT" != *"a request line"* && "$(tail -1 <<<"$OUT")" == "$RULE" ]]'

cat > "$WORK/log" <<EOF
orcpub NOT STARTED: no mail server is set (EMAIL_SERVER_URL). Without one nobody can sign
up or reset a password, and admin alerts reach nobody.
  Fix: set EMAIL_SERVER_URL (with EMAIL_ACCESS_KEY, EMAIL_SECRET_KEY, EMAIL_FROM_ADDRESS).
  Or, to run without email on purpose (sign-up stays off): EMAIL_REQUIRED=false.
EOF
OUT=$(show_startup_report swarm 2026-10-24T00:00:00Z 2)
check "a refusal is shown with its fix" "$OUT" '[[ "$OUT" == *"NOT STARTED"* && "$OUT" == *"EMAIL_REQUIRED=false"* ]]'

echo "datomic: still connecting" > "$WORK/log"
OUT=$(show_startup_report compose 2026-10-24T00:00:00Z 2; echo "status=$?")
check "no report in time: it says where to read it, and returns 1" "$OUT" \
  '[[ "$OUT" == *"docker compose logs orcpub"* && "$OUT" == *"status=1"* ]]'

echo ""
echo "=== Results: ${TESTS} tests, ${FAILURES} failures ==="
[ "$FAILURES" -eq 0 ]
