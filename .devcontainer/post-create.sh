#!/usr/bin/env bash
set -euo pipefail

echo "=== OrcPub Development Container Setup ==="

# Verify Java 8
echo "Checking Java version..."
java -version 2>&1 | head -2
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
if [[ "${JAVA_VER}" != 1.8* ]]; then
  echo "WARNING: Expected Java 8, found $JAVA_VER"
else
  echo "✓ Java 8 verified"
fi

# Verify Leiningen
echo "Checking Leiningen..."
lein --version
echo "✓ Leiningen verified"

# Extract Datomic if not already extracted
DATOMIC_DIR="lib/datomic-free-0.9.5703"
DATOMIC_ARCHIVE="lib/datomic-free-0.9.5703.tar.gz"

if [ -f "$DATOMIC_ARCHIVE" ] && [ ! -d "$DATOMIC_DIR/bin" ]; then
  echo "Extracting Datomic..."
  tar -xzf "$DATOMIC_ARCHIVE" -C lib/
  echo "✓ Datomic extracted"
elif [ -d "$DATOMIC_DIR/bin" ]; then
  echo "✓ Datomic already extracted"
else
  echo "WARNING: Datomic archive not found at $DATOMIC_ARCHIVE"
fi

# Fetch project dependencies
echo "Fetching project dependencies (this may take a while on first run)..."
lein deps || echo "WARNING: Could not fetch all dependencies"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "To start the development environment, run:"
echo "  ./start.sh"
echo ""
echo "Or manually:"
echo "  1. Start Datomic:  $DATOMIC_DIR/bin/transactor $DATOMIC_DIR/config/samples/free-transactor-template.properties"
echo "  2. Start server:   lein run"
echo "  3. Start REPL:     lein repl"
echo ""
