#!/usr/bin/env bash
set -euo pipefail

# scripts/dev-setup.sh
# Usage: ./scripts/dev-setup.sh [--no-start] [--skip-datomic] [--start]
NO_START=false
SKIP_DATOMIC=false
START=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-start) NO_START=true; shift ;;
    --skip-datomic) SKIP_DATOMIC=true; shift ;;
    --start) START=1; shift ;;
    -h|--help)
      cat <<EOF
Usage: $0 [--no-start] [--skip-datomic] [--start]

Options:
  --no-start       Only perform setup steps (deps, DB) and do NOT start servers (default)
  --skip-datomic   Don't attempt to start Datomic via docker-compose
  --start          After setup, start the backend and figwheel in background (not recommended in postCreate)
EOF
      exit 0 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

echo "Dev setup: NO_START=$NO_START SKIP_DATOMIC=$SKIP_DATOMIC START=$START"

# Start Datomic transactor if requested and docker-compose is available
if [ "$SKIP_DATOMIC" = false ]; then
  if command -v docker-compose >/dev/null 2>&1; then
    echo "Starting Datomic transactor via docker-compose..."
    docker-compose up -d datomic || true

    echo "Waiting for Datomic on localhost:4334 (timeout 60s) ..."
    for i in $(seq 1 60); do
      if timeout 1 bash -c '</dev/tcp/localhost/4334' >/dev/null 2>&1; then
        echo "Datomic is reachable"
        break
      fi
      sleep 1
      echo "Waiting for Datomic... ($i/60)"
    done
  else
    echo "docker-compose not found; skipping starting Datomic. If you need a local transactor, run it manually or set SKIP_DATOMIC=false when Docker is available."
  fi
else
  echo "Skipping Datomic startup as requested."
fi

# Ensure dependencies are downloaded
echo "Running lein deps..."
lein deps

# Run idempotent DB initialization via dev-init main
echo "Initializing database (idempotent)..."
# Only attempt DB init if Datomic is reachable on the expected port
if timeout 1 bash -c '</dev/tcp/localhost/4334' >/dev/null 2>&1; then
  if lein run -m orcpub.dev-init; then
    echo "DB init succeeded."
  else
    echo "DB init failed but continuing (non-fatal)." >&2
  fi
else
  echo "Datomic not reachable on localhost:4334; skipping DB init (post-create will not fail)."
fi

if [ "$START" -eq 1 ] && [ "$NO_START" = false ]; then
  echo "Starting backend and figwheel in background..."
  nohup lein with-profile +start-server repl >/tmp/orcpub-server.log 2>&1 &
  nohup lein figwheel >/tmp/figwheel.log 2>&1 &
  echo "Started server & figwheel (logs: /tmp/orcpub-server.log, /tmp/figwheel.log)"
else
  echo "Setup complete. To start server manually:"
  echo "  lein with-profile +start-server repl" 
  echo "To start figwheel:"
  echo "  lein figwheel"
fi

exit 0
