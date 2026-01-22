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
#   ./stop.sh repl                Stop nREPL only
#   ./stop.sh server              Stop server only
#   ./stop.sh datomic             Stop Datomic only
#   ./stop.sh port <port>         Stop process on specific port
#   ./stop.sh name <pattern>      Stop processes matching pattern
# =============================================================================

# --- Port Configuration ---
# TODO: Consider moving to scripts/common.sh if start.sh needs these too
NREPL_PORT="${NREPL_PORT:-7888}"
SERVER_PORT="${SERVER_PORT:-8890}"
DATOMIC_PORT="${DATOMIC_PORT:-4334}"

# --- Colors ---
# TODO: Consider moving to scripts/common.sh
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# -----------------------------------------------------------------------------
# Helper Functions
# TODO: Consider moving to scripts/common.sh if reused elsewhere
# -----------------------------------------------------------------------------

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Find PIDs listening on a port (lsof -> ss -> netstat fallback)
find_pids_by_port() {
    local port="$1"
    local pids=""

    if command -v lsof >/dev/null 2>&1; then
        pids=$(lsof -t -i ":${port}" 2>/dev/null || true)
    elif command -v ss >/dev/null 2>&1; then
        pids=$(ss -tlnp 2>/dev/null | grep ":${port}" | grep -oP 'pid=\K[0-9]+' || true)
    elif command -v netstat >/dev/null 2>&1; then
        pids=$(netstat -tlnp 2>/dev/null | grep ":${port}" | awk '{print $7}' | cut -d'/' -f1 | grep -E '^[0-9]+$' || true)
    fi

    echo "$pids" | tr '\n' ' ' | xargs
}

# Find PIDs by process name pattern (pgrep -> ps fallback)
find_pids_by_name() {
    local pattern="$1"
    local pids=""

    if command -v pgrep >/dev/null 2>&1; then
        pids=$(pgrep -f "$pattern" 2>/dev/null || true)
    else
        pids=$(ps aux 2>/dev/null | grep -E "$pattern" | grep -v grep | awk '{print $2}' || true)
    fi

    # Filter out our own PID
    local self_pid=$$
    local filtered=""
    for pid in $pids; do
        [[ "$pid" != "$self_pid" ]] && filtered="$filtered $pid"
    done

    echo "$filtered" | xargs
}

# Get process info for display
get_process_info() {
    local pid="$1"
    [[ -z "$pid" ]] && return
    ps -p "$pid" -o pid=,user=,args= 2>/dev/null | head -c 80 || echo "$pid (info unavailable)"
}

# Get process uptime
get_uptime() {
    local pid="$1"
    [[ -z "$pid" ]] && echo "-" && return
    local etime
    etime=$(ps -p "$pid" -o etime= 2>/dev/null | xargs || true)
    echo "${etime:-unknown}"
}

# -----------------------------------------------------------------------------
# Status Display (--dry-run)
# -----------------------------------------------------------------------------

show_status() {
    echo ""
    echo -e "${BOLD}OrcPub Service Status${NC}"
    echo "───────────────────────────────────────────────────────────────"
    printf "%-16s %-8s %-10s %-10s %s\n" "Service" "Port" "Status" "PID" "Uptime"
    echo "───────────────────────────────────────────────────────────────"

    local services=("Datomic:$DATOMIC_PORT:datomic.*transactor"
                    "Server:$SERVER_PORT:lein run"
                    "nREPL:$NREPL_PORT:nrepl")

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

    if [[ -z "$pids" ]]; then
        log_warn "No processes found for: $description"
        return 1
    fi

    echo ""
    echo "Found processes to stop ($description):"
    echo "────────────────────────────────────────"
    for pid in $pids; do
        echo "  $(get_process_info "$pid")"
    done
    echo "────────────────────────────────────────"

    [[ "$skip_confirm" == "true" ]] && return 0

    read -p "Stop these processes? [y/N] " -n 1 -r
    echo
    [[ $REPLY =~ ^[Yy]$ ]] && return 0
    log_info "Aborted."
    return 1
}

kill_pids() {
    local pids="$1"
    local use_force="${2:-false}"
    local wait_time=3

    [[ -z "$pids" ]] && return 0

    log_info "Sending SIGTERM to PIDs: $pids"
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
            log_warn "Processes still running, sending SIGKILL: $remaining"
            for pid in $remaining; do
                kill -KILL "$pid" 2>/dev/null || true
            done
            sleep 1
        else
            log_warn "Some processes still running: $remaining"
            log_info "Use --force to send SIGKILL"
            return 1
        fi
    fi

    log_info "All processes terminated successfully"
}

# -----------------------------------------------------------------------------
# Stop Targets
# -----------------------------------------------------------------------------

stop_repl() {
    local skip="$1" force="$2"
    local pids
    pids=$(echo "$(find_pids_by_port "$NREPL_PORT") $(find_pids_by_name 'nrepl\|lein.*repl')" | tr ' ' '\n' | sort -u | xargs)
    confirm_kill "$pids" "nREPL (port $NREPL_PORT)" "$skip" && kill_pids "$pids" "$force"
}

stop_server() {
    local skip="$1" force="$2"
    local pids
    pids=$(echo "$(find_pids_by_port "$SERVER_PORT") $(find_pids_by_name 'lein run')" | tr ' ' '\n' | sort -u | xargs)
    confirm_kill "$pids" "Server (port $SERVER_PORT)" "$skip" && kill_pids "$pids" "$force"
}

stop_datomic() {
    local skip="$1" force="$2"
    local pids
    pids=$(echo "$(find_pids_by_port "$DATOMIC_PORT") $(find_pids_by_name 'datomic.*transactor')" | tr ' ' '\n' | sort -u | xargs)
    confirm_kill "$pids" "Datomic (port $DATOMIC_PORT)" "$skip" && kill_pids "$pids" "$force"
}

stop_port() {
    local port="$1" skip="$2" force="$3"
    [[ -z "$port" ]] && { log_error "Usage: $0 port <port>"; exit 1; }
    [[ ! "$port" =~ ^[0-9]+$ ]] && { log_error "Invalid port: $port"; exit 1; }
    local pids
    pids=$(find_pids_by_port "$port")
    confirm_kill "$pids" "port $port" "$skip" && kill_pids "$pids" "$force"
}

stop_name() {
    local pattern="$1" skip="$2" force="$3"
    [[ -z "$pattern" ]] && { log_error "Usage: $0 name <pattern>"; exit 1; }
    local pids
    pids=$(find_pids_by_name "$pattern")
    confirm_kill "$pids" "pattern '$pattern'" "$skip" && kill_pids "$pids" "$force"
}

stop_all() {
    local skip="$1" force="$2"
    local pids
    pids=$(echo "$(find_pids_by_port "$NREPL_PORT") $(find_pids_by_port "$SERVER_PORT") $(find_pids_by_port "$DATOMIC_PORT") $(find_pids_by_name 'lein run\|lein.*repl\|nrepl\|datomic.*transactor')" | tr ' ' '\n' | sort -u | xargs)
    confirm_kill "$pids" "all OrcPub services" "$skip" && kill_pids "$pids" "$force"
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
  port <port>     Stop process on specific port
  name <pattern>  Stop processes matching pattern

Options:
  --dry-run       Show status without stopping anything
  --yes, -y       Skip confirmation prompt
  --force, -f     Use SIGKILL if SIGTERM doesn't stop the process
  --help, -h      Show this help

Examples:
  ./stop.sh                    # Stop all (interactive)
  ./stop.sh --dry-run          # Show what's running
  ./stop.sh --yes              # Stop all without prompting
  ./stop.sh repl --yes         # Stop nREPL only
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
    local positional=()

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dry-run|--status) dry_run="true"; shift ;;
            --yes|-y)           skip_confirm="true"; shift ;;
            --force|-f)         use_force="true"; shift ;;
            --help|-h)          show_help; exit 0 ;;
            -*)                 log_error "Unknown option: $1"; show_help; exit 1 ;;
            *)                  positional+=("$1"); shift ;;
        esac
    done

    [[ "$dry_run" == "true" ]] && { show_status; exit 0; }

    target="${positional[0]:-all}"

    case "$target" in
        all)     stop_all "$skip_confirm" "$use_force" ;;
        repl)    stop_repl "$skip_confirm" "$use_force" ;;
        server)  stop_server "$skip_confirm" "$use_force" ;;
        datomic) stop_datomic "$skip_confirm" "$use_force" ;;
        port)    stop_port "${positional[1]:-}" "$skip_confirm" "$use_force" ;;
        name)    stop_name "${positional[1]:-}" "$skip_confirm" "$use_force" ;;
        *)       log_error "Unknown target: $target"; show_help; exit 1 ;;
    esac
}

main "$@"
