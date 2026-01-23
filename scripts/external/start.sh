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
#   ./start.sh --install    Run Datomic Pro installation (post-create.sh)
#   ./start.sh --tmux       Run service(s) in tmux session 'orcpub'
#   ./start.sh help         Show this help
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# -----------------------------------------------------------------------------
# Environment Configuration
# -----------------------------------------------------------------------------

# Source .env if present (authoritative config)
if [[ -f "$REPO_ROOT/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    . "$REPO_ROOT/.env"
    set +a
fi

# Defaults (used if not set in .env)
DATOMIC_VERSION="${DATOMIC_VERSION:-1.0.7482}"
DATOMIC_TYPE="${DATOMIC_TYPE:-pro}"
JAVA_MIN_VERSION="${JAVA_MIN_VERSION:-11}"
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs}"

# Derived paths
DATOMIC_DIR="$REPO_ROOT/lib/com/datomic/datomic-${DATOMIC_TYPE}/${DATOMIC_VERSION}"
DATOMIC_CONFIG="$DATOMIC_DIR/config/working-transactor.properties"
DATOMIC_CONFIG_TEMPLATE="$DATOMIC_DIR/config/samples/dev-transactor-template.properties"

# Ensure logs directory exists
mkdir -p "$LOG_DIR"

# --- Colors ---
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# -----------------------------------------------------------------------------
# Checks
# -----------------------------------------------------------------------------

check_java() {
    local java_version
    java_version=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')

    if [[ -z "$java_version" ]]; then
        log_error "Java not found. Please install Java $JAVA_MIN_VERSION or higher."
        exit 1
    fi

    if [[ "$java_version" -lt "$JAVA_MIN_VERSION" ]]; then
        log_error "Java $JAVA_MIN_VERSION+ required (found Java $java_version)."
        log_info "Use the devcontainer or install a compatible JDK."
        exit 1
    fi

    log_info "Java $java_version detected (minimum: $JAVA_MIN_VERSION)"
}

check_lein() {
    if ! command -v lein >/dev/null 2>&1; then
        log_error "Leiningen not found. Please use the devcontainer or install leiningen."
        exit 1
    fi
}

check_datomic_installed() {
    if [[ ! -d "$DATOMIC_DIR" ]]; then
        log_error "Datomic ${DATOMIC_TYPE} ${DATOMIC_VERSION} not found."
        log_error "Expected at: $DATOMIC_DIR"
        log_info "Run './start.sh --install' to install Datomic."
        exit 1
    fi

    if [[ ! -f "$DATOMIC_DIR/bin/transactor" ]]; then
        log_error "Datomic transactor not found. Installation may be incomplete."
        log_error "Expected at: $DATOMIC_DIR/bin/transactor"
        log_info "Run './start.sh --install' to reinstall Datomic."
        exit 1
    elif [[ ! -x "$DATOMIC_DIR/bin/transactor" ]]; then
        log_error "Datomic transactor exists but is not executable."
        log_error "Path: $DATOMIC_DIR/bin/transactor"
        log_info "Try: chmod +x $DATOMIC_DIR/bin/transactor"
        exit 1
    fi
}

prepare_datomic_config() {
    if [[ ! -f "$DATOMIC_CONFIG" ]]; then
        if [[ -f "$DATOMIC_CONFIG_TEMPLATE" ]]; then
            cp "$DATOMIC_CONFIG_TEMPLATE" "$DATOMIC_CONFIG"
            log_info "Created transactor config from template: $DATOMIC_CONFIG"
        else
            log_error "Datomic config template not found."
            log_error "Expected at: $DATOMIC_CONFIG_TEMPLATE"
            exit 1
        fi
    fi
}

# -----------------------------------------------------------------------------
# Install
# -----------------------------------------------------------------------------

run_install() {
    local post_create="$REPO_ROOT/.devcontainer/post-create.sh"

    if [[ ! -x "$post_create" ]]; then
        log_error "Install script not found or not executable: $post_create"
        exit 1
    fi

    log_info "Running Datomic ${DATOMIC_TYPE} ${DATOMIC_VERSION} installation..."
    "$post_create"
    log_info "Installation complete."
}

# -----------------------------------------------------------------------------
# Tmux Support
# -----------------------------------------------------------------------------
# Runs a command in a tmux session. Creates session 'orcpub' if needed,
# otherwise adds a new window. Allows non-blocking service starts.

TMUX_SESSION="orcpub"

run_in_tmux() {
    local window_name="$1"
    local cmd="$2"

    if ! command -v tmux >/dev/null 2>&1; then
        log_error "tmux not found. Install tmux or run without --tmux."
        exit 1
    fi

    # Build the command to run (re-invoke this script without --tmux)
    local full_cmd="cd $REPO_ROOT && $cmd; echo ''; echo 'Press Enter to close...'; read"

    if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
        # Session exists - add new window
        tmux new-window -t "$TMUX_SESSION" -n "$window_name" "bash -c '$full_cmd'"
        log_info "Started '$window_name' in tmux window (session: $TMUX_SESSION)"
    else
        # Create new session with this window
        tmux new-session -d -s "$TMUX_SESSION" -n "$window_name" "bash -c '$full_cmd'"
        log_info "Created tmux session '$TMUX_SESSION' with window '$window_name'"
    fi

    log_info "Attach with: tmux attach -t $TMUX_SESSION"
}

# -----------------------------------------------------------------------------
# Start Targets
# -----------------------------------------------------------------------------

start_datomic() {
    check_datomic_installed
    prepare_datomic_config
    log_info "Starting Datomic transactor (${DATOMIC_TYPE} ${DATOMIC_VERSION})..."
    "$DATOMIC_DIR/bin/transactor" "$DATOMIC_CONFIG"
}

start_server() {
    log_info "Starting REPL with server (profile: +dev,+start-server)..."
    cd "$REPO_ROOT"
    lein with-profile +dev,+start-server repl
}

start_figwheel() {
    log_info "Starting Figwheel (ClojureScript hot-reload)..."
    cd "$REPO_ROOT"
    lein with-profile +dev figwheel
}

start_garden() {
    log_info "Starting Garden (CSS auto-compilation)..."
    cd "$REPO_ROOT"
    lein garden auto
}

start_all() {
    check_datomic_installed
    prepare_datomic_config

    log_info "Starting Datomic transactor (background)..."
    "$DATOMIC_DIR/bin/transactor" "$DATOMIC_CONFIG" &
    local datomic_pid=$!
    log_info "Datomic transactor started (PID $datomic_pid)"

    # Wait for Datomic to initialize
    sleep 3

    log_info "Starting REPL with server (profile: +dev,+start-server)..."
    cd "$REPO_ROOT"
    lein with-profile +dev,+start-server repl
}

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------

show_help() {
    cat << EOF
OrcPub Service Launcher

Usage:
  ./start.sh [target] [options]

Targets:
  (none)      Start Datomic (background) + REPL with server (foreground)
  datomic     Start Datomic transactor only (foreground)
  server      Start REPL with server only (foreground, requires Datomic)
  figwheel    Start Figwheel for ClojureScript hot-reload
  garden      Start Garden for CSS auto-compilation
  help        Show this help

Options:
  --install, -i   Install/reinstall Datomic Pro (runs post-create.sh)
  --tmux, -t      Run in tmux session 'orcpub' (non-blocking)

Environment Variables (via .env or shell):
  DATOMIC_VERSION   Datomic version (default: 1.0.7482)
  DATOMIC_TYPE      Datomic type: pro or dev (default: pro)
  JAVA_MIN_VERSION  Minimum Java version required (default: 11)
  LOG_DIR           Directory for log files (default: ./logs)

Configuration:
  Config is loaded from: \$REPO_ROOT/.env
  Datomic is expected at: lib/com/datomic/datomic-\${TYPE}/\${VERSION}/

Examples:
  ./start.sh                # Full dev stack: Datomic + server
  ./start.sh --install      # Install Datomic Pro
  ./start.sh datomic        # Just Datomic (run in separate terminal)
  ./start.sh server         # Just REPL+server (after Datomic is running)
  ./start.sh figwheel       # ClojureScript hot-reload (separate terminal)
  ./start.sh garden         # CSS watcher (separate terminal)
  ./start.sh --tmux         # All services in tmux session
  ./start.sh datomic --tmux # Datomic in tmux window

Notes:
  - For full development, run in separate terminals:
    1. ./start.sh datomic
    2. ./start.sh server
    3. ./start.sh figwheel  (optional)
    4. ./start.sh garden    (optional)
  - Or use ./start.sh alone for Datomic + server in one terminal
  - Or use ./start.sh --tmux to run all in a tmux session
EOF
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    local target=""
    local do_install="false"
    local use_tmux="false"
    local positional=()

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --install|-i)  do_install="true"; shift ;;
            --tmux|-t)     use_tmux="true"; shift ;;
            --help|-h)     show_help; exit 0 ;;
            -*)            log_error "Unknown option: $1"; show_help; exit 1 ;;
            *)             positional+=("$1"); shift ;;
        esac
    done

    target="${positional[0]:-all}"

    # Handle install flag
    if [[ "$do_install" == "true" ]]; then
        run_install
        exit 0
    fi

    # Always check prerequisites for runtime targets
    check_java
    check_lein

    # If --tmux, delegate to tmux runner
    if [[ "$use_tmux" == "true" ]]; then
        case "$target" in
            all|"")
                # Start each service in its own tmux window
                run_in_tmux "datomic" "$SCRIPT_DIR/start.sh datomic"
                sleep 1
                run_in_tmux "server" "$SCRIPT_DIR/start.sh server"
                ;;
            datomic)
                run_in_tmux "datomic" "$SCRIPT_DIR/start.sh datomic"
                ;;
            server|repl)
                run_in_tmux "server" "$SCRIPT_DIR/start.sh server"
                ;;
            figwheel|cljs)
                run_in_tmux "figwheel" "$SCRIPT_DIR/start.sh figwheel"
                ;;
            garden|css)
                run_in_tmux "garden" "$SCRIPT_DIR/start.sh garden"
                ;;
            *)
                log_error "Unknown target: $target"
                show_help
                exit 1
                ;;
        esac
        exit 0
    fi

    # Direct execution (foreground)
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
        help)
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
