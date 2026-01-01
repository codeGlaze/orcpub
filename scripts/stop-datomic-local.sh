#!/usr/bin/env bash
set -euo pipefail

PIDFILE="/tmp/datomic-transactor.pid"
TRANSACTOR_LOG="/tmp/datomic-transactor.log"

if [ ! -f "$PIDFILE" ]; then
  echo "No PID file found at $PIDFILE; is Datomic running?"
  exit 1
fi

PID=$(cat "$PIDFILE")
if kill -0 "$PID" 2>/dev/null; then
  echo "Stopping datomic transactor (pid $PID)..."
  kill "$PID"
  # wait up to 10s
  for i in $(seq 1 10); do
    if kill -0 "$PID" 2>/dev/null; then
      sleep 1
    else
      break
    fi
  done
  if kill -0 "$PID" 2>/dev/null; then
    echo "Transactor did not exit; killing..."
    kill -9 "$PID" || true
  fi
  rm -f "$PIDFILE"
  echo "Stopped. See logs at $TRANSACTOR_LOG"
  exit 0
else
  echo "Process $PID not running; removing stale PID file." 
  rm -f "$PIDFILE"
  exit 1
fi