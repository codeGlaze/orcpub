#!/usr/bin/env bash
# Browser end-to-end checks against a real server and a real database.
#
#   ./scripts/e2e/run.sh --describe <suite>  prints: <bundle> <profiles> <own-server yes|no> <kind>
#   ./scripts/e2e/run.sh [suite] [args...]   a file in scripts/e2e/ (default run.js), or a path
#                                            such as test/browser/boot_rescue_e2e.js; any args
#                                            are passed to the suite
#
# Builds the bundle the suite needs, boots the app on an in-memory Datomic with a seeded user,
# proves the app starts in a browser, runs the suite, and judges what it printed. Every way a
# run can test nothing (no browser, a stale or wrong-kind bundle, an app that never mounts, a
# suite that reports no checks) stops the run and says so. Ends with one line for run-all.sh:
#   E2E RESULT <suite> <PASS|FAIL|PROBE> checks=<n> failed=<n> bundle=<prod|dev> [reason]
#
# A suite's header can declare:
#   Needs: dev bundle   it reads the app's internals, which a production bundle compiles away.
#                       Otherwise it runs on production, the build the public site serves.
#   Needs: busy server  it needs the export queue small enough to fill (profile busy-export).
#   Kind: probe         it measures and prints, and is judged by its exit code only.
#   Needs: pack argument   it imports the homebrew pack named by its first argument; with none,
#                       test/fixtures/test-pak.orcbrew is used and the result says so.
#   Needs: fresh server  run-all.sh gives it a server of its own instead of the shared one.
#   Overlays: NOT suppressed   it tests an overlay itself; otherwise the cookie banner and the
#                       What's New panel are stamped as seen before any page loads.
#
# A suite is killed after E2E_SUITE_TIMEOUT seconds (default 1200) and reported as timed out.
#
# The database is datomic:mem://, which only exists inside the JVM that created it -- that is
# why dev/e2e_boot.clj starts the server AND seeds the user in one process.
set -uo pipefail
cd "$(dirname "$0")/../.."

PORT="${E2E_PORT:-8890}"
LOG="${E2E_LOG:-/tmp/e2e-server.log}"
BASE="http://localhost:${PORT}"
BUNDLE=resources/public/js/compiled/orcpub.js
SHEET=resources/public/css/compiled/styles.css

DESCRIBE=""
[ "${1:-}" = --describe ] && { DESCRIBE=1; shift; }
SUITE_ARG="${1:-run.js}"
[ $# -gt 0 ] && shift
SUITE_ARGS=("$@")
case "$SUITE_ARG" in */*) SUITE="$SUITE_ARG" ;; *) SUITE="scripts/e2e/$SUITE_ARG" ;; esac
NAME="$(basename "$SUITE")"
NEED=prod; grep -qiE '^//.*Needs:.*dev bundle' "$SUITE" 2>/dev/null && NEED=dev
KIND=test; grep -qiE '^//.*Kind:.*probe' "$SUITE" 2>/dev/null && KIND=probe
SUPPRESS=1; grep -qiE '^//.*Overlays:.*NOT suppressed' "$SUITE" 2>/dev/null && SUPPRESS=0
PROFILES=init-db; grep -qiE '^//.*Needs:.*busy server' "$SUITE" 2>/dev/null && PROFILES=init-db,busy-export
OWN_SERVER=no
grep -qiE '^//.*Needs:.*fresh server' "$SUITE" 2>/dev/null && OWN_SERVER=yes
[ "$PROFILES" != init-db ] && OWN_SERVER=yes

# `run.sh --describe <suite>` prints what the suite needs, so run-all.sh reads these headers through
# this one parser rather than a second copy of it.
if [ "${DESCRIBE:-}" = 1 ]; then
  echo "$NEED $PROFILES $OWN_SERVER $KIND"; exit 0
fi

NOTE=""
if grep -qiE '^//.*Needs:.*pack argument' "$SUITE" 2>/dev/null && [ ${#SUITE_ARGS[@]} -eq 0 ]; then
  SUITE_ARGS=("$(pwd)/test/fixtures/test-pak.orcbrew")
  NOTE="(fixture pack)"; echo "$NAME takes a pack; none given, so using test/fixtures/test-pak.orcbrew."
fi

result() { echo "E2E RESULT $NAME $1 checks=${2:-0} failed=${3:-0} bundle=$NEED${4:+ $4}"; }
fail() { echo; echo "E2E RUN STOPPED: $*"; result FAIL 0 0 "(stopped: $*)"; exit 1; }

[ -f "$SUITE" ] || fail "no such suite: $SUITE"

# Playwright is installed under scripts/e2e; a suite elsewhere (test/browser) resolves it from there.
export NODE_PATH="$(pwd)/scripts/e2e/node_modules${NODE_PATH:+:$NODE_PATH}"
node -e "require('playwright')" 2>/dev/null \
  || fail "playwright is not installed: (cd scripts/e2e && PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install)"

# --- one browser for every suite ------------------------------------------------------------
# Suites read four different variables, each falling back to a sandbox path that exists only in
# the cloud. Found once here and exported under every name, so no suite falls back silently.
find_browser() {
  if [ -n "${E2E_CHROMIUM:-}" ]; then
    [ -x "$E2E_CHROMIUM" ] && echo "$E2E_CHROMIUM"; return
  fi
  local p
  p=$(node -e "try{const c=require('playwright').chromium.executablePath();if(require('fs').existsSync(c))process.stdout.write(c)}catch(e){}")
  [ -n "$p" ] && { echo "$p"; return; }
  for d in ${PLAYWRIGHT_BROWSERS_PATH:-} "$HOME/.cache/ms-playwright" /opt/pw-browsers; do
    [ -d "$d" ] || continue
    p=$(ls -d "$d"/chromium-*/chrome-linux*/chrome 2>/dev/null | grep -v headless | sort -V | tail -1)
    [ -n "$p" ] && [ -x "$p" ] && { echo "$p"; return; }
    [ -x "$d/chromium" ] && { echo "$d/chromium"; return; }
  done
}
BROWSER="$(find_browser)"
[ -n "$BROWSER" ] || fail "no Chromium found (E2E_CHROMIUM${E2E_CHROMIUM:+=$E2E_CHROMIUM is not executable}, playwright's own, ~/.cache/ms-playwright, /opt/pw-browsers)"
export E2E_CHROMIUM="$BROWSER" CHROME="$BROWSER" CHROME_PATH="$BROWSER" PLAYWRIGHT_CHROMIUM="$BROWSER"

# --- the bundle the suite needs, fresh ------------------------------------------------------
# The server compiles from source at every boot; the bundle is an artifact nothing rebuilds. So a
# fresh server can serve old client code, and every signal says it is new.
bundle_kind() {
  [ -f "$BUNDLE" ] || { echo none; return; }
  grep -q CLOSURE_UNCOMPILED_DEFINES "$BUNDLE" 2>/dev/null && echo dev || echo prod
}
# The folders the bundle compiles from, read from its own build config, never kept here: a list
# kept here once missed web/cljs, and startup edits were tested against an old bundle unwarned.
cljs_dirs() {
  local cfg=prod.cljs.edn; [ "$1" = dev ] && cfg=dev.cljs.edn
  sed -n 's/.*:watch-dirs *\[\([^]]*\)\].*/\1/p' "$cfg" | tr -d '"'
}
newer_sources() { local a="$1"; shift; find "$@" -name '*.clj*' -newer "$a" 2>/dev/null | head -5; }
# The build config and the externs it names are inputs too: an externs change alters the
# production bundle without touching a source file.
newer_build_inputs() {
  local cfg=prod.cljs.edn; [ "$1" = dev ] && cfg=dev.cljs.edn
  for f in "$cfg" $(sed -n 's/.*:externs *\[\([^]]*\)\].*/\1/p' "$cfg" | tr -d '"'); do
    [ -f "$f" ] && [ "$f" -nt "$BUNDLE" ] && echo "$f"
  done
}
build_bundle() {
  echo "  Building the $NEED bundle..."
  if [ "$NEED" = dev ]; then lein fig:build; else lein fig:prod; fi || fail "the $NEED bundle did not build"
}

HAVE="$(bundle_kind)"
DIRS="$(cljs_dirs "$NEED")"
[ -n "$DIRS" ] || fail "could not read :watch-dirs from the $NEED build config, so cannot tell whether the bundle is stale"
if [ "$HAVE" = none ]; then
  echo "No bundle on disk."; build_bundle
elif [ "$HAVE" != "$NEED" ]; then
  [ -n "${E2E_SKIP_BUILD:-}" ] && fail "$NAME needs a $NEED bundle and a $HAVE bundle is on disk; E2E_SKIP_BUILD forbids the rebuild"
  echo "$NAME needs a $NEED bundle; a $HAVE bundle is on disk."; build_bundle
elif [ -n "$(newer_sources "$BUNDLE" $DIRS; newer_build_inputs "$NEED")" ]; then
  echo "ClojureScript changed since the bundle was built (sources: $DIRS):"
  { newer_sources "$BUNDLE" $DIRS; newer_build_inputs "$NEED"; } | sed 's/^/    /'
  if [ -n "${E2E_SKIP_BUILD:-}" ]; then
    echo "  E2E_SKIP_BUILD is set, so this run tests the OLD client code."
  else
    build_bundle
  fi
fi
[ "$(bundle_kind)" = "$NEED" ] || fail "expected a $NEED bundle after building, found $(bundle_kind)"

# styles.css is generated by garden and untracked; without it every page 404s its stylesheet.
if [ ! -f "$SHEET" ] || [ -n "$(newer_sources "$SHEET" src/clj/orcpub/styles)" ]; then
  echo "Compiling the stylesheet..."; lein garden once || fail "the stylesheet did not compile"
fi

# A development bundle loads its code as separate scripts, which the strict Content Security
# Policy blocks, so it is off for dev-bundle suites only. Production runs keep the real policy.
if [ "$NEED" = dev ]; then
  export CSP_POLICY="${CSP_POLICY:-none}"
  echo "Development bundle, so the Content Security Policy is off for this run."
fi

# --- the server ---------------------------------------------------------------------------
# run-all.sh starts one server per batch and sets E2E_SHARED_SERVER; otherwise this run has its own.
if [ -n "${E2E_SHARED_SERVER:-}" ]; then
  curl -sf -o /dev/null "$BASE/" || fail "E2E_SHARED_SERVER is set but nothing answers on :$PORT"
  echo "Using the shared server on :${PORT}."
else
  ./scripts/e2e/server.sh start "$PROFILES" || fail "the server did not start (reason above)"
  trap './scripts/e2e/server.sh stop' EXIT
fi

# --- the app must start before anything is judged ------------------------------------------
node scripts/e2e/boot-check.js "$BASE" || fail "the app did not start in a browser (reason above)"

# --- run the suite and judge what it printed ----------------------------------------------
MARK="$(mktemp)"
OUTPUT="$(mktemp)"
# Overlays: the cookie banner and the What's New panel cover the page and swallow clicks, so every
# suite's browser contexts stamp them as seen (test/browser/lib/suppress-overlays-preload.js),
# unless the suite declares it tests them. The release id is read from the app's own list, so a
# new release cannot silently bring the panel back over every suite.
RELEASE="$(sed -n 's/.*{:id "\([^"]*\)".*/\1/p' src/cljc/orcpub/whats_new.cljc | head -1)"
[ -n "$RELEASE" ] || fail "could not read the newest What's New id from src/cljc/orcpub/whats_new.cljc"
PRELOAD="$(pwd)/test/browser/lib/suppress-overlays-preload.js"
if [ "$SUPPRESS" = 1 ]; then
  SUITE_NODE_OPTIONS="--require $PRELOAD"
else
  SUITE_NODE_OPTIONS=""; echo "Overlays left on: $NAME tests them."
fi

# A suite that hangs (an awaited click on a hidden element, a browser never closed on an error)
# would hold the run forever. Past the limit it is killed and reported, never waited on.
LIMIT="${E2E_SUITE_TIMEOUT:-1200}"
E2E_BASE="$BASE" PROBE_SUPPRESS="$SUPPRESS" PROBE_WHATS_NEW_RELEASE="$RELEASE" \
  NODE_OPTIONS="${NODE_OPTIONS:+$NODE_OPTIONS }$SUITE_NODE_OPTIONS" \
  timeout --kill-after=10 "$LIMIT" node "$SUITE" "${SUITE_ARGS[@]}" 2>&1 | tee "$OUTPUT"
NODE_RC=${PIPESTATUS[0]}
if [ "$NODE_RC" -eq 124 ] || [ "$NODE_RC" -eq 137 ]; then
  rm -f "$MARK" "$OUTPUT"
  echo; result FAIL 0 0 "(timed out after ${LIMIT}s and was killed; E2E_SUITE_TIMEOUT raises the limit)"; exit 1
fi
# A check may carry a timing prefix ("[+1.2s] PASS ..."); a probe or a crash handler prints FAILED.
PASSED=$(grep -cE '^\s*(\[[^]]*\]\s*)?(PASS|ok)\b' "$OUTPUT")
FAILED=$(grep -cE '^\s*(\[[^]]*\]\s*)?(FAIL|not ok)\b' "$OUTPUT")
SAID_FAILED=$(grep -cE '\bFAILED\b' "$OUTPUT")

# The browser cannot read PDF field names, so exported PDFs are inspected here with PDFBox. Only
# those this run wrote: a leftover from an earlier run is not evidence about this one.
OUT="${E2E_OUT:-/tmp/e2e-pdf}"
if [ "$NODE_RC" -eq 0 ]; then
  mapfile -t PDFS < <(find "$OUT" -maxdepth 1 -name '*.pdf' -newer "$MARK" 2>/dev/null)
  for pdf in "${PDFS[@]}"; do
    MIN_PAGES=""
    [ -f "${pdf%.pdf}.min-pages" ] && MIN_PAGES=$(cat "${pdf%.pdf}.min-pages")
    echo; echo "Inspecting $(basename "$pdf")..."
    if ! lein with-profile init-db run -m clojure.main dev/inspect_export.clj \
           "$pdf" $MIN_PAGES < /dev/null 2>&1 | grep -Ev "JAVA_TOOL|^WARNING|WARN "; then
      NODE_RC=1
    fi
  done
fi
rm -f "$MARK" "$OUTPUT"

echo
if [ "$KIND" = probe ]; then
  [ "$SAID_FAILED" -gt 0 ] && { result FAIL "$PASSED" "$FAILED" "(probe printed FAILED, so a measurement did not complete)"; exit 1; }
  [ "$NODE_RC" -eq 0 ] && { result PROBE "$PASSED" "$FAILED" "(measured, not judged)${NOTE:+ $NOTE}"; exit 0; }
  result FAIL "$PASSED" "$FAILED" "(probe exited $NODE_RC)"; exit 1
fi
[ "$NODE_RC" -ne 0 ] && { result FAIL "$PASSED" "$FAILED" "(exited $NODE_RC)"; exit 1; }
[ "$FAILED" -gt 0 ] && { result FAIL "$PASSED" "$FAILED" "(a check failed though it exited 0)"; exit 1; }
[ "$SAID_FAILED" -gt 0 ] && { result FAIL "$PASSED" "$FAILED" "(printed FAILED though it exited 0)"; exit 1; }
[ "$PASSED" -eq 0 ] && { result FAIL 0 0 "(reported no checks: it ran nothing, or prints no PASS/ok lines)"; exit 1; }
result PASS "$PASSED" 0 "$NOTE"
exit 0
