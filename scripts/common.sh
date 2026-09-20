#!/usr/bin/env bash
# =============================================================================
# common.sh - Shared utilities for OrcPub external scripts
# =============================================================================
# Source this file in start.sh, stop.sh, and menu:
#   source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
# =============================================================================

# Prevent double-sourcing
[[ -n "${_ORCPUB_COMMON_LOADED:-}" ]] && return
_ORCPUB_COMMON_LOADED=1

# -----------------------------------------------------------------------------
# Path Setup
# -----------------------------------------------------------------------------

# SCRIPT_DIR should be set by the sourcing script, but provide fallback
COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "$COMMON_DIR/.." && pwd)}"

# -----------------------------------------------------------------------------
# Environment Configuration
# -----------------------------------------------------------------------------

# Source .env if present (authoritative config)
# tr -d '\r' strips Windows line endings so values don't silently include \r
if [[ -f "$REPO_ROOT/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    . <(tr -d '\r' < "$REPO_ROOT/.env")
    set +a
fi

# Show the configuration this run will actually use, and where it came from.
#
# A launcher is the one moment the operator is present and paying attention, so
# it is the right place to surface configuration rather than let it be
# discovered through a symptom. Two settings in particular fail silently:
# CSP_POLICY/DEV_MODE (blocks Figwheel's websocket with no visible cause) and
# the ports (a busy port used to be reported as free on Windows).
#
# Respects QUIET via log_info/log_warn. Never blocks a non-interactive run.
report_env_config() {
    local env_file="$REPO_ROOT/.env"
    local example="$REPO_ROOT/.env.example"

    if [[ -f "$env_file" ]]; then
        log_info "Config: .env  (edit it to change any of the below)"
    else
        log_info "Config: built-in defaults — no .env  (see .env.example)"
    fi

    log_info "  ports    server=$SERVER_PORT datomic=$DATOMIC_PORT figwheel=$FIGWHEEL_PORT nrepl=$NREPL_PORT"

    local policy="${CSP_POLICY:-strict}"
    local dev="${DEV_MODE:-}"
    local dev_on=false
    case "$(printf '%s' "$dev" | tr '[:upper:]' '[:lower:]')" in true) dev_on=true ;; esac

    if [[ "$policy" != "strict" || "$dev_on" == "true" ]]; then
        log_info "  csp      policy=$policy DEV_MODE=${dev:-<unset>}"
    elif [[ -n "$dev" ]]; then
        # Explicitly set to something other than true: a decision, not an
        # accident. State the consequence once, without the lecture.
        log_info "  csp      strict, ENFORCING (DEV_MODE=$dev) — Figwheel hot reload will be blocked"
    else
        # Unset: nobody chose this, and the symptom is a dead hot-reload socket
        # with no visible cause. This is the case worth interrupting for.
        log_warn "  csp      strict and ENFORCING (DEV_MODE unset, which means false)"
        log_warn "           ws://localhost:$FIGWHEEL_PORT is not in connect-src, so Figwheel"
        log_warn "           hot reload will be blocked. Set DEV_MODE=true in .env to develop."
    fi

    # First run: offer to start a .env from the example. Only when there is
    # nothing to lose -- no .env present -- and only with someone at the
    # keyboard to answer. A non-interactive run is told where to look instead.
    if [[ ! -f "$env_file" && -f "$example" ]]; then
        if is_interactive; then
            local reply=""
            if read -t 30 -p "Create .env from .env.example now? [y/N] " -n 1 -r reply; then
                echo
                if [[ "$reply" =~ ^[Yy]$ ]]; then
                    if cp "$example" "$env_file"; then
                        log_info "Wrote $env_file — review it (SIGNATURE especially) before going further."
                        log_info "Re-run this script to pick it up; this run continues on defaults."
                    else
                        log_error "Could not write $env_file"
                    fi
                fi
            else
                echo
                # read returns non-zero for both a 30s timeout and EOF, and
                # they are not distinguishable here -- so do not claim either.
                log_info "No answer — continuing on defaults."
            fi
        else
            log_info "  To configure: cp .env.example .env"
        fi
    fi
}

# Defaults (used if not set in .env)
DATOMIC_VERSION="${DATOMIC_VERSION:-1.0.7482}"
DATOMIC_TYPE="${DATOMIC_TYPE:-pro}"
JAVA_MIN_VERSION="${JAVA_MIN_VERSION:-11}"
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs}"

# Port configuration
DATOMIC_PORT="${DATOMIC_PORT:-4334}"
SERVER_PORT="${SERVER_PORT:-8890}"
NREPL_PORT="${NREPL_PORT:-7888}"
FIGWHEEL_PORT="${FIGWHEEL_PORT:-3449}"
GARDEN_PORT="${GARDEN_PORT:-3000}"

# Figwheel WebSocket connect URL override (for remote dev environments).
# Auto-detected for GitHub Codespaces; set explicitly for other remote setups.
# Leave empty for local development (uses Figwheel default: ws://localhost:PORT).
FIGWHEEL_CONNECT_URL="${FIGWHEEL_CONNECT_URL:-}"

# Derived paths
DATOMIC_DIR="$REPO_ROOT/lib/com/datomic/datomic-${DATOMIC_TYPE}/${DATOMIC_VERSION}"
DATOMIC_CONFIG="$DATOMIC_DIR/config/working-transactor.properties"
DATOMIC_CONFIG_TEMPLATE="$DATOMIC_DIR/config/samples/dev-transactor-template.properties"

# Ensure logs directory exists
mkdir -p "$LOG_DIR" 2>/dev/null || true

# -----------------------------------------------------------------------------
# Colors
# -----------------------------------------------------------------------------

# Disable colors if not a terminal or NO_COLOR is set
if [[ -t 1 ]] && [[ -z "${NO_COLOR:-}" ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    CYAN=''
    BOLD=''
    NC=''
fi

# -----------------------------------------------------------------------------
# Exit Codes (standardized across all scripts)
# -----------------------------------------------------------------------------
# 0 = Success (or already running in idempotent mode)
# 1 = Usage / invalid args
# 2 = Prerequisite / config failure
# 3 = Runtime failure (process crashed, timeout, port conflict)

EXIT_SUCCESS=0
EXIT_USAGE=1
EXIT_PREREQ=2
EXIT_RUNTIME=3

# -----------------------------------------------------------------------------
# Configurable Timeouts
# -----------------------------------------------------------------------------

KILL_WAIT="${KILL_WAIT:-5}"
PORT_WAIT="${PORT_WAIT:-30}"

# -----------------------------------------------------------------------------
# Quiet Mode Support
# -----------------------------------------------------------------------------

# Global quiet mode flag (set by scripts via --quiet)
QUIET="${QUIET:-false}"

# -----------------------------------------------------------------------------
# Interactive Detection
# -----------------------------------------------------------------------------

# Check if running interactively (both stdin and stdout are terminals)
is_interactive() {
    [[ -t 0 && -t 1 ]]
}

# -----------------------------------------------------------------------------
# Logging
# -----------------------------------------------------------------------------

# log_info and log_warn respect QUIET mode
# log_error ALWAYS outputs (to stderr) - errors should never be silenced
log_info() {
    [[ "$QUIET" == "true" ]] && return
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    [[ "$QUIET" == "true" ]] && return
    echo -e "${YELLOW}[WARN]${NC} $1" >&2
}

log_error() {
    # Always output errors to stderr, even in quiet mode
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

# -----------------------------------------------------------------------------
# Port Utilities
# -----------------------------------------------------------------------------

# Check if a port is in use (returns 0 if in use, 1 if free)
# True under Git Bash / MSYS2 / Cygwin, where `netstat` is Windows' netstat.exe.
is_windows() {
    case "$(uname -s 2>/dev/null)" in
        MINGW*|MSYS*|CYGWIN*) return 0 ;;
        *) return 1 ;;
    esac
}

# Windows netstat.exe has no -l flag, so the GNU-style `netstat -tln` below
# exits with "Invalid argument" and prints nothing to stdout. With stderr
# discarded that reads as "no match" — i.e. every port looks free, the
# pre-flight check never warns, and the JVM is the first thing to discover the
# conflict (BindException: Address already in use). Ask Windows its own way.
port_in_use() {
    local port="$1"
    if is_windows; then
        # Match on the LOCAL ADDRESS column, not the state word: netstat.exe
        # prints "LISTENING" where the docs say "LISTEN", and a non-English
        # Windows translates it outright. Column 2 is the local address on
        # every row; the last column is the PID.
        [ -n "$(netstat -ano 2>/dev/null | awk -v p="[:.]${port}\$" '$2 ~ p {print; exit}')" ]
    elif command -v lsof >/dev/null 2>&1; then
        lsof -i ":${port}" >/dev/null 2>&1
    elif command -v ss >/dev/null 2>&1; then
        ss -tln 2>/dev/null | grep -q ":${port}\b"
    elif command -v netstat >/dev/null 2>&1; then
        netstat -tln 2>/dev/null | grep -q ":${port}\b"
    else
        # Fallback: try to connect
        timeout 1 bash -c "</dev/tcp/localhost/$port" 2>/dev/null
    fi
}

# Wait for a port to become available (up to timeout seconds)
wait_for_port() {
    local port="$1"
    local timeout="${2:-30}"
    local elapsed=0

    while [[ $elapsed -lt $timeout ]]; do
        if port_in_use "$port"; then
            return 0
        fi
        sleep 1
        ((elapsed++))
    done
    return 1
}

# Wait for a port to become available, but fail fast if the process dies
# Usage: wait_for_port_or_die PORT PID [TIMEOUT]
wait_for_port_or_die() {
    local port="$1"
    local pid="$2"
    local timeout="${3:-60}"
    local elapsed=0

    while [[ $elapsed -lt $timeout ]]; do
        # Check if process is still alive
        if ! kill -0 "$pid" 2>/dev/null; then
            log_error "Process $pid died while waiting for port $port"
            return 1
        fi
        # Check if port is ready
        if port_in_use "$port"; then
            return 0
        fi
        sleep 1
        ((elapsed++))
    done
    log_error "Timeout waiting for port $port (process $pid still running)"
    return 1
}

# Wait for a port to become free (up to timeout seconds)
wait_for_port_free() {
    local port="$1"
    local timeout="${2:-10}"
    local elapsed=0

    while [[ $elapsed -lt $timeout ]]; do
        if ! port_in_use "$port"; then
            return 0
        fi
        sleep 1
        ((elapsed++))
    done
    return 1
}

# Find PIDs listening on a port (cross-platform)
find_pids_by_port() {
    local port="$1"
    local pids=""

    if is_windows; then
        # Last column of a LISTENING row is the owning PID.
        pids=$(netstat -ano 2>/dev/null \
               | awk -v p="[:.]${port}\$" '$2 ~ p && $NF ~ /^[0-9]+$/ {print $NF}' \
               | sort -u || true)
        echo "$pids" | tr '\n' ' ' | xargs
        return
    fi

    if command -v lsof >/dev/null 2>&1; then
        pids=$(lsof -t -i ":${port}" 2>/dev/null || true)
    elif command -v ss >/dev/null 2>&1; then
        # Cross-platform: use sed instead of grep -oP
        pids=$(ss -tlnp 2>/dev/null | grep ":${port}\b" | sed -n 's/.*pid=\([0-9]*\).*/\1/p' || true)
    elif command -v netstat >/dev/null 2>&1; then
        pids=$(netstat -tlnp 2>/dev/null | grep ":${port}\b" | awk '{print $7}' | cut -d'/' -f1 | grep -E '^[0-9]+$' || true)
    fi

    echo "$pids" | tr '\n' ' ' | xargs
}

# Find PIDs by process name pattern (cross-platform)
find_pids_by_name() {
    local pattern="$1"
    local pids=""

    if command -v pgrep >/dev/null 2>&1; then
        pids=$(pgrep -f "$pattern" 2>/dev/null || true)
    else
        pids=$(ps aux 2>/dev/null | grep -E "$pattern" | grep -v grep | awk '{print $2}' || true)
    fi

    # Filter out our own PID and parent
    local self_pid=$$
    local parent_pid=$PPID
    local filtered=""
    for pid in $pids; do
        [[ "$pid" != "$self_pid" && "$pid" != "$parent_pid" ]] && filtered="$filtered $pid"
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
# Prerequisite Checks
# -----------------------------------------------------------------------------

check_java() {
    local java_version
    java_version=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')

    if [[ -z "$java_version" ]]; then
        log_error "Java not found. Please install Java $JAVA_MIN_VERSION or higher."
        return 1
    fi

    if [[ "$java_version" -lt "$JAVA_MIN_VERSION" ]]; then
        log_error "Java $JAVA_MIN_VERSION+ required (found Java $java_version)."
        log_info "Use the devcontainer or install a compatible JDK."
        return 1
    fi

    log_info "Java $java_version detected (minimum: $JAVA_MIN_VERSION)"
    return 0
}

check_lein() {
    if ! command -v lein >/dev/null 2>&1; then
        log_error "Leiningen not found. Please use the devcontainer or install leiningen."
        return 1
    fi
    return 0
}

check_tmux() {
    if ! command -v tmux >/dev/null 2>&1; then
        log_error "tmux not found. Install tmux or run without --tmux."
        return 1
    fi
    return 0
}

check_datomic_installed() {
    if [[ ! -d "$DATOMIC_DIR" ]]; then
        log_error "Datomic ${DATOMIC_TYPE} ${DATOMIC_VERSION} not found."
        log_error "Expected at: $DATOMIC_DIR"
        log_info "Run './start.sh --install' to install Datomic."
        return 1
    fi

    if [[ ! -f "$DATOMIC_DIR/bin/transactor" ]]; then
        log_error "Datomic transactor not found. Installation may be incomplete."
        log_error "Expected at: $DATOMIC_DIR/bin/transactor"
        log_info "Run './start.sh --install' to reinstall Datomic."
        return 1
    fi

    if [[ ! -x "$DATOMIC_DIR/bin/transactor" ]]; then
        log_error "Datomic transactor exists but is not executable."
        log_error "Path: $DATOMIC_DIR/bin/transactor"
        log_info "Try: chmod +x $DATOMIC_DIR/bin/transactor"
        return 1
    fi

    return 0
}

# -----------------------------------------------------------------------------
# Process Management
# -----------------------------------------------------------------------------

# Signal a process. On Windows the PIDs we discover come from netstat -ano and
# are native Windows PIDs, which Git Bash's `kill` cannot reliably signal — so
# stop.sh would report success while the process kept holding the port. The
# leading `//` stops MSYS rewriting /PID into a path.
signal_pid() {
    local pid="$1" sig="${2:-TERM}"
    if is_windows; then
        # A pid here can be either kind: find_service_pids checks the PID FILE
        # first, which holds an MSYS pid (written from $! by start.sh), and only
        # falls back to netstat -ano, which yields a native Windows pid. `kill`
        # handles the first, taskkill the second, and neither handles both — so
        # try kill, then taskkill.
        kill "-$sig" "$pid" 2>/dev/null && return 0
        if [[ "$sig" == "KILL" ]]; then
            taskkill //PID "$pid" //F >/dev/null 2>&1
        else
            taskkill //PID "$pid" >/dev/null 2>&1
        fi
    else
        kill "-$sig" "$pid" 2>/dev/null
    fi
}

# Is this PID still alive?
pid_alive() {
    local pid="$1"
    if is_windows; then
        # Same two-kinds-of-pid problem as signal_pid: ask both.
        kill -0 "$pid" 2>/dev/null && return 0
        tasklist //FI "PID eq $pid" 2>/dev/null | grep -qE "[[:space:]]${pid}[[:space:]]"
    else
        kill -0 "$pid" 2>/dev/null
    fi
}

# When the JVM dies with "Address already in use", say why in terms the user can
# act on. This runs AFTER lein exits, which is the only moment it can: the REPL
# holds the terminal while it lives, so nothing downstream runs until it stops.
# The pre-flight check cannot cover this case — a port RESERVED by Windows reads
# as free to every listing tool, right up until bind fails.
explain_bind_failure() {
    local port="$1"
    local pids
    pids="$(find_pids_by_port "$port")"
    if [[ -n "${pids// /}" ]]; then
        echo ""
        log_error "The server could not bind port $port."
        log_error "Something is already listening on it (PID: $pids)."
        if is_windows; then
            log_error "  Stop it with:  taskkill /PID ${pids%% *} /F"
        else
            log_error "  Stop it with:  kill ${pids%% *}"
        fi
        return
    fi

    if is_windows && command -v netsh >/dev/null 2>&1; then
        local ranges reserved=""
        ranges="$(netsh interface ipv4 show excludedportrange protocol=tcp 2>/dev/null | tr -d '\r')"
        while read -r lo hi _rest; do
            [[ "$lo" =~ ^[0-9]+$ ]] || continue
            [[ "$hi" =~ ^[0-9]+$ ]] || continue
            if (( port >= lo && port <= hi )); then reserved="$lo-$hi"; fi
        done <<< "$ranges"
        if [[ -n "$reserved" ]]; then
            echo ""
            log_error "The server could not bind port $port."
            log_error "Nothing is listening, but Windows has RESERVED $port (range $reserved)."
            log_error "  Hyper-V/WSL2/Docker take these ranges. In an admin terminal:"
            log_error "      net stop winnat && net start winnat"
            return
        fi
    fi

    # Nothing holds the port and it is not reserved, so there is no evidence
    # this was a bind failure at all — lein exits non-zero for ordinary reasons
    # too, Ctrl+C among them. Saying "could not bind" here would be crying wolf
    # after a normal shutdown, so say nothing.
    return 0
}

# Which REPL mode should the server start in?
#
# Git Bash reports stdin as a terminal, so the old `[[ -t 0 ]]` test chose the
# interactive REPL there — but its terminal is not a Windows console. The REPL
# prints its prompt, exits immediately, and takes the already-bound server down
# with it ("Subprocess failed (exit code: 1)" / "Bye for now!"). Headless is the
# same server without that passenger, so Windows always gets headless.
repl_mode() {
    if is_windows; then
        echo headless
    elif [[ -t 0 ]]; then
        echo interactive
    else
        echo headless
    fi
}

# Graceful shutdown with SIGKILL fallback
kill_gracefully() {
    local pid="$1"
    local wait_secs="${2:-$KILL_WAIT}"

    # Try SIGTERM first
    signal_pid "$pid" TERM || return 0

    # Wait for process to exit
    for ((i=0; i<wait_secs; i++)); do
        pid_alive "$pid" || return 0
        sleep 1
    done

    # Process still running - escalate to SIGKILL
    log_warn "Process $pid didn't stop gracefully, sending SIGKILL"
    signal_pid "$pid" KILL || true
}

# Clean up stale PID files
cleanup_stale_pid() {
    local name="$1"
    local pid_file="$LOG_DIR/${name}.pid"

    if [[ -f "$pid_file" ]]; then
        local old_pid
        old_pid=$(cat "$pid_file" 2>/dev/null || true)
        if [[ -n "$old_pid" ]] && ! kill -0 "$old_pid" 2>/dev/null; then
            rm -f "$pid_file"
            log_info "Cleaned up stale PID file for $name"
        fi
    fi
}

# Find service PIDs using PID file first, then port/pattern fallback
find_service_pids() {
    local name="$1"
    local port="$2"
    local pattern="$3"
    local pids=""

    # 1. Check PID file first (most reliable)
    local pid_file="$LOG_DIR/${name}.pid"
    if [[ -f "$pid_file" ]]; then
        local file_pid
        file_pid=$(cat "$pid_file" 2>/dev/null || true)
        if [[ -n "$file_pid" ]] && kill -0 "$file_pid" 2>/dev/null; then
            pids="$file_pid"
        fi
    fi

    # 2. Fall back to port scan + name pattern
    if [[ -z "$pids" ]]; then
        pids=$(echo "$(find_pids_by_port "$port") $(find_pids_by_name "$pattern")" | tr ' ' '\n' | sort -u | xargs)
    fi

    echo "$pids"
}

# -----------------------------------------------------------------------------
# Failure Diagnostics
# -----------------------------------------------------------------------------

# Show detailed diagnostics when a service fails to start
show_startup_failure() {
    local name="$1"
    local log_file="$2"
    local port="${3:-}"

    log_error "Service '$name' failed to start. Diagnostics:"
    echo "─────────────────────────────────────────────────────────────"

    if [[ -n "$log_file" && -f "$log_file" ]]; then
        echo "Last 30 lines of $log_file:"
        tail -30 "$log_file" 2>/dev/null || echo "(could not read log file)"
    else
        echo "Log file: (not available)"
    fi

    echo "─────────────────────────────────────────────────────────────"

    if [[ -n "$port" ]]; then
        echo "Processes on port $port:"
        local pids
        pids=$(find_pids_by_port "$port")
        if [[ -n "$pids" ]]; then
            for pid in $pids; do
                ps -p "$pid" -o pid,user,args 2>/dev/null || echo "  PID $pid (info unavailable)"
            done
        else
            echo "  (none)"
        fi
    fi

    echo "─────────────────────────────────────────────────────────────"
}

# -----------------------------------------------------------------------------
# Datomic Config Helpers
# -----------------------------------------------------------------------------

# Parse port from transactor config file
get_datomic_port_from_config() {
    local config="$1"
    if [[ -f "$config" ]]; then
        grep -E '^port=' "$config" 2>/dev/null | cut -d= -f2 | tr -d '\r' || echo "$DATOMIC_PORT"
    else
        echo "$DATOMIC_PORT"
    fi
}
