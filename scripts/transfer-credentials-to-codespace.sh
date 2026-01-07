#!/usr/bin/env bash
# scripts/transfer-credentials-to-codespace.sh
# Transfer Leiningen credentials from local machine to Codespace
# Usage: ./scripts/transfer-credentials-to-codespace.sh <codespace-name>

set -euo pipefail

CODESPACE_NAME="${1:-}"
LOCAL_CREDS_FILE="${HOME}/.lein/credentials.clj"
LOCAL_CREDS_GPG="${HOME}/.lein/credentials.clj.gpg"

if [ -z "$CODESPACE_NAME" ]; then
  echo "Usage: $0 <codespace-name>"
  echo ""
  echo "Example:"
  echo "  $0 psychic-cod-4j74w44vxvr3764j"
  echo ""
  echo "Or list your Codespaces:"
  echo "  gh codespace list"
  exit 1
fi

# Check if local credentials exist
if [ ! -f "$LOCAL_CREDS_FILE" ] && [ ! -f "$LOCAL_CREDS_GPG" ]; then
  echo "❌ No credentials file found locally."
  echo ""
  echo "Expected location:"
  echo "  $LOCAL_CREDS_FILE"
  echo "  or"
  echo "  $LOCAL_CREDS_GPG"
  echo ""
  echo "Create it first with:"
  echo "  mkdir -p ~/.lein"
  echo "  cat > ~/.lein/credentials.clj << 'EOF'"
  echo '  {#"my\\.datomic\\.com" {:username "your-email@example.com" :password "your-key"}}'
  echo "  EOF"
  exit 1
fi

# Determine which file to transfer
if [ -f "$LOCAL_CREDS_GPG" ]; then
  CREDS_FILE="$LOCAL_CREDS_GPG"
  REMOTE_PATH="~/.lein/credentials.clj.gpg"
  echo "📦 Found GPG-encrypted credentials file"
elif [ -f "$LOCAL_CREDS_FILE" ]; then
  CREDS_FILE="$LOCAL_CREDS_FILE"
  REMOTE_PATH="~/.lein/credentials.clj"
  echo "📦 Found plain credentials file"
  echo "⚠️  Warning: Plain credentials file. Consider encrypting with GPG."
fi

echo "Transferring to Codespace: $CODESPACE_NAME"
echo "  From: $CREDS_FILE"
echo "  To:   $REMOTE_PATH"

# Use gh codespace cp to transfer
gh codespace cp "$CREDS_FILE" "$CODESPACE_NAME:$REMOTE_PATH" || {
  echo "❌ Transfer failed. Trying alternative method..."
  
  # Alternative: use scp via gh codespace ssh
  echo "Attempting via SSH..."
  gh codespace ssh -c "$CODESPACE_NAME" -- "mkdir -p ~/.lein"
  cat "$CREDS_FILE" | gh codespace ssh -c "$CODESPACE_NAME" -- "cat > $REMOTE_PATH"
}

if [ $? -eq 0 ]; then
  echo "✅ Credentials transferred successfully!"
  echo ""
  echo "Testing in Codespace..."
  gh codespace ssh -c "$CODESPACE_NAME" -- "cd /workspaces/datomic-pro && lein deps :tree 2>&1 | grep -E '(datomic-pro|ERROR|401|403)' | head -5 || echo '✅ Dependency resolution successful'"
else
  echo "❌ Transfer failed. Please check:"
  echo "  1. Codespace name is correct"
  echo "  2. You have access to the Codespace"
  echo "  3. GitHub CLI is authenticated: gh auth status"
  exit 1
fi
