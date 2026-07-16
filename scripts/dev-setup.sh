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

echo "Dev setup: NO_START=$NO_START SKIP_DATOMIC=$SKIP_DATOMIC SKIP_TEST_USER=$SKIP_TEST_USER START=$START"

# Start Datomic transactor if requested
if [ "$SKIP_DATOMIC" = false ]; then
  echo "Starting Datomic transactor..."
  "$SCRIPT_DIR/start.sh" datomic --quiet --idempotent || {
    echo "Datomic start failed; continuing but DB init may be skipped." >&2
  }
else
  echo "Skipping Datomic startup as requested."
fi

# Ensure dependencies are downloaded
echo "Running lein deps..."
lein deps

# Run idempotent DB initialization via dev/user.clj CLI entrypoint
echo "Initializing database (idempotent)..."
# Only attempt DB init if Datomic is reachable on the expected port
if timeout 1 bash -c '</dev/tcp/localhost/4334' >/dev/null 2>&1; then
  if lein with-profile init-db run -m user init-db; then
    echo "DB init succeeded."

    # Create a default test user so you can log in immediately after setup.
    # Credentials: test / test@example.com / testpass (already verified).
    # Idempotent: create-user! checks for existing email/username before inserting.
    if [ "$SKIP_TEST_USER" = false ]; then
      echo "Creating test user (test / test@test.com / testpass)..."
      # Log credentials before lein so the entry persists even if the JVM is slow to exit
      users_file="$SCRIPT_DIR/../.test-users"
      if [[ ! -f "$users_file" ]]; then
        echo "# Test users created by dev tooling (gitignored)" > "$users_file"
        echo "# username | email | password | status | created" >> "$users_file"
      fi
      echo "test | test@test.com | testpass | verified | $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$users_file"
      if lein with-profile init-db run -m user create-user test test@test.com testpass verify; then
        echo "Test user created and verified."
      else
        echo "Test user creation failed (may already exist)." >&2
      fi
    fi
  else
    echo "DB init failed but continuing (non-fatal)." >&2
  fi
else
  echo "Datomic not reachable on localhost:4334; skipping DB init (post-create will not fail)."
fi

if [ "$START" -eq 1 ] && [ "$NO_START" = false ]; then
  echo "Starting backend and figwheel in background..."
  "$SCRIPT_DIR/start.sh" server --background --quiet || true
  "$SCRIPT_DIR/start.sh" figwheel --background --quiet || true
  echo "Started server & figwheel (logs in ./logs/)"
else
  echo "Setup complete. To start services:"
  echo "  ./scripts/start.sh server"
  echo "  ./scripts/start.sh figwheel"
  echo "Or use the interactive menu:"
  echo "  ./menu"
fi

exit 0
