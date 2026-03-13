#!/usr/bin/env bash
# ===========================================================================
# Datomic Transactor Startup
# ===========================================================================
# Substitutes secrets into transactor.properties.template and launches the
# transactor. Uses pure bash sed (no envsubst/gettext — Alpine doesn't have it).
#
# Secret resolution order (per variable):
#   1. Docker secret file at /run/secrets/<name>  (Swarm / compose secrets)
#   2. Environment variable                       (compose environment / .env)
#   3. (none — required vars exit 1 if missing)
#
# Required: ADMIN_PASSWORD, DATOMIC_PASSWORD
# Optional: ALT_HOST, ENCRYPT_CHANNEL,
#           ADMIN_PASSWORD_OLD, DATOMIC_PASSWORD_OLD
# ===========================================================================

set -euo pipefail

TEMPLATE="/datomic/transactor.properties.template"
OUTPUT="/datomic/transactor.properties"

# --- Read Docker secrets (if mounted) ----------------------------------------
# Docker secrets are files at /run/secrets/<name>. When present, the secret
# file OVERRIDES any env var — this matches the resolution order documented
# above and in config.clj. If no secret file exists, the env var is used as-is.

read_secret() {
  local var_name="$1"
  local secret_file="/run/secrets/${2:-$(echo "$var_name" | tr '[:upper:]' '[:lower:]')}"
  if [ -f "$secret_file" ]; then
    local val
    val=$(<"$secret_file")
    # Strip trailing whitespace: \r (Windows), \n (common in secret files)
    val="${val%$'\r\n'}"
    val="${val%$'\n'}"
    val="${val%$'\r'}"
    export "$var_name=$val"
  fi
}

read_secret ADMIN_PASSWORD
read_secret DATOMIC_PASSWORD
read_secret ADMIN_PASSWORD_OLD
read_secret DATOMIC_PASSWORD_OLD

# --- Validate required secrets ------------------------------------------------

if [ -z "${ADMIN_PASSWORD:-}" ]; then
  echo "ERROR: ADMIN_PASSWORD not set (checked env var and /run/secrets/admin_password)."
  exit 1
fi

if [ -z "${DATOMIC_PASSWORD:-}" ]; then
  echo "ERROR: DATOMIC_PASSWORD not set (checked env var and /run/secrets/datomic_password)."
  exit 1
fi

# --- Substitute env vars into template ----------------------------------------
# Pipe delimiter in sed avoids conflicts with passwords containing /.
# escape_sed_replacement handles \, &, and | in values so passwords with
# special characters don't break or corrupt the substitution.

escape_sed_replacement() {
  # Escape: backslash first (so we don't double-escape), then & and |
  printf '%s' "$1" | sed -e 's/[\\]/\\&/g' -e 's/[&|]/\\&/g'
}

ALT_HOST="${ALT_HOST:-127.0.0.1}"
ENCRYPT_CHANNEL="${ENCRYPT_CHANNEL:-true}"

sed \
  -e "s|\${ADMIN_PASSWORD}|$(escape_sed_replacement "$ADMIN_PASSWORD")|g" \
  -e "s|\${DATOMIC_PASSWORD}|$(escape_sed_replacement "$DATOMIC_PASSWORD")|g" \
  -e "s|\${ALT_HOST:-127.0.0.1}|$(escape_sed_replacement "$ALT_HOST")|g" \
  -e "s|\${ENCRYPT_CHANNEL:-true}|$(escape_sed_replacement "$ENCRYPT_CHANNEL")|g" \
  "$TEMPLATE" > "$OUTPUT"

chmod 600 "$OUTPUT"

# --- Password rotation (conditional) -----------------------------------------
# Append old-password lines only when rotation env vars are set.
# The transactor accepts both old and new passwords during the transition.

if [ -n "${ADMIN_PASSWORD_OLD:-}" ]; then
  if [[ "${ADMIN_PASSWORD_OLD}" == *$'\n'* ]]; then
    echo "ERROR: ADMIN_PASSWORD_OLD contains newline characters." >&2; exit 1
  fi
  echo "old-storage-admin-password=${ADMIN_PASSWORD_OLD}" >> "$OUTPUT"
fi

if [ -n "${DATOMIC_PASSWORD_OLD:-}" ]; then
  if [[ "${DATOMIC_PASSWORD_OLD}" == *$'\n'* ]]; then
    echo "ERROR: DATOMIC_PASSWORD_OLD contains newline characters." >&2; exit 1
  fi
  echo "old-storage-datomic-password=${DATOMIC_PASSWORD_OLD}" >> "$OUTPUT"
fi

echo "Transactor config written to ${OUTPUT}"

# --- Fix bind-mount ownership ------------------------------------------------
# Build-time chown is overridden by Docker bind mounts — the host directory's
# ownership takes over. Fix at runtime before dropping privileges.
# This is the standard entrypoint-chown-drop pattern (cf. postgres, redis).
chown -R datomic:datomic /data /log /backups /datomic

# --- Launch transactor --------------------------------------------------------
# su-exec drops from root to datomic user. exec replaces the shell so the
# transactor is PID 1 and receives Docker signals (SIGTERM) directly.
# su-exec is Alpine's lightweight alternative to gosu — it execs directly
# without an intermediate process.

exec su-exec datomic /datomic/bin/transactor "$OUTPUT"
