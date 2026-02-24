#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# prod.sh - Build OrcPub Production Uberjar
# =============================================================================
# Builds the full production artifact: CLJS + CSS + AOT-compiled uberjar.
#
# Uses a three-step build to work around lein's compile subprocess hang
# (see docs/LEIN-UBERJAR-HANG.md):
#   1. CLJS via figwheel-main (exits cleanly)
#   2. AOT compile with timeout (hangs but .class files are written)
#   3. Package uberjar (no re-compile, no clean)
#
# Usage:
#   ./prod.sh              Build production uberjar
#   ./prod.sh --skip-cljs  Skip CLJS compilation (reuse existing)
#   ./prod.sh --help       Show this help
#
# Output:
#   target/orcpub.jar
#
# Exit Codes:
#   0 - Success
#   1 - Usage error
#   2 - Prerequisite failure
#   3 - Build failure
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source shared utilities
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# Build configuration
COMPILE_TIMEOUT="${COMPILE_TIMEOUT:-300}"   # 5 minutes for AOT compile
UBERJAR_TIMEOUT="${UBERJAR_TIMEOUT:-600}"   # 10 minutes for jar packaging
CLJS_OUTPUT="resources/public/js/compiled/orcpub.js"
AOT_MARKER="target/classes/orcpub/server__init.class"
JAR_OUTPUT="target/orcpub.jar"

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------

show_help() {
    cat << 'EOF'
OrcPub Production Build

Usage:
  ./prod.sh              Build production uberjar (CLJS + CSS + AOT + jar)
  ./prod.sh --skip-cljs  Skip CLJS compilation (reuse existing JS)
  ./prod.sh --help       Show this help

Steps:
  1. CLJS compilation via figwheel-main (prod.cljs.edn, :advanced optimizations)
  2. AOT compile (with timeout — lein compile hangs, but .class files are written)
  3. Uberjar packaging (Garden CSS + jar, no re-compile)

Output:
  target/orcpub.jar

Environment:
  COMPILE_TIMEOUT   AOT compile timeout in seconds (default: 300)
  UBERJAR_TIMEOUT   Uberjar packaging timeout in seconds (default: 600)

Notes:
  - Step 2 uses timeout because lein's compile subprocess hangs after finishing
    (non-daemon threads from Datomic/core.async). See docs/LEIN-UBERJAR-HANG.md
  - The --skip-cljs flag is useful when iterating on backend-only changes
  - To run the jar: java -jar target/orcpub.jar
  - Or use: ./start.sh prod (builds + runs)
EOF
}

# -----------------------------------------------------------------------------
# Timeout helper
# -----------------------------------------------------------------------------

# Run a command with a timeout. Uses coreutils `timeout` if available,
# otherwise falls back to a background process with kill.
run_with_timeout() {
    local seconds="$1"
    shift

    if command -v timeout >/dev/null 2>&1; then
        timeout "$seconds" "$@"
    else
        # Fallback for systems without coreutils timeout (e.g. macOS)
        "$@" &
        local pid=$!
        (
            sleep "$seconds"
            kill "$pid" 2>/dev/null || true
        ) &
        local watchdog=$!
        wait "$pid" 2>/dev/null
        local exit_code=$?
        kill "$watchdog" 2>/dev/null || true
        wait "$watchdog" 2>/dev/null || true
        return "$exit_code"
    fi
}

# -----------------------------------------------------------------------------
# Build Steps
# -----------------------------------------------------------------------------

build_cljs() {
    log_info "Step 1/3: Building production CLJS (advanced optimizations)..."
    cd "$REPO_ROOT"

    if lein fig:prod; then
        if [[ -f "$CLJS_OUTPUT" ]]; then
            log_info "CLJS build complete: $CLJS_OUTPUT"
        else
            log_error "CLJS build succeeded but output not found: $CLJS_OUTPUT"
            exit "$EXIT_RUNTIME"
        fi
    else
        log_error "CLJS build failed"
        exit "$EXIT_RUNTIME"
    fi
}

aot_compile() {
    log_info "Step 2/3: AOT compiling (timeout: ${COMPILE_TIMEOUT}s)..."
    cd "$REPO_ROOT"

    # The compile subprocess hangs after writing all .class files due to
    # non-daemon threads. We use timeout to kill it, then verify the output.
    # uberjar,uberjar-package avoids clean (which would wipe CLJS from step 1).
    run_with_timeout "$COMPILE_TIMEOUT" \
        lein with-profile uberjar,uberjar-package compile || true

    if [[ -f "$AOT_MARKER" ]]; then
        log_info "AOT compile complete (classes written)"
    else
        log_error "AOT compile failed — marker class not found: $AOT_MARKER"
        exit "$EXIT_RUNTIME"
    fi
}

package_uberjar() {
    log_info "Step 3/3: Packaging uberjar (Garden CSS + jar)..."
    cd "$REPO_ROOT"

    # uberjar-package profile: auto-clean false, prep-tasks ^:replace [["garden" "once"]]
    # This skips clean and compile, only runs Garden CSS then packages.
    run_with_timeout "$UBERJAR_TIMEOUT" \
        lein with-profile uberjar,uberjar-package uberjar

    if [[ -f "$JAR_OUTPUT" ]]; then
        local size
        size=$(du -h "$JAR_OUTPUT" | cut -f1)
        log_info "Build complete: $JAR_OUTPUT ($size)"
    else
        log_error "Uberjar packaging failed — jar not found: $JAR_OUTPUT"
        exit "$EXIT_RUNTIME"
    fi
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    local skip_cljs="false"

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --skip-cljs)  skip_cljs="true"; shift ;;
            --help|-h)    show_help; exit "$EXIT_SUCCESS" ;;
            *)            log_error "Unknown option: $1"; show_help; exit "$EXIT_USAGE" ;;
        esac
    done

    # Prerequisites
    check_java || exit "$EXIT_PREREQ"
    check_lein || exit "$EXIT_PREREQ"

    log_info "Building production uberjar..."
    echo ""

    # Step 1: CLJS
    if [[ "$skip_cljs" == "true" ]]; then
        if [[ -f "$REPO_ROOT/$CLJS_OUTPUT" ]]; then
            log_info "Step 1/3: Skipping CLJS (--skip-cljs, using existing)"
        else
            log_warn "Step 1/3: --skip-cljs but no existing CLJS output found"
            log_warn "Building CLJS anyway..."
            build_cljs
        fi
    else
        build_cljs
    fi

    # Step 2: AOT compile
    aot_compile

    # Step 3: Package
    package_uberjar

    echo ""
    log_info "Production build successful!"
    log_info "Run with: java -jar $JAR_OUTPUT"
}

main "$@"
