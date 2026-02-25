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

info()  { printf '%s[OK]%s    %s\n' "$color_green" "$color_reset" "$*"; }
error() { printf '%s[ERROR]%s %s\n' "$color_red" "$color_reset" "$*" >&2; }
warn()  { printf '%s[WARN]%s  %s\n' "$color_yellow" "$color_reset" "$*"; }

usage() {
  cat <<'USAGE'
OrcPub Docker User Management

Usage:
  ./docker-user.sh init
      Create the initial admin user from INIT_ADMIN_* variables in .env
      Safe to run multiple times — duplicates are skipped.

  ./docker-user.sh create <username> <email> <password>
      Create a new user (auto-verified, skips email)

  ./docker-user.sh batch <file>
      Create multiple users from a file (one JVM startup).
      File format: one user per line — username email password
      Lines starting with # and blank lines are skipped.
      Duplicates are logged and skipped (not treated as errors).

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
  ./docker-user.sh init
  ./docker-user.sh create admin admin@example.com MySecurePass123
  ./docker-user.sh batch users.txt
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
  local max_wait=120
  local waited=0

  # Check container is running
  if ! docker inspect --format='{{.State.Running}}' "$container" 2>/dev/null | grep -q true; then
    error "Container $container is not running."
    error "Start it first: docker-compose up -d"
    exit 1
  fi

  # Wait for Docker's native healthcheck (defined in docker-compose.yaml)
  # to report the container as healthy. This avoids spawning a JVM per check.
  local health
  health=$(docker inspect --format='{{if .State.Health}}yes{{end}}' "$container" 2>/dev/null || true)

  if [ "$health" = "yes" ]; then
    printf "Waiting for container health check"
    while [ $waited -lt $max_wait ]; do
      local status
      status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || true)
      if [ "$status" = "healthy" ]; then
        echo ""
        info "Container is healthy"
        return 0
      fi
      if [ "$status" = "unhealthy" ]; then
        echo ""
        error "Container reported unhealthy"
        exit 1
      fi
      printf "."
      sleep 2
      waited=$((waited + 2))
    done
    echo ""
    error "Timed out waiting for healthy status (${max_wait}s)."
    exit 1
  fi

  # Fallback: no healthcheck defined — check HTTP readiness directly
  warn "No Docker healthcheck found; polling HTTP on container port..."
  printf "Waiting for app"
  while [ $waited -lt $max_wait ]; do
    # BusyBox wget (Alpine): only -q and --spider are supported
    if docker exec "$container" wget -q --spider \
      "http://localhost:${PORT:-8890}/" 2>/dev/null; then
      echo ""
      return 0
    fi
    printf "."
    sleep 2
    waited=$((waited + 2))
  done

  echo ""
  error "Timed out waiting for app (${max_wait}s). Is the datomic container running?"
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

# For init: read INIT_ADMIN_* from .env and create the user
if [ "${1:-}" = "init" ]; then
  ENV_FILE="${SCRIPT_DIR}/.env"
  if [ ! -f "$ENV_FILE" ]; then
    error "No .env file found. Run ./docker-setup.sh first."
    exit 1
  fi

  # Source .env to get INIT_ADMIN_* variables
  # shellcheck disable=SC1090
  . "$ENV_FILE"

  if [ -z "${INIT_ADMIN_USER:-}" ]; then
    error "INIT_ADMIN_USER is not set in .env"
    error "Run ./docker-setup.sh to configure, or set it manually in .env"
    exit 1
  fi
  if [ -z "${INIT_ADMIN_EMAIL:-}" ] || [ -z "${INIT_ADMIN_PASSWORD:-}" ]; then
    error "INIT_ADMIN_EMAIL and INIT_ADMIN_PASSWORD must also be set in .env"
    exit 1
  fi

  info "Creating initial admin user: ${INIT_ADMIN_USER} <${INIT_ADMIN_EMAIL}>"
  run_in_container "$CONTAINER" create "$INIT_ADMIN_USER" "$INIT_ADMIN_EMAIL" "$INIT_ADMIN_PASSWORD"

# For batch: copy the user file into the container and rewrite the path
elif [ "${1:-}" = "batch" ]; then
  USER_FILE="${2:-}"
  if [ -z "$USER_FILE" ]; then
    error "Usage: ./docker-user.sh batch <file>"
    exit 1
  fi
  if [ ! -f "$USER_FILE" ]; then
    error "File not found: $USER_FILE"
    exit 1
  fi
  docker cp "$USER_FILE" "${CONTAINER}:/tmp/batch-users.txt"
  run_in_container "$CONTAINER" batch /tmp/batch-users.txt
else
  run_in_container "$CONTAINER" "$@"
fi
