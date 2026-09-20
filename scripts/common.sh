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

# Configuration reporting and the DEV_MODE decision.
#
# Deliberately NOT printed at the top of every run. Output at the start of a
# script scrolls past before the REPL takes the terminal, and nobody reads it.
# Attention exists in two places only: at a prompt, and in a command whose
# output IS the product. So:
#
#   print_env_config   -> called from run_checks (--check), where it is the point
#   offer_env_file     -> a prompt, which is a genuine pause
#   confirm_dev_mode   -> a decision, raised at figwheel start where it bites

# The settings that fail silently: ports (a busy one used to read as free on
# Windows) and CSP/DEV_MODE (blocks Figwheel's socket with no visible cause).
print_env_config() {
    if [[ -f "$REPO_ROOT/.env" ]]; then
        echo -e "Config:      ${GREEN}.env${NC}  (edit it to change any of the below)"
    else
        echo -e "Config:      ${YELLOW}built-in defaults${NC}  (no .env — see .env.example)"
    fi
    echo "  ports      server=$SERVER_PORT datomic=$DATOMIC_PORT figwheel=$FIGWHEEL_PORT nrepl=$NREPL_PORT"

    local policy="${CSP_POLICY:-strict}"
    local dev="${DEV_MODE:-<unset>}"
    if dev_mode_blocks_figwheel; then
        echo -e "  csp        ${YELLOW}$policy, ENFORCING${NC} (DEV_MODE=$dev) — Figwheel hot reload blocked"
    else
        echo "  csp        policy=$policy DEV_MODE=$dev"
    fi
}

# True when the server will send an enforcing CSP whose connect-src omits the
# Figwheel websocket. Matches the server's own comparison: case-insensitive,
# exactly "true".
dev_mode_blocks_figwheel() {
    local policy="${CSP_POLICY:-strict}"
    # "permissive" is not permissive about this: permissive-csp-settings sets
    # default-src 'self' and NO connect-src, so connect-src falls back to 'self'
    # and ws://localhost:3449 is blocked exactly as under strict. Only "none"
    # sends no policy at all.
    case "$policy" in strict|permissive) ;; *) return 1 ;; esac
    # An UNSET DEV_MODE does not mean false here. Every server path in start.sh
    # launches `lein with-profile +dev,+start-server`, and the :dev profile sets
    # :env {:dev-mode "true"} (project.clj:244), which environ reads. So on a
    # fresh checkout with nothing exported, the server we are about to start has
    # dev-mode ON and skips CSP entirely -- and warning that CSP will block
    # Figwheel would be false, and would talk the user out of a working setup.
    #
    # Only an explicit false-y DEV_MODE flips it: environ lets a real environment
    # variable override the profile's value, so DEV_MODE=false does reach the
    # server and does re-enable CSP.
    case "$(printf '%s' "${DEV_MODE:-}" | tr '[:upper:]' '[:lower:]')" in
        false|0|no|off) return 0 ;;
        *)              return 1 ;;
    esac
}

# Generate a random secret. Hex only, so it is safe to substitute into a file
# without quoting concerns. Empty string if no source is available.
random_secret() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 24 2>/dev/null && return 0
    fi
    if [[ -r /dev/urandom ]] && command -v od >/dev/null 2>&1; then
        od -An -tx1 -N24 /dev/urandom 2>/dev/null | tr -d ' \n' && return 0
    fi
    printf ''
}

# Replace KEY=... in a file, portably. sed -i differs between GNU and BSD, so
# write to a temp file and move it into place instead.
set_env_value() {
    local file="$1" key="$2" value="$3" tmp
    tmp="$(mktemp)" || return 1
    awk -v k="$key" -v v="$value" '
        $0 ~ "^" k "=" { print k "=" v; next }
        { print }
    ' "$file" > "$tmp" && mv "$tmp" "$file"
}

# First run: a short guided setup. A prompt is one of the two moments an
# operator is actually reading, so this is where configuration is worth
# raising -- and while they are here, the three change-me placeholders are
# worth resolving, because each is a credential that otherwise ships as a
# known string.
#
# Only when there is nothing to lose (no .env present) and someone is at the
# keyboard. A non-interactive run is told what to do and never blocked.
offer_env_file() {
    local env_file="$REPO_ROOT/.env" example="$REPO_ROOT/.env.example"
    [[ -f "$env_file" || ! -f "$example" ]] && return 0

    if ! is_interactive; then
        log_info "No .env — using built-in defaults. To configure: cp .env.example .env"
        return 0
    fi

    echo ""
    log_warn "No .env found. Defaults will be used: CSP_POLICY=strict, and the"
    log_warn "placeholder SIGNATURE / ADMIN_PASSWORD / DATOMIC_PASSWORD from the"
    log_warn "template, which are published values and must not face a network."

    local reply=""
    read -t 30 -p "Create .env from .env.example now? [y/N] " -n 1 -r reply || { echo; log_info "No answer — continuing on defaults."; return 0; }
    echo
    [[ "$reply" =~ ^[Yy]$ ]] || { log_info "Skipped. Continuing on defaults."; return 0; }

    cp "$example" "$env_file" || { log_error "Could not write $env_file"; return 0; }
    chmod 600 "$env_file" 2>/dev/null || true

    # --- development or production -------------------------------------------
    local mode=""
    read -t 30 -p "Set up for [d]evelopment or [p]roduction? [D/p] " -n 1 -r mode || mode=""
    echo
    if [[ "$mode" =~ ^[Pp]$ ]]; then
        set_env_value "$env_file" DEV_MODE false
        log_info "  DEV_MODE=false — CSP enforcing. Figwheel hot reload will not work."
    else
        set_env_value "$env_file" DEV_MODE true
        log_info "  DEV_MODE=true — no CSP header, so Figwheel works."
    fi

    # --- the three change-me credentials -------------------------------------
    local gen=""
    read -t 30 -p "Generate random values for the change-me passwords/secret? [Y/n] " -n 1 -r gen || gen=""
    echo
    if [[ "$gen" =~ ^[Nn]$ ]]; then
        log_warn "  Left as-is. SIGNATURE, ADMIN_PASSWORD and DATOMIC_PASSWORD are"
        log_warn "  published placeholders — change them before exposing this server."
    else
        local key secret failed=0
        for key in SIGNATURE ADMIN_PASSWORD DATOMIC_PASSWORD; do
            secret="$(random_secret)"
            if [[ -n "$secret" ]]; then
                set_env_value "$env_file" "$key" "$secret"
            else
                failed=1
            fi
        done
        if [[ $failed -eq 0 ]]; then
            # Deliberately not echoed. They are in the file; printing them puts
            # them in scrollback and shell history exports.
            log_info "  SIGNATURE, ADMIN_PASSWORD, DATOMIC_PASSWORD set to random values."
        else
            log_warn "  No random source (openssl / /dev/urandom) — placeholders left in place."
        fi
    fi

    log_info "Wrote $env_file (mode 600). Review it, then re-run to start."
    # Returning 10 means "written, caller should stop". Continuing is not an
    # option: .env was sourced near the top of this file, before it existed, and
    # every default and derived path was computed from what was loaded then. The
    # DEV_MODE just chosen and the credentials just generated are not in this
    # shell and cannot be, so carrying on would launch the server on exactly the
    # configuration the user was asked about and answered.
    return 10
}

# Raised where it actually bites. Starting Figwheel with an enforcing CSP gives
# a dev loop that looks fine and silently never reloads, so this is a decision,
# not a line of output to scroll past.
confirm_dev_mode() {
    dev_mode_blocks_figwheel || return 0

    log_warn "CSP is strict and ENFORCING (DEV_MODE=${DEV_MODE:-<unset>})."
    log_warn "ws://localhost:$FIGWHEEL_PORT is not in connect-src, so hot reload"
    log_warn "will silently not work. Set DEV_MODE=true in .env to develop."

    is_interactive || { log_warn "Continuing anyway (non-interactive)."; return 0; }

    local reply=""
    if read -t 30 -p "Start Figwheel anyway? [y/N] " -n 1 -r reply; then
        echo
        [[ "$reply" =~ ^[Yy]$ ]] && return 0
        log_info "Aborted. Set DEV_MODE=true in .env, then re-run."
        return 1
    fi
    echo
    log_info "No answer — not starting Figwheel."
    return 1
}

# Defaults (used if not set in .env)
DATOMIC_VERSION="${DATOMIC_VERSION:-1.0.7482}"
DATOMIC_TYPE="${DATOMIC_TYPE:-pro}"
JAVA_MIN_VERSION="${JAVA_MIN_VERSION:-11}"
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs}"

# Port configuration
DATOMIC_PORT="${DATOMIC_PORT:-4334}"
# Deliberately NOT ${PORT:-8890}. PORT is read by the PRODUCTION service map;
# these scripts launch the DEV one, and orcpub.system/dev-service-map-overrides
# pins ::http/port to a literal 8890 (system.clj:13). Honouring PORT here made
# the scripts probe, report and stop 9000 while the server sat on 8890 -- the
# checks confidently describing a port nothing was listening on.
#
# The alternative fix is to make the dev service map read PORT. That is the
# better end state, but it changes where a dev server binds, which is not a
# hotfix-sized change; SERVER_PORT still overrides if you need to move it.
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
        # A bound port is one with a LISTENING socket. Matching the address
        # column alone also matches TIME_WAIT and ESTABLISHED rows, so a
        # connection that closed seconds ago reads as "in use" and start.sh
        # refuses to start. Identify listening rows by the WILDCARD FOREIGN
        # ADDRESS rather than the state word -- the state word is localised
        # (LISTENING/LISTEN/translated), the foreign address is not.
        # $1 == "TCP" is load-bearing. A UDP row is printed with four columns
        # and a literal "*:*" foreign address, so it satisfies the wildcard test
        # above; without the protocol check a UDP socket on this port makes a
        # free TCP port read as busy and start.sh refuses to start.
        [ -n "$(netstat -ano 2>/dev/null \
                | awk -v p="[:.]${port}\$" \
                      '$1 == "TCP" && $2 ~ p && $3 ~ /^(0\.0\.0\.0:0|\[::\]:0|\*:\*)$/ {print; exit}')" ]
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
        elapsed=$((elapsed + 1))
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
        elapsed=$((elapsed + 1))
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
        elapsed=$((elapsed + 1))
    done
    return 1
}

# Find PIDs listening on a port (cross-platform)
find_pids_by_port() {
    local port="$1"
    local pids=""

    if is_windows; then
        # Last column of a LISTENING row is the owning PID.
        # $1 == "TCP" for the same reason as port_in_use, and it matters more
        # here: stop.sh feeds these PIDs to kill, so a UDP row passing the
        # wildcard test would terminate an unrelated process that merely shares
        # the port number.
        pids=$(netstat -ano 2>/dev/null \
               | awk -v p="[:.]${port}\$" \
                     '$1 == "TCP" && $2 ~ p && $3 ~ /^(0\.0\.0\.0:0|\[::\]:0|\*:\*)$/ && $NF ~ /^[0-9]+$/ {print $NF}' \
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
    local raw java_version
    if ! raw="$(java -version 2>&1)"; then
        log_error "Java not found. Please install Java $JAVA_MIN_VERSION or higher."
        return 1
    fi

    # Find the version line wherever it is, rather than assuming line 1.
    # JAVA_TOOL_OPTIONS and _JAVA_OPTIONS make the JVM print a "Picked up ..."
    # preamble first, which is common behind a proxy and in CI images.
    java_version="$(printf '%s\n' "$raw" | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -n1)"

    # Guard the comparison below. [[ str -lt n ]] evaluates str as ARITHMETIC,
    # so a non-numeric value is read as a variable name -- and under `set -u`
    # an unset name is a FATAL error, not a false comparison. That killed this
    # script outright, and silently, because callers use `check_java 2>/dev/null`.
    if [[ ! "$java_version" =~ ^[0-9]+$ ]]; then
        log_error "Could not read a Java version. First line of 'java -version':"
        log_error "  $(printf '%s\n' "$raw" | head -n1)"
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
