#!/usr/bin/env bash

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
      lib/datomic-free-0.9.5703/bin/transactor lib/datomic-free-0.9.5703/config/working-transactor.properties &
      ;;
    2)
      echo "Starting Clojure REPL..."
      lein repl &
      ;;
    3)
      echo "Starting Figwheel..."
      lein figwheel &
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
