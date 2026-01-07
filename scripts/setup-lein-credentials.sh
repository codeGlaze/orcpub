#!/usr/bin/env bash
# scripts/setup-lein-credentials.sh
# Set up Leiningen credentials file for Datomic Pro access

set -euo pipefail

LEIN_DIR="$HOME/.lein"
CREDS_FILE="$LEIN_DIR/credentials.clj"
CREDS_GPG="$CREDS_FILE.gpg"

echo "Setting up Leiningen credentials for Datomic Pro..."

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
    echo "   Install GPG to encrypt: sudo apk add gnupg (Alpine) or sudo apt-get install gnupg (Debian)"
  fi
fi

echo ""
echo "Testing connection..."
cd "$(dirname "$0")/.."

if lein deps :tree 2>&1 | grep -q "datomic-pro"; then
  echo "✅ Success! Datomic Pro dependency can be resolved."
else
  echo "⚠️  Could not verify Datomic Pro access."
  echo "   You can test manually with: lein deps"
fi
