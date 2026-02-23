#!/usr/bin/env bash
# ===========================================================================
# Datomic Transactor Startup
# ===========================================================================
# Substitutes secrets into transactor.properties.template and launches the
# transactor. Uses pure bash sed (no envsubst/gettext — Alpine doesn't have it).
#
# Required env vars: ADMIN_PASSWORD, DATOMIC_PASSWORD
# Optional env vars: ALT_HOST, ENCRYPT_CHANNEL,
#                    ADMIN_PASSWORD_OLD, DATOMIC_PASSWORD_OLD
# ===========================================================================

set -euo pipefail

TEMPLATE="/datomic/transactor.properties.template"
OUTPUT="/datomic/transactor.properties"

# --- Validate required secrets ------------------------------------------------

if [ -z "${ADMIN_PASSWORD:-}" ]; then
  echo "ERROR: ADMIN_PASSWORD not set."
  echo "See https://docs.datomic.com/on-prem/configuring-embedded.html#sec-2-1"
  exit 1
fi

if [ -z "${DATOMIC_PASSWORD:-}" ]; then
  echo "ERROR: DATOMIC_PASSWORD not set."
  echo "See https://docs.datomic.com/on-prem/configuring-embedded.html#sec-2-1"
  exit 1
fi

# --- Substitute env vars into template ----------------------------------------
# Pipe delimiter avoids conflicts with passwords containing / or &.
# Only known variables are substituted — no risk of expanding Datomic's own
# ${...} patterns (there are none in the template, but this is defensive).

ALT_HOST="${ALT_HOST:-127.0.0.1}"
ENCRYPT_CHANNEL="${ENCRYPT_CHANNEL:-true}"

sed \
  -e "s|\${ADMIN_PASSWORD}|${ADMIN_PASSWORD}|g" \
  -e "s|\${DATOMIC_PASSWORD}|${DATOMIC_PASSWORD}|g" \
  -e "s|\${ALT_HOST:-127.0.0.1}|${ALT_HOST}|g" \
  -e "s|\${ENCRYPT_CHANNEL:-true}|${ENCRYPT_CHANNEL}|g" \
  "$TEMPLATE" > "$OUTPUT"

# --- Password rotation (conditional) -----------------------------------------
# Append old-password lines only when rotation env vars are set.
# The transactor accepts both old and new passwords during the transition.

if [ -n "${ADMIN_PASSWORD_OLD:-}" ]; then
  echo "old-storage-admin-password=${ADMIN_PASSWORD_OLD}" >> "$OUTPUT"
fi

if [ -n "${DATOMIC_PASSWORD_OLD:-}" ]; then
  echo "old-storage-datomic-password=${DATOMIC_PASSWORD_OLD}" >> "$OUTPUT"
fi

echo "Transactor config written to ${OUTPUT}"

# --- Launch transactor --------------------------------------------------------
# exec replaces the shell so the transactor is PID 1 and receives Docker
# signals (SIGTERM on docker stop) directly.

exec /datomic/bin/transactor "$OUTPUT"
