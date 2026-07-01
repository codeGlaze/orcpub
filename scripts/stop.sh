#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# stop.sh - OrcPub Process Management (Stop/Kill/Status)
# =============================================================================
# Usage:
#   ./stop.sh                     Stop all OrcPub services (with confirmation)
#   ./stop.sh --dry-run           Show status without stopping anything
#   ./stop.sh --yes               Stop without confirmation
#   ./stop.sh --force             Use SIGKILL if SIGTERM doesn't work
#   ./stop.sh --quiet             Minimal output (for scripting)
#   ./stop.sh repl                Stop nREPL only
#   ./stop.sh server              Stop server only
#   ./stop.sh datomic             Stop Datomic only
#   ./stop.sh figwheel            Stop Figwheel only
#   ./stop.sh port <port>         Stop process on specific port
#   ./stop.sh name <pattern>      Stop processes matching pattern
#
# Exit Codes:
#   0 - Success (processes stopped or none found)
#   1 - Usage error (invalid args)
#   3 - Runtime failure (failed to stop processes)
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source shared utilities
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# -----------------------------------------------------------------------------
# Status Display (--dry-run)
# -----------------------------------------------------------------------------

show_status() {
    local quiet="${1:-false}"

    [[ "$quiet" == "true" ]] && {
        # Quiet mode: just exit codes
        local running=0
        for port in "$DATOMIC_PORT" "$SERVER_PORT" "$NREPL_PORT"; do
            port_in_use "$port" && ((running++))
        done
        echo "$running"
        return
    }

    echo ""
    echo -e "${BOLD}OrcPub Service Status${NC}"
    echo "───────────────────────────────────────────────────────────────"
    printf "%-16s %-8s %-10s %-10s %s\n" "Service" "Port" "Status" "PID" "Uptime"
    echo "───────────────────────────────────────────────────────────────"

    local services=(
        "Datomic:$DATOMIC_PORT:datomic.*transactor"
        "Server:$SERVER_PORT:lein.*start-server"
        "nREPL:$NREPL_PORT:nrepl"
        "Figwheel:$FIGWHEEL_PORT:figwheel"
        "Garden:$GARDEN_PORT:garden"
    )

    for entry in "${services[@]}"; do
        IFS=':' read -r name port pattern <<< "$entry"
        local pid
        pid=$(find_pids_by_port "$port" | awk '{print $1}')

        if [[ -n "$pid" ]]; then
            local uptime
            uptime=$(get_uptime "$pid")
            printf "%-16s %-8s ${GREEN}%-10s${NC} %-10s %s\n" "$name" "$port" "running" "$pid" "$uptime"
        else
            printf "%-16s %-8s ${YELLOW}%-10s${NC} %-10s %s\n" "$name" "$port" "stopped" "-" "-"
        fi
    done

    echo "───────────────────────────────────────────────────────────────"
    echo ""
}

# -----------------------------------------------------------------------------
# Kill Functions
# -----------------------------------------------------------------------------

confirm_kill() {
    local pids="$1"
    local description="$2"
    local skip_confirm="${3:-false}"
    local quiet="${4:-false}"

    if [[ -z "$pids" ]]; then
        [[ "$quiet" != "true" ]] && log_warn "No processes found for: $description"
        return 1
    fi

    if [[ "$quiet" != "true" ]]; then
        echo ""
        echo "Found processes to stop ($description):"
        echo "────────────────────────────────────────"
        for pid in $pids; do
            echo "  $(get_process_info "$pid")"
        done
        echo "────────────────────────────────────────"
    fi

    [[ "$skip_confirm" == "true" ]] && return 0

    # Non-interactive protection: fail fast instead of hanging on read
    if ! is_interactive; then
        log_error "Cannot prompt for confirmation (non-interactive mode)"
        log_error "Use --yes to skip confirmation in scripts/CI"
        return 1
    fi

    if ! read -t 30 -p "Stop these processes? [y/N] " -n 1 -r; then
        echo
        log_error "Prompt timed out after 30 seconds"
        return 1
    fi
    echo
    [[ $REPLY =~ ^[Yy]$ ]] && return 0
    [[ "$quiet" != "true" ]] && log_info "Aborted."
    return 1
}

kill_pids() {
    local pids="$1"
    local use_force="${2:-false}"
    local quiet="${3:-false}"
    local wait_time=3

    [[ -z "$pids" ]] && return 0

    [[ "$quiet" != "true" ]] && log_info "Sending SIGTERM to PIDs: $pids"
    for pid in $pids; do
        kill -TERM "$pid" 2>/dev/null || true
    done

    sleep "$wait_time"

    # Check for survivors
    local remaining=""
    for pid in $pids; do
        kill -0 "$pid" 2>/dev/null && remaining="$remaining $pid"
    done
    remaining=$(echo "$remaining" | xargs)

    if [[ -n "$remaining" ]]; then
        if [[ "$use_force" == "true" ]]; then
            [[ "$quiet" != "true" ]] && log_warn "Processes still running, sending SIGKILL: $remaining"
            for pid in $remaining; do
                kill -KILL "$pid" 2>/dev/null || true
            done
            sleep 1
        else
            [[ "$quiet" != "true" ]] && log_warn "Some processes still running: $remaining"
            [[ "$quiet" != "true" ]] && log_info "Use --force to send SIGKILL"
            return 1
        fi
    fi

    [[ "$quiet" != "true" ]] && log_info "All processes terminated successfully"
}

# -----------------------------------------------------------------------------
# Stop Targets (PID-first, port-fallback approach)
# -----------------------------------------------------------------------------

stop_repl() {
    local skip="$1" force="$2" quiet="$3"
    local pids result=0
    # Use PID-first lookup from common.sh
    pids=$(find_service_pids "nrepl" "$NREPL_PORT" 'nrepl')
    if confirm_kill "$pids" "nREPL (port $NREPL_PORT)" "$skip" "$quiet"; then
        kill_pids "$pids" "$force" "$quiet" || result=$?
        # Clean up PID file after stopping
        rm -f "$LOG_DIR/nrepl.pid" 2>/dev/null || true
    fi
    return $result
}

stop_server() {
    local skip="$1" force="$2" quiet="$3"
    local pids result=0
    pids=$(find_service_pids "server" "$SERVER_PORT" 'lein.*start-server')
    if confirm_kill "$pids" "Server (port $SERVER_PORT)" "$skip" "$quiet"; then
        kill_pids "$pids" "$force" "$quiet" || result=$?
        rm -f "$LOG_DIR/server.pid" 2>/dev/null || true
    fi
    return $result
}

stop_datomic() {
    local skip="$1" force="$2" quiet="$3"
    local pids result=0
    pids=$(find_service_pids "datomic" "$DATOMIC_PORT" 'datomic.*transactor')
    if confirm_kill "$pids" "Datomic (port $DATOMIC_PORT)" "$skip" "$quiet"; then
        kill_pids "$pids" "$force" "$quiet" || result=$?
        rm -f "$LOG_DIR/datomic.pid" 2>/dev/null || true
    fi
    return $result
}

stop_figwheel() {
    local skip="$1" force="$2" quiet="$3"
    local pids result=0
    pids=$(find_service_pids "figwheel" "$FIGWHEEL_PORT" 'figwheel')
    if confirm_kill "$pids" "Figwheel (port $FIGWHEEL_PORT)" "$skip" "$quiet"; then
        kill_pids "$pids" "$force" "$quiet" || result=$?
        rm -f "$LOG_DIR/figwheel.pid" 2>/dev/null || true
    fi
    return $result
}

stop_garden() {
    local skip="$1" force="$2" quiet="$3"
    local pids result=0
    pids=$(find_service_pids "garden" "$GARDEN_PORT" 'garden.*auto')
    if confirm_kill "$pids" "Garden" "$skip" "$quiet"; then
        kill_pids "$pids" "$force" "$quiet" || result=$?
        rm -f "$LOG_DIR/garden.pid" 2>/dev/null || true
    fi
    return $result
}

stop_port() {
    local port="$1" skip="$2" force="$3" quiet="$4"
    [[ -z "$port" ]] && { log_error "Usage: $0 port <port>"; exit $EXIT_USAGE; }
    [[ ! "$port" =~ ^[0-9]+$ ]] && { log_error "Invalid port: $port"; exit $EXIT_USAGE; }
    local pids
    pids=$(find_pids_by_port "$port")
    confirm_kill "$pids" "port $port" "$skip" "$quiet" && kill_pids "$pids" "$force" "$quiet"
}

stop_name() {
    local pattern="$1" skip="$2" force="$3" quiet="$4"
    [[ -z "$pattern" ]] && { log_error "Usage: $0 name <pattern>"; exit $EXIT_USAGE; }

    # Warn about very broad patterns (security/safety measure)
    if [[ ${#pattern} -lt 4 ]]; then
        log_warn "Pattern '$pattern' is very broad (${#pattern} chars)"
        if ! is_interactive; then
            log_error "Refusing to use broad pattern in non-interactive mode"
            exit $EXIT_USAGE
        fi
        if ! read -t 30 -p "This may match many processes. Continue? [y/N] " -n 1 -r; then
            echo
            log_error "Prompt timed out after 30 seconds"
            exit $EXIT_RUNTIME
        fi
        echo
        [[ ! $REPLY =~ ^[Yy]$ ]] && { log_info "Aborted."; exit $EXIT_SUCCESS; }
    fi

    local pids
    pids=$(find_pids_by_name "$pattern")
    confirm_kill "$pids" "pattern '$pattern'" "$skip" "$quiet" && kill_pids "$pids" "$force" "$quiet"
}

stop_all() {
    local skip="$1" force="$2" quiet="$3"
    local pids="" result=0

    # Collect PIDs from all services using PID-first approach
    for service in datomic server nrepl figwheel garden; do
        local service_pids=""
        case "$service" in
            datomic)  service_pids=$(find_service_pids "datomic" "$DATOMIC_PORT" 'datomic.*transactor') ;;
            server)   service_pids=$(find_service_pids "server" "$SERVER_PORT" 'lein.*start-server') ;;
            nrepl)    service_pids=$(find_service_pids "nrepl" "$NREPL_PORT" 'nrepl') ;;
            figwheel) service_pids=$(find_service_pids "figwheel" "$FIGWHEEL_PORT" 'figwheel') ;;
            garden)   service_pids=$(find_service_pids "garden" "$GARDEN_PORT" 'garden.*auto') ;;
        esac
        [[ -n "$service_pids" ]] && pids="$pids $service_pids"
    done

    pids=$(echo "$pids" | tr ' ' '\n' | sort -u | xargs)

    if confirm_kill "$pids" "all OrcPub services" "$skip" "$quiet"; then
        kill_pids "$pids" "$force" "$quiet" || result=$?
        # Clean up all PID files
        rm -f "$LOG_DIR"/*.pid 2>/dev/null || true
    fi
    return $result
}

# -----------------------------------------------------------------------------
# Help
# -----------------------------------------------------------------------------

show_help() {
    cat << 'EOF'
OrcPub Process Management

Usage:
  ./stop.sh [target] [options]

Targets:
  (none)          Stop all OrcPub services
  repl            Stop nREPL processes
  server          Stop OrcPub server
  datomic         Stop Datomic transactor
  figwheel        Stop Figwheel
  garden          Stop Garden CSS watcher
  port <port>     Stop process on specific port
  name <pattern>  Stop processes matching pattern

Options:
  --dry-run       Show status without stopping anything
  --yes, -y       Skip confirmation prompt
  --force, -f     Use SIGKILL if SIGTERM doesn't stop the process
  --quiet, -q     Minimal output (for scripting)
  --help, -h      Show this help

Examples:
  ./stop.sh                    # Stop all (interactive)
  ./stop.sh --dry-run          # Show what's running
  ./stop.sh --yes              # Stop all without prompting
  ./stop.sh repl --yes         # Stop nREPL only
  ./stop.sh figwheel --yes     # Stop Figwheel only
  ./stop.sh port 8890 --force  # Force kill port 8890
EOF
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

main() {
    local target=""
    local skip_confirm="false"
    local use_force="false"
    local dry_run="false"
    local quiet="false"
    local positional=()

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dry-run|--status) dry_run="true"; shift ;;
            --yes|-y)           skip_confirm="true"; shift ;;
            --force|-f)         use_force="true"; shift ;;
            --quiet|-q)         quiet="true"; QUIET="true"; export QUIET; skip_confirm="true"; shift ;;
            --help|-h)          show_help; exit $EXIT_SUCCESS ;;
            -*)                 log_error "Unknown option: $1"; show_help; exit $EXIT_USAGE ;;
            *)                  positional+=("$1"); shift ;;
        esac
    done

    [[ "$dry_run" == "true" ]] && { show_status "$quiet"; exit $EXIT_SUCCESS; }

    target="${positional[0]:-all}"

    case "$target" in
        all)      stop_all "$skip_confirm" "$use_force" "$quiet" ;;
        repl)     stop_repl "$skip_confirm" "$use_force" "$quiet" ;;
        server)   stop_server "$skip_confirm" "$use_force" "$quiet" ;;
        datomic)  stop_datomic "$skip_confirm" "$use_force" "$quiet" ;;
        figwheel) stop_figwheel "$skip_confirm" "$use_force" "$quiet" ;;
        garden)   stop_garden "$skip_confirm" "$use_force" "$quiet" ;;
        port)     stop_port "${positional[1]:-}" "$skip_confirm" "$use_force" "$quiet" ;;
        name)     stop_name "${positional[1]:-}" "$skip_confirm" "$use_force" "$quiet" ;;
        *)        log_error "Unknown target: $target"; show_help; exit $EXIT_USAGE ;;
    esac
}

main "$@"
