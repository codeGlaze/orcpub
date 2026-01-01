#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <username> <email> <password> [verify]"
  echo "Example: $0 testuser test@example.com s3cret verify"
  exit 1
fi

username="$1"
email="$2"
password="$3"
shift 3

override="${1:-}" # optional "verify"

if [ "$override" = "verify" ]; then
  lein run -m orcpub.dev-tools "$username" "$email" "$password" verify
else
  lein run -m orcpub.dev-tools "$username" "$email" "$password"
fi
