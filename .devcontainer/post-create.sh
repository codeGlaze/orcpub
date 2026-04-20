#!/usr/bin/env bash
#
# Datomic Pro install script for devcontainer
#
# 1. Always remove lib/com/datomic/datomic-{type}/<version> if it exists
# 2. Unzip the Datomic zip (from /lib or /tmp, download if missing) into target dir
# 3. Flatten top-level subdir if present (some zips nest contents)
# 4. Run vendor maven-install from bin/
#
# This script does NOT cherry-pick, rename, or check for specific files before extraction.
# All contents of the zip are placed in the target directory, overwriting any previous install.
#

set -euo pipefail

# Source .env from repo root if present (authoritative config)
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$REPO_ROOT/.env"
  set +a
fi

# Redirect all output to persistent logs for visibility during Codespace creation
LOG="/tmp/orcpub-post-create.log"
WORKSPACE_LOG="$REPO_ROOT/.devcontainer/post-create.log"
# Ensure workspace log exists and is writable (best-effort)
mkdir -p "$(dirname "$WORKSPACE_LOG")" 2>/dev/null || true
touch "$WORKSPACE_LOG" 2>/dev/null || true
# Tee to both /tmp and workspace log so it's inspectable in Codespaces UI
exec > >(tee -a "$LOG" "$WORKSPACE_LOG") 2>&1

# Optional verbose tracing: set POST_CREATE_VERBOSE=1 to enable `set -x`
if [ "${POST_CREATE_VERBOSE:-0}" = "1" ]; then
  echo "[POST-CREATE] Verbose tracing enabled"
  set -x
fi

# Timestamp helper
ts() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }
log() { echo "$(ts) [POST-CREATE] $*"; }

log "Starting postCreateCommand... (logging to $LOG and $WORKSPACE_LOG)"

# Configuration with defaults
DATOMIC_TYPE="${DATOMIC_TYPE:-pro}"
RAW_DATOMIC_VERSION="${DATOMIC_VERSION:-1.0.7482}"
# basename in case a path was provided (e.g., /tmp/datomic-pro-1.0.7482.zip)
RAW_DATOMIC_VERSION="$(basename "$RAW_DATOMIC_VERSION")"
# strip leading prefix and trailing .zip if present
DATOMIC_VERSION="${RAW_DATOMIC_VERSION#datomic-${DATOMIC_TYPE}-}"
DATOMIC_VERSION="${DATOMIC_VERSION%.zip}"

log "Using DATOMIC_TYPE=$DATOMIC_TYPE, DATOMIC_VERSION=$DATOMIC_VERSION"

# Paths
TARGET_DIR="lib/com/datomic/datomic-${DATOMIC_TYPE}/${DATOMIC_VERSION}"
ZIP_NAME="datomic-${DATOMIC_TYPE}-${DATOMIC_VERSION}.zip"
DOWNLOAD_URL="https://datomic-pro-downloads.s3.amazonaws.com/${DATOMIC_VERSION}/${ZIP_NAME}"

# Clean and prepare target directory
if [ -d "${TARGET_DIR}" ]; then
  log "Removing existing installation at ${TARGET_DIR}"
  rm -rf "${TARGET_DIR}"
fi
mkdir -p "${TARGET_DIR}"

# Find Datomic zip in /lib/ or /tmp/, download if missing
ZIP_PATH="/lib/${ZIP_NAME}"
if [ ! -f "$ZIP_PATH" ]; then
  ZIP_PATH="/tmp/${ZIP_NAME}"
  if [ ! -f "$ZIP_PATH" ]; then
    log "Downloading Datomic from $DOWNLOAD_URL"
    curl --fail --location --progress-bar -o "$ZIP_PATH" "$DOWNLOAD_URL"
  fi
fi

# Verify zip integrity (handles corrupt/incomplete downloads)
if ! unzip -t "$ZIP_PATH" >/dev/null 2>&1; then
  log "Corrupt or incomplete zip detected, removing and re-downloading..."
  rm -f "$ZIP_PATH"
  curl --fail --location --progress-bar -o "$ZIP_PATH" "$DOWNLOAD_URL"

  # Verify the re-download
  if ! unzip -t "$ZIP_PATH" >/dev/null 2>&1; then
    log "ERROR: Re-downloaded zip is still corrupt. Check network connection or URL."
    log "URL: $DOWNLOAD_URL"
    exit 1
  fi
fi

# Unzip Datomic distribution into target dir
log "Extracting $ZIP_PATH to $TARGET_DIR"
unzip -q "$ZIP_PATH" -d "${TARGET_DIR}"

# Flatten if needed (some zips nest contents in a subdirectory)
TOP_SUBDIR=$(find "${TARGET_DIR}" -mindepth 1 -maxdepth 1 -type d -print -quit || true)
if [ -n "${TOP_SUBDIR}" ] && [ -z "$(find "${TARGET_DIR}" -maxdepth 1 -type f -print -quit)" ]; then
  log "Flattening nested directory structure"
  mv "${TOP_SUBDIR}"/* "${TARGET_DIR}/"
  rmdir "${TOP_SUBDIR}"
fi

# Run vendor maven-install
if [ -x "${TARGET_DIR}/bin/maven-install" ]; then
  log "Running maven-install..."
  (cd "${TARGET_DIR}" && bash bin/maven-install)
else
  log "ERROR: bin/maven-install not found or not executable in ${TARGET_DIR}/bin"
  exit 1
fi

log "Datomic ${DATOMIC_TYPE} ${DATOMIC_VERSION} installed successfully to ${TARGET_DIR}"
