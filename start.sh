#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Running start.sh from $SCRIPT_DIR; CWD=$(pwd)"

# Validate Java 8 is present
JAVA_BIN="$(command -v java || true)"
if [ -z "${JAVA_BIN}" ]; then
  echo "Java not found in PATH. Java 8 (1.8.x) is required. Install via SDKMAN (recommended) or your package manager."
  echo "SDKMAN: curl -s \"https://get.sdkman.io\" | bash && source \"$HOME/.sdkman/bin/sdkman-init.sh\" && sdk install java 8.0.xx-tem"
  exit 1
fi
JAVA_VER="$($JAVA_BIN -version 2>&1 | awk -F '"' '/version/ {print $2}')"
if [[ "${JAVA_VER}" != 1.8* ]]; then
  echo "Java 8 is required, but found: $JAVA_VER"
  echo "Please install Java 8 and ensure 'java' points to it (or set JAVA_HOME appropriately)."
  exit 1
fi

echo "Found Java $JAVA_VER at $JAVA_BIN"

# Validate Leiningen is present
if ! command -v lein >/dev/null 2>&1; then
  echo "Leiningen not found in PATH. Install via SDKMAN (sdk install leiningen) or your package manager."
  echo "If you prefer not to install now, you can run the transactor manually and run 'lein run' in another shell."
  exit 1
fi

# Set default values for environment variables if not already set
: "${ADMIN_PASSWORD:=admin}"
: "${DATOMIC_PASSWORD:=datomic}"
: "${ALT_HOST:=127.0.0.1}"
: "${ENCRYPT_CHANNEL:=true}"

DATOMIC_DIR="$SCRIPT_DIR/lib/datomic-free-0.9.5703"
PROPERTIES_FILE="$DATOMIC_DIR/config/samples/free-transactor-template.properties"

echo "Looking for properties file: $PROPERTIES_FILE"
if [ ! -f "$PROPERTIES_FILE" ]; then
  echo "Datomic properties file not found: $PROPERTIES_FILE"
  echo "Contents of $DATOMIC_DIR/config/samples:"
  ls -la "$DATOMIC_DIR/config/samples" || true
  exit 1
fi

# Ensure config dir exists
mkdir -p "$DATOMIC_DIR/config"
# Copy the template to a working properties file
cp -f "$PROPERTIES_FILE" "$DATOMIC_DIR/config/working-transactor.properties"
WORKING_PROPERTIES="$DATOMIC_DIR/config/working-transactor.properties"

echo "Using working properties: $WORKING_PROPERTIES"

# Update the properties file with environment variables
if grep -q "^alt-host=" "$WORKING_PROPERTIES"; then
  sed -i "s/^alt-host=.*/alt-host=${ALT_HOST}/" "$WORKING_PROPERTIES"
else
  sed -i "/^host=/a alt-host=${ALT_HOST}" "$WORKING_PROPERTIES"
fi

# Replace commented password lines if present
sed -i "s/^#\s*storage-admin-password=.*/storage-admin-password=${ADMIN_PASSWORD}/" "$WORKING_PROPERTIES" || true
sed -i "s/^#\s*storage-datomic-password=.*/storage-datomic-password=${DATOMIC_PASSWORD}/" "$WORKING_PROPERTIES" || true
sed -i "s/^#\s*encrypt-channel=true/encrypt-channel=${ENCRYPT_CHANNEL}/" "$WORKING_PROPERTIES" || true

if [ -n "${ADMIN_PASSWORD_OLD:-}" ]; then
  sed -i "s/^#\s*old-storage-admin-password=.*/old-storage-admin-password=${ADMIN_PASSWORD_OLD}/" "$WORKING_PROPERTIES" || true
fi

if [ -n "${DATOMIC_PASSWORD_OLD:-}" ]; then
  sed -i "s/^#\s*old-storage-datomic-password=.*/old-storage-datomic-password=${DATOMIC_PASSWORD_OLD}/" "$WORKING_PROPERTIES" || true
fi

# Verify transactor exists
TRANS="$DATOMIC_DIR/bin/transactor"
if [ ! -x "$TRANS" ]; then
  echo "Datomic transactor not found or not executable: $TRANS"
  echo "Contents of $DATOMIC_DIR/bin:"
  ls -la "$DATOMIC_DIR/bin" || true
  exit 1
fi

# Cleanup handler
cleanup() {
  echo "Cleaning up..."
  if [ -n "${DATOMIC_PID:-}" ]; then
    kill "$DATOMIC_PID" 2>/dev/null || true
  fi
  if [ -n "${SERVER_PID:-}" ]; then
    kill "$SERVER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

# Start Datomic transactor
"$TRANS" "$WORKING_PROPERTIES" &
DATOMIC_PID=$!
echo "Started Datomic transactor with PID $DATOMIC_PID"

# Wait and check if port is listening
sleep 3

# Start the Clojure server
if command -v lein >/dev/null 2>&1; then
  lein run &
  SERVER_PID=$!
  echo "Started Clojure server with PID $SERVER_PID"
else
  echo "Leiningen not found in PATH; skipping server start. You can run 'lein run' manually." 
fi

# Optionally, start a REPL for testing if lein exists
if command -v lein >/dev/null 2>&1; then
  echo "Starting Leiningen REPL for testing..."
  lein repl
else
  echo "Leiningen not available; exiting after starting transactor and server if any."
fi
