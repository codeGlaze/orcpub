#!/usr/bin/env bash
#
# start.sh: Root project startup menu for Dungeon Master's Vault
#
# Provides an interactive menu to start Datomic, REPL, server, DB init, and more.
#
# Usage: ./start.sh

set -euo pipefail

show_menu() {
  echo
  echo "==== Dungeon Master's Vault Startup Menu ===="
  echo "1) Start Datomic transactor (scripts/start-datomic.sh)"
  echo "2) Start REPL (lein repl)"
  echo "3) Start server (lein with-profile +start-server repl)"
  echo "4) Init DB (init-database via REPL)"
  echo "5) Start Figwheel (lein figwheel)"
  echo "6) Tail server log (tail -F /tmp/orcpub-server.log)"
  echo "7) Tail Datomic log (tail -F /tmp/datomic-transactor.log)"
  echo "8) Abort"
  echo "9) Add test user (dev only)"
  echo
}

while true; do
  show_menu
  read -p "Choose an option [1-9]: " CHOICE
  case "$CHOICE" in
    1)
      echo "Starting Datomic transactor..."
      ./scripts/start-datomic.sh
      ;;
    2)
      echo "Starting REPL..."
      lein repl
      ;;
    3)
      echo "Starting server..."
      lein with-profile +start-server repl
      ;;
    4)
      echo "Initializing DB via init-database..."
      lein repl <<EOF
(init-database)
(exit)
EOF
      ;;
    5)
      echo "Starting Figwheel..."
      lein figwheel
      ;;
    6)
      echo "Tailing server log... (Ctrl+C to stop)"
      tail -F /tmp/orcpub-server.log
      ;;
    7)
      echo "Tailing Datomic log... (Ctrl+C to stop)"
      tail -F /tmp/datomic-transactor.log
      ;;
    8)
      echo "Abort selected. Exiting."
      exit 0
      ;;
    9)
      echo "Adding test user (dev only)..."
      lein repl <<EOF
(add-test-user)
(exit)
EOF
      ;;
    *)
      echo "Invalid option."
      ;;
  esac
  echo
  sleep 1

done
