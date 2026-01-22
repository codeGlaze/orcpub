#!/usr/bin/env bash
set -euo pipefail

# Ensure Java 8 and Leiningen are installed (devcontainer should handle this)
if ! java -version 2>&1 | grep -q '1.8'; then
  echo "Java 8 is required. Please use the devcontainer or install Java 8."
  exit 1
fi
if ! command -v lein >/dev/null 2>&1; then
  echo "Leiningen not found. Please use the devcontainer or install leiningen."
  exit 1
fi

# Prepare Datomic properties if missing
if [ ! -f lib/datomic-free-0.9.5703/config/working-transactor.properties ]; then
  cp lib/datomic-free-0.9.5703/config/samples/free-transactor-template.properties lib/datomic-free-0.9.5703/config/working-transactor.properties
  echo "Copied Datomic properties template to working-transactor.properties"
fi

# Start Datomic transactor
lib/datomic-free-0.9.5703/bin/transactor lib/datomic-free-0.9.5703/config/working-transactor.properties &
DATOMIC_PID=$!
echo "Started Datomic transactor (PID $DATOMIC_PID)"

# Wait a moment for Datomic to start
sleep 3

# Start the Clojure server
lein run &
SERVER_PID=$!
echo "Started Clojure server (PID $SERVER_PID)"

# Optionally, start a REPL
lein repl
