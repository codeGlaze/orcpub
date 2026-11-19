#!/usr/bin/env bash
# =============================================================================
# docker-migrate.sh — Datomic Free → Pro migration for Docker deployments
# =============================================================================
# Wraps the bin/datomic CLI (backup-db / restore-db) in Docker containers.
# For bare-metal, use scripts/migrate-db.sh instead.
#
# The backup format is storage-protocol-independent — a Free backup restores
# into Pro. Uses bind-mounted volumes so 20GB+ databases write directly to
# the host filesystem without filling the container overlay.
#
# Database URIs (which carry the password) reach the container as environment
# variables, not command-line arguments. The shared functions are in scripts/backup-lib.sh.
#
# Usage:
#   ./docker-migrate.sh backup     Back up the running (Free) database
#   ./docker-migrate.sh restore    Restore into the new (Pro) database
#   ./docker-migrate.sh verify     Verify backup integrity
#   ./docker-migrate.sh full       Guided full migration (backup → swap → restore)
#
# Prerequisites:
#   - Docker Compose v2 (docker compose plugin)
#   - For backup:  OLD datomic container must be running
#   - For restore: NEW datomic container must be running
#   - .env file must exist (run run first)
#
# See docs/migration/datomic-data-migration.md for the full guide.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${SCRIPT_DIR}/backup"

# Run all docker compose commands from the repo root so project context
# is correct regardless of the caller's working directory.
cd "$SCRIPT_DIR"

# Interrupt handler — warn about potentially incomplete operations
trap 'echo ""; error "Interrupted. If a backup or restore was in progress, it may be incomplete."; exit 130' INT TERM

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

# Colors (disabled when not a terminal)
if [[ -t 1 ]] && [[ -z "${NO_COLOR:-}" ]]; then
  _green='\033[0;32m' _yellow='\033[1;33m'
  _red='\033[0;31m'   _cyan='\033[0;36m' _nc='\033[0m'
else
  _green='' _yellow='' _red='' _cyan='' _nc=''
fi

info()   { printf '%b[OK]%b    %s\n' "$_green" "$_nc" "$*"; }
warn()   { printf '%b[WARN]%b  %s\n' "$_yellow" "$_nc" "$*"; }
error()  { printf '%b[ERROR]%b %s\n' "$_red" "$_nc" "$*" >&2; }
header() { printf '\n%b=== %s ===%b\n\n' "$_cyan" "$*" "$_nc"; }

# Datomic backup/restore functions shared with ./run --backup.
# shellcheck source=scripts/backup-lib.sh
. "${SCRIPT_DIR}/scripts/backup-lib.sh"

usage() {
  cat <<'USAGE'
Datomic Free → Pro Migration (Docker)

Migrates data between Datomic storage protocols using the bin/datomic CLI
inside Docker containers. Backup/restore use bind-mounted volumes so
databases of any size are handled without filling the container overlay.

Usage:
  ./docker-migrate.sh backup     Back up the running (Free) database
  ./docker-migrate.sh restore    Restore into the new (Pro) database
  ./docker-migrate.sh verify     Verify backup integrity
  ./docker-migrate.sh full       Guided full migration (backup → swap → restore)

Options (must come BEFORE the command):
  --compose-yaml <file>  Override compose file for rebuild (default: docker-compose.yaml)
  --old-uri <uri>        Override source database URI detection
  --new-uri <uri>        Override target database URI detection
  --help                 Show this help

Examples:
  # Step-by-step (recommended for large databases)
  ./docker-migrate.sh backup           # With old stack running
  docker compose down
  docker compose -f docker-compose.yaml up -d
  ./docker-migrate.sh restore          # After new stack is healthy
  ./docker-migrate.sh verify           # Verify backup integrity

  # Automatic
  ./docker-migrate.sh full
USAGE
}

# Source .env for password/URI configuration.
# Uses a subshell to avoid exporting all .env vars (which could set Docker
# Compose's COMPOSE_FILE env var and change compose behavior globally).
load_env() {
  local env_file="${SCRIPT_DIR}/.env"
  if [[ -f "$env_file" ]]; then
    # Read specific variables we need rather than exporting everything
    # tr -d '\r' handles Windows line endings in .env
    # shellcheck disable=SC1090
    # shellcheck source=scripts/load-env.sh
    DATOMIC_PASSWORD="${DATOMIC_PASSWORD:-$(. "${SCRIPT_DIR}/scripts/load-env.sh" && load_env "$env_file" && echo "${DATOMIC_PASSWORD:-}")}"
  fi
}

# Check that Docker Compose v2 is available
check_docker_compose() {
  if ! docker compose version &>/dev/null; then
    error "Docker Compose v2 plugin required ('docker compose')."
    error "The standalone 'docker-compose' v1 is not supported."
    exit 1
  fi
}

# get_compose_network: prints the Compose network of the running datomic container
# (shared with ./run --backup: scripts/backup-lib.sh). Exit 1 if it is not running.
get_compose_network() {
  bk_network || exit 1
}

# get_datomic_image: prints the image of the running datomic container.
# Exit 1 if it is not running.
get_datomic_image() {
  bk_datomic_cid >/dev/null || { error "No running datomic container found. Is the stack running?"; exit 1; }
  bk_image
}

# Detect DATOMIC_URL from the running orcpub container's environment.
# The orcpub container (the peer) holds the connection URI including password;
# the datomic container (the transactor) doesn't have DATOMIC_URL.
get_container_datomic_url() {
  local cid
  cid=$(docker compose ps -q orcpub 2>/dev/null || true)
  if [[ -z "$cid" ]]; then
    return 1
  fi
  docker inspect "$cid" \
    -f '{{range .Config.Env}}{{println .}}{{end}}' \
    | grep '^DATOMIC_URL=' | cut -d= -f2- || true
}

# Wait for a compose service to become healthy
wait_healthy() {
  local service="$1"
  local max_wait="${2:-180}"
  local waited=0
  local cid=""
  local status=""

  printf "Waiting for %s to become healthy" "$service"
  while [[ $waited -lt $max_wait ]]; do
    cid=$(docker compose ps -q "$service" 2>/dev/null || true)
    if [[ -n "$cid" ]]; then
      status=$(docker inspect --format='{{.State.Health.Status}}' "$cid" 2>/dev/null || echo "starting")
      if [[ "$status" == "healthy" ]]; then
        echo ""
        info "$service is healthy (${waited}s)"
        return 0
      fi
      if [[ "$status" == "unhealthy" ]]; then
        echo ""
        error "$service reported unhealthy"
        docker compose logs "$service" | tail -20
        exit 1
      fi
    fi
    printf "."
    sleep 2
    waited=$((waited + 2))
  done
  echo ""
  error "$service did not become healthy within ${max_wait}s"
  error "Check: docker compose ps; docker compose logs $service"
  exit 1
}

# get_latest_t: prints the latest restore point of ${BACKUP_DIR}/orcpub (backup-lib.sh,
# bk_latest_t, which also explains why it scans the folder). Exit 1 if there is none.
get_latest_t() {
  bk_latest_t "${BACKUP_DIR}/orcpub"
}

# run_datomic_cli <image> <bin/datomic args...>: runs bin/datomic in a temporary container on
# the Compose network with ${BACKUP_DIR} mounted at /backup (backup-lib.sh, bk_run_cli).
# Exit: that of bin/datomic.
run_datomic_cli() {
  local image="$1"
  shift
  bk_run_cli "$image" "$BACKUP_DIR" "$@"
}

# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------

do_backup() {
  header "Backup — Datomic Database"

  local datomic_image
  datomic_image=$(get_datomic_image)
  info "Datomic image: $datomic_image"

  local old_url="${OLD_URI:-}"
  if [[ -z "$old_url" ]]; then
    old_url=$(get_container_datomic_url 2>/dev/null || true)
  fi
  if [[ -z "$old_url" ]]; then
    old_url="datomic:free://datomic:4334/orcpub"
    warn "Could not detect DATOMIC_URL; using default: $old_url"
  fi
  info "Source URI: $old_url"

  mkdir -p "$BACKUP_DIR"

  # Handle existing backup safely
  if [[ -d "${BACKUP_DIR}/orcpub" ]]; then
    warn "Existing backup found at ${BACKUP_DIR}/orcpub"
    if [[ -t 0 ]]; then
      read -rp "Overwrite? [y/N] " confirm
      if [[ "${confirm,,}" != "y" ]]; then
        echo "Aborted."
        exit 0
      fi
    else
      warn "Non-interactive — overwriting existing backup."
    fi
  fi

  info "Backup destination: file:/backup/orcpub"
  info "Starting backup (large databases may take 30+ minutes)..."
  echo ""

  if ! run_datomic_cli "$datomic_image" \
    backup-db "$old_url" "file:/backup/orcpub"; then
    error "backup-db failed. Is the source transactor running and reachable?"
    exit 1
  fi

  echo ""
  # Save metadata for the restore phase
  echo "$old_url" > "${BACKUP_DIR}/.source-uri"
  date -u +"%Y-%m-%dT%H:%M:%SZ" > "${BACKUP_DIR}/.backup-timestamp"
  info "Backup complete → ${BACKUP_DIR}/orcpub"
}

do_restore() {
  header "Restore — Datomic Pro Database"

  if [[ ! -d "${BACKUP_DIR}/orcpub" ]]; then
    error "No backup found at ${BACKUP_DIR}/orcpub"
    error "Run './docker-migrate.sh backup' first (with the old stack running)."
    exit 1
  fi

  if [[ -f "${BACKUP_DIR}/.backup-timestamp" ]]; then
    info "Backup timestamp: $(cat "${BACKUP_DIR}/.backup-timestamp")"
  fi

  local datomic_image
  datomic_image=$(get_datomic_image)
  info "Datomic image: $datomic_image"

  local new_url="${NEW_URI:-}"
  if [[ -z "$new_url" ]]; then
    new_url=$(get_container_datomic_url 2>/dev/null || true)
  fi
  if [[ -z "$new_url" ]]; then
    load_env
    if [[ -z "${DATOMIC_PASSWORD:-}" ]]; then
      warn "DATOMIC_PASSWORD not set in .env — falling back to default 'datomic'"
    fi
    new_url="datomic:dev://datomic:4334/orcpub?password=${DATOMIC_PASSWORD:-datomic}"
    warn "Could not detect DATOMIC_URL; constructed from DATOMIC_PASSWORD"
  fi
  info "Target URI: $new_url"

  # Auto-discover the latest restore point from the filesystem.
  # Always pass t explicitly to work around a JVM bug where Datomic
  # fails to enumerate the roots/ directory on some platforms.
  local restore_t=""
  if restore_t=$(get_latest_t); then
    info "Restore point: t=$restore_t (from roots/ directory)"
  else
    warn "Could not discover restore point from filesystem."
    warn "restore-db will attempt automatic root discovery."
  fi

  info "Starting restore (large databases may take 30+ minutes)..."
  echo ""

  # Pass t value explicitly when available (3rd positional arg to restore-db)
  if [[ -n "$restore_t" ]]; then
    if ! run_datomic_cli "$datomic_image" \
      restore-db "file:/backup/orcpub" "$new_url" "$restore_t"; then
      error "restore-db failed. Is the target transactor running?"
      error "If 'database already exists', clear ./data and restart the transactor."
      exit 1
    fi
  else
    if ! run_datomic_cli "$datomic_image" \
      restore-db "file:/backup/orcpub" "$new_url"; then
      error "restore-db failed. Is the target transactor running?"
      error "If 'database already exists', clear ./data and restart the transactor."
      exit 1
    fi
  fi

  echo ""
  info "Restore complete. Log in and verify your data."
}

do_verify() {
  header "Verify — Backup Integrity"

  if [[ ! -d "${BACKUP_DIR}/orcpub" ]]; then
    error "No backup found at ${BACKUP_DIR}/orcpub"
    exit 1
  fi

  local datomic_image
  datomic_image=$(get_datomic_image)

  # List available backup points
  info "Backup points:"
  run_datomic_cli "$datomic_image" list-backups "file:/backup/orcpub"
  echo ""

  # The check itself is shared with ./run --verify-backup (backup-lib.sh, bk_verify).
  bk_verify "$BACKUP_DIR" || exit 1

  echo ""
  info "Backup verification passed."
}

do_full() {
  header "Full Migration — Datomic Free → Pro"

  # Use a distinct variable name to avoid colliding with Docker Compose's
  # COMPOSE_FILE env var (which changes docker compose's behavior globally).
  local compose_yaml="${COMPOSE_YAML:-docker-compose.yaml}"
  info "New compose file: $compose_yaml"
  echo ""

  if ! [[ -t 0 ]]; then
    error "Full migration requires an interactive terminal."
    error "Run the steps individually — see docs/migration/datomic-data-migration.md"
    exit 1
  fi

  # Phase 1: Backup from old stack
  do_backup

  # Phase 2: Verify backup before we tear anything down
  do_verify

  # Phase 3: Swap stacks
  header "Swapping Stacks"
  info "Stopping old containers..."
  docker compose down
  info "Old stack stopped."
  echo ""

  # Move old data directory safely (avoid nesting if target exists)
  if [[ -d "${SCRIPT_DIR}/data" ]]; then
    if [[ -d "${SCRIPT_DIR}/data.free-backup" ]]; then
      warn "data.free-backup already exists — removing stale copy"
      rm -rf "${SCRIPT_DIR}/data.free-backup"
    fi
    warn "Moving ./data → ./data.free-backup (Free storage format)"
    mv "${SCRIPT_DIR}/data" "${SCRIPT_DIR}/data.free-backup"
    mkdir -p "${SCRIPT_DIR}/data"
  fi

  info "Building and starting new stack with ${compose_yaml}..."
  docker compose -f "$compose_yaml" build
  docker compose -f "$compose_yaml" up -d
  wait_healthy datomic 180
  wait_healthy orcpub 120

  # Phase 4: Restore into new stack
  do_restore

  echo ""
  header "Migration Complete"
  info "Old Free data preserved at: ./data.free-backup"
  info "New Pro data stored in:     ./data"
  info "Log in and verify your data, then:"
  info "  rm -rf ./data.free-backup   # remove old Free storage"
  info "  rm -rf ./backup             # remove backup (or keep as insurance)"
  info ""
  info "To rollback: docker compose down, rm -rf data, mv data.free-backup data,"
  info "  then restart with the original compose file."
}

# ---------------------------------------------------------------------------
# Argument parsing — options must come BEFORE the command word
# ---------------------------------------------------------------------------

OLD_URI=""
NEW_URI=""
COMPOSE_YAML=""
CMD=""

# Pre-flight check
check_docker_compose

while [[ $# -gt 0 ]]; do
  case "$1" in
    --compose-yaml)
      if [[ $# -lt 2 ]]; then error "--compose-yaml requires a value"; exit 1; fi
      COMPOSE_YAML="$2"; shift 2 ;;
    --old-uri)
      if [[ $# -lt 2 ]]; then error "--old-uri requires a value"; exit 1; fi
      OLD_URI="$2"; shift 2 ;;
    --new-uri)
      if [[ $# -lt 2 ]]; then error "--new-uri requires a value"; exit 1; fi
      NEW_URI="$2"; shift 2 ;;
    --help|-h)
      usage; exit 0 ;;
    -*)
      error "Unknown option: $1"
      error "Options must come BEFORE the command. Run with --help for usage."
      exit 1 ;;
    backup|restore|verify|full)
      if [[ -n "$CMD" ]]; then
        error "Multiple commands given: '$CMD' and '$1'"
        exit 1
      fi
      CMD="$1"; shift ;;
    *)
      error "Unknown argument: $1"
      usage
      exit 1 ;;
  esac
done

if [[ -z "${CMD:-}" ]]; then
  usage
  exit 1
fi

load_env

case "$CMD" in
  backup)  do_backup ;;
  restore) do_restore ;;
  verify)  do_verify ;;
  full)    do_full ;;
esac
