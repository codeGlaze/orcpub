#!/usr/bin/env bash
#
# Datomic Pro install script for devcontainer
#
# 1. Always remove lib/com/datomic/datomic-pro/<version> if it exists
# 2. Unzip the Datomic zip (from /lib or /tmp, download if missing) into lib/com/datomic/datomic-pro/<version>
# 3. Flatten top-level subdir if present (some zips nest contents)
# 4. Run vendor maven-install from bin/
#
# This script does NOT cherry-pick, rename, or check for specific files before extraction.
# All contents of the zip are placed in the target directory, overwriting any previous install.
#
set -euo pipefail

# Redirect all output to persistent logs for visibility during Codespace creation
LOG="/tmp/orcpub-post-create.log"
WORKSPACE_LOG="/workspaces/orcpub/.devcontainer/post-create.log"
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

# Timestamp and timing helpers
ts() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }
log() { echo "$(ts) [POST-CREATE] $*"; }
step_start() { STEP_START_TS=$(date +%s); }
step_done() { local name="$1"; local now=$(date +%s); local elapsed=$((now-STEP_START_TS)); log "STEP DONE: ${name} (elapsed ${elapsed}s)"; }

log "Starting postCreateCommand... (logging to $LOG and $WORKSPACE_LOG)"
DATOMIC_VERSION='1.0.7482'
DATOMIC_JAR="lib/com/datomic/datomic-pro/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.jar"

TARGET_DIR="lib/com/datomic/datomic-pro/${DATOMIC_VERSION}"
if [ -d "${TARGET_DIR}" ]; then
  rm -rf "${TARGET_DIR}"
fi
mkdir -p "${TARGET_DIR}"

# Find Datomic zip in /lib/ or /tmp/, download if missing
ZIP_PATH="/lib/datomic-pro-${DATOMIC_VERSION}.zip"
if [ ! -f "$ZIP_PATH" ]; then
  ZIP_PATH="/tmp/datomic-pro-${DATOMIC_VERSION}.zip"
  if [ ! -f "$ZIP_PATH" ]; then
    curl --fail --location --progress-bar -o "$ZIP_PATH" "https://datomic-pro-downloads.s3.amazonaws.com/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.zip"
  fi
fi

# 2. Unzip Datomic distribution directly into vendor dir
unzip -q "$ZIP_PATH" -d "${TARGET_DIR}"

# Flatten if needed
TOP_SUBDIR=$(find "${TARGET_DIR}" -mindepth 1 -maxdepth 1 -type d -print -quit || true)
if [ -n "${TOP_SUBDIR}" ] && [ -z "$(find "${TARGET_DIR}" -maxdepth 1 -type f -print -quit)" ]; then
  mv "${TOP_SUBDIR}"/* "${TARGET_DIR}/"
  rmdir "${TOP_SUBDIR}"
fi

# 3. Run vendor maven-install
if [ -x "${TARGET_DIR}/bin/maven-install" ]; then
  (cd "${TARGET_DIR}" && bash bin/maven-install)
else
  echo "ERROR: vendor bin/maven-install not found or not executable in ${TARGET_DIR}/bin"
  exit 1
fi
