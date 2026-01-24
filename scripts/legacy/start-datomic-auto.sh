#!/usr/bin/env bash
set -euo pipefail


# Source .env from repo root if present (authoritative config)
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Allow overriding logs directory via LOG_DIR env var; default to repo-root logs/
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs}"
mkdir -p "$LOG_DIR"
if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$REPO_ROOT/.env"
  set +a
fi

echo "[start-datomic-auto.sh] Starting Datomic transactor (advanced/auto mode) script..." >&2
sleep 0.1

# Minimal Datomic Pro transactor start script (advanced/auto)
# Behavior: robustly starts transactor, checks for existing transactor processes and processes holding the configured port (default 4334), offers controlled kills when run interactively, or runs non-interactively in automation.
# Usage: ./scripts/start-datomic-auto.sh [DATOMIC_DIR] [CONFIG_PATH]

# Options: --check or --no-start to run validations without starting the transactor
#          --install to run the canonical installer (./.devcontainer/post-create.sh)
NO_START=0
INSTALL=0
POSITIONAL=()
for arg in "$@"; do
  case "$arg" in
    --check|--no-start)
      NO_START=1
      ;;
    --install)
      INSTALL=1
      ;;
    --help|-h)
      echo "Usage: $0 [--check|--no-start] [--install] [DATOMIC_DIR] [CONFIG_PATH]" >&2
      exit 0
      ;;
    *)
      POSITIONAL+=("$arg")
      ;;
  esac
done
# restore positional parameters to the non-flag args
set -- "${POSITIONAL[@]}"

# If --install was requested, run the canonical installer (post-create)
if [ "$INSTALL" -eq 1 ]; then
  REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
  INSTALL_SCRIPT="$REPO_ROOT/.devcontainer/post-create.sh"
  if [ -x "$INSTALL_SCRIPT" ]; then
    echo "Running Datomic installer: $INSTALL_SCRIPT" >&2
    "$INSTALL_SCRIPT"
    exit $? 
  else
    echo "ERROR: Installer not found or not executable: $INSTALL_SCRIPT" >&2
    exit 2
  fi
fi

DATOMIC_DIR="${1:-lib/com/datomic/datomic-pro/1.0.7482}"
CONFIG_PATH="${2:-config/samples/dev-transactor-template.properties}"
PIDFILE="$LOG_DIR/datomic-transactor.pid"
TRANSACTOR_LOG="$LOG_DIR/datomic-transactor.log"

# Determine service port (default 4334). Done early so post-start checks can reference it.
SERVICE_PORT=4334
if [ -f "$DATOMIC_DIR/$CONFIG_PATH" ]; then
  cfg_port=$(grep -E '(^|[[:space:]])port[[:space:]]*=' "$DATOMIC_DIR/$CONFIG_PATH" | head -n1 | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/') || true
  if [[ "$cfg_port" =~ ^[0-9]+$ ]]; then
    SERVICE_PORT="$cfg_port"
  fi
fi

# Timeouts (seconds)
KILL_WAIT=5
PORT_WAIT=10

# Helpers
kill_pids() {
  local pids="$1"
  local remaining=""
  echo "Attempting to kill PIDs: $pids" >&2
  for pid in $pids; do
    echo "Sending TERM to $pid" >&2
    kill "$pid" || true
  done

  # wait and escalate
  for pid in $pids; do
    for i in $(seq 1 $KILL_WAIT); do
      if ps -p "$pid" >/dev/null 2>&1; then
        sleep 1
      else
        break
      fi
    done
    if ps -p "$pid" >/dev/null 2>&1; then
      echo "Process $pid did not exit; sending KILL" >&2
      kill -9 "$pid" || true
    else
      echo "Process $pid exited" >&2
    fi
  done

  # collect any remaining
  for pid in $pids; do
    if ps -p "$pid" >/dev/null 2>&1; then
      remaining="$remaining $pid"
    fi
  done

  if [ -n "$remaining" ]; then
    echo "Remaining PIDs after kill attempt:$remaining" >&2
    return 1
  fi
  return 0
}

port_in_use() {
  local p=$1
  if ss -ltn 2>/dev/null | grep -q ":$p\b" || netstat -ltn 2>/dev/null | grep -q ":$p\b"; then
    return 0
  fi
  return 1
}

get_port_pids() {
  local p=$1
  local pids=""
  pids=$(ss -ltnp 2>/dev/null | grep ":$p\b" | sed -nE 's/.*pid=([0-9]+),.*/\1/p' || true)
  if [ -z "$pids" ]; then
    pids=$(lsof -nPi :$p -sTCP:LISTEN -t 2>/dev/null || true)
  fi
  echo "$pids" | tr '\n' ' ' | sed 's/^ *//;s/ *$//'
}

wait_for_port_free() {
  local p=$1
  local timeout=${2:-$PORT_WAIT}
  for i in $(seq 1 $timeout); do
    if ! port_in_use "$p"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

# If not in check mode, check for running transactor processes
if [ "$NO_START" -eq 0 ]; then

  # Prefer pgrep to reliably find running transactor processes and show their commands
  EXISTING_PROCS=$(pgrep -af 'transactor' || true)
  if [ -n "$EXISTING_PROCS" ]; then
    echo "Datomic transactor process(es) found:" >&2
    echo "$EXISTING_PROCS" | sed 's/^/  /' >&2

    # interactive prompt only when stdin is a tty
    if [ -t 0 ]; then
      read -p "Kill all running transactor processes? [y/N]: " REPLY
    else
      echo "Non-interactive shell; not killing existing transactor processes. Exiting." >&2
      exit 0
    fi

    PRE_PORT_PIDS=$(get_port_pids "$SERVICE_PORT")
    echo "Pre-kill port PIDs for $SERVICE_PORT: ${PRE_PORT_PIDS:-<none>}" >&2

    if [[ "$REPLY" =~ ^[Yy]$ ]]; then
      PIDS_TO_KILL=$(echo "$EXISTING_PROCS" | awk '{print $1}')
      if ! kill_pids "$PIDS_TO_KILL"; then
        echo "ERROR: some transactor processes did not exit cleanly." >&2
        exit 1
      fi

      MID_PORT_PIDS=$(get_port_pids "$SERVICE_PORT")
      echo "Mid-kill port PIDs for $SERVICE_PORT: ${MID_PORT_PIDS:-<none>}" >&2

      # Wait for the configured service port to be free
      echo "Waiting up to $PORT_WAIT s for port $SERVICE_PORT to be free" >&2
      if ! wait_for_port_free "$SERVICE_PORT" "$PORT_WAIT"; then
        echo "ERROR: port $SERVICE_PORT still in use after killing processes. Aborting." >&2
        ss -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || netstat -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || true
        exit 1
      fi

      rm -f "$PIDFILE"
      echo "Killed Datomic transactor process(es). Continuing to start a new one." >&2
      sleep 0.1
    else
      echo "Exiting without starting a new transactor." >&2
      sleep 0.1
      exit 0
    fi
  else
    # No transactor-named processes; check if the configured/service port is in use by another process
    if (ss -ltnp 2>/dev/null | grep -q ":$SERVICE_PORT\\b" || netstat -ltnp 2>/dev/null | grep -q ":$SERVICE_PORT\\b"); then
      echo "Port $SERVICE_PORT appears to be in use by another process:" >&2
      ss -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || netstat -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || true

      # try to extract PIDs from ss or fall back to lsof
      PIDS=$(ss -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" | sed -nE 's/.*pid=([0-9]+),.*/\1/p' || true)
      if [ -z "$PIDS" ]; then
        PIDS=$(lsof -nPi :$SERVICE_PORT -sTCP:LISTEN -t 2>/dev/null || true)
      fi

      if [ -n "$PIDS" ]; then
        echo "Process(es) holding port $SERVICE_PORT: $PIDS" >&2
        echo "Detailed listeners (before kill):" >&2
        (ss -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || netstat -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b") || true

        if [ -t 0 ]; then
          read -p "Kill process(es) holding port $SERVICE_PORT? [y/N]: " REPLY
        else
          echo "Non-interactive shell; not killing port-hogging processes. Exiting." >&2
          exit 0
        fi

        if [[ "$REPLY" =~ ^[Yy]$ ]]; then
          echo "Attempting to kill PIDs: $PIDS" >&2
          if ! kill_pids "$PIDS"; then
            echo "ERROR: some port-hogging processes did not exit cleanly." >&2
            ss -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || netstat -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || true
            # try to extract PIDs again and show detailed ps for them
            REMAINING_PIDS=$(ss -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" | sed -nE 's/.*pid=([0-9]+),.*/\1/p' || true)
            if [ -n "$REMAINING_PIDS" ]; then
              echo "Remaining PIDs: $REMAINING_PIDS" >&2
              for rp in $REMAINING_PIDS; do
                echo "Details for $rp:" >&2
                ps -fp "$rp" || true
                echo "Open files for $rp:" >&2
                lsof -p "$rp" 2>/dev/null || true
              done
            fi
            exit 1
          fi

          # Confirm port is free
          echo "Waiting up to $PORT_WAIT s for port $SERVICE_PORT to be free" >&2
          if ! wait_for_port_free "$SERVICE_PORT" "$PORT_WAIT"; then
            echo "ERROR: port $SERVICE_PORT still in use after killing processes. Remaining listeners:" >&2
            ss -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || netstat -ltnp 2>/dev/null | grep ":$SERVICE_PORT\\b" || true
            exit 1
          fi

          rm -f "$PIDFILE"
          echo "Killed process(es) holding port $SERVICE_PORT. Continuing to start a new one." >&2
          sleep 0.1
        else
          echo "Exiting without starting a new transactor." >&2
          sleep 0.1
          exit 0
        fi
      fi
    fi
  fi
fi

if [ ! -d "$DATOMIC_DIR" ]; then
  echo "ERROR: Datomic directory not found: $DATOMIC_DIR" >&2
  sleep 0.1
  exit 2
fi

if [ ! -f "$DATOMIC_DIR/$CONFIG_PATH" ]; then
  echo "ERROR: Transactor config not found: $DATOMIC_DIR/$CONFIG_PATH" >&2
  sleep 0.1
  exit 2
fi

# If running in check-only mode, stop here after validations
if [ "$NO_START" -eq 1 ]; then
  echo "Check mode: validations passed; not starting transactor." >&2
  exit 0
fi

cd "$DATOMIC_DIR"

echo "Starting transactor (nohup bin/transactor \"$CONFIG_PATH\") -> log: $TRANSACTOR_LOG" >&2
nohup bin/transactor "$CONFIG_PATH" > "$TRANSACTOR_LOG" 2>&1 &
TRANS_PID=$!
echo "Started transactor PID: $TRANS_PID" >&2
echo "Transactor logs: $TRANSACTOR_LOG" >&2
echo "You can monitor logs with: tail -F $TRANSACTOR_LOG" >&2
echo "$TRANS_PID" > "$PIDFILE"
# Ensure transactor PID and logs are stored in repo logs/ (not /tmp)
chmod 644 "$TRANSACTOR_LOG" || true


# Verify the process is alive shortly after spawn
sleep 0.2
if ! ps -p "$TRANS_PID" >/dev/null 2>&1; then
  echo "ERROR: transactor process $TRANS_PID exited immediately. Showing last 200 lines of log:" >&2
  tail -n 200 "$TRANSACTOR_LOG" >&2 || true
  rm -f "$PIDFILE" || true
  exit 1
fi

# Wait for configured service port to be reachable (blocking, 60s)
for i in $(seq 1 60); do
  if timeout 1 bash -c "</dev/tcp/localhost/$SERVICE_PORT" >/dev/null 2>&1; then
    echo "Datomic transactor is up (port $SERVICE_PORT reachable)." >&2
    POST_PORT_PIDS=$(get_port_pids "$SERVICE_PORT")
    echo "Post-start port PIDs for $SERVICE_PORT: ${POST_PORT_PIDS:-<none>}" >&2
    if [ -n "${PRE_PORT_PIDS:-}" ]; then
      if [ "$POST_PORT_PIDS" = "$PRE_PORT_PIDS" ]; then
        echo "WARNING: port $SERVICE_PORT is owned by same PID(s) after start: $POST_PORT_PIDS" >&2
      else
        echo "Port ownership changed (pre: ${PRE_PORT_PIDS:-<none>}, post: ${POST_PORT_PIDS:-<none>})" >&2
      fi
    fi
    exit 0
  fi
  sleep 1
done

# timed out
echo "Timed out waiting for Datomic on port $SERVICE_PORT; tailing last 200 lines of transactor log:" >&2
sleep 0.1
tail -n 200 "$TRANSACTOR_LOG" >&2 || true
exit 1
