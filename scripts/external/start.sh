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
#   ./start.sh init-db      Initialize the database
#   ./start.sh --install    Run Datomic Pro installation (post-create.sh)
#   ./start.sh --tmux       Run service(s) in tmux session 'orcpub'
#   ./start.sh --background Run service(s) in background with nohup
#   ./start.sh help         Show this help
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source shared utilities
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

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
# Port Conflict Detection
# -----------------------------------------------------------------------------

check_port_available() {
    local port="$1"
    local service="$2"

    if port_in_use "$port"; then
        local pid
        pid=$(find_pids_by_port "$port" | awk '{print $1}')
        log_error "Port $port is already in use (PID: ${pid:-unknown})"
        log_info "Stop the existing $service with: ./stop.sh $service"
        return 1
    fi
    return 0
}

# -----------------------------------------------------------------------------
# Datomic Readiness
# -----------------------------------------------------------------------------

wait_for_datomic() {
    local timeout="${1:-30}"
    log_info "Waiting for Datomic to be ready (port $DATOMIC_PORT)..."

    if wait_for_port "$DATOMIC_PORT" "$timeout"; then
        log_info "Datomic is ready"
        return 0
    else
        log_error "Datomic did not start within ${timeout}s"
        return 1
    fi
}

prepare_datomic_config() {
    if [[ ! -f "$DATOMIC_CONFIG" ]]; then
        if [[ -f "$DATOMIC_CONFIG_TEMPLATE" ]]; then
            cp "$DATOMIC_CONFIG_TEMPLATE" "$DATOMIC_CONFIG"
            log_info "Created transactor config from template"
        else
            log_error "Datomic config template not found."
            log_error "Expected at: $DATOMIC_CONFIG_TEMPLATE"
            exit 1
        fi
    fi
}

# -----------------------------------------------------------------------------
# Tmux Support
# -----------------------------------------------------------------------------

TMUX_SESSION="orcpub"

run_in_tmux() {
    local window_name="$1"
    local cmd="$2"

    check_tmux || exit 1

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
# Background Support
# -----------------------------------------------------------------------------

run_in_background() {
    local name="$1"
    local cmd="$2"
    local log_file="$LOG_DIR/${name}.log"
    local pid_file="$LOG_DIR/${name}.pid"

    log_info "Starting $name in background..."
    log_info "Log file: $log_file"

    # Run command in background
    nohup bash -c "cd $REPO_ROOT && $cmd" > "$log_file" 2>&1 &
    local pid=$!
    echo "$pid" > "$pid_file"

    log_info "$name started (PID: $pid)"
    log_info "Tail logs: tail -f $log_file"
}

# -----------------------------------------------------------------------------
# Start Targets
# -----------------------------------------------------------------------------

start_datomic() {
    check_datomic_installed || exit 1
    check_port_available "$DATOMIC_PORT" "datomic" || exit 1
    prepare_datomic_config

    log_info "Starting Datomic transactor (${DATOMIC_TYPE} ${DATOMIC_VERSION})..."
    "$DATOMIC_DIR/bin/transactor" "$DATOMIC_CONFIG"
}

start_server() {
    check_port_available "$SERVER_PORT" "server" || exit 1

    log_info "Starting REPL with server (profile: +dev,+start-server)..."
    cd "$REPO_ROOT"
    lein with-profile +dev,+start-server repl
}

start_figwheel() {
    check_port_available "$FIGWHEEL_PORT" "figwheel" || exit 1

    log_info "Starting Figwheel (ClojureScript hot-reload)..."
    cd "$REPO_ROOT"
    lein with-profile +dev figwheel
}

start_garden() {
    log_info "Starting Garden (CSS auto-compilation)..."
    cd "$REPO_ROOT"
    lein garden auto
}

init_database() {
    log_info "Initializing database..."

    # Check if Datomic is running
    if ! port_in_use "$DATOMIC_PORT"; then
        log_error "Datomic is not running on port $DATOMIC_PORT"
        log_info "Start Datomic first: ./start.sh datomic"
        exit 1
    fi

    cd "$REPO_ROOT"
    lein run -m orcpub.dev-init
    log_info "Database initialized successfully"
}

start_all() {
    check_datomic_installed || exit 1
    check_port_available "$DATOMIC_PORT" "datomic" || exit 1
    prepare_datomic_config

    log_info "Starting Datomic transactor (background)..."
    "$DATOMIC_DIR/bin/transactor" "$DATOMIC_CONFIG" > "$LOG_DIR/datomic.log" 2>&1 &
    local datomic_pid=$!
    echo "$datomic_pid" > "$LOG_DIR/datomic.pid"
    log_info "Datomic transactor started (PID $datomic_pid)"

    # Wait for Datomic to be ready (with proper readiness check)
    if ! wait_for_datomic 30; then
        log_error "Failed to start Datomic. Check logs: $LOG_DIR/datomic.log"
        exit 1
    fi

    check_port_available "$SERVER_PORT" "server" || exit 1
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
  init-db     Initialize the database (requires Datomic running)
  help        Show this help

Options:
  --install, -i      Install/reinstall Datomic Pro (runs post-create.sh)
  --tmux, -t         Run in tmux session 'orcpub' (non-blocking)
  --background, -b   Run in background with nohup (logs to $LOG_DIR/)

Environment Variables (via .env or shell):
  DATOMIC_VERSION   Datomic version (default: 1.0.7482)
  DATOMIC_TYPE      Datomic type: pro or dev (default: pro)
  JAVA_MIN_VERSION  Minimum Java version required (default: 11)
  LOG_DIR           Directory for log files (default: ./logs)
  DATOMIC_PORT      Datomic port (default: 4334)
  SERVER_PORT       Server port (default: 8890)

Configuration:
  Config is loaded from: \$REPO_ROOT/.env
  Datomic is expected at: lib/com/datomic/datomic-\${TYPE}/\${VERSION}/

Examples:
  ./start.sh                  # Full dev stack: Datomic + server
  ./start.sh --install        # Install Datomic Pro
  ./start.sh datomic          # Just Datomic (run in separate terminal)
  ./start.sh server           # Just REPL+server (after Datomic is running)
  ./start.sh init-db          # Initialize the database
  ./start.sh --tmux           # All services in tmux session
  ./start.sh datomic --tmux   # Datomic in tmux window
  ./start.sh datomic -b       # Datomic in background

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
    local use_background="false"
    local positional=()

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --install|-i)      do_install="true"; shift ;;
            --tmux|-t)         use_tmux="true"; shift ;;
            --background|-b)   use_background="true"; shift ;;
            --help|-h)         show_help; exit 0 ;;
            -*)                log_error "Unknown option: $1"; show_help; exit 1 ;;
            *)                 positional+=("$1"); shift ;;
        esac
    done

    target="${positional[0]:-all}"

    # Handle install flag (no prereq checks needed)
    if [[ "$do_install" == "true" ]]; then
        run_install
        exit 0
    fi

    # Handle help (no prereq checks needed)
    if [[ "$target" == "help" ]]; then
        show_help
        exit 0
    fi

    # Check prerequisites only for runtime targets
    check_java || exit 1
    check_lein || exit 1

    # If --tmux, delegate to tmux runner
    if [[ "$use_tmux" == "true" ]]; then
        case "$target" in
            all|"")
                # Start each service in its own tmux window
                run_in_tmux "datomic" "$SCRIPT_DIR/start.sh datomic"
                sleep 2
                run_in_tmux "server" "$SCRIPT_DIR/start.sh server"
                ;;
            datomic)   run_in_tmux "datomic" "$SCRIPT_DIR/start.sh datomic" ;;
            server)    run_in_tmux "server" "$SCRIPT_DIR/start.sh server" ;;
            figwheel)  run_in_tmux "figwheel" "$SCRIPT_DIR/start.sh figwheel" ;;
            garden)    run_in_tmux "garden" "$SCRIPT_DIR/start.sh garden" ;;
            init-db)   run_in_tmux "init-db" "$SCRIPT_DIR/start.sh init-db" ;;
            *)         log_error "Unknown target: $target"; show_help; exit 1 ;;
        esac
        exit 0
    fi

    # If --background, delegate to background runner
    if [[ "$use_background" == "true" ]]; then
        case "$target" in
            all|"")
                run_in_background "datomic" "$SCRIPT_DIR/start.sh datomic"
                log_info "Waiting for Datomic..."
                if wait_for_datomic 30; then
                    run_in_background "server" "$SCRIPT_DIR/start.sh server"
                else
                    log_error "Datomic failed to start"
                    exit 1
                fi
                ;;
            datomic)   run_in_background "datomic" "$SCRIPT_DIR/start.sh datomic" ;;
            server)    run_in_background "server" "$SCRIPT_DIR/start.sh server" ;;
            figwheel)  run_in_background "figwheel" "$SCRIPT_DIR/start.sh figwheel" ;;
            garden)    run_in_background "garden" "$SCRIPT_DIR/start.sh garden" ;;
            *)         log_error "Cannot run '$target' in background"; exit 1 ;;
        esac
        exit 0
    fi

    # Direct execution (foreground)
    case "$target" in
        all|"")    start_all ;;
        datomic)   start_datomic ;;
        server)    start_server ;;
        figwheel)  start_figwheel ;;
        garden)    start_garden ;;
        init-db)   init_database ;;
        *)         log_error "Unknown target: $target"; show_help; exit 1 ;;
    esac
}

main "$@"
