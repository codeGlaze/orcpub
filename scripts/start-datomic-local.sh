#!/usr/bin/env bash
set -euo pipefail

# scripts/start-datomic-local.sh
# Unpack datomic-free tar under .datomic if needed, prepare transactor properties,
# and start the Datomic transactor in background, writing a PID file.

DATOMIC_TAR="lib/datomic-free-0.9.5703.tar.gz"
DATOMIC_DIR=".datomic"
PIDFILE="/tmp/datomic-transactor.pid"
TRANSACTOR_LOG="/tmp/datomic-transactor.log"
TRANS_PROPERTIES_PATH="$DATOMIC_DIR/config/samples/free-transactor-template.properties"
TRANS_COPY="$DATOMIC_DIR/transactor.properties"

if [ ! -f "$DATOMIC_TAR" ]; then
  echo "Datomic tar not found at $DATOMIC_TAR" >&2
  echo "Please place the datomic tar in the repo under lib/ or use docker-compose if available." >&2
  exit 2
fi

# Unpack if needed
if [ ! -d "$DATOMIC_DIR" ]; then
  echo "Extracting Datomic to $DATOMIC_DIR..."
  mkdir -p "$DATOMIC_DIR"
  tar -xzf "$DATOMIC_TAR" -C "$DATOMIC_DIR" --strip-components=1
fi

if [ ! -f "$TRANS_PROPERTIES_PATH" ]; then
  echo "Transactor properties template not found at $TRANS_PROPERTIES_PATH" >&2
  exit 2
fi

# Prepare transactor properties
cp "$TRANS_PROPERTIES_PATH" "$TRANS_COPY"
# configure sensible defaults for development
sed -i "s/# data-dir=data/data-dir=./data/" "$TRANS_COPY"
sed -i "s/# log-dir=log/log-dir=./log/" "$TRANS_COPY"
sed -i "s/host=localhost/host=0.0.0.0/" "$TRANS_COPY"

# Make sure data/log dirs exist
mkdir -p "$DATOMIC_DIR/data" "$DATOMIC_DIR/log"

# Start transactor if not already running
if [ -f "$PIDFILE" ] && kill -0 "$(cat $PIDFILE)" 2>/dev/null; then
  echo "Datomic transactor already running (pid $(cat $PIDFILE))." 
  exit 0
fi

echo "Starting Datomic transactor (logs: $TRANSACTOR_LOG)..."
# Start in background
nohup "$DATOMIC_DIR/bin/transactor" "$TRANS_COPY" > "$TRANSACTOR_LOG" 2>&1 &
TRANS_PID=$!

# write pid
echo "$TRANS_PID" > "$PIDFILE"

# Wait for port 4334 to be reachable (blocking, 60s)
for i in $(seq 1 60); do
  if timeout 1 bash -c '</dev/tcp/localhost/4334' >/dev/null 2>&1; then
    echo "Datomic transactor is up (port 4334 reachable)."
    exit 0
  fi
  sleep 1
done

echo "Timed out waiting for Datomic on port 4334; tailing last 200 lines of transactor log:" >&2
tail -n 200 "$TRANSACTOR_LOG" >&2
exit 1
