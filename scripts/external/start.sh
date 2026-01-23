#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# start.sh - OrcPub Service Launcher
# =============================================================================
# Usage:
#   ./start.sh              Start Datomic + REPL with server (default)
#   ./start.sh datomic      Start Datomic transactor only
#   ./start.sh server       Start REPL with server (requires Datomic running)
#   ./start.sh figwheel     Start Figwheel for ClojureScript hot-reload
#   ./start.sh garden       Start Garden for CSS auto-compilation
#   ./start.sh help         Show this help
# =============================================================================

# --- Colors ---
# TODO: Consider moving to scripts/common.sh
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# -----------------------------------------------------------------------------
# Checks
# -----------------------------------------------------------------------------

check_java() {
    if ! java -version 2>&1 | grep -q '1.8'; then
        log_error "Java 8 is required. Please use the devcontainer or install Java 8."
        exit 1
    fi
}

check_lein() {
    if ! command -v lein >/dev/null 2>&1; then
        log_error "Leiningen not found. Please use the devcontainer or install leiningen."
        exit 1
    fi
}

prepare_datomic_config() {
    if [ ! -f lib/datomic-free-0.9.5703/config/working-transactor.properties ]; then
        cp lib/datomic-free-0.9.5703/config/samples/free-transactor-template.properties \
           lib/datomic-free-0.9.5703/config/working-transactor.properties
        log_info "Copied Datomic properties template to working-transactor.properties"
    fi
}

# -----------------------------------------------------------------------------
# Start Targets
# -----------------------------------------------------------------------------

start_datomic() {
    prepare_datomic_config
    log_info "Starting Datomic transactor..."
    lib/datomic-free-0.9.5703/bin/transactor \
        lib/datomic-free-0.9.5703/config/working-transactor.properties
}

start_server() {
    log_info "Starting REPL with server (profile: +dev,+start-server)..."
    lein with-profile +dev,+start-server repl
}

start_figwheel() {
    log_info "Starting Figwheel (ClojureScript hot-reload)..."
    lein with-profile +dev figwheel
}

start_garden() {
    log_info "Starting Garden (CSS auto-compilation)..."
    lein garden auto
}

start_all() {
    prepare_datomic_config

    log_info "Starting Datomic transactor (background)..."
    lib/datomic-free-0.9.5703/bin/transactor \
        lib/datomic-free-0.9.5703/config/working-transactor.properties &
    local datomic_pid=$!
    log_info "Datomic transactor started (PID $datomic_pid)"

    # Wait for Datomic to initialize
    sleep 3

    log_info "Starting REPL with server (profile: +dev,+start-server)..."
    lein with-profile +dev,+start-server repl
}

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------

show_help() {
    cat << 'EOF'
OrcPub Service Launcher

Usage:
  ./start.sh [target]

Targets:
  (none)      Start Datomic (background) + REPL with server (foreground)
  datomic     Start Datomic transactor only (foreground)
  server      Start REPL with server only (foreground, requires Datomic)
  figwheel    Start Figwheel for ClojureScript hot-reload
  garden      Start Garden for CSS auto-compilation
  help        Show this help

Examples:
  ./start.sh                # Full dev stack: Datomic + server
  ./start.sh datomic        # Just Datomic (run in separate terminal)
  ./start.sh server         # Just REPL+server (after Datomic is running)
  ./start.sh figwheel       # ClojureScript hot-reload (separate terminal)
  ./start.sh garden         # CSS watcher (separate terminal)

Notes:
  - For full development, run in separate terminals:
    1. ./start.sh datomic
    2. ./start.sh server
    3. ./start.sh figwheel  (optional)
    4. ./start.sh garden    (optional)
  - Or use ./start.sh alone for Datomic + server in one terminal
EOF
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    local target="${1:-all}"

    # Always check prerequisites
    check_java
    check_lein

    case "$target" in
        all|"")
            start_all
            ;;
        datomic)
            start_datomic
            ;;
        server|repl)
            start_server
            ;;
        figwheel|cljs)
            start_figwheel
            ;;
        garden|css)
            start_garden
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "Unknown target: $target"
            show_help
            exit 1
            ;;
    esac
}

main "$@"