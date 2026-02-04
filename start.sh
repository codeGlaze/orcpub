#!/usr/bin/env bash
# start.sh - OrcPub Development Environment
#
# Usage: ./start.sh [command]
#
# Commands (optional - shows menu if omitted):
#   all         - Start transactor + server
#   transactor  - Start transactor only
#   server      - Start server only (assumes transactor running)
#   kill        - Kill running Datomic/server processes
#   status      - Show running processes

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Formatting
BOLD="\033[1m"
RESET="\033[0m"
CYAN="\033[36m"
YELLOW="\033[33m"
GREEN="\033[32m"
RED="\033[31m"

# --- Dependency Checks ---

check_java() {
  local java_bin
  java_bin="$(command -v java || true)"
  if [[ -z "$java_bin" ]]; then
    echo -e "${RED}✗ Java not found. Java 8 is required.${RESET}"
    echo "   Install via SDKMAN: sdk install java 8.0.412-tem"
    return 1
  fi
  local java_ver
  java_ver="$($java_bin -version 2>&1 | awk -F '"' '/version/ {print $2}')"
  if [[ "$java_ver" != 1.8* ]]; then
    echo -e "${RED}✗ Java 8 required, found: $java_ver${RESET}"
    return 1
  fi
  echo -e "${GREEN}✓${RESET} Java $java_ver"
}

check_lein() {
  if ! command -v lein >/dev/null 2>&1; then
    echo -e "${RED}✗ Leiningen not found.${RESET}"
    echo "   Install via SDKMAN: sdk install leiningen"
    return 1
  fi
  echo -e "${GREEN}✓${RESET} Leiningen available"
}

check_deps() {
  check_java && check_lein
}

# --- Process Management ---

find_datomic_pids() {
  pgrep -f "datomic.*transactor" 2>/dev/null || true
}

find_server_pids() {
  pgrep -f "lein.*run" 2>/dev/null || true
}

show_status() {
  echo -e "${BOLD}=== Process Status ===${RESET}"
  echo ""
  
  local datomic_pids server_pids
  datomic_pids=$(find_datomic_pids)
  server_pids=$(find_server_pids)
  
  if [[ -n "$datomic_pids" ]]; then
    echo -e "${GREEN}●${RESET} Datomic transactor running (PID: $datomic_pids)"
  else
    echo -e "${RED}○${RESET} Datomic transactor not running"
  fi
  
  if [[ -n "$server_pids" ]]; then
    echo -e "${GREEN}●${RESET} Server running (PID: $server_pids)"
  else
    echo -e "${RED}○${RESET} Server not running"
  fi
  
  # Check if port 4334 is listening
  if lsof -i :4334 >/dev/null 2>&1; then
    echo -e "${GREEN}●${RESET} Port 4334 (Datomic) is listening"
  else
    echo -e "${RED}○${RESET} Port 4334 (Datomic) not listening"
  fi
  
  if lsof -i :8890 >/dev/null 2>&1; then
    echo -e "${GREEN}●${RESET} Port 8890 (HTTP) is listening"
  else
    echo -e "${RED}○${RESET} Port 8890 (HTTP) not listening"
  fi
  echo ""
}

kill_processes() {
  echo -e "${BOLD}=== Stopping Processes ===${RESET}"
  echo ""
  
  local datomic_pids server_pids killed=0
  
  server_pids=$(find_server_pids)
  if [[ -n "$server_pids" ]]; then
    echo -e "${YELLOW}Stopping server (PID: $server_pids)...${RESET}"
    kill $server_pids 2>/dev/null || true
    sleep 1
    killed=1
  fi
  
  datomic_pids=$(find_datomic_pids)
  if [[ -n "$datomic_pids" ]]; then
    echo -e "${YELLOW}Stopping Datomic transactor (PID: $datomic_pids)...${RESET}"
    kill $datomic_pids 2>/dev/null || true
    sleep 2
    # Force kill if still running
    datomic_pids=$(find_datomic_pids)
    if [[ -n "$datomic_pids" ]]; then
      echo -e "${YELLOW}Force killing...${RESET}"
      kill -9 $datomic_pids 2>/dev/null || true
    fi
    killed=1
  fi
  
  if [[ $killed -eq 0 ]]; then
    echo "No running processes found."
  else
    echo -e "${GREEN}✓${RESET} Processes stopped."
  fi
  echo ""
}

# --- Datomic Setup ---

setup_datomic() {
  : "${ADMIN_PASSWORD:=admin}"
  : "${DATOMIC_PASSWORD:=datomic}"
  : "${ALT_HOST:=127.0.0.1}"

  local datomic_dir="$SCRIPT_DIR/lib/datomic-free-0.9.5703"
  local template="$datomic_dir/config/samples/free-transactor-template.properties"
  local working="$datomic_dir/config/working-transactor.properties"

  if [[ ! -f "$template" ]]; then
    echo -e "${RED}✗ Datomic template not found: $template${RESET}"
    return 1
  fi

  mkdir -p "$datomic_dir/config"
  cp -f "$template" "$working"

  # Set alt-host
  if grep -q "^alt-host=" "$working"; then
    sed -i "s/^alt-host=.*/alt-host=${ALT_HOST}/" "$working"
  else
    sed -i "/^host=/a alt-host=${ALT_HOST}" "$working"
  fi

  # Set passwords
  sed -i "s/^#\s*storage-admin-password=.*/storage-admin-password=${ADMIN_PASSWORD}/" "$working" 2>/dev/null || true
  sed -i "s/^#\s*storage-datomic-password=.*/storage-datomic-password=${DATOMIC_PASSWORD}/" "$working" 2>/dev/null || true

  echo -e "${GREEN}✓${RESET} Datomic configured"
  DATOMIC_DIR="$datomic_dir"
  WORKING_PROPERTIES="$working"
}

# --- Start Functions ---

start_transactor() {
  if [[ -n "$(find_datomic_pids)" ]]; then
    echo -e "${YELLOW}Datomic transactor already running.${RESET}"
    return 0
  fi

  setup_datomic || return 1

  local transactor="$DATOMIC_DIR/bin/transactor"
  if [[ ! -x "$transactor" ]]; then
    echo -e "${RED}✗ Transactor not executable: $transactor${RESET}"
    return 1
  fi

  echo -e "${CYAN}Starting Datomic transactor...${RESET}"
  "$transactor" "$WORKING_PROPERTIES" &
  local pid=$!
  
  # Wait for transactor to be ready
  echo -n "Waiting for transactor"
  for i in {1..15}; do
    if lsof -i :4334 >/dev/null 2>&1; then
      echo ""
      echo -e "${GREEN}✓${RESET} Datomic transactor started (PID $pid)"
      return 0
    fi
    echo -n "."
    sleep 1
  done
  echo ""
  echo -e "${YELLOW}⚠ Transactor started but port 4334 not yet listening${RESET}"
}

start_server() {
  if [[ -n "$(find_server_pids)" ]]; then
    echo -e "${YELLOW}Server already running.${RESET}"
    return 0
  fi

  if [[ -z "$(find_datomic_pids)" ]]; then
    echo -e "${RED}✗ Datomic transactor not running. Start it first.${RESET}"
    return 1
  fi

  echo -e "${CYAN}Starting server on port ${PORT:-8890}...${RESET}"
  echo ""
  exec lein run
}

start_all() {
  check_deps || return 1
  echo ""
  start_transactor || return 1
  echo ""
  start_server
}

# --- Menu ---

show_menu() {
  while true; do
    clear
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
    echo -e "${BOLD}          OrcPub Development Server${RESET}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
    echo ""
    show_status
    echo -e "${BOLD}Options:${RESET}"
    echo "   1) Start all (transactor + server)"
    echo "   2) Start transactor only"
    echo "   3) Start server only"
    echo "   4) Stop all processes"
    echo "   5) Refresh status"
    echo "   0) Exit"
    echo ""
    read -p "Select option: " choice
    
    case "$choice" in
      1)
        echo ""
        start_all
        ;;
      2)
        echo ""
        check_deps && start_transactor
        echo ""
        read -p "Press Enter to continue..."
        ;;
      3)
        echo ""
        check_deps && start_server
        ;;
      4)
        echo ""
        kill_processes
        read -p "Press Enter to continue..."
        ;;
      5)
        continue
        ;;
      0)
        echo "Goodbye!"
        exit 0
        ;;
      *)
        echo "Invalid option."
        sleep 1
        ;;
    esac
  done
}

# --- Main ---

main() {
  case "${1:-}" in
    all)
      start_all
      ;;
    transactor)
      check_deps && start_transactor
      echo ""
      echo "Transactor running. Press Ctrl+C to stop."
      wait
      ;;
    server)
      check_deps && start_server
      ;;
    kill|stop)
      kill_processes
      ;;
    status)
      show_status
      ;;
    *)
      show_menu
      ;;
  esac
}

main "$@"
