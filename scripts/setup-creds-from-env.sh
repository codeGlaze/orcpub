#!/usr/bin/env bash
# scripts/setup-creds-from-env.sh
# Create Leiningen credentials file from Codespace environment variables
# Run this in your Codespace terminal (not via SSH)

set -euo pipefail

if [ -z "${LEIN_USERNAME:-}" ] || [ -z "${LEIN_PASSWORD:-}" ]; then
  echo "❌ LEIN_USERNAME or LEIN_PASSWORD not set in environment"
  echo "   Make sure you're running this in the Codespace terminal (not via SSH)"
  exit 1
fi

mkdir -p ~/.lein

cat > ~/.lein/credentials.clj <<EOF
{#"my\\.datomic\\.com" {:username "$LEIN_USERNAME" :password "$LEIN_PASSWORD"}}
EOF

echo "✅ Credentials file created at ~/.lein/credentials.clj"
echo "Testing Datomic Pro dependency fetch..."

cd "$(dirname "$0")/.."
lein deps 2>&1 | tail -20
