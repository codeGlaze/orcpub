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
  bash "$SCRIPT_DIR/start-datomic-local.sh"
}

function start_datomic_docker(){
  echo "Starting Datomic via docker-compose..."
  docker-compose up -d datomic || true
  echo "Waiting for Datomic on port 4334..."
  for i in $(seq 1 60); do
    if timeout 1 bash -c '</dev/tcp/localhost/4334' >/dev/null 2>&1; then
      echo "Datomic available"
      return 0
    fi
    sleep 1
    echo -n "."
  done
  echo "\nTimed out waiting for Datomic"
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

while true; do
  cat <<'EOF'

Orcpub Dev Menu
===============
1) Start Datomic (local)
2) Start Datomic (docker-compose)
3) Init DB (idempotent)
4) Start backend (server)
5) Start Figwheel
6) Tail logs
7) Status
8) Stop services
9) Open tmux monitor
0) Exit

Enter choice: 
EOF
  read -r choice
  case "$choice" in
    1) start_local_datomic; pause;;
    2) start_datomic_docker; pause;;
    3) init_db; pause;;
    4) start_server; pause;;
    5) start_figwheel; pause;;
    6) tail_logs;;
    7) status; pause;;
    8) stop_all; pause;;
    9) open_tmux_monitor; pause;;
    0) echo "Bye"; exit 0;;
    *) echo "Invalid choice"; pause;;
  esac
done
