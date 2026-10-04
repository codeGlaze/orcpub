#!/usr/bin/env bash
# Every browser test, then one table.
#
#   ./scripts/e2e/run-all.sh [--prod] [--dev] [--probes] [--jobs N] [name-filter]
#
# --prod runs only the suites that work on a production bundle (the build the public site
# serves); --dev only those that read the app's internals. The batch whose bundle is already on
# disk goes first, so a run rebuilds at most once. Each batch shares one server (scripts/e2e/server.sh);
# a suite that needs other server settings or a clean database gets its own. What each suite needs
# comes from `run.sh --describe`, the one reader of the suite headers. A suite with no E2E RESULT
# line counts as FAIL: a run that cannot say what it did has not passed.
#
# Probes (`Kind: probe`) measure and print; they pass or fail nothing about the app, and were 8 of
# the 21 minutes a full run took on 2026-10-03. They run only with --probes, one at a time once a
# batch's parallel tests are done, because a probe sharing the machine with other suites measures
# the other suites. They are not held until every batch ends: that would boot each server twice.
#
# Per batch: the bundle builds (run.sh --build-only) while the shared server boots. Then tests on
# the shared server run --jobs at a time (default 3: the machine this was sized on has 7GB, and each
# job is a Chromium beside the server's JVM), all with E2E_SKIP_BUILD so none rebuilds; suites that
# need their own server run one by one after the batch. Each suite writes its
# PDFs to its own folder, since run.sh inspects every new PDF in that folder.
set -uo pipefail
cd "$(dirname "$0")/../.."

WANT_PROD=1; WANT_DEV=1; WANT_PROBES=0; JOBS=3; FILTER=""
while [ $# -gt 0 ]; do
  case "$1" in
    --prod) WANT_DEV=0 ;; --dev) WANT_PROD=0 ;; --probes) WANT_PROBES=1 ;;
    --jobs) JOBS="${2:?--jobs needs a number}"; shift ;;
    *) FILTER="$1" ;;
  esac
  shift
done
# 0 or a negative count would leave the scheduler waiting for a job slot no job will ever free.
[[ "$JOBS" =~ ^[1-9][0-9]*$ ]] || { echo "run-all.sh: --jobs needs a whole number of 1 or more, got '$JOBS'"; exit 2; }

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
SKIPPED_PROBES=$(awk '$4 == "probe"' "$LOGDIR/plan" | wc -l)
[ "$WANT_PROBES" = 1 ] && SKIPPED_PROBES=0

pick() {  # <bundle> <own-server yes|no> <kind test|probe>
  awk -v k="$1" -v o="$2" -v t="$3" '$1 == k && $3 == o && $4 == t {print $5}' "$LOGDIR/plan"
}

run_one() {  # <file> <bundle> [shared] [skip-build]
  local f="$1" kind="$2" name; name="$(basename "$1")"
  echo "=== $name ($kind${3:+, shared server})"
  mkdir -p "$LOGDIR/out-$name"
  E2E_SHARED_SERVER="${3:-}" E2E_SKIP_BUILD="${4:-}" E2E_OUT="$LOGDIR/out-$name" \
    ./scripts/e2e/run.sh "$f" > "$LOGDIR/$name.log" 2>&1
  local line; line="$(grep '^E2E RESULT ' "$LOGDIR/$name.log" | tail -1)"
  [ -n "$line" ] || line="E2E RESULT $name FAIL checks=0 failed=0 bundle=$kind (no result line; see $LOGDIR/$name.log)"
  echo "$line" | tee -a "$RESULTS"
}

run_parallel() {  # <bundle> <file>...   --jobs at a time, counting only these jobs
  local kind="$1"; shift; local pids=() live p
  for f in "$@"; do
    while :; do
      live=(); for p in "${pids[@]}"; do kill -0 "$p" 2>/dev/null && live+=("$p"); done
      pids=("${live[@]}"); [ ${#pids[@]} -lt "$JOBS" ] && break
      wait -n "${pids[@]}"
    done
    run_one "$f" "$kind" 1 1 & pids+=($!)
  done
  [ ${#pids[@]} -gt 0 ] && wait "${pids[@]}"
}

fail_all() {  # <bundle> <reason> <file>...
  local kind="$1" why="$2"; shift 2
  for f in "$@"; do
    echo "E2E RESULT $(basename "$f") FAIL checks=0 failed=0 bundle=$kind ($why)" | tee -a "$RESULTS"
  done
}

# Peak memory: parallel tests are bounded by it, and a run that gets close slows or fails for
# reasons that are not the app.
( while :; do free -m | awk '/^Mem:/ {print $3, $2}'; sleep 2; done ) > "$LOGDIR/memory" &
MEMPID=$!
SHARED_UP=0
# One exit path for an interrupted run: the sampler and a shared server both go.
cleanup() { kill "$MEMPID" 2>/dev/null; [ "$SHARED_UP" = 1 ] && ./scripts/e2e/server.sh stop; }
trap cleanup EXIT

# Production and development bundles share one output folder, so switching costs a rebuild (about
# 1.5 min). Start with the kind already on disk: one rebuild per run instead of two.
ORDER="prod dev"
grep -q CLOSURE_UNCOMPILED_DEFINES resources/public/js/compiled/orcpub.js 2>/dev/null && ORDER="dev prod"

for kind in $ORDER; do
  [ "$kind" = prod ] && [ "$WANT_PROD" = 0 ] && continue
  [ "$kind" = dev ] && [ "$WANT_DEV" = 0 ] && continue
  tests=($(pick "$kind" no test)); own=($(pick "$kind" yes test))
  probes=(); own_probes=()
  [ "$WANT_PROBES" = 1 ] && { probes=($(pick "$kind" no probe)); own_probes=($(pick "$kind" yes probe)); }
  all=("${tests[@]}" "${own[@]}" "${probes[@]}" "${own_probes[@]}")
  [ ${#all[@]} -eq 0 ] && continue

  # The server reads CSP_POLICY at start: off for development bundles, the real policy otherwise.
  if [ "$kind" = dev ]; then export CSP_POLICY=none; else unset CSP_POLICY; fi
  # The bundle builds while the shared server boots; neither needs the other (about 1 min saved).
  # Empty E2E_SKIP_BUILD: this is the one build of the batch, so a caller's setting must not turn
  # it into a run on stale client code.
  E2E_SKIP_BUILD= ./scripts/e2e/run.sh --build-only "${all[0]}" > "$LOGDIR/build-$kind.log" 2>&1 &
  BUILDPID=$!
  if [ $(( ${#tests[@]} + ${#probes[@]} )) -gt 0 ]; then
    if ./scripts/e2e/server.sh start init-db > "$LOGDIR/server-$kind.log" 2>&1; then
      SHARED_UP=1
    else
      cat "$LOGDIR/server-$kind.log"
    fi
  fi
  if ! wait "$BUILDPID"; then
    tail -20 "$LOGDIR/build-$kind.log"
    fail_all "$kind" "the $kind bundle did not build; see $LOGDIR/build-$kind.log" "${all[@]}"
    [ "$SHARED_UP" = 1 ] && { ./scripts/e2e/server.sh stop; SHARED_UP=0; }
    unset CSP_POLICY; continue
  fi

  if [ "$SHARED_UP" = 1 ]; then
    run_parallel "$kind" "${tests[@]}"
  else
    fail_all "$kind" "the shared server did not start" "${tests[@]}" "${probes[@]}"; probes=()
  fi
  # Probes measure, so they run only once nothing else is running.
  for f in "${probes[@]}"; do run_one "$f" "$kind" 1 1; done
  [ "$SHARED_UP" = 1 ] && { ./scripts/e2e/server.sh stop; SHARED_UP=0; }
  # Own-server suites (busy-export) run after, on the same port: the app's PDF form posts to :8890
  # whenever the page is on localhost (views.cljs download-form), so a second port cannot work.
  for f in "${own[@]}" "${own_probes[@]}"; do run_one "$f" "$kind" "" 1; done
  unset CSP_POLICY
done
kill "$MEMPID" 2>/dev/null

echo
printf '%-44s %-6s %-6s %7s %7s  %s\n' SUITE BUNDLE RESULT CHECKS FAILED NOTE
sort -k7,7r -k3,3 "$RESULTS" |
awk '{ name=$3; res=$4; sub("checks=","",$5); sub("failed=","",$6); sub("bundle=","",$7);
       note=""; for (i=8; i<=NF; i++) note=note" "$i;
       printf "%-44s %-6s %-6s %7s %7s %s\n", name, $7, res, $5, $6, note }'
echo
for kind in prod dev; do
  n=$(awk -v k="bundle=$kind" '$7 == k' "$RESULTS" | wc -l)
  [ "$n" -eq 0 ] && continue
  p=$(awk -v k="bundle=$kind" '$7 == k && $4 == "PASS"' "$RESULTS" | wc -l)
  r=$(awk -v k="bundle=$kind" '$7 == k && $4 == "PROBE"' "$RESULTS" | wc -l)
  f=$(awk -v k="bundle=$kind" '$7 == k && $4 == "FAIL"' "$RESULTS" | wc -l)
  label=$([ "$kind" = prod ] && echo "production (what the site serves)" || echo "development only (reads internals)")
  echo "$label: $p passed, $f failed${r:+, $r probes measured}, of $n" | sed 's/, 0 probes measured//'
done
[ "$SKIPPED_PROBES" -gt 0 ] && echo "$SKIPPED_PROBES probes not run (they measure, not test; add --probes)"
awk 'NR == 1 || $1 > m {m = $1; t = $2} END {if (m) printf "peak memory in use: %d of %d MB\n", m, t}' "$LOGDIR/memory"
echo "took $(( ($(date +%s) - START) / 60 )) min $(( ($(date +%s) - START) % 60 )) s; logs: $LOGDIR"
! grep -q ' FAIL ' "$RESULTS"
