#!/usr/bin/env bash
set -euo pipefail

# Create a user in the database (requires Datomic running).
# Uses :init-db profile for fast startup (skips ClojureScript/Garden).
# The CLI entrypoint is dev/user.clj -main with the "create-user" command.
# Duplicate checking (email/username) happens inside create-user!.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Source common.sh for shared config and logging
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <username> <email> <password> [verify]"
  echo "Example: $0 testuser test@example.com s3cret verify"
  echo ""
  echo "Options:"
  echo "  verify    Mark user as verified (can log in immediately)"
  exit "$EXIT_USAGE"
fi

username="$1"
email="$2"
password="$3"
shift 3

override="${1:-}" # optional "verify"

# Append credentials to .test-users (gitignored) so you can look them up later
log_test_user() {
  local file="$REPO_ROOT/.test-users"
  if [[ ! -f "$file" ]]; then
    echo "# Test users created by dev tooling (gitignored)" > "$file"
    echo "# username | email | password | status | created" >> "$file"
  fi
  echo "$1 | $2 | $3 | $4 | $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$file"
}

# Log credentials before lein so the entry persists even if the JVM is slow to exit
if [ "$override" = "verify" ]; then
  log_test_user "$username" "$email" "$password" "verified"
  lein with-profile init-db run -m user create-user "$username" "$email" "$password" verify
else
  log_test_user "$username" "$email" "$password" "unverified"
  lein with-profile init-db run -m user create-user "$username" "$email" "$password"
fi
