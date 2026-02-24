#!/usr/bin/env bash
set -euo pipefail

# scripts/dev-setup.sh
# Usage: ./scripts/dev-setup.sh [--no-start] [--skip-datomic] [--start] [--no-test-user]
#
# This script orchestrates initial dev environment setup:
# 1. Start Datomic (if not skipped)
# 2. Run lein deps
# 3. Initialize database + apply schema
# 4. Create a verified test user (unless --no-test-user)
# 5. Optionally start server/figwheel

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Source common.sh for shared config (DATOMIC_PORT, logging, etc.)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

NO_START=false
SKIP_DATOMIC=false
SKIP_TEST_USER=false
START=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-start) NO_START=true; shift ;;
    --skip-datomic) SKIP_DATOMIC=true; shift ;;
    --no-test-user) SKIP_TEST_USER=true; shift ;;
    --start) START=1; shift ;;
    -h|--help)
      cat <<EOF
Usage: $0 [--no-start] [--skip-datomic] [--start] [--no-test-user]

Options:
  --no-start       Only perform setup steps (deps, DB, test user) and do NOT start servers (default)
  --skip-datomic   Don't attempt to start Datomic
  --no-test-user   Skip creating the default test user
  --start          After setup, start the backend and figwheel in background (not recommended in postCreate)
EOF
      exit 0 ;;
    *) echo "Unknown argument: $1"; exit 1 ;;
  esac
done

log_info "Dev setup: NO_START=$NO_START SKIP_DATOMIC=$SKIP_DATOMIC SKIP_TEST_USER=$SKIP_TEST_USER START=$START"

# Start Datomic transactor if requested
if [ "$SKIP_DATOMIC" = false ]; then
  log_info "Starting Datomic transactor..."
  "$SCRIPT_DIR/start.sh" datomic --quiet --idempotent || {
    log_warn "Datomic start failed; continuing but DB init may be skipped."
  }
else
  log_info "Skipping Datomic startup as requested."
fi

# Ensure dependencies are downloaded
log_info "Running lein deps..."
lein deps

# Run idempotent DB initialization via dev/user.clj CLI entrypoint
log_info "Initializing database (idempotent)..."
# Only attempt DB init if Datomic is reachable on the expected port
if port_in_use "$DATOMIC_PORT"; then
  if lein with-profile init-db run -m user init-db; then
    log_info "DB init succeeded."

    # Create a default test user so you can log in immediately after setup.
    # Credentials: test / test@example.com / testpass (already verified).
    # Idempotent: create-user! checks for existing email/username before inserting.
    if [ "$SKIP_TEST_USER" = false ]; then
      log_info "Creating test user (test / test@test.com / testpass)..."
      # Log credentials before lein so the entry persists even if the JVM is slow to exit
      users_file="$REPO_ROOT/.test-users"
      if [[ ! -f "$users_file" ]]; then
        echo "# Test users created by dev tooling (gitignored)" > "$users_file"
        echo "# username | email | password | status | created" >> "$users_file"
      fi
      echo "test | test@test.com | testpass | verified | $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$users_file"
      if lein with-profile init-db run -m user create-user test test@test.com testpass verify; then
        log_info "Test user created and verified."
      else
        log_warn "Test user creation failed (may already exist)."
      fi
    fi
  else
    log_warn "DB init failed but continuing (non-fatal)."
  fi
else
  log_warn "Datomic not reachable on port $DATOMIC_PORT; skipping DB init."
fi

if [ "$START" -eq 1 ] && [ "$NO_START" = false ]; then
  log_info "Starting backend and figwheel in background..."
  "$SCRIPT_DIR/start.sh" server --background --quiet || true
  "$SCRIPT_DIR/start.sh" figwheel --background --quiet || true
  log_info "Started server & figwheel (logs in $LOG_DIR/)"
else
  log_info "Setup complete. To start services:"
  log_info "  ./scripts/start.sh server"
  log_info "  ./scripts/start.sh figwheel"
  log_info "Or use the interactive menu:"
  log_info "  ./menu"
fi

exit 0
