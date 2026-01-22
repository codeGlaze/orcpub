#!/bin/bash
#
# setup-worktrees.sh - Create worktrees for parallel branch development
#
# This script sets up isolated working directories for each major branch,
# preventing cross-contamination between feature work, docs, and testing.
#
# Usage: ./scripts/git/setup-worktrees.sh [--remove]
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PARENT_DIR="$(dirname "$REPO_ROOT")"
REPO_NAME="$(basename "$REPO_ROOT")"

# Define worktrees: directory_suffix:branch_name
WORKTREES=(
    "develop:develop"
    "testing:testing/develop"
    "agents:agents/develop"
)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

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

show_usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Create git worktrees for parallel branch development.

OPTIONS:
    --remove    Remove all managed worktrees
    --status    Show current worktree status
    --help      Show this help message

WORKTREES CREATED:
    ${PARENT_DIR}/${REPO_NAME}-develop     → develop branch
    ${PARENT_DIR}/${REPO_NAME}-testing     → testing/develop branch
    ${PARENT_DIR}/${REPO_NAME}-agents      → agents/develop branch

EOF
}

remove_worktrees() {
    print_header "Removing Worktrees"

    for worktree_def in "${WORKTREES[@]}"; do
        suffix="${worktree_def%%:*}"
        worktree_path="${PARENT_DIR}/${REPO_NAME}-${suffix}"

        if [ -d "$worktree_path" ]; then
            echo "Removing: $worktree_path"
            git -C "$REPO_ROOT" worktree remove "$worktree_path" --force 2>/dev/null || true
            print_success "Removed ${REPO_NAME}-${suffix}"
        else
            print_info "${REPO_NAME}-${suffix} doesn't exist, skipping"
        fi
    done

    # Prune any stale worktree references
    git -C "$REPO_ROOT" worktree prune
    print_success "Pruned stale worktree references"
}

show_status() {
    print_header "Worktree Status"

    echo ""
    git -C "$REPO_ROOT" worktree list
    echo ""

    for worktree_def in "${WORKTREES[@]}"; do
        suffix="${worktree_def%%:*}"
        branch="${worktree_def##*:}"
        worktree_path="${PARENT_DIR}/${REPO_NAME}-${suffix}"

        if [ -d "$worktree_path" ]; then
            print_success "${REPO_NAME}-${suffix} → ${branch}"
        else
            print_warning "${REPO_NAME}-${suffix} not created (would track ${branch})"
        fi
    done
}

create_worktrees() {
    print_header "Setting Up Worktrees"

    echo ""
    print_info "Repository: $REPO_ROOT"
    print_info "Worktrees will be created in: $PARENT_DIR"
    echo ""

    # Fetch all branches first
    echo "Fetching remote branches..."
    git -C "$REPO_ROOT" fetch --all --prune
    print_success "Fetched all remote branches"
    echo ""

    for worktree_def in "${WORKTREES[@]}"; do
        suffix="${worktree_def%%:*}"
        branch="${worktree_def##*:}"
        worktree_path="${PARENT_DIR}/${REPO_NAME}-${suffix}"

        echo "Setting up: ${REPO_NAME}-${suffix} → ${branch}"

        if [ -d "$worktree_path" ]; then
            print_warning "Already exists: $worktree_path"
            continue
        fi

        # Check if branch exists locally or remotely
        if git -C "$REPO_ROOT" show-ref --verify --quiet "refs/heads/${branch}" 2>/dev/null; then
            # Local branch exists
            git -C "$REPO_ROOT" worktree add "$worktree_path" "$branch"
            print_success "Created worktree from local branch"
        elif git -C "$REPO_ROOT" show-ref --verify --quiet "refs/remotes/origin/${branch}" 2>/dev/null; then
            # Remote branch exists, create tracking branch
            git -C "$REPO_ROOT" worktree add "$worktree_path" -b "$branch" "origin/${branch}"
            print_success "Created worktree tracking origin/${branch}"
        else
            print_error "Branch '${branch}' not found locally or remotely"
            print_info "Create it first: git checkout -b ${branch}"
            continue
        fi
    done

    echo ""
    print_header "Setup Complete"
    echo ""
    echo "Your worktree structure:"
    echo ""
    echo "  ${REPO_ROOT}"
    echo "  └── (your current branch - integration/feature work)"
    echo ""
    for worktree_def in "${WORKTREES[@]}"; do
        suffix="${worktree_def%%:*}"
        branch="${worktree_def##*:}"
        echo "  ${PARENT_DIR}/${REPO_NAME}-${suffix}"
        echo "  └── ${branch}"
        echo ""
    done

    echo ""
    print_info "Next steps:"
    echo "  1. Install git hooks: ./scripts/git/install-hooks.sh"
    echo "  2. Route commits:     ./scripts/git/route-commit.sh <sha> <target>"
    echo ""
}

# Parse arguments
case "${1:-}" in
    --remove)
        remove_worktrees
        ;;
    --status)
        show_status
        ;;
    --help|-h)
        show_usage
        ;;
    "")
        create_worktrees
        ;;
    *)
        print_error "Unknown option: $1"
        show_usage
        exit 1
        ;;
esac
