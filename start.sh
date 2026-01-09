#!/usr/bin/env bash
#
# start.sh: Root project startup menu for Dungeon Master's Vault
#
# Provides an interactive menu to start Datomic, REPL, server, DB init, and more.
#
# Usage: ./start.sh

set -euo pipefail

main_menu() {
  echo
  echo "==== Dungeon Master's Vault Startup Menu ===="
  echo "1) Quickstart (Full Dev Bootstrap)"
  echo "2) Datomic Options"
  echo "3) Backend Options"
  echo "4) Frontend Options"
  echo "5) Database Options"
  echo "6) Utilities"
  echo "7) Abort"
  echo
}

datomic_menu() {
  echo
  echo "-- Datomic Options --"
  echo "1) Start Datomic transactor"
  echo "2) Tail Datomic log"
  echo "3) Back"
  echo
}

backend_menu() {
  echo
  echo "-- Backend Options --"
  echo "1) Start REPL"
  echo "2) Start server"
  echo "3) Tail server log"
  echo "4) Back"
  echo
}

frontend_menu() {
  echo
  echo "-- Frontend Options --"
  echo "1) Start Figwheel"
  echo "2) Back"
  echo
}

db_menu() {
  echo
  echo "-- Database Options --"
  echo "1) Init DB"
  echo "2) Add test user (dev only)"
  echo "3) Back"
  echo
}

utils_menu() {
  echo
  echo "-- Utilities --"
  echo "1) (Reserved for future: Check/Install Deps)"
  echo "2) Back"
  echo
}

while true; do
  main_menu
  read -p "Choose an option [1-7]: " CHOICE
  case "$CHOICE" in
    1)
      echo "[Quickstart] Starting Datomic transactor..."
      ./scripts/start-datomic.sh
      echo "[Quickstart] Initializing DB..."
      lein repl <<EOF
(init-database)
(exit)
EOF
      echo "[Quickstart] Starting server..."
      lein with-profile +start-server repl &
      echo "[Quickstart] Starting Figwheel..."
      lein figwheel &
      echo "Quickstart complete."
      ;;
    2)
      while true; do
        datomic_menu
        read -p "Choose Datomic option [1-3]: " DCHOICE
        case "$DCHOICE" in
          1)
            echo "Initializing DB via init-database..."
            lein run -m orcpub.dev-init
            ;;
            tail -F logs/datomic-transactor.log
            echo "Initializing DB and adding test user..."
            lein run -m orcpub.dev-init --add-test-user
            ;;
            echo "Invalid option." ;;
        esac
      done
      ;;
    3)
      while true; do
        backend_menu
        read -p "Choose Backend option [1-4]: " BCHOICE
        case "$BCHOICE" in
          1)
            echo "Starting REPL..."
            lein repl
            ;;
          2)
            echo "Starting server..."
            lein with-profile +start-server repl
            ;;
          3)
            echo "Tailing server log... (Ctrl+C to stop)"
            tail -F logs/orcpub-server.log
            ;;
          4)
            break
            ;;
          *)
            echo "Invalid option." ;;
        esac
      done
      ;;
    4)
      while true; do
        frontend_menu
        read -p "Choose Frontend option [1-2]: " FCHOICE
        case "$FCHOICE" in
          1)
            echo "Starting Figwheel..."
            lein figwheel
            ;;
          2)
            break
            ;;
          *)
            echo "Invalid option." ;;
        esac
      done
      ;;
    5)
      while true; do
        db_menu
        read -p "Choose Database option [1-3]: " DBCHOICE
        case "$DBCHOICE" in
          1)
            echo "Initializing DB via init-database..."
            lein repl <<EOF
(init-database)
(exit)
EOF
            ;;
          2)
            echo "Adding test user (dev only)..."
            lein repl <<EOF
(add-test-user)
(exit)
EOF
            ;;
          3)
            break
            ;;
          *)
            echo "Invalid option." ;;
        esac
      done
      ;;
    6)
      while true; do
        utils_menu
        read -p "Choose Utility option [1-2]: " UCHOICE
        case "$UCHOICE" in
          1)
            echo "(Reserved for future: Check/Install Deps)"
            ;;
          2)
            break
            ;;
          *)
            echo "Invalid option." ;;
        esac
      done
      ;;
    7)
      echo "Abort selected. Exiting."
      exit 0
      ;;
    *)
      echo "Invalid option." ;;
  esac
  echo
  sleep 1
done
