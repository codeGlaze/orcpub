#!/usr/bin/env bash
# ensure_fresh_build (scripts/bundle-check.sh): a server start builds what is out of date and nothing
# else. Runs in a throwaway folder with lein replaced by a stub that records its calls.
set -u
root="$(cd "$(dirname "$0")/../.." && pwd)"
t="$(mktemp -d)"; trap 'rm -rf "$t"' EXIT
mkdir -p "$t/bin" "$t/r/resources/public/js/compiled" "$t/r/resources/public/css/compiled" \
  "$t/r/web/cljs" "$t/r/src/cljc" "$t/r/src/cljs" "$t/r/src/clj/orcpub/styles"
printf '#!/bin/sh\necho "$*" >>"%s/calls"\n[ -z "$LEIN_FAILS" ]\n' "$t" >"$t/bin/lein"; chmod +x "$t/bin/lein"
cd "$t/r" || exit 1
cp "$root/dev.cljs.edn" "$root/prod.cljs.edn" .
export PATH="$t/bin:$PATH" LOG_DIR="$t/r/logs" FIGWHEEL_PORT=3449 DEV_MODE=true LEIN_FAILS=""
log_info() { echo "$*"; }; log_warn() { echo "WARN $*"; }; log_error() { echo "ERROR $*"; }
HELD=""; port_in_use() { [ -n "$HELD" ]; }; find_pids_by_port() { echo "$HELD"; }; pid_alive() { [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null; }
source "$root/scripts/bundle-check.sh"

pass=0; bad=0
fresh() { # a current dev bundle and stylesheet, newer than every source
  find web src dev.cljs.edn prod.cljs.edn -type f -exec touch -d '2 hours ago' {} +
  echo 'CLOSURE_UNCOMPILED_DEFINES' >"$BUNDLE"; echo x >"$SHEET"; : >"$t/calls"
  touch -d '1 hour ago' "$BUNDLE" "$SHEET"
}
expect() { # name, wanted calls ('' for none), wanted exit
  local got; ensure_fresh_build >"$t/out" 2>&1; local rc=$?; got="$(tr '\n' ';' <"$t/calls")"
  if [ "$got" = "$2" ] && [ "$rc" = "$3" ]; then pass=$((pass+1)); else bad=$((bad+1)); echo "FAIL $1: calls '$got' exit $rc"; fi
}

fresh; expect "nothing changed builds nothing" "" 0
fresh; touch web/cljs/a.cljs; expect "a changed source builds the client" "fig:build;" 0
fresh; touch dev.cljs.edn; expect "a changed build config builds the client" "fig:build;" 0
fresh; rm "$BUNDLE"; expect "no bundle builds the client" "fig:build;" 0
fresh; echo compiled >"$BUNDLE"; touch -d '1 hour ago' "$BUNDLE"; expect "a prod bundle under dev mode builds the client" "fig:build;" 0
fresh; touch src/clj/orcpub/styles/core.clj; expect "a changed style builds the stylesheet" "garden once;" 0
fresh; rm "$SHEET"; expect "no stylesheet builds it" "garden once;" 0
fresh; echo compiled >"$BUNDLE"; touch -d '1 hour ago' "$BUNDLE"; touch web/cljs/a.cljs; DEV_MODE=false; expect "production mode builds prod" "fig:prod;" 0; DEV_MODE=true
fresh; touch web/cljs/a.cljs; LEIN_FAILS=1; expect "a failed build stops the start" "fig:build;" 1; LEIN_FAILS=""
(cd / && exec sleep 300) & own=$!
fresh; touch web/cljs/a.cljs; (cd "$t/r" && exec sleep 300) & here=$!; HELD=$here; expect "Figwheel running here means no second lein" "" 0
kill $here 2>/dev/null
fresh; touch web/cljs/a.cljs; HELD=$own; expect "Figwheel from another folder still builds" "fig:build;" 0
grep -q 'held from' "$t/out" && pass=$((pass+1)) || { bad=$((bad+1)); echo "FAIL the other folder is not named"; }
HELD=""; kill $own 2>/dev/null

echo "start build: $pass passed, $bad failed"
[ "$bad" -eq 0 ]
