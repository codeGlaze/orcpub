#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# start.sh - OrcPub Development Server Launcher with Process Management
# =============================================================================
# Usage:
#   ./start.sh                     - Start Datomic, server, and REPL
#   ./start.sh kill-repl           - Kill nREPL processes
#   ./start.sh kill-server         - Kill OrcPub server processes
#   ./start.sh kill-port <port>    - Kill process on specific port
#   ./start.sh kill-name <pattern> - Kill processes matching pattern
#   ./start.sh kill-all            - Kill all OrcPub-related processes
#
# Flags:
#   --yes    Skip confirmation prompt
#   --force  Use SIGKILL if SIGTERM doesn't stop the process
# =============================================================================

# Default ports used by OrcPub
NREPL_PORT="${NREPL_PORT:-7888}"
SERVER_PORT="${SERVER_PORT:-8890}"
DATOMIC_PORT="${DATOMIC_PORT:-4334}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# -----------------------------------------------------------------------------
# Helper Functions
# -----------------------------------------------------------------------------

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Find PIDs listening on a given port using lsof with fallback to ss/netstat
find_pids_by_port() {
    local port="$1"
    local pids=""

    if command -v lsof >/dev/null 2>&1; then
        pids=$(lsof -t -i ":${port}" 2>/dev/null || true)
    elif command -v ss >/dev/null 2>&1; then
        # ss doesn't give PIDs directly without root, try netstat
        pids=$(ss -tlnp 2>/dev/null | grep ":${port}" | grep -oP 'pid=\K[0-9]+' || true)
    elif command -v netstat >/dev/null 2>&1; then
        pids=$(netstat -tlnp 2>/dev/null | grep ":${port}" | awk '{print $7}' | cut -d'/' -f1 | grep -E '^[0-9]+$' || true)
    fi

    echo "$pids" | tr '\n' ' ' | xargs
}

# Find PIDs matching a process name pattern using pgrep with fallback to ps
find_pids_by_name() {
    local pattern="$1"
    local pids=""

    if command -v pgrep >/dev/null 2>&1; then
        pids=$(pgrep -f "$pattern" 2>/dev/null || true)
    else
        pids=$(ps aux 2>/dev/null | grep -E "$pattern" | grep -v grep | awk '{print $2}' || true)
    fi

    # Filter out our own script's PID
    local self_pid=$$
    local filtered=""
    for pid in $pids; do
        if [[ "$pid" != "$self_pid" ]]; then
            filtered="$filtered $pid"
        fi
    done

    echo "$filtered" | xargs
}

# Get process info for display
get_process_info() {
    local pid="$1"
    if [[ -z "$pid" ]]; then
        return
    fi
    ps -p "$pid" -o pid=,user=,args= 2>/dev/null || echo "$pid (process info unavailable)"
}

# Ask for confirmation before killing
confirm_kill() {
    local pids="$1"
    local description="$2"
    local skip_confirm="${3:-false}"

    if [[ -z "$pids" ]]; then
        log_warn "No processes found for: $description"
        return 1
    fi

    echo ""
    echo "Found processes to kill ($description):"
    echo "----------------------------------------"
    for pid in $pids; do
        echo "  $(get_process_info "$pid")"
    done
    echo "----------------------------------------"

    if [[ "$skip_confirm" == "true" ]]; then
        return 0
    fi

    read -p "Kill these processes? [y/N] " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        return 0
    else
        log_info "Aborted."
        return 1
    fi
}

# Kill processes with TERM, optionally escalate to KILL
kill_pids() {
    local pids="$1"
    local use_force="${2:-false}"
    local wait_time=3

    if [[ -z "$pids" ]]; then
        return 0
    fi

    log_info "Sending SIGTERM to PIDs: $pids"
    for pid in $pids; do
        kill -TERM "$pid" 2>/dev/null || true
    done

    # Wait and check if processes stopped
    sleep "$wait_time"

    local remaining=""
    for pid in $pids; do
        if kill -0 "$pid" 2>/dev/null; then
            remaining="$remaining $pid"
        fi
    done
    remaining=$(echo "$remaining" | xargs)

    if [[ -n "$remaining" ]]; then
        if [[ "$use_force" == "true" ]]; then
            log_warn "Processes still running, sending SIGKILL: $remaining"
            for pid in $remaining; do
                kill -KILL "$pid" 2>/dev/null || true
            done
            sleep 1

            # Final check
            local still_running=""
            for pid in $remaining; do
                if kill -0 "$pid" 2>/dev/null; then
                    still_running="$still_running $pid"
                fi
            done

            if [[ -n "$(echo "$still_running" | xargs)" ]]; then
                log_error "Failed to kill some processes: $still_running"
                return 1
            fi
        else
            log_warn "Some processes still running: $remaining"
            log_info "Use --force to send SIGKILL"
            return 1
        fi
    fi

    log_info "All processes terminated successfully"
    return 0
}

# -----------------------------------------------------------------------------
# Kill Subcommands
# -----------------------------------------------------------------------------

do_kill_repl() {
    local skip_confirm="$1"
    local use_force="$2"

    # Find nREPL processes by port and name
    local port_pids=$(find_pids_by_port "$NREPL_PORT")
    local name_pids=$(find_pids_by_name "nrepl\|lein.*repl")

    # Combine and deduplicate
    local all_pids=$(echo "$port_pids $name_pids" | tr ' ' '\n' | sort -u | xargs)

    if confirm_kill "$all_pids" "nREPL (port $NREPL_PORT)" "$skip_confirm"; then
        kill_pids "$all_pids" "$use_force"
    fi
}

do_kill_server() {
    local skip_confirm="$1"
    local use_force="$2"

    # Find server processes by port and name
    local port_pids=$(find_pids_by_port "$SERVER_PORT")
    local name_pids=$(find_pids_by_name "lein run\|orcpub.*server")

    # Combine and deduplicate
    local all_pids=$(echo "$port_pids $name_pids" | tr ' ' '\n' | sort -u | xargs)

    if confirm_kill "$all_pids" "OrcPub server (port $SERVER_PORT)" "$skip_confirm"; then
        kill_pids "$all_pids" "$use_force"
    fi
}

do_kill_datomic() {
    local skip_confirm="$1"
    local use_force="$2"

    # Find Datomic processes by port and name
    local port_pids=$(find_pids_by_port "$DATOMIC_PORT")
    local name_pids=$(find_pids_by_name "datomic.*transactor")

    # Combine and deduplicate
    local all_pids=$(echo "$port_pids $name_pids" | tr ' ' '\n' | sort -u | xargs)

    if confirm_kill "$all_pids" "Datomic transactor (port $DATOMIC_PORT)" "$skip_confirm"; then
        kill_pids "$all_pids" "$use_force"
    fi
}

do_kill_port() {
    local port="$1"
    local skip_confirm="$2"
    local use_force="$3"

    if [[ -z "$port" ]]; then
        log_error "Usage: $0 kill-port <port> [--yes] [--force]"
        exit 1
    fi

    if ! [[ "$port" =~ ^[0-9]+$ ]]; then
        log_error "Invalid port number: $port"
        exit 1
    fi

    local pids=$(find_pids_by_port "$port")

    if confirm_kill "$pids" "processes on port $port" "$skip_confirm"; then
        kill_pids "$pids" "$use_force"
    fi
}

do_kill_name() {
    local pattern="$1"
    local skip_confirm="$2"
    local use_force="$3"

    if [[ -z "$pattern" ]]; then
        log_error "Usage: $0 kill-name <pattern> [--yes] [--force]"
        exit 1
    fi

    local pids=$(find_pids_by_name "$pattern")

    if confirm_kill "$pids" "processes matching '$pattern'" "$skip_confirm"; then
        kill_pids "$pids" "$use_force"
    fi
}

do_kill_all() {
    local skip_confirm="$1"
    local use_force="$2"

    # Collect all OrcPub-related processes
    local repl_pids=$(find_pids_by_port "$NREPL_PORT")
    local server_pids=$(find_pids_by_port "$SERVER_PORT")
    local datomic_pids=$(find_pids_by_port "$DATOMIC_PORT")
    local name_pids=$(find_pids_by_name "lein run\|lein.*repl\|nrepl\|datomic.*transactor\|orcpub")

    # Combine and deduplicate
    local all_pids=$(echo "$repl_pids $server_pids $datomic_pids $name_pids" | tr ' ' '\n' | sort -u | xargs)

    if confirm_kill "$all_pids" "all OrcPub processes" "$skip_confirm"; then
        kill_pids "$all_pids" "$use_force"
    fi
}

# -----------------------------------------------------------------------------
# Main Start Function
# -----------------------------------------------------------------------------

do_start() {
    # Ensure Java 8 and Leiningen are installed (devcontainer should handle this)
    if ! java -version 2>&1 | grep -q '1.8'; then
        log_error "Java 8 is required. Please use the devcontainer or install Java 8."
        exit 1
    fi
    if ! command -v lein >/dev/null 2>&1; then
        log_error "Leiningen not found. Please use the devcontainer or install leiningen."
        exit 1
    fi

    # Prepare Datomic properties if missing
    if [ ! -f lib/datomic-free-0.9.5703/config/working-transactor.properties ]; then
        cp lib/datomic-free-0.9.5703/config/samples/free-transactor-template.properties lib/datomic-free-0.9.5703/config/working-transactor.properties
        log_info "Copied Datomic properties template to working-transactor.properties"
    fi

    # Start Datomic transactor
    lib/datomic-free-0.9.5703/bin/transactor lib/datomic-free-0.9.5703/config/working-transactor.properties &
    DATOMIC_PID=$!
    log_info "Started Datomic transactor (PID $DATOMIC_PID)"

    # Wait a moment for Datomic to start
    sleep 3

    # Start the Clojure server
    lein run &
    SERVER_PID=$!
    log_info "Started Clojure server (PID $SERVER_PID)"

    # Optionally, start a REPL
    lein repl
}

# -----------------------------------------------------------------------------
# Argument Parsing and Main Entry Point
# -----------------------------------------------------------------------------

show_help() {
    cat << 'EOF'
OrcPub Development Server Launcher

Usage:
  ./start.sh                     Start Datomic, server, and REPL
  ./start.sh kill-repl           Kill nREPL processes
  ./start.sh kill-server         Kill OrcPub server processes
  ./start.sh kill-datomic        Kill Datomic transactor
  ./start.sh kill-port <port>    Kill process on specific port
  ./start.sh kill-name <pattern> Kill processes matching pattern
  ./start.sh kill-all            Kill all OrcPub-related processes
  ./start.sh help                Show this help message

Options:
  --yes    Skip confirmation prompt
  --force  Use SIGKILL if SIGTERM doesn't stop the process

Examples:
  ./start.sh kill-repl                    # Interactive prompt before kill
  ./start.sh kill-repl --yes              # No prompt, use SIGTERM
  ./start.sh kill-repl --yes --force      # No prompt, escalate to SIGKILL
  ./start.sh kill-port 7888 --yes
  ./start.sh kill-name "lein run" --yes
  ./start.sh kill-all --yes --force

Environment Variables:
  NREPL_PORT    nREPL port (default: 7888)
  SERVER_PORT   Server port (default: 8890)
  DATOMIC_PORT  Datomic port (default: 4334)
EOF
}

main() {
    local command="${1:-start}"
    shift || true

    # Parse flags
    local skip_confirm="false"
    local use_force="false"
    local positional_args=()

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --yes|-y)
                skip_confirm="true"
                shift
                ;;
            --force|-f)
                use_force="true"
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            -*)
                log_error "Unknown option: $1"
                show_help
                exit 1
                ;;
            *)
                positional_args+=("$1")
                shift
                ;;
        esac
    done

    case "$command" in
        start)
            do_start
            ;;
        kill-repl|kill_repl)
            do_kill_repl "$skip_confirm" "$use_force"
            ;;
        kill-server|kill_server)
            do_kill_server "$skip_confirm" "$use_force"
            ;;
        kill-datomic|kill_datomic)
            do_kill_datomic "$skip_confirm" "$use_force"
            ;;
        kill-port|kill_port)
            do_kill_port "${positional_args[0]:-}" "$skip_confirm" "$use_force"
            ;;
        kill-name|kill_name)
            do_kill_name "${positional_args[0]:-}" "$skip_confirm" "$use_force"
            ;;
        kill-all|kill_all)
            do_kill_all "$skip_confirm" "$use_force"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "Unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

main "$@"
