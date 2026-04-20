#!/usr/bin/env bash
# Reset Docker test environment to a clean state.
# Usage: ./test/docker/reset-test.sh [scenario]
#
# Scenarios:
#   fresh       — no .env, templated compose (default)
#   conflict    — no .env, hardcoded compose values that conflict with transactor
#   upgrade     — old-format .env (v1-free-localhost), templated compose
#   secrets     — modern .env with passwords, ready for --secrets
#
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$SCRIPT_DIR"

scenario="${1:-fresh}"

# --- Clean everything ---
rm -f .env .env.backup.* .env.secrets.backup
rm -rf secrets/ docker-compose.secrets.yaml
docker secret rm datomic_password admin_password signature 2>/dev/null || true
git checkout docker-compose.yaml 2>/dev/null || true
git checkout docker/transactor.properties.template 2>/dev/null || true

# H2 database has ADMIN_PASSWORD locked in at creation. A fresh .env with
# new passwords will crash the transactor unless the DB is wiped or backed up.
if [ -f data/db/datomic.mv.db ]; then
  echo ""
  echo "Existing H2 database found in data/db/."
  echo "The admin password is locked into this database."
  echo ""
  echo "  1) Back up to data/db.bak/ and wipe (can restore later)"
  echo "  2) Wipe data/db/ (permanent)"
  echo "  3) Keep it (next test may fail if passwords don't match)"
  echo ""
  printf "Choice [1]: "
  read -r _choice </dev/tty 2>/dev/null || _choice="1"
  _choice="${_choice:-1}"
  case "$_choice" in
    1)
      rm -rf data/db.bak
      mv data/db data/db.bak
      mkdir -p data/db
      echo "Moved data/db/ → data/db.bak/"
      ;;
    2)
      rm -rf data/db/*
      echo "Wiped data/db/"
      ;;
    3)
      echo "Keeping existing database"
      ;;
    *)
      echo "Invalid choice, keeping existing database"
      ;;
  esac
fi

case "$scenario" in
  fresh)
    echo "Reset: fresh (no .env, templated compose)"
    ;;
  conflict)
    echo "Reset: conflict (hardcoded compose vs transactor)"
    sed -i 's|DATOMIC_URL: ${DATOMIC_URL:-datomic:dev://datomic:4334/orcpub}|DATOMIC_URL: datomic:free://localhost:4334/orcpub?password=compose-pass|' docker-compose.yaml
    sed -i 's|DATOMIC_PASSWORD: ${DATOMIC_PASSWORD:-change-me}|DATOMIC_PASSWORD: compose-pass|g' docker-compose.yaml
    sed -i 's|SIGNATURE: ${SIGNATURE:-change-me-to-something-unique}|SIGNATURE: compose-signature-value|' docker-compose.yaml
    sed -i 's|ADMIN_PASSWORD: ${ADMIN_PASSWORD:-change-me-admin}|ADMIN_PASSWORD: compose-admin-pw|' docker-compose.yaml
    ;;
  upgrade)
    echo "Reset: upgrade (old v1 .env)"
    cp test/docker/fixtures/env-v1-free-localhost.env .env
    ;;
  secrets)
    echo "Reset: secrets (modern .env with passwords)"
    cp test/docker/fixtures/env-v3-current.env .env
    ;;
  *)
    echo "Unknown scenario: $scenario"
    echo "Options: fresh, conflict, upgrade, secrets"
    exit 1
    ;;
esac
echo "Done."
