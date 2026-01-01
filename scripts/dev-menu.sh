#!/usr/bin/env bash
set -euo pipefail

# Interactive dev menu for orcpub
# Presents options to start local or docker datomic, init DB, start backend, start figwheel, tail logs, status, stop services, and open tmux monitor.

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
SERVER_LOG="/tmp/orcpub-server.log"
FIGWHEEL_LOG="/tmp/figwheel.log"
DATOMIC_PID_FILE="/tmp/datomic-transactor.pid"
SERVER_PID_FILE="/tmp/orcpub-server.pid"
FIGWHEEL_PID_FILE="/tmp/figwheel.pid"

function pause(){ read -r -p "Press enter to continue..."; }

function start_local_datomic(){
  echo "Starting local Datomic transactor..."
  # Do a quick port check to avoid accidentally starting another datomic instance
  for p in 4334 4335; do
    if timeout 1 bash -c "</dev/tcp/localhost/$p" >/dev/null 2>&1; then
      echo "Port $p already listening — aborting start to avoid duplicate Datomic instances. Run 'make dev-menu' then choose option 3 to stop local Datomic first or use 'datomic_listeners' to inspect." >&2
      datomic_listeners
      return 1
    fi
  done

  # Prefer launching the start script in a tmux window so the menu isn't blocked
  if command -v tmux >/dev/null 2>&1; then
    echo "tmux available — attempting to start Datomic in tmux session 'orcpub'..."
    if tmux has-session -t orcpub 2>/dev/null; then
      if tmux new-window -t orcpub -n datomic "bash -lc 'bash \"$SCRIPT_DIR/start-datomic-local.sh\"; echo \"Datomic start finished (press Enter to close window)\"; read -r'" 2>/dev/null; then
        echo "Started Datomic in new tmux window 'datomic' of session 'orcpub'. Use 'tmux attach -t orcpub' or 'make dev-monitor' to view."
        # Try to open the datomic log in the editor if available
        if command -v code >/dev/null 2>&1; then
          if [ -f "/tmp/datomic-transactor.log" ]; then
            echo "Opening datomic log in VS Code editor for easy scrolling..."
            code --goto /tmp/datomic-transactor.log:1 || true
          fi
        fi
      fi
    fi

    # Try creating a new session if we couldn't add a window
    if tmux new-session -d -s orcpub -n datomic "bash -lc 'bash \"$SCRIPT_DIR/start-datomic-local.sh\"; echo \"Datomic start finished (press Enter to close window)\"; read -r'" 2>/dev/null; then
      echo "Created tmux session 'orcpub' and started Datomic in window 'datomic'."
      # Auto-attach if running interactively and not already in tmux

      echo "To view it: run 'make dev-monitor' (opens tmux in a new terminal) or 'tmux attach -t orcpub' in a separate terminal."

      # If `code` CLI is available, attempt to open the datomic log in the editor for easy scrolling
      if command -v code >/dev/null 2>&1; then
        if [ -f "/tmp/datomic-transactor.log" ]; then
          echo "Opening datomic log in VS Code editor for easy scrolling..."
          code --goto /tmp/datomic-transactor.log:1 || true
        fi
      fi
      return 0
    else
      echo "tmux start attempt failed (socket missing or permission); falling back to background start." >&2
    fi
  fi

  # Fallback: run the start script in background and log to /tmp/datomic-setup.log
  echo "Running start script in background and logging to /tmp/datomic-setup.log"
  nohup bash "$SCRIPT_DIR/start-datomic-local.sh" > /tmp/datomic-setup.log 2>&1 &
  echo "Datomic start launched in background (see /tmp/datomic-setup.log)."
  # If VS Code CLI is available, open the setup log and the transactor log (if present) for convenience
  if command -v code >/dev/null 2>&1; then
    if [ -f "/tmp/datomic-setup.log" ]; then
      echo "Opening datomic setup log in VS Code editor..."
      code --goto /tmp/datomic-setup.log:1 || true
    fi
    if [ -f "/tmp/datomic-transactor.log" ]; then
      echo "Opening datomic transactor log in VS Code editor..."
      code --goto /tmp/datomic-transactor.log:1 || true
    fi
  fi
}

function start_datomic_docker(){
  echo "Starting Datomic via docker-compose..."
  docker-compose up -d datomic || true
  echo "Waiting for Datomic on port 4334..."
  for i in $(seq 1 60); do
    if timeout 1 bash -c '</dev/tcp/localhost/4334' >/dev/null 2>&1; then
      echo "Datomic available"
      # Open datomic transactor log in editor if available
      if command -v code >/dev/null 2>&1 && [ -f "/tmp/datomic-transactor.log" ]; then
        echo "Opening datomic transactor log in VS Code editor..."
        code --goto /tmp/datomic-transactor.log:1 || true
      fi
      return 0
    fi
    sleep 1
    echo -n "."
  done
  echo "\nTimed out waiting for Datomic"
}

# Helper: find the pid listening on a TCP port (prefer lsof, fallback to ss)
function find_pid_on_port(){
  port=$1
  if command -v lsof >/dev/null 2>&1; then
    lsof -t -sTCP:LISTEN -iTCP:"$port" -n -P 2>/dev/null | head -n1
  elif command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null | awk -v p=":$port" '$0 ~ p { if (match($0, /pid=([0-9]+)/, a)) print a[1]; exit }'
  else
    return 1
  fi
}

# Helper: returns a short description of the process listening on given TCP port, if any
function port_owner(){
  port=$1
  if pid=$(find_pid_on_port "$port" 2>/dev/null); then
    if [ -n "$pid" ]; then
      cmd=$(ps -p "$pid" -o comm= 2>/dev/null || true)
      echo "${cmd:-unknown} pid:$pid"
      return 0
    fi
  fi
  return 1
}

function datomic_listeners(){
  echo "Checking datomic listeners on common ports:"
  for p in 4334 4335; do
    if pid=$(find_pid_on_port "$p" 2>/dev/null); then
      echo "  Port $p -> pid:$pid (cmd: $(ps -p $pid -o comm= 2>/dev/null))"
    else
      echo "  Port $p -> not listening"
    fi
  done
  if command -v docker >/dev/null 2>&1; then
    echo "Docker datomic containers:"
    docker ps --filter name=datomic --format '  {{.Names}} ({{.Image}}) - {{.Status}}' || true
  fi
}

function stop_local_datomic(){
  echo "Stopping local Datomic transactor (if any)..."
  if [ -f "$DATOMIC_PID_FILE" ]; then
    pid=$(cat "$DATOMIC_PID_FILE" 2>/dev/null || true)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      echo "Killing pid $pid"; kill "$pid" || true; sleep 1
    fi
    rm -f "$DATOMIC_PID_FILE" || true
  else
    if pid=$(find_pid_on_port 4334 2>/dev/null); then
      if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        echo "Killing detected datomic pid $pid"; kill "$pid" || true; sleep 1
      fi
    fi
  fi
  echo "Stopped local Datomic (if it was running)."
}

function restart_local_datomic(){
  stop_local_datomic
  sleep 1
  start_local_datomic || { echo "Failed to start local Datomic"; return 1; }
}

function datomic_menu(){
  while true; do
    cat <<'EOF'

Datomic submenu
===============
1) Start Datomic (local)
2) Start Datomic (docker-compose)
3) Stop Datomic (local)
4) Restart Datomic (local)
5) Show listeners
0) Back

Enter choice:
EOF
    read -r dchoice
    case "$dchoice" in
      1) start_local_datomic; pause;;
      2) start_datomic_docker; pause;;
      3) stop_local_datomic; pause;;
      4) restart_local_datomic; pause;;
      5) datomic_listeners; pause;;
      0) break;;
      *) echo "Invalid choice"; pause;;
    esac
  done
}


function init_db(){
  echo "Initializing database (idempotent)..."
  lein run -m orcpub.dev-init || { echo "DB init failed"; }
}

function start_server(){
  if timeout 1 bash -c '</dev/tcp/localhost/8890' >/dev/null 2>&1; then
    echo "Server already listening on 8890"
    return 0
  fi
  echo "Starting backend (nohup)... logs -> $SERVER_LOG"
  nohup lein with-profile +start-server repl > "$SERVER_LOG" 2>&1 &
  echo $! > "$SERVER_PID_FILE"
  echo "Started backend pid $(cat $SERVER_PID_FILE)"
  # wait for 8890
  for i in $(seq 1 60); do
    if timeout 1 bash -c '</dev/tcp/localhost/8890' >/dev/null 2>&1; then
      echo "Backend listening on 8890"
      # Open server log in VS Code if available
      if command -v code >/dev/null 2>&1; then
        if [ -f "$SERVER_LOG" ]; then
          echo "Opening server log in VS Code editor..."
          code --goto "$SERVER_LOG":1 || true
        fi
      fi
      return 0
    fi
    sleep 1
    echo -n "."
  done
  echo "\nBackend not responding on 8890 after timeout. See logs:"; tail -n 200 "$SERVER_LOG"
}

function start_figwheel(){
  if timeout 1 bash -c '</dev/tcp/localhost/3449' >/dev/null 2>&1; then
    echo "Figwheel already listening on 3449"
    return 0
  fi
  echo "Starting figwheel (nohup)... logs -> $FIGWHEEL_LOG"
  nohup lein figwheel > "$FIGWHEEL_LOG" 2>&1 &
  echo $! > "$FIGWHEEL_PID_FILE"
  echo "Started figwheel pid $(cat $FIGWHEEL_PID_FILE)"
  # wait for 3449
  for i in $(seq 1 180); do
    if timeout 1 bash -c '</dev/tcp/localhost/3449' >/dev/null 2>&1; then
      echo "Figwheel listening on 3449"
      # Open figwheel log in VS Code if available
      if command -v code >/dev/null 2>&1; then
        if [ -f "$FIGWHEEL_LOG" ]; then
          echo "Opening figwheel log in VS Code editor..."
          code --goto "$FIGWHEEL_LOG":1 || true
        fi
      fi
      return 0
    fi
    sleep 1
    echo -n "."
  done
  echo "\nFigwheel not responding on 3449 after timeout. See logs:"; tail -n 200 "$FIGWHEEL_LOG"
}

function tail_logs(){
  echo "Tailing server and figwheel logs (Ctrl-C to exit)"
  tail -f "$SERVER_LOG" "$FIGWHEEL_LOG"
}

function status(){
  echo "Status:\n"
  echo "Server:"
  if [ -f "$SERVER_PID_FILE" ] && kill -0 "$(cat $SERVER_PID_FILE)" 2>/dev/null; then
    echo "  pid $(cat $SERVER_PID_FILE) running"
  else
    echo "  not running"
  fi
  echo "Figwheel:"
  if [ -f "$FIGWHEEL_PID_FILE" ] && kill -0 "$(cat $FIGWHEEL_PID_FILE)" 2>/dev/null; then
    echo "  pid $(cat $FIGWHEEL_PID_FILE) running"
  else
    echo "  not running"
  fi
  echo "Datomic transactor:"
  if [ -f "$DATOMIC_PID_FILE" ] && kill -0 "$(cat $DATOMIC_PID_FILE)" 2>/dev/null; then
    echo "  pid $(cat $DATOMIC_PID_FILE) running"
  else
    echo "  not running"
  fi
}

function stop_all(){
  echo "Stopping server and figwheel (if running)..."
  for f in "$SERVER_PID_FILE" "$FIGWHEEL_PID_FILE" "$DATOMIC_PID_FILE"; do
    if [ -f "$f" ]; then
      pid=$(cat "$f")
      if kill -0 "$pid" 2>/dev/null; then
        echo "Killing $pid"
        kill "$pid" || true
        sleep 1
      fi
      rm -f "$f"
    fi
  done
  echo "Stopped."
}

function open_tmux_monitor(){
  bash "$SCRIPT_DIR/dev-monitor.sh"
}

function open_logs_in_editor(){
  if ! command -v code >/dev/null 2>&1; then
    echo "VS Code CLI ('code') not found — open the log files manually or use 'tail -f' in a terminal."
    return 1
  fi

  echo "Choose log to open in editor:"
  echo "  1) Datomic log (/tmp/datomic-transactor.log)"
  echo "  2) Server log (/tmp/orcpub-server.log)"
  echo "  3) Figwheel log (/tmp/figwheel.log)"
  read -r logchoice
  case "$logchoice" in
    1) [ -f /tmp/datomic-transactor.log ] && code --goto /tmp/datomic-transactor.log:1 || echo "Datomic log not found" ;;
    2) [ -f /tmp/orcpub-server.log ] && code --goto /tmp/orcpub-server.log:1 || echo "Server log not found" ;;
    3) [ -f /tmp/figwheel.log ] && code --goto /tmp/figwheel.log:1 || echo "Figwheel log not found" ;;
    *) echo "Invalid choice"; return 1 ;;
  esac
}

while true; do
  cat <<'EOF'

Orcpub Dev Menu
===============
1) Datomic (submenu)
2) Init DB (idempotent)
3) Start backend (server)
4) Start Figwheel
5) Tail logs
6) Status
7) Stop services
8) Open tmux monitor
9) Open logs in editor (Datomic/Server/Figwheel)
0) Exit

Enter choice: 
EOF
  read -r choice
  case "$choice" in
    1) datomic_menu; pause;;
    2) init_db; pause;;
    3) start_server; pause;;
    4) start_figwheel; pause;;
    5) tail_logs;;
    6) status; pause;;
    7) stop_all; pause;;
    8) open_tmux_monitor; pause;;
    9) open_logs_in_editor; pause;;
    0) echo "Bye"; exit 0;;
    *) echo "Invalid choice"; pause;;
  esac
done
