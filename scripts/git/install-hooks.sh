#!/bin/bash
#
# install-hooks.sh - Install git hooks for branch protection
#
# This script configures git to use the hooks in .githooks/ directory,
# providing automatic protection against committing files to wrong branches.
#
# Usage: ./scripts/git/install-hooks.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
HOOKS_DIR="$REPO_ROOT/.githooks"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Installing Git Hooks${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Check if hooks directory exists
if [ ! -d "$HOOKS_DIR" ]; then
    print_error "Hooks directory not found: $HOOKS_DIR"
    exit 1
fi

# Check if hooks exist
if [ ! -f "$HOOKS_DIR/pre-commit" ]; then
    print_error "pre-commit hook not found in $HOOKS_DIR"
    exit 1
fi

# Configure git to use our hooks directory
print_info "Configuring git to use hooks from: .githooks/"
git -C "$REPO_ROOT" config core.hooksPath .githooks

print_success "Git hooks path configured"

# Make hooks executable
chmod +x "$HOOKS_DIR"/*
print_success "Hooks made executable"

# Verify installation
echo ""
print_info "Verifying installation..."

current_hooks_path=$(git -C "$REPO_ROOT" config --get core.hooksPath || echo "")
if [ "$current_hooks_path" = ".githooks" ]; then
    print_success "Hooks are active"
else
    print_error "Hook configuration failed"
    exit 1
fi

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  Installation Complete${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "Installed hooks:"
echo ""

for hook in "$HOOKS_DIR"/*; do
    if [ -f "$hook" ] && [ -x "$hook" ]; then
        hook_name=$(basename "$hook")
        echo "  • $hook_name"
    fi
done

echo ""
echo "Branch protection rules:"
echo ""
echo "  testing/develop:"
echo "    ✓ Allows: e2e/*, .devcontainer/*, test/*, .github/workflows/*"
echo "    ✗ Blocks: *.clj, *.cljs, *.cljc (source code)"
echo ""
echo "  agents/develop:"
echo "    ✓ Allows: *.md, CLAUDE.md, .claude/*, agents/"
echo "    ✗ Blocks: e2e/*, .devcontainer/*, source code"
echo ""
echo "  develop, main, master:"
echo "    ✓ Allows: Source code, resources"
echo "    ✗ Blocks: Direct commits (use feature branches)"
echo ""
echo "  feature/*, integrate/*:"
echo "    ✓ Allows: Everything (integration work)"
echo ""

print_info "To uninstall hooks: git config --unset core.hooksPath"
echo ""
