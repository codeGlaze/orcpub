#!/usr/bin/env bash
# Every browser suite, one after another, then one table.
#
#   ./scripts/e2e/run-all.sh [--prod] [--dev] [name-filter]
#
# --prod runs only the suites that work on a production bundle (the build the public site
# serves); --dev only those that read the app's internals. Production first, then development, so
# the bundle is rebuilt once between them. Each batch shares one server (scripts/e2e/server.sh);
# a suite that needs other server settings or a clean database gets its own. What each suite needs
# comes from `run.sh --describe`, the one reader of the suite headers. A suite with no E2E RESULT
# line counts as FAIL: a run that cannot say what it did has not passed.
set -uo pipefail
cd "$(dirname "$0")/../.."

WANT_PROD=1; WANT_DEV=1; FILTER=""
for a in "$@"; do
  case "$a" in --prod) WANT_DEV=0 ;; --dev) WANT_PROD=0 ;; *) FILTER="$a" ;; esac
done

LOGDIR="$(mktemp -d /tmp/e2e-all.XXXX)"
RESULTS="$LOGDIR/results"
: > "$RESULTS"
START=$(date +%s)

# "<bundle> <profiles> <own-server> <kind> <file>" for every suite in scope.
for f in scripts/e2e/*.js test/browser/*_e2e.js; do
  case "$f" in */lib.js|*/boot-check.js) continue ;; esac
  [ -n "$FILTER" ] && [[ "$f" != *"$FILTER"* ]] && continue
  echo "$(./scripts/e2e/run.sh --describe "$f") $f"
done > "$LOGDIR/plan"

run_one() {  # <file> <bundle> [shared]
  local f="$1" kind="$2" name; name="$(basename "$1")"
  echo "=== $name ($kind${3:+, shared server})"
  E2E_SHARED_SERVER="${3:-}" ./scripts/e2e/run.sh "$f" > "$LOGDIR/$name.log" 2>&1
  local line; line="$(grep '^E2E RESULT ' "$LOGDIR/$name.log" | tail -1)"
  [ -n "$line" ] || line="E2E RESULT $name FAIL checks=0 failed=0 bundle=$kind (no result line; see $LOGDIR/$name.log)"
  echo "$line" | tee -a "$RESULTS"
}

for kind in prod dev; do
  [ "$kind" = prod ] && [ "$WANT_PROD" = 0 ] && continue
  [ "$kind" = dev ] && [ "$WANT_DEV" = 0 ] && continue
  shared=$(awk -v k="$kind" '$1 == k && $3 == "no" {print $5}' "$LOGDIR/plan")
  if [ -n "$shared" ]; then
    # The server reads CSP_POLICY at start: off for development bundles, the real policy otherwise.
    if [ "$kind" = dev ]; then export CSP_POLICY=none; else unset CSP_POLICY; fi
    if ./scripts/e2e/server.sh start init-db > "$LOGDIR/server-$kind.log" 2>&1; then
      trap './scripts/e2e/server.sh stop' EXIT
      for f in $shared; do run_one "$f" "$kind" 1; done
      ./scripts/e2e/server.sh stop; trap - EXIT
    else
      cat "$LOGDIR/server-$kind.log"
      for f in $shared; do
        echo "E2E RESULT $(basename "$f") FAIL checks=0 failed=0 bundle=$kind (the shared server did not start)" | tee -a "$RESULTS"
      done
    fi
    unset CSP_POLICY
  fi
  for f in $(awk -v k="$kind" '$1 == k && $3 == "yes" {print $5}' "$LOGDIR/plan"); do run_one "$f" "$kind"; done
done

echo
printf '%-44s %-6s %-6s %7s %7s  %s\n' SUITE BUNDLE RESULT CHECKS FAILED NOTE
awk '{ name=$3; res=$4; sub("checks=","",$5); sub("failed=","",$6); sub("bundle=","",$7);
       note=""; for (i=8; i<=NF; i++) note=note" "$i;
       printf "%-44s %-6s %-6s %7s %7s %s\n", name, $7, res, $5, $6, note }' "$RESULTS"
echo
for kind in prod dev; do
  n=$(awk -v k="bundle=$kind" '$7 == k' "$RESULTS" | wc -l)
  [ "$n" -eq 0 ] && continue
  p=$(awk -v k="bundle=$kind" '$7 == k && $4 == "PASS"' "$RESULTS" | wc -l)
  r=$(awk -v k="bundle=$kind" '$7 == k && $4 == "PROBE"' "$RESULTS" | wc -l)
  f=$(awk -v k="bundle=$kind" '$7 == k && $4 == "FAIL"' "$RESULTS" | wc -l)
  label=$([ "$kind" = prod ] && echo "production (what the site serves)" || echo "development only (reads internals)")
  echo "$label: $p passed, $f failed, $r probes measured, of $n"
done
echo "took $(( ($(date +%s) - START) / 60 )) min; logs: $LOGDIR"
! grep -q ' FAIL ' "$RESULTS"
