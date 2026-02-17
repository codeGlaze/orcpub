#!/usr/bin/env bash
set -euo pipefail

# Create a user in the database (requires Datomic running).
# Uses :init-db profile for fast startup (skips ClojureScript/Garden).
# The CLI entrypoint is dev/user.clj -main with the "create-user" command.
# Duplicate checking (email/username) happens inside create-user!.

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <username> <email> <password> [verify]"
  echo "Example: $0 testuser test@example.com s3cret verify"
  echo ""
  echo "Options:"
  echo "  verify    Mark user as verified (can log in immediately)"
  exit 1
fi

username="$1"
email="$2"
password="$3"
shift 3

override="${1:-}" # optional "verify"

# init-db profile: loads src/clj + src/cljc + dev/, skips CLJS/Garden prep-tasks
if [ "$override" = "verify" ]; then
  lein with-profile init-db run -m user create-user "$username" "$email" "$password" verify
else
  lein with-profile init-db run -m user create-user "$username" "$email" "$password"
fi
