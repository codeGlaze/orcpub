#!/usr/bin/env bash
# =============================================================================
# migrate-db.sh — Datomic Free → Pro data migration
# =============================================================================
# Migrates an existing Datomic Free database to Datomic Pro using the
# bin/datomic CLI (backup-db / restore-db). Works with any database size.
#
# This is the primary migration tool. For Docker deployments, use
# docker-migrate.sh which wraps this same approach in containers.
#
# The Free distribution (lib/datomic-free-0.9.5703.tar.gz) is bundled in the
# repo because it's no longer publicly distributed. For backup, the script
# searches lib/ for an extracted Free distribution, prompts the user if not
# found, and extracts the tarball as a last resort. Restore and verify use
# the installed Pro distribution.
#
# Usage:
#   ./scripts/migrate-db.sh backup                  Back up the running database
#   ./scripts/migrate-db.sh restore <target-uri>    Restore into a new database
#   ./scripts/migrate-db.sh verify                  Verify a backup's integrity
#   ./scripts/migrate-db.sh list                    List available backup points
#   ./scripts/migrate-db.sh full                    Guided full migration
#
# Prerequisites:
#   - Source transactor running (for backup)
#   - Target transactor running (for restore)
#   - .env file with DATOMIC_URL and DATOMIC_PASSWORD
#
# See docs/migration/datomic-data-migration.md for the full guide.
# =============================================================================

set -euo pipefail

# Interrupt handler — warn about potentially incomplete operations
trap 'echo ""; log_warn "Interrupted. If a backup or restore was in progress, it may be incomplete."; exit 130' INT TERM

# Source shared utilities (env, logging, Datomic paths)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "$SCRIPT_DIR/common.sh"

BACKUP_DIR="${BACKUP_DIR:-$REPO_ROOT/backup}"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

usage() {
  cat <<'USAGE'
Datomic Free → Pro Migration Tool

Migrates data between Datomic storage protocols using the bin/datomic CLI.
The backup format is storage-protocol-independent — a Free backup can be
restored into Pro and vice versa.

The Free distribution tarball (lib/datomic-free-*.tar.gz) is bundled in the
repo. For backup, the script searches lib/ for an extracted Free distribution,
prompts you if not found, or extracts the tarball as a fallback. Restore and
verify use the Pro distribution.

Usage:
  ./scripts/migrate-db.sh backup                  Back up the current database
  ./scripts/migrate-db.sh restore <target-uri>    Restore backup into a new database
  ./scripts/migrate-db.sh verify                  Verify backup integrity
  ./scripts/migrate-db.sh list                    List available backup points
  ./scripts/migrate-db.sh full                    Guided full migration

Options (must come BEFORE the command):
  --backup-dir <path>      Override backup directory (default: ./backup)
  --source-uri <uri>       Override source database URI (default: DATOMIC_URL from .env)
  --datomic-dir <path>     Override Datomic distribution path (overrides auto-detection)
  --help                   Show this help

Examples:
  # Backup (auto-detects Free distribution in lib/)
  ./scripts/migrate-db.sh backup

  # Restore into Pro (uses installed Pro distribution)
  ./scripts/migrate-db.sh restore "datomic:dev://localhost:4334/orcpub?password=secret"

  # Override backup location
  ./scripts/migrate-db.sh --backup-dir /mnt/backup backup
USAGE
}

# Bundled Free distribution tarball — safety net since it's no longer
# publicly distributed. Users running the old stack already have it
# extracted somewhere; this is the fallback if we can't find it.
DATOMIC_FREE_TARBALL="$REPO_ROOT/lib/datomic-free-0.9.5703.tar.gz"
DATOMIC_FREE_EXTRACT_DIR="$REPO_ROOT/lib/datomic-free-0.9.5703"

# Resolve the bin/datomic path from the active DATOMIC_DIR
datomic_bin() {
  echo "${DATOMIC_DIR}/bin/datomic"
}

# Check that the active Datomic CLI binary is available and executable
check_datomic_cli() {
  local bin
  bin="$(datomic_bin)"
  if [[ ! -x "$bin" ]]; then
    log_error "Datomic CLI not found at: $bin"
    log_error "Override with: --datomic-dir /path/to/datomic-distribution"
    exit "$EXIT_PREREQ"
  fi
}

# Search for an already-extracted Free distribution. Returns the path on
# stdout if found, nonzero exit otherwise. Does NOT extract — that's
# handled by use_free_datomic() after the interactive prompt.
find_free_datomic() {
  # Check the expected extraction path first
  if [[ -x "${DATOMIC_FREE_EXTRACT_DIR}/bin/datomic" ]]; then
    echo "$DATOMIC_FREE_EXTRACT_DIR"
    return 0
  fi

  # Search for any extracted Free distribution in lib/
  local candidate
  for candidate in "$REPO_ROOT"/lib/datomic-free-*/bin/datomic; do
    if [[ -x "$candidate" ]]; then
      echo "$(dirname "$(dirname "$candidate")")"
      return 0
    fi
  done

  return 1
}

# Switch DATOMIC_DIR to the Free distribution (for backup).
# The Free peer library is the only thing that can talk to a Free transactor.
# Resolution order:
#   1. find_free_datomic() searches lib/ for an extracted distribution
#   2. If interactive, prompt the user for a custom path
#   3. Extract the bundled tarball as a last resort
use_free_datomic() {
  local free_dir
  if free_dir=$(find_free_datomic); then
    DATOMIC_DIR="$free_dir"
    log_info "Using Free distribution: $DATOMIC_DIR"
    return 0
  fi

  # Not found automatically — ask the user if interactive
  if is_interactive; then
    log_warn "Datomic Free distribution not found in lib/."
    echo ""
    echo "The backup needs the Free distribution's bin/datomic to connect to"
    echo "your running Free transactor. If you have it extracted somewhere,"
    echo "enter the path now. Otherwise press Enter to extract the bundled"
    echo "tarball (lib/datomic-free-0.9.5703.tar.gz)."
    echo ""
    read -rp "Datomic Free path (or Enter to extract): " user_path
    if [[ -n "$user_path" ]]; then
      if [[ -x "${user_path}/bin/datomic" ]]; then
        DATOMIC_DIR="$user_path"
        log_info "Using Free distribution: $DATOMIC_DIR"
        return 0
      else
        log_error "No bin/datomic found at: $user_path"
        exit "$EXIT_PREREQ"
      fi
    fi
  fi

  # Extract the bundled tarball
  if [[ -f "$DATOMIC_FREE_TARBALL" ]]; then
    log_info "Extracting Free distribution from $(basename "$DATOMIC_FREE_TARBALL")..."
    tar xzf "$DATOMIC_FREE_TARBALL" -C "$REPO_ROOT/lib"
    chmod +x "${DATOMIC_FREE_EXTRACT_DIR}/bin/datomic" \
             "${DATOMIC_FREE_EXTRACT_DIR}/bin/run" \
             "${DATOMIC_FREE_EXTRACT_DIR}/bin/classpath"
    DATOMIC_DIR="$DATOMIC_FREE_EXTRACT_DIR"
    log_info "Extracted to: $DATOMIC_DIR"
    return 0
  fi

  log_error "Cannot find Datomic Free distribution and no tarball to extract."
  log_error "Provide one with: --datomic-dir /path/to/datomic-free"
  exit "$EXIT_PREREQ"
}

# Switch DATOMIC_DIR to the Pro distribution (for restore/verify/list).
# verify-backup is Pro-only (not available in the Free CLI).
use_pro_datomic() {
  DATOMIC_DIR="$REPO_ROOT/lib/com/datomic/datomic-pro/${DATOMIC_VERSION}"
  if [[ ! -x "${DATOMIC_DIR}/bin/datomic" ]]; then
    log_error "Pro distribution not found at: $DATOMIC_DIR"
    log_error "Run the devcontainer post-create script or install Datomic Pro."
    exit "$EXIT_PREREQ"
  fi
  log_info "Using Pro distribution: $DATOMIC_DIR"
}

# Build the file: URI for backup operations.
# Datomic's backup CLI uses file: URIs (not bare paths).
backup_uri() {
  echo "file:${BACKUP_DIR}/orcpub"
}

# Discover the latest restore point (t value) from the backup's roots/ directory.
# Root filenames ARE the t values (e.g., roots/168103969 → t=168103969).
#
# This filesystem scan is more reliable than list-backups, which can fail on
# Windows due to a directory enumeration bug in Datomic's Java code (the JVM
# silently fails to read roots/, producing :restore/no-roots even though the
# files exist). On macOS, the same code path crashes on .DS_Store files with
# NumberFormatException (https://github.com/Datomic/mbrainz-sample/issues/10).
# The underlying issue is that backup.clj parses every filename in roots/ with
# Long.parseLong() without filtering non-numeric entries or handling platform
# directory enumeration quirks. Always passing the explicit t value bypasses
# this fragile code path.
get_latest_t() {
  local roots_dir="${BACKUP_DIR}/orcpub/roots"
  local latest_t=""

  if [[ -d "$roots_dir" ]]; then
    # Find the highest numeric filename — that's the latest restore point
    latest_t=$(ls -1 "$roots_dir" 2>/dev/null \
      | grep -E '^[0-9]+$' \
      | sort -n \
      | tail -1 || true)
  fi

  if [[ -n "$latest_t" ]]; then
    echo "$latest_t"
    return 0
  fi

  return 1
}

# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------

do_backup() {
  local source_uri="${SOURCE_URI:-${DATOMIC_URL:-}}"
  if [[ -z "$source_uri" ]]; then
    log_error "No source URI. Set DATOMIC_URL in .env or use --source-uri."
    exit "$EXIT_USAGE"
  fi

  # Auto-switch to the correct distribution based on URI protocol.
  # Only auto-switch if the user didn't explicitly set --datomic-dir.
  if [[ -z "${DATOMIC_DIR_OVERRIDE:-}" ]]; then
    if [[ "$source_uri" == datomic:free://* ]]; then
      use_free_datomic
    fi
  fi

  check_datomic_cli
  mkdir -p "$BACKUP_DIR"

  # Warn about existing backup
  if [[ -d "${BACKUP_DIR}/orcpub" ]]; then
    log_warn "Existing backup found at ${BACKUP_DIR}/orcpub"
    if is_interactive; then
      read -rp "Overwrite existing backup? [y/N] " confirm
      if [[ "${confirm,,}" != "y" ]]; then
        echo "Aborted."
        exit 0
      fi
    else
      log_warn "Non-interactive mode — overwriting existing backup."
    fi
  fi

  log_info "Datomic CLI: $(datomic_bin)"
  log_info "Source URI:  $source_uri"
  log_info "Backup dir:  $(backup_uri)"
  log_info "Starting backup (large databases may take 30+ minutes)..."
  echo ""

  if ! "$(datomic_bin)" backup-db "$source_uri" "$(backup_uri)"; then
    log_error "backup-db failed. Is the source transactor running?"
    exit "$EXIT_RUNTIME"
  fi

  echo ""
  log_info "Backup complete → ${BACKUP_DIR}/orcpub"

  # Save metadata for the restore phase
  echo "$source_uri" > "${BACKUP_DIR}/.source-uri"
  date -u +"%Y-%m-%dT%H:%M:%SZ" > "${BACKUP_DIR}/.backup-timestamp"
  log_info "Metadata saved to ${BACKUP_DIR}/"
}

do_restore() {
  local target_uri="${1:-}"
  if [[ -z "$target_uri" ]]; then
    log_error "Usage: migrate-db.sh restore <target-uri>"
    log_error "Example: migrate-db.sh restore \"datomic:dev://localhost:4334/orcpub?password=secret\""
    exit "$EXIT_USAGE"
  fi

  # Restore needs the Pro peer to talk to a Pro transactor.
  # Only auto-switch if the user didn't explicitly set --datomic-dir.
  if [[ -z "${DATOMIC_DIR_OVERRIDE:-}" ]]; then
    if [[ "$target_uri" == datomic:dev://* ]]; then
      use_pro_datomic
    fi
  fi

  check_datomic_cli

  if [[ ! -d "${BACKUP_DIR}/orcpub" ]]; then
    log_error "No backup found at ${BACKUP_DIR}/orcpub"
    log_error "Run 'migrate-db.sh backup' first (with the source transactor running)."
    exit "$EXIT_PREREQ"
  fi

  if [[ -f "${BACKUP_DIR}/.backup-timestamp" ]]; then
    log_info "Backup timestamp: $(cat "${BACKUP_DIR}/.backup-timestamp")"
  fi
  if [[ -f "${BACKUP_DIR}/.source-uri" ]]; then
    log_info "Backed up from:   $(cat "${BACKUP_DIR}/.source-uri")"
  fi

  # Auto-discover the latest restore point from the filesystem.
  # Always pass t explicitly to work around a Windows JVM bug where Datomic
  # fails to enumerate the roots/ directory (producing :restore/no-roots).
  local restore_t=""
  if restore_t=$(get_latest_t); then
    log_info "Restore point: t=$restore_t (from roots/ directory)"
  else
    log_warn "Could not discover restore point from filesystem."
    log_warn "restore-db will attempt automatic root discovery."
  fi

  log_info "Datomic CLI: $(datomic_bin)"
  log_info "Backup source: $(backup_uri)"
  log_info "Target URI:    $target_uri"
  log_info "Starting restore (large databases may take 30+ minutes)..."
  echo ""

  # Pass t value explicitly when available (3rd positional arg to restore-db)
  if [[ -n "$restore_t" ]]; then
    if ! "$(datomic_bin)" restore-db "$(backup_uri)" "$target_uri" "$restore_t"; then
      log_error "restore-db failed. Is the target transactor running?"
      log_error "If 'database already exists', clear ./data and restart the transactor."
      exit "$EXIT_RUNTIME"
    fi
  else
    if ! "$(datomic_bin)" restore-db "$(backup_uri)" "$target_uri"; then
      log_error "restore-db failed. Is the target transactor running?"
      log_error "If 'database already exists', clear ./data and restart the transactor."
      log_error "On Windows: if :restore/no-roots, see docs/migration/datomic-data-migration.md"
      exit "$EXIT_RUNTIME"
    fi
  fi

  echo ""
  log_info "Restore complete. Log in and verify your data."
}

do_verify() {
  # verify-backup is Pro-only (not in the Free CLI)
  if [[ -z "${DATOMIC_DIR_OVERRIDE:-}" ]]; then
    use_pro_datomic
  fi
  check_datomic_cli

  if [[ ! -d "${BACKUP_DIR}/orcpub" ]]; then
    log_error "No backup found at ${BACKUP_DIR}/orcpub"
    exit "$EXIT_PREREQ"
  fi

  log_info "Verifying backup at $(backup_uri) ..."
  log_info "(This reads every segment — may take a while for large backups)"
  echo ""

  # verify-backup positional args: <backup-uri> <read-all> <t>
  # Try list-backups first, fall back to filesystem scan if it fails
  # (list-backups can fail on Windows due to root directory enumeration bug)
  local latest_t
  latest_t=$("$(datomic_bin)" list-backups "$(backup_uri)" 2>&1 | tail -1 || true)

  if [[ -z "$latest_t" ]]; then
    log_warn "list-backups returned empty — trying filesystem scan..."
    if latest_t=$(get_latest_t); then
      log_info "Found restore point from filesystem: t=$latest_t"
    else
      log_error "Could not determine latest backup point. Is the backup complete?"
      exit "$EXIT_RUNTIME"
    fi
  fi

  log_info "Latest backup point: t=$latest_t"

  if ! "$(datomic_bin)" verify-backup "$(backup_uri)" true "$latest_t"; then
    log_error "Backup verification failed."
    exit "$EXIT_RUNTIME"
  fi

  echo ""
  log_info "Backup verification passed."
}

do_list() {
  # list-backups exists in both Free and Pro, but use Pro since it's the
  # default installed distribution and the Free one may not be extracted.
  if [[ -z "${DATOMIC_DIR_OVERRIDE:-}" ]]; then
    use_pro_datomic
  fi
  check_datomic_cli

  if [[ ! -d "${BACKUP_DIR}/orcpub" ]]; then
    log_error "No backup found at ${BACKUP_DIR}/orcpub"
    exit "$EXIT_PREREQ"
  fi

  log_info "Backup points at $(backup_uri):"
  "$(datomic_bin)" list-backups "$(backup_uri)"
}

do_full() {
  echo ""
  echo "=============================================="
  echo "  Datomic Free → Pro Migration (guided)"
  echo "=============================================="
  echo ""
  echo "This will:"
  echo "  1. Back up your current (Free) database"
  echo "  2. Verify the backup"
  echo "  3. Guide you through the transactor swap"
  echo "  4. Restore into the new (Pro) database"
  echo ""
  echo "The script auto-selects the correct distribution for each phase."
  echo ""

  if ! is_interactive; then
    log_error "Full migration requires an interactive terminal."
    log_error "Run the steps individually — see docs/migration/datomic-data-migration.md"
    exit "$EXIT_USAGE"
  fi

  read -rp "Ready to start? [y/N] " confirm
  if [[ "${confirm,,}" != "y" ]]; then
    echo "Aborted."
    exit 0
  fi

  # Phase 1: Backup (uses Free distribution automatically)
  echo ""
  log_info "=== Phase 1: Backup ==="
  do_backup

  # Phase 2: Verify (uses Pro distribution — verify-backup is Pro-only)
  echo ""
  log_info "=== Phase 2: Verify Backup ==="
  do_verify

  # Phase 3: Transactor swap (manual — we can't automate stopping/starting)
  echo ""
  log_info "=== Phase 3: Swap Transactors ==="
  echo ""
  echo "Backup complete and verified. Now you need to:"
  echo ""
  echo "  1. Stop the Free transactor"
  echo "  2. Move ./data to ./data.free-backup"
  echo "  3. Start the Pro transactor (see docs/migration/datomic-pro.md)"
  echo "  4. Wait for it to become healthy"
  echo ""
  echo "To rollback: stop Pro, rm -rf data, mv data.free-backup data, restart Free."
  echo ""
  echo "When the Pro transactor is running, press Enter to continue with restore."
  read -rp "Press Enter when ready (or Ctrl+C to abort)... "

  # Phase 4: Restore (uses Pro distribution automatically)
  echo ""
  log_info "=== Phase 4: Restore ==="

  local target_uri
  if [[ -n "${DATOMIC_PASSWORD:-}" ]]; then
    target_uri="datomic:dev://localhost:${DATOMIC_PORT}/${DATOMIC_DB_NAME:-orcpub}?password=${DATOMIC_PASSWORD}"
    log_info "Auto-constructed target URI from .env"
    log_info "  $target_uri"
    read -rp "Use this URI? [Y/n] " confirm
    if [[ "${confirm,,}" == "n" ]]; then
      read -rp "Enter target Datomic URI: " target_uri
    fi
  else
    read -rp "Enter target Datomic URI: " target_uri
  fi
  do_restore "$target_uri"

  echo ""
  log_info "=== Migration Complete ==="
  log_info "Log in and verify your data. If everything looks good:"
  log_info "  rm -rf ./data.free-backup   # remove old Free storage"
  log_info "  rm -rf ./backup             # remove backup (or keep for safety)"
  log_info ""
  log_info "To rollback: stop Pro, rm -rf data, mv data.free-backup data, restart Free."
}

# ---------------------------------------------------------------------------
# Argument parsing — options must come BEFORE the command word
# ---------------------------------------------------------------------------

SOURCE_URI=""
DATOMIC_DIR_OVERRIDE=""  # set when user passes --datomic-dir (disables auto-switch)
CMD=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-dir)
      if [[ $# -lt 2 ]]; then
        log_error "--backup-dir requires a value"
        exit "$EXIT_USAGE"
      fi
      BACKUP_DIR="$2"; shift 2 ;;
    --source-uri)
      if [[ $# -lt 2 ]]; then
        log_error "--source-uri requires a value"
        exit "$EXIT_USAGE"
      fi
      SOURCE_URI="$2"; shift 2 ;;
    --datomic-dir)
      if [[ $# -lt 2 ]]; then
        log_error "--datomic-dir requires a value"
        exit "$EXIT_USAGE"
      fi
      DATOMIC_DIR="$2"
      DATOMIC_DIR_OVERRIDE="$2"
      shift 2 ;;
    --help|-h)
      usage; exit 0 ;;
    -*)
      log_error "Unknown option: $1"
      log_error "Options must come BEFORE the command. Run with --help for usage."
      exit "$EXIT_USAGE" ;;
    backup|restore|verify|list|full)
      if [[ -n "$CMD" ]]; then
        log_error "Multiple commands given: '$CMD' and '$1'"
        exit "$EXIT_USAGE"
      fi
      CMD="$1"; shift
      break ;;  # Remaining args are positional for the command
    *)
      log_error "Unknown argument: $1"
      usage
      exit "$EXIT_USAGE" ;;
  esac
done

if [[ -z "$CMD" ]]; then
  usage
  exit "$EXIT_USAGE"
fi

# Validate no unexpected trailing args for commands that don't take them
case "$CMD" in
  backup|verify|list|full)
    if [[ $# -gt 0 ]]; then
      log_error "Unexpected arguments after '$CMD': $*"
      log_error "Options must come BEFORE the command word."
      exit "$EXIT_USAGE"
    fi ;;
esac

case "$CMD" in
  backup)  do_backup ;;
  restore) do_restore "$@" ;;
  verify)  do_verify ;;
  list)    do_list ;;
  full)    do_full ;;
esac
