#!/usr/bin/env bash
set -euo pipefail

# scripts/start-datomic-local.sh
# Unpack Datomic Pro tar under .datomic if needed, prepare transactor properties,
# and start the Datomic transactor in background, writing a PID file.

# Datomic Pro 1.0.7469 (free under Apache 2.0)
DATOMIC_TAR="lib/datomic-pro-1.0.7469.tar.gz"
DATOMIC_DIR=".datomic"
PIDFILE="/tmp/datomic-transactor.pid"
TRANSACTOR_LOG="/tmp/datomic-transactor.log"
# Datomic Pro uses dev-transactor-template.properties (or free-transactor-template.properties for compatibility)
TRANS_PROPERTIES_PATH="$DATOMIC_DIR/config/samples/dev-transactor-template.properties"
# Fallback to free template if dev doesn't exist (for compatibility)
[ ! -f "$TRANS_PROPERTIES_PATH" ] && TRANS_PROPERTIES_PATH="$DATOMIC_DIR/config/samples/free-transactor-template.properties"
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

# Prepare transactor properties using an absolute path so the transactor (which cd's into its bin/..) can locate it reliably
TRANS_COPY_ABS="$(cd "$DATOMIC_DIR" && pwd)/transactor.properties"
cp "$TRANS_PROPERTIES_PATH" "$TRANS_COPY_ABS"
# configure sensible defaults for development (use portable sed with alternate delimiter and create a backup then remove it)
sed -i.bak "s|# data-dir=data|data-dir=./data|" "$TRANS_COPY_ABS" && rm -f "$TRANS_COPY_ABS.bak"
sed -i.bak "s|# log-dir=log|log-dir=./log|" "$TRANS_COPY_ABS" && rm -f "$TRANS_COPY_ABS.bak"
sed -i.bak "s|host=localhost|host=0.0.0.0|" "$TRANS_COPY_ABS" && rm -f "$TRANS_COPY_ABS.bak"
# disable encrypted transport for local dev to avoid SSL handshake requirements
# Template usually has '# encrypt-channel=true' commented out; replace/comment accordingly
sed -i.bak "s|# encrypt-channel=true|encrypt-channel=false|" "$TRANS_COPY_ABS" && rm -f "$TRANS_COPY_ABS.bak"
# If the property wasn't present at all, append it for clarity
if ! grep -q "^encrypt-channel=" "$TRANS_COPY_ABS"; then
  printf "\n# Disable SSL for local development\nencrypt-channel=false\n" >> "$TRANS_COPY_ABS"
fi

# Make sure data/log dirs exist
mkdir -p "$DATOMIC_DIR/data" "$DATOMIC_DIR/log"

# Start transactor if not already running
if [ -f "$PIDFILE" ] && kill -0 "$(cat $PIDFILE)" 2>/dev/null; then
  echo "Datomic transactor already running (pid $(cat $PIDFILE))." 
  exit 0
fi

echo "Starting Datomic transactor (logs: $TRANSACTOR_LOG)..."
# Note which properties file we'll be using
echo "Using transactor properties: $TRANS_COPY_ABS"
# Start in background; pass the absolute path to the transactor properties so it is found regardless of transactor's CWD
nohup "$DATOMIC_DIR/bin/transactor" "$TRANS_COPY_ABS" > "$TRANSACTOR_LOG" 2>&1 &
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
