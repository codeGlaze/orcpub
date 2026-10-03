#!/usr/bin/env bash
# Every browser suite, one after another, then one table.
#
#   ./scripts/e2e/run-all.sh [--prod] [--dev] [name-filter]
#
# --prod runs only the suites that work on a production bundle (the build the public site
# serves); --dev only those that read the app's internals. Default: production first, then
# development, so the bundle is rebuilt once between them rather than per suite. A suite with no
# E2E RESULT line counts as FAIL: a run that cannot say what it did has not passed.
set -uo pipefail
cd "$(dirname "$0")/../.."

WANT_PROD=1; WANT_DEV=1; FILTER=""
for a in "$@"; do
  case "$a" in --prod) WANT_DEV=0 ;; --dev) WANT_PROD=0 ;; *) FILTER="$a" ;; esac
done

suites() {
  for f in scripts/e2e/*.js test/browser/*_e2e.js; do
    case "$f" in */lib.js|*/boot-check.js) continue ;; esac
    [ -n "$FILTER" ] && [[ "$f" != *"$FILTER"* ]] && continue
    if grep -qiE '^//.*Needs:.*dev bundle' "$f"; then echo "dev $f"; else echo "prod $f"; fi
  done
}

LOGDIR="$(mktemp -d /tmp/e2e-all.XXXX)"
RESULTS="$LOGDIR/results"
: > "$RESULTS"
for kind in prod dev; do
  [ "$kind" = prod ] && [ "$WANT_PROD" = 0 ] && continue
  [ "$kind" = dev ] && [ "$WANT_DEV" = 0 ] && continue
  for f in $(suites | awk -v k="$kind" '$1 == k {print $2}'); do
    name="$(basename "$f")"
    echo "=== $name ($kind)"
    ./scripts/e2e/run.sh "$f" > "$LOGDIR/$name.log" 2>&1
    line="$(grep '^E2E RESULT ' "$LOGDIR/$name.log" | tail -1)"
    [ -n "$line" ] || line="E2E RESULT $name FAIL checks=0 failed=0 bundle=$kind (no result line; see $LOGDIR/$name.log)"
    echo "$line" | tee -a "$RESULTS"
  done
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
echo "logs: $LOGDIR"
! grep -q ' FAIL ' "$RESULTS"
