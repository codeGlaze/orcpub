#!/usr/bin/env bash
# Everything that has to be green before a commit, in one command with one
# exit code.
#
#   ./scripts/check.sh          lint + JVM tests + ClojureScript tests
#   ./scripts/check.sh lint     just the linter
#   ./scripts/check.sh clj      just the JVM tests
#   ./scripts/check.sh cljs     just the ClojureScript tests
#
# Runs every stage even when an earlier one fails, so one run tells you
# everything that is wrong rather than only the first thing.
set -uo pipefail
cd "$(dirname "$0")/.."

FAILED=()

run_stage() {
  local name="$1"; shift
  echo
  echo "───── ${name} ─────"
  if "$@"; then
    echo "✓ ${name}"
  else
    echo "✗ ${name}"
    FAILED+=("${name}")
  fi
}

stage_lint() { lein lint; }
stage_clj()  { lein test; }
stage_cljs() { ./scripts/test-cljs.sh; }

case "${1:-all}" in
  lint) run_stage "lint"          stage_lint ;;
  clj)  run_stage "clj tests"     stage_clj  ;;
  cljs) run_stage "cljs tests"    stage_cljs ;;
  all)
    run_stage "lint"       stage_lint
    run_stage "clj tests"  stage_clj
    run_stage "cljs tests" stage_cljs
    ;;
  *)
    echo "usage: $0 [all|lint|clj|cljs]" >&2
    exit 2
    ;;
esac

echo
if [ ${#FAILED[@]} -eq 0 ]; then
  echo "All checks passed."
  exit 0
fi
echo "FAILED: ${FAILED[*]}"
exit 1
