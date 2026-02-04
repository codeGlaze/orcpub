#!/usr/bin/env bash


# Always resolve the script directory for full path execution
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

while true; do
  echo ""
  echo "OrcPub Service Launcher"
  echo "----------------------"
  echo "1) Start Datomic Transactor"
  echo "2) Start Clojure REPL"
  echo "3) Start Figwheel (ClojureScript)"
  echo "4) Quit"
  echo ""
  read -p "Select a service to launch [1-4]: " choice

  case $choice in
    1)
      echo "Starting Datomic Transactor..."
      "$SCRIPT_DIR/lib/datomic-free-0.9.5703/bin/transactor" "$SCRIPT_DIR/lib/datomic-free-0.9.5703/config/working-transactor.properties" &
      ;;
    2)
      echo "Starting Clojure REPL..."
      (cd "$SCRIPT_DIR" && lein repl &)
      ;;
    3)
      echo "Starting Figwheel..."
      (cd "$SCRIPT_DIR" && lein figwheel &)
      ;;
    4)
      echo "Exiting."
      exit 0
      ;;
    *)
      echo "Invalid choice."
      ;;
  esac
  sleep 1
done
