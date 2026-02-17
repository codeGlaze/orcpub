#!/usr/bin/env bash
set -euo pipefail

# Create a user in the database (requires Datomic running)
# Uses :init-db profile for faster startup (skips ClojureScript)

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

# Use init-db profile for fast startup (no ClojureScript/Garden)
if [ "$override" = "verify" ]; then
  lein with-profile init-db run -m orcpub.dev-tools "$username" "$email" "$password" verify
else
  lein with-profile init-db run -m orcpub.dev-tools "$username" "$email" "$password"
fi
