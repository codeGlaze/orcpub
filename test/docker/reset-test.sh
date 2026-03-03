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
