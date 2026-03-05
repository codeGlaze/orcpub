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
#
# Options:
#   --install, -i      Run Datomic Pro installation (post-create.sh)
#   --tmux, -t         Run service(s) in tmux session 'orcpub'
#   --background, -b   Run service(s) in background with nohup
#   --quiet, -q        Minimal output (for automation)
#   --check, -c        Validate prerequisites without starting services
#   --idempotent, -I   Succeed if service is already running
#   --help, -h         Show this help
#
# Exit Codes:
#   0 - Success (or already running with --idempotent)
#   1 - Usage error (invalid args)
#   2 - Prerequisite failure (missing Java, Datomic, etc.)
#   3 - Runtime failure (port conflict, startup timeout)
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

# Check port availability, respecting --idempotent and non-interactive mode
# Returns: 0 if available (or idempotent+running), 1 if should abort, 2 if should skip
# Sets IDEMPOTENT_ALREADY_RUNNING=true if service already running in idempotent mode
# Sets SKIP_SERVICE=true if user chose to skip this service
check_port_available() {
    local port="$1"
    local service="$2"
    local idempotent="${3:-false}"

    IDEMPOTENT_ALREADY_RUNNING=false
    SKIP_SERVICE=false

    if port_in_use "$port"; then
        local pid
        pid=$(find_pids_by_port "$port" | awk '{print $1}')

        # Idempotent mode: succeed if already running
        if [[ "$idempotent" == "true" ]]; then
            log_info "$service already running on port $port (PID: ${pid:-unknown})"
            IDEMPOTENT_ALREADY_RUNNING=true
            return 0
        fi

        # Non-interactive mode: fail immediately with clear error
        if ! is_interactive; then
            log_error "Port $port in use (non-interactive mode, exiting)"
            log_error "PID: ${pid:-unknown}"
            return 1
        fi

        # Interactive mode: offer to stop, skip, or abort (with timeout)
        log_warn "Port $port is already in use (PID: ${pid:-unknown})"
        if ! read -t 30 -p "$service: [s]kip, s[t]op existing, or [a]bort? [s/t/a] " -n 1 -r; then
            echo
            log_error "Prompt timed out after 30 seconds"
            return 1
        fi
        echo
        case "$REPLY" in
            t|T|y|Y)
                log_info "Stopping existing $service..."
                "$SCRIPT_DIR/stop.sh" "$service" --yes --quiet
                sleep 1
                if port_in_use "$port"; then
                    log_error "Failed to stop $service on port $port"
                    return 1
                fi
                log_info "Port $port is now available"
                return 0
                ;;
            s|S|"")
                log_info "Skipping $service (already running)"
                SKIP_SERVICE=true
                return 2
                ;;
            *)
                log_error "Aborting."
                return 1
                ;;
        esac
    fi
    return 0
}

# -----------------------------------------------------------------------------
# Datomic Readiness
# -----------------------------------------------------------------------------

wait_for_datomic() {
    local timeout="${1:-$PORT_WAIT}"
    local log_file="${2:-$LOG_DIR/datomic.log}"

    log_info "Waiting for Datomic to be ready (port $DATOMIC_PORT)..."

    if wait_for_port "$DATOMIC_PORT" "$timeout"; then
        log_info "Datomic is ready"
        return 0
    else
        # Show diagnostics on failure
        show_startup_failure "datomic" "$log_file" "$DATOMIC_PORT"
        return 1
    fi
}

prepare_datomic_config() {
    if [[ ! -f "$DATOMIC_CONFIG" ]]; then
        if [[ -f "$DATOMIC_CONFIG_TEMPLATE" ]]; then
            cp "$DATOMIC_CONFIG_TEMPLATE" "$DATOMIC_CONFIG"
            # Restrict permissions (config may contain secrets)
            chmod 600 "$DATOMIC_CONFIG"
            log_info "Created transactor config from template"
        else
            log_error "Datomic config template not found."
            log_error "Expected at: $DATOMIC_CONFIG_TEMPLATE"
            return 1
        fi
    fi
    return 0
}

# -----------------------------------------------------------------------------
# Pre-flight Checks (--check mode)
# -----------------------------------------------------------------------------

# Run all prerequisite checks for a target without starting services
# Returns: 0 if all checks pass, 2 (EXIT_PREREQ) otherwise
run_checks() {
    local target="$1"
    local failed=0

    log_info "Running pre-flight checks for target: $target"
    echo ""

    # Common checks for all targets
    echo -n "Java ($JAVA_MIN_VERSION+): "
    if check_java 2>/dev/null; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${RED}FAILED${NC}"
        ((failed++))
    fi

    echo -n "Leiningen: "
    if check_lein 2>/dev/null; then
        echo -e "${GREEN}OK${NC}"
    else
        echo -e "${RED}FAILED${NC}"
        ((failed++))
    fi

    # Target-specific checks
    case "$target" in
        all|datomic)
            echo -n "Datomic installed: "
            if check_datomic_installed 2>/dev/null; then
                echo -e "${GREEN}OK${NC}"
            else
                echo -e "${RED}FAILED${NC}"
                ((failed++))
            fi

            echo -n "Datomic config: "
            if [[ -f "$DATOMIC_CONFIG" ]] || [[ -f "$DATOMIC_CONFIG_TEMPLATE" ]]; then
                echo -e "${GREEN}OK${NC}"
                # Check port consistency if config exists
                if [[ -f "$DATOMIC_CONFIG" ]]; then
                    local config_port
                    config_port=$(get_datomic_port_from_config "$DATOMIC_CONFIG")
                    if [[ "$config_port" != "$DATOMIC_PORT" ]]; then
                        echo -e "  ${YELLOW}WARNING: Config port ($config_port) differs from DATOMIC_PORT ($DATOMIC_PORT)${NC}"
                    fi
                fi
            else
                echo -e "${RED}FAILED${NC} (no config or template)"
                ((failed++))
            fi

            echo -n "Datomic port ($DATOMIC_PORT): "
            if port_in_use "$DATOMIC_PORT"; then
                echo -e "${YELLOW}IN USE${NC} (PID: $(find_pids_by_port "$DATOMIC_PORT" | head -1))"
            else
                echo -e "${GREEN}AVAILABLE${NC}"
            fi
            ;;
    esac

    case "$target" in
        all|server)
            echo -n "Server port ($SERVER_PORT): "
            if port_in_use "$SERVER_PORT"; then
                echo -e "${YELLOW}IN USE${NC} (PID: $(find_pids_by_port "$SERVER_PORT" | head -1))"
            else
                echo -e "${GREEN}AVAILABLE${NC}"
            fi

            if [[ "$target" == "server" ]]; then
                echo -n "Datomic reachable: "
                if port_in_use "$DATOMIC_PORT"; then
                    echo -e "${GREEN}YES${NC}"
                else
                    echo -e "${YELLOW}NO${NC} (server requires Datomic)"
                fi
            fi
            ;;
    esac

    case "$target" in
        figwheel)
            echo -n "Figwheel port ($FIGWHEEL_PORT): "
            if port_in_use "$FIGWHEEL_PORT"; then
                echo -e "${YELLOW}IN USE${NC}"
            else
                echo -e "${GREEN}AVAILABLE${NC}"
            fi
            ;;
    esac

    echo ""
    if [[ $failed -gt 0 ]]; then
        log_error "$failed prerequisite check(s) failed"
        return $EXIT_PREREQ
    else
        log_info "All prerequisite checks passed"
        return $EXIT_SUCCESS
    fi
}

# -----------------------------------------------------------------------------
# Tmux Support
# -----------------------------------------------------------------------------

TMUX_SESSION="orcpub"

run_in_tmux() {
    local window_name="$1"
    shift
    local cmd_args=("$@")

    check_tmux || exit $EXIT_PREREQ

    # Build a properly quoted command string to avoid argument splitting
    local quoted_cmd
    quoted_cmd=$(printf '%q ' "${cmd_args[@]}")

    if tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
        # Session exists - add new window
        # Use -c for working directory (safer than bash -c string building)
        tmux new-window -t "$TMUX_SESSION" -n "$window_name" -c "$REPO_ROOT"
        # Set remain-on-exit so window stays open after command finishes
        tmux set-option -t "$TMUX_SESSION:$window_name" remain-on-exit on
        # Send the command (properly quoted)
        tmux send-keys -t "$TMUX_SESSION:$window_name" "$quoted_cmd" C-m
        log_info "Started '$window_name' in tmux window (session: $TMUX_SESSION)"
    else
        # Create new session with this window
        tmux new-session -d -s "$TMUX_SESSION" -n "$window_name" -c "$REPO_ROOT"
        tmux set-option -t "$TMUX_SESSION:$window_name" remain-on-exit on
        tmux send-keys -t "$TMUX_SESSION:$window_name" "$quoted_cmd" C-m
        log_info "Created tmux session '$TMUX_SESSION' with window '$window_name'"
    fi

    log_info "Attach with: tmux attach -t $TMUX_SESSION"
}

# -----------------------------------------------------------------------------
# Background Support
# -----------------------------------------------------------------------------

run_in_background() {
    local name="$1"
    shift
    local cmd_args=("$@")
    local log_file="$LOG_DIR/${name}.log"
    local pid_file="$LOG_DIR/${name}.pid"

    # Clean up any stale PID file
    cleanup_stale_pid "$name"

    log_info "Starting $name in background..."
    log_info "Log file: $log_file"

    # Run command in background from REPO_ROOT
    # Use pushd/popd instead of bash -c for safer execution
    (
        cd "$REPO_ROOT" || exit 1
        nohup "${cmd_args[@]}" > "$log_file" 2>&1 &
        echo $! > "$pid_file"
    )

    local pid
    pid=$(cat "$pid_file" 2>/dev/null || true)

    log_info "$name started (PID: ${pid:-unknown})"
    log_info "Tail logs: tail -f $log_file"
}

# -----------------------------------------------------------------------------
# Start Targets
# -----------------------------------------------------------------------------

start_datomic() {
    local idempotent="${1:-false}"
    local port_result=0

    check_datomic_installed || exit $EXIT_PREREQ

    # Check port (0=available, 1=abort, 2=skip)
    check_port_available "$DATOMIC_PORT" "datomic" "$idempotent" || port_result=$?
    [[ $port_result -eq 1 ]] && exit $EXIT_RUNTIME

    # If already running (idempotent or user chose skip), exit success
    if [[ "$IDEMPOTENT_ALREADY_RUNNING" == "true" || "$SKIP_SERVICE" == "true" ]]; then
        exit $EXIT_SUCCESS
    fi

    prepare_datomic_config || exit $EXIT_PREREQ

    # Clean up stale PID file
    cleanup_stale_pid "datomic"

    log_info "Starting Datomic transactor (${DATOMIC_TYPE} ${DATOMIC_VERSION})..."
    nohup "$DATOMIC_DIR/bin/transactor" "$DATOMIC_CONFIG" > "$LOG_DIR/datomic.log" 2>&1 &
    local datomic_pid=$!
    echo "$datomic_pid" > "$LOG_DIR/datomic.pid"
    log_info "Datomic transactor started (PID $datomic_pid)"
    log_info "Logs: $LOG_DIR/datomic.log"

    # Early verification: ensure process didn't die immediately
    sleep 0.5
    if ! kill -0 "$datomic_pid" 2>/dev/null; then
        log_error "Datomic process died immediately after starting"
        show_startup_failure "datomic" "$LOG_DIR/datomic.log" "$DATOMIC_PORT"
        exit $EXIT_RUNTIME
    fi

    # Wait for Datomic to be ready
    if ! wait_for_datomic "$PORT_WAIT" "$LOG_DIR/datomic.log"; then
        log_error "Failed to start Datomic. Check logs: $LOG_DIR/datomic.log"
        exit $EXIT_RUNTIME
    fi
}

start_server() {
    local idempotent="${1:-false}"
    local port_result=0

    # Check port (0=available, 1=abort, 2=skip)
    check_port_available "$SERVER_PORT" "server" "$idempotent" || port_result=$?
    [[ $port_result -eq 1 ]] && exit $EXIT_RUNTIME

    # If already running (idempotent or user chose skip), exit success
    if [[ "$IDEMPOTENT_ALREADY_RUNNING" == "true" || "$SKIP_SERVICE" == "true" ]]; then
        exit $EXIT_SUCCESS
    fi

    cd "$REPO_ROOT"

    # Use headless mode if not running interactively (background/nohup)
    if [[ -t 0 ]]; then
        log_info "Starting REPL with server (profile: +dev,+start-server)..."
        lein with-profile +dev,+start-server repl
    else
        log_info "Starting headless server (profile: +dev,+start-server)..."
        lein with-profile +dev,+start-server repl :headless
    fi
}

start_figwheel() {
    local idempotent="${1:-false}"
    local port_result=0

    # Check port (0=available, 1=abort, 2=skip)
    check_port_available "$FIGWHEEL_PORT" "figwheel" "$idempotent" || port_result=$?
    [[ $port_result -eq 1 ]] && exit $EXIT_RUNTIME

    # If already running (idempotent or user chose skip), exit success
    if [[ "$IDEMPOTENT_ALREADY_RUNNING" == "true" || "$SKIP_SERVICE" == "true" ]]; then
        exit $EXIT_SUCCESS
    fi

    # Clean up stale PID file
    cleanup_stale_pid "figwheel"

    log_info "Starting Figwheel (ClojureScript hot-reload)..."
    cd "$REPO_ROOT"
    # Use fig:watch alias (headless build + watch, no REPL — works with nohup)
    # For interactive REPL use: lein fig:dev (needs a terminal)
    nohup lein fig:watch > "$LOG_DIR/figwheel.log" 2>&1 &
    local figwheel_pid=$!
    echo "$figwheel_pid" > "$LOG_DIR/figwheel.pid"
    log_info "Figwheel started (PID $figwheel_pid)"
    log_info "Logs: $LOG_DIR/figwheel.log"

    # Early verification: ensure process didn't die immediately
    sleep 0.5
    if ! kill -0 "$figwheel_pid" 2>/dev/null; then
        log_error "Figwheel process died immediately after starting"
        show_startup_failure "figwheel" "$LOG_DIR/figwheel.log" "$FIGWHEEL_PORT"
        exit $EXIT_RUNTIME
    fi

    # Wait for Figwheel to be ready (fail fast if process dies)
    log_info "Waiting for Figwheel to be ready (port $FIGWHEEL_PORT)..."
    if wait_for_port_or_die "$FIGWHEEL_PORT" "$figwheel_pid" "$PORT_WAIT"; then
        log_info "Figwheel is ready"
    elif kill -0 "$figwheel_pid" 2>/dev/null; then
        # Process alive but port not ready — likely first-run compile/dep download
        log_warn "Figwheel still starting (PID $figwheel_pid alive, port $FIGWHEEL_PORT not yet open)"
        log_warn "First run may take a few minutes for ClojureScript compilation"
        log_info "Logs: $LOG_DIR/figwheel.log"
    else
        show_startup_failure "figwheel" "$LOG_DIR/figwheel.log" "$FIGWHEEL_PORT"
        exit $EXIT_RUNTIME
    fi
}

start_garden() {
    # Clean up stale PID file
    cleanup_stale_pid "garden"

    log_info "Starting Garden (CSS auto-compilation)..."
    cd "$REPO_ROOT"
    nohup lein garden auto > "$LOG_DIR/garden.log" 2>&1 &
    local garden_pid=$!
    echo "$garden_pid" > "$LOG_DIR/garden.pid"
    log_info "Garden started (PID $garden_pid)"
    log_info "Logs: $LOG_DIR/garden.log"

    # Garden has no port to check - verify process survives startup
    # Check multiple times to catch delayed failures (e.g., lein project parsing)
    local checks=0
    local max_checks=5
    while [[ $checks -lt $max_checks ]]; do
        sleep 1
        if ! kill -0 "$garden_pid" 2>/dev/null; then
            log_error "Garden process died during startup"
            show_startup_failure "garden" "$LOG_DIR/garden.log" ""
            exit $EXIT_RUNTIME
        fi
        ((checks++))
    done
    log_info "Garden is running"
}

init_database() {
    log_info "Initializing database..."

    # Check if Datomic is running
    if ! port_in_use "$DATOMIC_PORT"; then
        log_error "Datomic is not running on port $DATOMIC_PORT"
        log_info "Start Datomic first: ./start.sh datomic"
        exit $EXIT_PREREQ
    fi

    cd "$REPO_ROOT"
    # Use init-db profile to skip ClojureScript/Garden compilation.
    # This loads src/clj, src/cljc, and dev/ — much faster than a full dev build.
    # The CLI entrypoint is dev/user.clj -main, which dispatches by command name.
    if lein with-profile init-db run -m user init-db; then
        log_info "Database initialized successfully"
    else
        log_error "Database initialization failed"
        exit $EXIT_RUNTIME
    fi
}

start_all() {
    local idempotent="${1:-false}"
    local started_datomic="false"
    local datomic_pid=""
    local port_result=0

    # Cleanup function for signal handling
    cleanup_on_exit() {
        local exit_code=$?
        if [[ "$started_datomic" == "true" && -n "$datomic_pid" ]]; then
            log_info "Stopping Datomic (PID $datomic_pid)..."
            kill "$datomic_pid" 2>/dev/null || true
            # Wait briefly for graceful shutdown
            sleep 1
            kill -0 "$datomic_pid" 2>/dev/null && kill -9 "$datomic_pid" 2>/dev/null || true
            rm -f "$LOG_DIR/datomic.pid"
            log_info "Datomic stopped"
        fi
        exit "$exit_code"
    }

    # Set up trap for cleanup on interrupt/termination
    trap cleanup_on_exit INT TERM

    check_datomic_installed || exit $EXIT_PREREQ

    # Check Datomic port (0=available, 1=abort, 2=skip)
    port_result=0
    check_port_available "$DATOMIC_PORT" "datomic" "$idempotent" || port_result=$?
    if [[ $port_result -eq 1 ]]; then
        exit $EXIT_RUNTIME
    fi

    # If Datomic already running (idempotent or skipped), skip starting it
    if [[ "$IDEMPOTENT_ALREADY_RUNNING" != "true" && "$SKIP_SERVICE" != "true" ]]; then
        prepare_datomic_config || exit $EXIT_PREREQ

        # Clean up stale PID
        cleanup_stale_pid "datomic"

        log_info "Starting Datomic transactor (background)..."
        nohup "$DATOMIC_DIR/bin/transactor" "$DATOMIC_CONFIG" > "$LOG_DIR/datomic.log" 2>&1 &
        datomic_pid=$!
        started_datomic="true"
        echo "$datomic_pid" > "$LOG_DIR/datomic.pid"
        log_info "Datomic transactor started (PID $datomic_pid)"

        # Early verification: ensure process didn't die immediately
        sleep 0.5
        if ! kill -0 "$datomic_pid" 2>/dev/null; then
            log_error "Datomic process died immediately after starting"
            show_startup_failure "datomic" "$LOG_DIR/datomic.log" "$DATOMIC_PORT"
            started_datomic="false"  # Don't try to clean up a dead process
            exit $EXIT_RUNTIME
        fi

        # Wait for Datomic to be ready (with proper readiness check)
        if ! wait_for_datomic "$PORT_WAIT" "$LOG_DIR/datomic.log"; then
            log_error "Failed to start Datomic. Check logs: $LOG_DIR/datomic.log"
            exit $EXIT_RUNTIME
        fi
    else
        log_info "Datomic already running, skipping startup"
    fi

    # Check server port (0=available, 1=abort, 2=skip)
    port_result=0
    check_port_available "$SERVER_PORT" "server" "$idempotent" || port_result=$?
    if [[ $port_result -eq 1 ]]; then
        exit $EXIT_RUNTIME
    fi

    if [[ "$IDEMPOTENT_ALREADY_RUNNING" == "true" || "$SKIP_SERVICE" == "true" ]]; then
        log_info "Server already running on port $SERVER_PORT"
        # Clear trap since we're not managing Datomic lifecycle
        trap - INT TERM
        exit $EXIT_SUCCESS
    fi

    log_info "Starting REPL with server (profile: +dev,+start-server)..."
    log_info "Note: Ctrl+C will stop both server and Datomic"
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
  --install, -i              Install/reinstall Datomic Pro (runs post-create.sh)
  --tmux, -t                 Run in tmux session 'orcpub' (non-blocking)
  --background, -b           Run in background with nohup (logs to $LOG_DIR/)
  --quiet, -q                Minimal output (for automation)
  --check, -c                Validate prerequisites without starting
  --idempotent, -I           Succeed if service already running
  --if-not-running           Alias for --idempotent

Exit Codes:
  0  Success (or already running with --idempotent)
  1  Usage error (invalid args)
  2  Prerequisite failure (missing Java, Datomic not installed, etc.)
  3  Runtime failure (port conflict, startup timeout, process crash)

Environment Variables (via .env or shell):
  DATOMIC_VERSION   Datomic version (default: 1.0.7482)
  DATOMIC_TYPE      Datomic type: pro or dev (default: pro)
  JAVA_MIN_VERSION  Minimum Java version required (default: 11)
  LOG_DIR           Directory for log files (default: ./logs)
  DATOMIC_PORT      Datomic port (default: 4334)
  SERVER_PORT       Server port (default: 8890)
  PORT_WAIT         Timeout for port readiness (default: 30)
  KILL_WAIT         Timeout for graceful shutdown (default: 5)

Configuration:
  Config is loaded from: \$REPO_ROOT/.env
  Datomic is expected at: lib/com/datomic/datomic-\${TYPE}/\${VERSION}/

Examples:
  ./start.sh                              # Full dev stack: Datomic + server
  ./start.sh --install                    # Install Datomic Pro
  ./start.sh datomic                      # Just Datomic (run in separate terminal)
  ./start.sh server                       # Just REPL+server (after Datomic is running)
  ./start.sh init-db                      # Initialize the database
  ./start.sh --tmux                       # All services in tmux session
  ./start.sh datomic --tmux               # Datomic in tmux window
  ./start.sh datomic -b                   # Datomic in background

Automation examples:
  ./start.sh --check                      # Pre-flight: validate all prerequisites
  ./start.sh datomic --check              # Pre-flight: validate Datomic only
  ./start.sh datomic -q --idempotent      # Start or succeed if already running
  ./start.sh datomic --if-not-running -q  # Same as above

Notes:
  - For full development, run in separate terminals:
    1. ./start.sh datomic
    2. ./start.sh server
    3. ./start.sh figwheel  (optional)
    4. ./start.sh garden    (optional)
  - Or use ./start.sh alone for Datomic + server in one terminal
  - Or use ./start.sh --tmux to run all in a tmux session
  - In non-interactive mode (CI/cron), port conflicts fail immediately
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
    local do_check="false"
    local idempotent="false"
    local positional=()

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --install|-i)         do_install="true"; shift ;;
            --tmux|-t)            use_tmux="true"; shift ;;
            --background|-b)      use_background="true"; shift ;;
            --quiet|-q)           QUIET="true"; export QUIET; shift ;;
            --check|-c)           do_check="true"; shift ;;
            --idempotent|-I|--if-not-running)
                                  idempotent="true"; shift ;;
            --help|-h)            show_help; exit $EXIT_SUCCESS ;;
            -*)                   log_error "Unknown option: $1"; show_help; exit $EXIT_USAGE ;;
            *)                    positional+=("$1"); shift ;;
        esac
    done

    target="${positional[0]:-all}"

    # Handle install flag (no prereq checks needed)
    if [[ "$do_install" == "true" ]]; then
        run_install
        exit $EXIT_SUCCESS
    fi

    # Handle help (no prereq checks needed)
    if [[ "$target" == "help" ]]; then
        show_help
        exit $EXIT_SUCCESS
    fi

    # Handle --check mode (pre-flight validation)
    if [[ "$do_check" == "true" ]]; then
        run_checks "$target"
        exit $?
    fi

    # Check prerequisites for runtime targets
    check_java || exit $EXIT_PREREQ
    check_lein || exit $EXIT_PREREQ

    # If --tmux, delegate to tmux runner
    if [[ "$use_tmux" == "true" ]]; then
        case "$target" in
            all|"")
                # Start each service in its own tmux window
                run_in_tmux "datomic" "$SCRIPT_DIR/start.sh" datomic
                sleep 2
                run_in_tmux "server" "$SCRIPT_DIR/start.sh" server
                ;;
            datomic)   run_in_tmux "datomic" "$SCRIPT_DIR/start.sh" datomic ;;
            server)    run_in_tmux "server" "$SCRIPT_DIR/start.sh" server ;;
            figwheel)  run_in_tmux "figwheel" "$SCRIPT_DIR/start.sh" figwheel ;;
            garden)    run_in_tmux "garden" "$SCRIPT_DIR/start.sh" garden ;;
            init-db)   run_in_tmux "init-db" "$SCRIPT_DIR/start.sh" init-db ;;
            *)         log_error "Unknown target: $target"; show_help; exit $EXIT_USAGE ;;
        esac
        exit $EXIT_SUCCESS
    fi

    # If --background, delegate to background runner
    if [[ "$use_background" == "true" ]]; then
        case "$target" in
            all|"")
                run_in_background "datomic" "$SCRIPT_DIR/start.sh" datomic
                log_info "Waiting for Datomic..."
                if wait_for_datomic "$PORT_WAIT" "$LOG_DIR/datomic.log"; then
                    run_in_background "server" "$SCRIPT_DIR/start.sh" server
                else
                    log_error "Datomic failed to start"
                    exit $EXIT_RUNTIME
                fi
                ;;
            datomic)   run_in_background "datomic" "$SCRIPT_DIR/start.sh" datomic ;;
            server)    run_in_background "server" "$SCRIPT_DIR/start.sh" server ;;
            figwheel)  run_in_background "figwheel" "$SCRIPT_DIR/start.sh" figwheel ;;
            garden)    run_in_background "garden" "$SCRIPT_DIR/start.sh" garden ;;
            *)         log_error "Cannot run '$target' in background"; exit $EXIT_USAGE ;;
        esac
        exit $EXIT_SUCCESS
    fi

    # Direct execution (foreground)
    case "$target" in
        all|"")    start_all "$idempotent" ;;
        datomic)   start_datomic "$idempotent" ;;
        server)    start_server "$idempotent" ;;
        figwheel)  start_figwheel "$idempotent" ;;
        garden)    start_garden ;;
        init-db)   init_database ;;
        *)         log_error "Unknown target: $target"; show_help; exit $EXIT_USAGE ;;
    esac
}

main "$@"
