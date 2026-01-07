#!/usr/bin/env bash
# scripts/create-local-credentials.sh
# Create Leiningen credentials file on your local machine
# This file can then be transferred to Codespaces when needed

set -euo pipefail

LEIN_DIR="$HOME/.lein"
CREDS_FILE="$LEIN_DIR/credentials.clj"
CREDS_GPG="$CREDS_FILE.gpg"

echo "Creating Leiningen credentials file locally..."
echo ""

# Create .lein directory if it doesn't exist
mkdir -p "$LEIN_DIR"

# Check if credentials already exist
if [ -f "$CREDS_FILE" ] || [ -f "$CREDS_GPG" ]; then
  echo "⚠️  Credentials file already exists."
  read -p "Overwrite? (y/N): " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
  fi
  rm -f "$CREDS_FILE" "$CREDS_GPG"
fi

# Get credentials
echo "Enter your my.datomic.com credentials:"
read -p "Username (email): " USERNAME
read -sp "Password/Download Key: " PASSWORD
echo

# Write plain credentials file
cat > "$CREDS_FILE" <<EOF
{#"my\\.datomic\\.com" {:username "$USERNAME" :password "$PASSWORD"}}
EOF

echo "✅ Credentials file created at $CREDS_FILE"

# Optionally encrypt with GPG
read -p "Encrypt with GPG? (recommended) (Y/n): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Nn]$ ]]; then
  if command -v gpg &> /dev/null; then
    echo "Encrypting credentials..."
    gpg --default-recipient-self -e "$CREDS_FILE"
    rm -f "$CREDS_FILE"
    echo "✅ Encrypted credentials file created at $CREDS_GPG"
    echo "   Original file removed for security."
  else
    echo "⚠️  GPG not found. Keeping plain credentials file."
    echo "   Install GPG to encrypt (varies by OS)"
  fi
fi

echo ""
echo "📋 Next steps:"
echo "  1. Transfer to Codespace:"
echo "     bash scripts/transfer-credentials-to-codespace.sh <codespace-name>"
echo ""
echo "  2. Or manually copy:"
echo "     gh codespace cp $CREDS_FILE <codespace-name>:~/.lein/credentials.clj"
echo ""
echo "  3. List your Codespaces:"
echo "     gh codespace list"
