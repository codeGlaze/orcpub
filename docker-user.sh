#!/usr/bin/env bash
#
# OrcPub Docker User Management
#
# Injects and verifies users in the Datomic database running inside Docker.
# Works by executing Clojure code inside the orcpub container, using the
# uberjar classpath (which already has datomic.api and buddy.hashers).
#
# Usage:
#   ./docker-user.sh create <username> <email> <password>
#   ./docker-user.sh verify <username-or-email>
#   ./docker-user.sh check  <username-or-email>
#   ./docker-user.sh list
#
# The script auto-detects the orcpub container name from docker-compose.
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANAGE_SCRIPT="${SCRIPT_DIR}/docker/scripts/manage-user.clj"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

color_green='\033[0;32m'
color_red='\033[0;31m'
color_yellow='\033[1;33m'
color_reset='\033[0m'

info()  { printf "${color_green}[OK]${color_reset}    %s\n" "$*"; }
error() { printf "${color_red}[ERROR]${color_reset} %s\n" "$*" >&2; }
warn()  { printf "${color_yellow}[WARN]${color_reset}  %s\n" "$*"; }

usage() {
  cat <<'USAGE'
OrcPub Docker User Management

Usage:
  ./docker-user.sh create <username> <email> <password>
      Create a new user (auto-verified, skips email)

  ./docker-user.sh verify <username-or-email>
      Verify an existing unverified user

  ./docker-user.sh check <username-or-email>
      Check if a user exists and show their status

  ./docker-user.sh list
      List all users in the database

Options:
  --container <name>    Override container name detection
  --help                Show this help

Examples:
  ./docker-user.sh create admin admin@example.com MySecurePass123
  ./docker-user.sh check admin
  ./docker-user.sh list
USAGE
}

# ---------------------------------------------------------------------------
# Find the orcpub container
# ---------------------------------------------------------------------------

find_container() {
  local container=""

  # Try docker-compose/docker compose service name first
  if command -v docker-compose &>/dev/null; then
    container=$(docker-compose ps -q orcpub 2>/dev/null || true)
  fi
  if [ -z "$container" ] && docker compose version &>/dev/null 2>&1; then
    container=$(docker compose ps -q orcpub 2>/dev/null || true)
  fi

  # Fallback: search by image name
  if [ -z "$container" ]; then
    container=$(docker ps -q --filter "ancestor=orcpub/orcpub:latest" 2>/dev/null | head -1 || true)
  fi

  # Fallback: search by container name pattern
  if [ -z "$container" ]; then
    container=$(docker ps -q --filter "name=orcpub" 2>/dev/null | head -1 || true)
  fi

  echo "$container"
}

# ---------------------------------------------------------------------------
# Wait for container and Datomic to be ready
# ---------------------------------------------------------------------------

wait_for_ready() {
  local container="$1"
  local max_wait=30
  local waited=0

  # Check container is running
  if ! docker inspect --format='{{.State.Running}}' "$container" 2>/dev/null | grep -q true; then
    error "Container $container is not running."
    error "Start it first: docker-compose up -d"
    exit 1
  fi

  # Wait for the app to have connected to Datomic (the uberjar starts the
  # Component system which connects on boot). We test by attempting a
  # trivial Datomic query via clojure.main.
  printf "Waiting for Datomic connection"
  while [ $waited -lt $max_wait ]; do
    if docker exec "$container" java -cp /orcpub.jar clojure.main -e \
      '(require (quote [datomic.api :as d])) (d/connect (System/getenv "DATOMIC_URL")) (println "ready")' \
      2>/dev/null | grep -q "ready"; then
      echo ""
      return 0
    fi
    printf "."
    sleep 2
    waited=$((waited + 2))
  done

  echo ""
  error "Timed out waiting for Datomic (${max_wait}s). Is the datomic container running?"
  exit 1
}

# ---------------------------------------------------------------------------
# Run the management script inside the container
# ---------------------------------------------------------------------------

run_in_container() {
  local container="$1"
  shift

  # Copy the management script into the container
  docker cp "$MANAGE_SCRIPT" "${container}:/tmp/manage-user.clj"

  # Run it with the uberjar classpath
  docker exec "$container" \
    java -cp /orcpub.jar clojure.main /tmp/manage-user.clj "$@"
}

# ---------------------------------------------------------------------------
# Parse args and dispatch
# ---------------------------------------------------------------------------

CONTAINER_OVERRIDE=""

# Extract --container flag if present
ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --container)
      CONTAINER_OVERRIDE="$2"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      ARGS+=("$1")
      shift
      ;;
  esac
done

set -- "${ARGS[@]+"${ARGS[@]}"}"

if [ $# -eq 0 ]; then
  usage
  exit 1
fi

# Verify manage-user.clj exists
if [ ! -f "$MANAGE_SCRIPT" ]; then
  error "Management script not found at: $MANAGE_SCRIPT"
  exit 1
fi

# Find or use specified container
if [ -n "$CONTAINER_OVERRIDE" ]; then
  CONTAINER="$CONTAINER_OVERRIDE"
else
  CONTAINER=$(find_container)
fi

if [ -z "$CONTAINER" ]; then
  error "Cannot find the orcpub container."
  error "Make sure the containers are running: docker-compose up -d"
  exit 1
fi

# Wait for Datomic to be reachable, then run the command
wait_for_ready "$CONTAINER"
run_in_container "$CONTAINER" "$@"
