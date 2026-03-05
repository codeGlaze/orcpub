#!/usr/bin/env bash
#
# Test run --upgrade against historical .env formats.
#
# Copies each fixture to a temp directory as .env, runs --upgrade --auto,
# then validates the result. No Docker daemon needed — only tests the
# .env transformation logic.
#
# Usage:
#   ./test/docker/test-upgrade.sh           # Run all fixtures
#   ./test/docker/test-upgrade.sh <name>    # Run one fixture (e.g., "v1-free-localhost")

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FIXTURE_DIR="${SCRIPT_DIR}/fixtures"
SETUP_SCRIPT="${PROJECT_ROOT}/run"

# Colors
green='\033[0;32m'
red='\033[0;31m'
yellow='\033[1;33m'
cyan='\033[0;36m'
reset='\033[0m'

pass() { printf '%sPASS%s  %s\n' "$green" "$reset" "$*"; }
fail() { printf '%sFAIL%s  %s\n' "$red" "$reset" "$*"; FAILURES=$((FAILURES + 1)); }
info() { printf '%sINFO%s  %s\n' "$cyan" "$reset" "$*"; }
warn() { printf '%sWARN%s  %s\n' "$yellow" "$reset" "$*"; }

TESTS=0
FAILURES=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

# Read a value from an .env file (same logic as run)
read_val() {
  grep -m1 "^${1}=" "$2" 2>/dev/null | cut -d= -f2- | tr -d '\r'
}

# Check a value in the upgraded .env
assert_val() {
  local var="$1" expected="$2" file="$3" label="${4:-}"
  local actual
  actual=$(read_val "$var" "$file")

  if [ "$actual" = "$expected" ]; then
    pass "${label}${var} = ${expected}"
  else
    fail "${label}${var}: expected '${expected}', got '${actual}'"
  fi
  TESTS=$((TESTS + 1))
}

# Check that a variable exists (any value)
assert_exists() {
  local var="$1" file="$2" label="${3:-}"
  if grep -q "^${var}=" "$file"; then
    pass "${label}${var} exists"
  else
    fail "${label}${var} missing"
  fi
  TESTS=$((TESTS + 1))
}

# Check that a variable does NOT exist
assert_missing() {
  local var="$1" file="$2" label="${3:-}"
  if grep -q "^${var}=" "$file"; then
    fail "${label}${var} should not exist"
  else
    pass "${label}${var} absent (expected)"
  fi
  TESTS=$((TESTS + 1))
}

# Check that a value does NOT contain a substring
assert_not_contains() {
  local var="$1" substring="$2" file="$3" label="${4:-}"
  local actual
  actual=$(read_val "$var" "$file")
  if [[ "$actual" == *"$substring"* ]]; then
    fail "${label}${var} should not contain '${substring}' (got '${actual}')"
  else
    pass "${label}${var} does not contain '${substring}'"
  fi
  TESTS=$((TESTS + 1))
}

# Check that output contains a string
assert_output_contains() {
  local needle="$1" output="$2" label="${3:-}"
  if echo "$output" | grep -qF "$needle"; then
    pass "${label}output contains '${needle}'"
  else
    fail "${label}output missing '${needle}'"
  fi
  TESTS=$((TESTS + 1))
}

# ---------------------------------------------------------------------------
# Run one fixture through --upgrade --auto
# ---------------------------------------------------------------------------

run_fixture() {
  local fixture_file="$1"
  local fixture_name
  fixture_name=$(basename "$fixture_file" .env | sed 's/^env-//')

  local tmpdir
  tmpdir=$(mktemp -d)
  # Don't trap RETURN — we clean up at the end of the function

  # Copy fixture as .env and the setup script into tmpdir.
  # run uses SCRIPT_DIR (dirname of the script) to find .env,
  # so the script must live next to the .env for it to find the fixture.
  cp "$fixture_file" "${tmpdir}/.env"
  cp "$SETUP_SCRIPT" "${tmpdir}/run"

  local output
  output=$(bash "${tmpdir}/run" --upgrade --auto 2>&1) || true

  local result_env="${tmpdir}/.env"
  local label="[${fixture_name}] "

  printf '\n%s--- %s ---%s\n' "$cyan" "$fixture_name" "$reset"

  case "$fixture_name" in

    v1-free-localhost)
      # Password extracted from URL
      assert_exists DATOMIC_PASSWORD "$result_env" "$label"
      assert_val DATOMIC_PASSWORD "changeme" "$result_env" "$label"
      # URL cleaned: no password, free→dev, localhost→datomic
      assert_not_contains DATOMIC_URL "password=" "$result_env" "$label"
      assert_not_contains DATOMIC_URL "datomic:free://" "$result_env" "$label"
      assert_not_contains DATOMIC_URL "localhost" "$result_env" "$label"
      assert_val DATOMIC_URL "datomic:dev://datomic:4334/orcpub" "$result_env" "$label"
      # Output mentions what was fixed
      assert_output_contains "datomic:free://" "$output" "$label"
      assert_output_contains "localhost" "$output" "$label"
      # SIGNATURE and ADMIN_PASSWORD auto-generated
      assert_exists SIGNATURE "$result_env" "$label"
      assert_exists ADMIN_PASSWORD "$result_env" "$label"
      ;;

    v2-password-in-url)
      # Password extracted, URL cleaned
      assert_val DATOMIC_PASSWORD "MyD4tom1cP4ss" "$result_env" "$label"
      assert_not_contains DATOMIC_URL "password=" "$result_env" "$label"
      # Existing vars untouched
      assert_val ADMIN_PASSWORD "MyAdm1nP4ss" "$result_env" "$label"
      assert_val SIGNATURE "a7b3c9d2e1f4a8b6c3d7e2f5a9b4c8d1" "$result_env" "$label"
      assert_val PORT "8890" "$result_env" "$label"
      ;;

    v2-password-mismatch)
      # URL password wins over DATOMIC_PASSWORD
      assert_val DATOMIC_PASSWORD "the-REAL-password" "$result_env" "$label"
      assert_not_contains DATOMIC_URL "password=" "$result_env" "$label"
      # Warn about mismatch
      assert_output_contains "differs" "$output" "$label"
      ;;

    v2-missing-vars)
      # URL was already clean
      assert_val DATOMIC_URL "datomic:dev://datomic:4334/orcpub" "$result_env" "$label"
      # Missing vars auto-generated
      assert_exists DATOMIC_PASSWORD "$result_env" "$label"
      assert_exists SIGNATURE "$result_env" "$label"
      # Existing var untouched
      assert_val ADMIN_PASSWORD "MyAdm1nP4ss" "$result_env" "$label"
      # SMTP config preserved
      assert_val EMAIL_SERVER_URL "smtp.example.com" "$result_env" "$label"
      ;;

    v2-windows-crlf)
      # CRLF shouldn't corrupt values
      assert_val DATOMIC_PASSWORD "WinD4tom1cP4ss" "$result_env" "$label"
      assert_not_contains DATOMIC_URL "password=" "$result_env" "$label"
      assert_val SIGNATURE "w1nd0ws3d1t3d4s1gn4tur3k3y0000" "$result_env" "$label"
      assert_val ADMIN_PASSWORD "WinAdm1nP4ss" "$result_env" "$label"
      ;;

    v3-current)
      # No changes needed
      assert_output_contains "No changes needed" "$output" "$label"
      # All values preserved exactly
      assert_val DATOMIC_PASSWORD "aL5nR8sU2wY4bD7fH9jM3qT6" "$result_env" "$label"
      assert_val DATOMIC_URL "datomic:dev://datomic:4334/orcpub" "$result_env" "$label"
      assert_val SIGNATURE "gZ4kP7tX2vA8dG5jN9qS3wF6mC1rY8bH" "$result_env" "$label"
      assert_val ADMIN_PASSWORD "xK7mN2pQ9rT4vW8yB3cF6hJ" "$result_env" "$label"
      ;;

    production-like)
      # Password extracted, URL cleaned
      assert_val DATOMIC_PASSWORD "prod-db-placeholder" "$result_env" "$label"
      assert_not_contains DATOMIC_URL "password=" "$result_env" "$label"
      # Non-password config preserved
      assert_val ADMIN_PASSWORD "prod-admin-placeholder" "$result_env" "$label"
      assert_val SIGNATURE "prod-signature-placeholder-long-enough" "$result_env" "$label"
      assert_val EMAIL_SERVER_URL "smtp.production.example.com" "$result_env" "$label"
      assert_val INIT_ADMIN_USER "dmadmin" "$result_env" "$label"
      assert_val DEV_MODE "false" "$result_env" "$label"
      ;;

    *)
      warn "No assertions defined for fixture: $fixture_name"
      ;;
  esac

  rm -rf "$tmpdir"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if [ ! -f "$SETUP_SCRIPT" ]; then
  echo "run not found at: $SETUP_SCRIPT" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Test --secrets compose override generation
# ---------------------------------------------------------------------------

run_secrets_test() {
  printf '\n%s--- secrets (compose override) ---%s\n' "$cyan" "$reset"

  local tmpdir
  tmpdir=$(mktemp -d)

  # Use v3-current as the base .env (already up to date)
  cp "${FIXTURE_DIR}/env-v3-current.env" "${tmpdir}/.env"
  cp "$SETUP_SCRIPT" "${tmpdir}/run"

  local output
  output=$(bash "${tmpdir}/run" --secrets --auto 2>&1) || true

  local label="[secrets] "

  # Secret files created
  if [ -f "${tmpdir}/secrets/datomic_password" ]; then
    pass "${label}secrets/datomic_password created"
  else
    fail "${label}secrets/datomic_password missing"
  fi
  TESTS=$((TESTS + 1))

  if [ -f "${tmpdir}/secrets/signature" ]; then
    pass "${label}secrets/signature created"
  else
    fail "${label}secrets/signature missing"
  fi
  TESTS=$((TESTS + 1))

  # Compose override created
  if [ -f "${tmpdir}/docker-compose.secrets.yaml" ]; then
    pass "${label}docker-compose.secrets.yaml created"
  else
    fail "${label}docker-compose.secrets.yaml missing"
  fi
  TESTS=$((TESTS + 1))

  # Compose override has file-based secrets (not external)
  if [ -f "${tmpdir}/docker-compose.secrets.yaml" ]; then
    assert_output_contains "file: ./secrets/datomic_password" \
      "$(cat "${tmpdir}/docker-compose.secrets.yaml")" "$label"
    assert_output_contains "file: ./secrets/signature" \
      "$(cat "${tmpdir}/docker-compose.secrets.yaml")" "$label"
  fi

  # COMPOSE_FILE added to .env
  assert_exists COMPOSE_FILE "${tmpdir}/.env" "$label"
  assert_val COMPOSE_FILE "docker-compose.yaml:docker-compose.secrets.yaml" \
    "${tmpdir}/.env" "$label"

  rm -rf "$tmpdir"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

# Run specific fixture or all
if [ $# -gt 0 ]; then
  if [ "$1" = "secrets" ]; then
    run_secrets_test
  else
    fixture="${FIXTURE_DIR}/env-${1}.env"
    if [ ! -f "$fixture" ]; then
      echo "Fixture not found: $fixture" >&2
      echo "Available:" >&2
      ls "$FIXTURE_DIR"/*.env 2>/dev/null | sed 's/.*env-/  /;s/\.env$//' >&2
      echo "  secrets" >&2
      exit 1
    fi
    run_fixture "$fixture"
  fi
else
  for f in "$FIXTURE_DIR"/env-*.env; do
    run_fixture "$f"
  done
  run_secrets_test
fi

# Summary
printf '\n%s===%s Results: %d tests, ' "$cyan" "$reset" "$TESTS"
if [ "$FAILURES" -eq 0 ]; then
  printf '%s0 failures%s\n' "$green" "$reset"
else
  printf '%s%d failure(s)%s\n' "$red" "$FAILURES" "$reset"
  exit 1
fi
