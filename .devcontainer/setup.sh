#!/bin/bash
#############################################################################
# OrcPub Devcontainer Setup Script
#
# This script runs during codespace creation to set up all dependencies.
#############################################################################

set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  OrcPub Devcontainer Setup"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# --- Claude Code data persistence ---
# Store Claude Code data (conversation history, settings, etc.) in the workspace
# so it survives codespace rebuilds
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLAUDE_WORKSPACE_DATA="$REPO_ROOT/.claude-data"
CLAUDE_HOME_DIR="$HOME/.claude"

if [ ! -d "$CLAUDE_WORKSPACE_DATA" ]; then
  echo "[setup] Creating Claude Code data directory at $CLAUDE_WORKSPACE_DATA"
  mkdir -p "$CLAUDE_WORKSPACE_DATA"
fi

# If ~/.claude exists and is not a symlink, migrate existing data
if [ -d "$CLAUDE_HOME_DIR" ] && [ ! -L "$CLAUDE_HOME_DIR" ]; then
  echo "[setup] Migrating existing Claude Code data to workspace..."
  cp -rn "$CLAUDE_HOME_DIR"/* "$CLAUDE_WORKSPACE_DATA/" 2>/dev/null || true
  rm -rf "$CLAUDE_HOME_DIR"
fi

# Create symlink from ~/.claude to workspace location
if [ ! -L "$CLAUDE_HOME_DIR" ]; then
  echo "[setup] Creating symlink: $CLAUDE_HOME_DIR -> $CLAUDE_WORKSPACE_DATA"
  ln -sf "$CLAUDE_WORKSPACE_DATA" "$CLAUDE_HOME_DIR"
fi
# --- End Claude Code data persistence ---

# Install Java 17 for clojure-mcp compatibility (OrcPub uses Java 8)
echo "[1/5] Installing Java 17 (for clojure-mcp)..."
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk

# Install Leiningen
echo "[2/5] Installing Leiningen..."
sudo curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein
sudo chmod +x /usr/local/bin/lein
lein version

# Install Clojure CLI (for clojure-mcp)
echo "[3/5] Installing Clojure CLI..."
curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
chmod +x linux-install.sh
sudo ./linux-install.sh
rm linux-install.sh

# Install clojure-mcp (optional, for MCP integration)
echo "[4/5] Installing clojure-mcp..."
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 clojure -Ttools install-latest :lib io.github.bhauman/clojure-mcp :as mcp || true

# Install Playwright dependencies
echo "[5/6] Installing Playwright system dependencies..."
sudo apt-get install -y \
    libnss3 \
    libnspr4 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libasound2 \
    libpango-1.0-0 \
    libcairo2 \
    libatspi2.0-0

# Install git workflow tools (hooks, worktrees)
echo "[6/6] Setting up git workflow tools..."
if [ -f "./scripts/git/install-hooks.sh" ]; then
    chmod +x ./scripts/git/*.sh 2>/dev/null || true
    ./scripts/git/install-hooks.sh || echo "  (hooks not available on this branch)"

    # Set up worktrees if not already done
    if [ -f "./scripts/git/setup-worktrees.sh" ]; then
        echo "Setting up worktrees for parallel branch development..."
        ./scripts/git/setup-worktrees.sh 2>/dev/null || echo "  (worktrees setup skipped)"
    fi
else
    echo "  Git workflow scripts not found (may not be on testing/develop branch)"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Setup complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Next steps:"
echo "  1. Run './start.sh' to start OrcPub"
echo "  2. Run 'cd e2e && npm test' for E2E tests"
echo ""
echo "Git workflow (if available):"
echo "  • Start a feature: ./scripts/git/start-feature.sh <name>"
echo "  • Route commits:   ./scripts/git/route-commit.sh HEAD <target>"
echo "  • See help:        ./scripts/git/README.md"
echo ""
