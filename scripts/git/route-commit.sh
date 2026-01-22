#!/bin/bash
#
# route-commit.sh - Cherry-pick commits to the appropriate worktree/branch
#
# This script helps route commits from an integration branch to the correct
# destination branch (develop, testing/develop, or agents/develop).
#
# Usage:
#   ./scripts/git/route-commit.sh <commit-or-range> <target>
#   ./scripts/git/route-commit.sh HEAD develop
#   ./scripts/git/route-commit.sh HEAD~3..HEAD testing
#   ./scripts/git/route-commit.sh abc123 agents
#
# Targets:
#   develop  → routes to develop branch (orcpub-develop worktree)
#   testing  → routes to testing/develop branch (orcpub-testing worktree)
#   agents   → routes to agents/develop branch (orcpub-agents worktree)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PARENT_DIR="$(dirname "$REPO_ROOT")"
REPO_NAME="$(basename "$REPO_ROOT")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
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

print_agent_help() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}  AGENT GUIDANCE${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "  To route your most recent commit, run:"
    echo ""
    echo "    ./scripts/git/route-commit.sh HEAD <target>"
    echo ""
    echo "  Where <target> is one of:"
    echo "    develop  - Code fixes, features → develop branch"
    echo "    testing  - E2E tests, devcontainer, CI → testing/develop"
    echo "    agents   - CLAUDE.md, docs, agent config → agents/develop"
    echo ""
    echo "  Examples:"
    echo "    ./scripts/git/route-commit.sh HEAD develop"
    echo "    ./scripts/git/route-commit.sh HEAD~2..HEAD testing"
    echo ""
}

show_usage() {
    cat << 'EOF'
Usage: route-commit.sh <commit-or-range> <target>

Route commits from your current branch to the appropriate destination.

ARGUMENTS:
    <commit-or-range>   A commit SHA, HEAD, HEAD~N, or range (HEAD~3..HEAD)
    <target>            One of: develop, testing, agents

TARGETS:
    develop   Code fixes and features      → develop branch
    testing   E2E tests, CI, devcontainer  → testing/develop branch
    agents    Docs, CLAUDE.md, agent config → agents/develop branch

EXAMPLES:
    # Route the last commit to develop
    route-commit.sh HEAD develop

    # Route a specific commit to testing
    route-commit.sh abc1234 testing

    # Route the last 3 commits to agents
    route-commit.sh HEAD~3..HEAD agents

PREREQUISITES:
    Worktrees must be set up first. Run:
    ./scripts/git/setup-worktrees.sh

EOF
}

# Resolve target to worktree path and branch name
resolve_target() {
    local target="$1"
    case "$target" in
        develop)
            echo "${PARENT_DIR}/${REPO_NAME}-develop:develop"
            ;;
        testing)
            echo "${PARENT_DIR}/${REPO_NAME}-testing:testing/develop"
            ;;
        agents)
            echo "${PARENT_DIR}/${REPO_NAME}-agents:agents/develop"
            ;;
        *)
            print_error "Unknown target: $target"
            echo ""
            echo "Valid targets: develop, testing, agents"
            exit 1
            ;;
    esac
}

# Main logic
main() {
    if [ $# -lt 2 ]; then
        print_error "Missing arguments"
        echo ""
        show_usage
        print_agent_help
        exit 1
    fi

    local commit_spec="$1"
    local target="$2"

    # Resolve target to path and branch
    local resolved
    resolved=$(resolve_target "$target")
    local worktree_path="${resolved%%:*}"
    local branch_name="${resolved##*:}"

    # Check if worktree exists
    if [ ! -d "$worktree_path" ]; then
        print_error "Worktree not found: $worktree_path"
        echo ""
        echo "Run setup first:"
        echo "  ./scripts/git/setup-worktrees.sh"
        print_agent_help
        exit 1
    fi

    # Get list of commits to cherry-pick
    local commits
    if [[ "$commit_spec" == *".."* ]]; then
        # It's a range
        commits=$(git -C "$REPO_ROOT" rev-list --reverse "$commit_spec")
    else
        # Single commit
        commits=$(git -C "$REPO_ROOT" rev-parse "$commit_spec")
    fi

    if [ -z "$commits" ]; then
        print_error "No commits found for: $commit_spec"
        exit 1
    fi

    # Count commits
    local commit_count
    commit_count=$(echo "$commits" | wc -l | tr -d ' ')

    echo ""
    print_info "Routing $commit_count commit(s) to $target ($branch_name)"
    echo ""

    # Show what we're about to do
    echo "Commits to route:"
    for sha in $commits; do
        local msg
        msg=$(git -C "$REPO_ROOT" log -1 --format="%h %s" "$sha")
        echo "  $msg"
    done
    echo ""
    echo "Destination: $worktree_path"
    echo ""

    # Change to worktree and cherry-pick
    local original_dir
    original_dir=$(pwd)
    cd "$worktree_path"

    # Ensure worktree is clean
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        print_error "Worktree has uncommitted changes: $worktree_path"
        echo ""
        echo "Please commit or stash changes in the target worktree first."
        cd "$original_dir"
        exit 1
    fi

    # Pull latest (optional, but helps avoid conflicts)
    print_info "Updating $branch_name..."
    git fetch origin "$branch_name" 2>/dev/null || true
    git merge --ff-only "origin/$branch_name" 2>/dev/null || true

    # Cherry-pick each commit
    local success_count=0
    local fail_count=0

    for sha in $commits; do
        local short_sha
        short_sha=$(git -C "$REPO_ROOT" rev-parse --short "$sha")
        local msg
        msg=$(git -C "$REPO_ROOT" log -1 --format="%s" "$sha")

        echo "Cherry-picking: $short_sha $msg"

        if git cherry-pick "$sha" 2>/dev/null; then
            print_success "Applied: $short_sha"
            ((success_count++))
        else
            print_error "Conflict in: $short_sha"
            echo ""
            echo "  Resolve the conflict in: $worktree_path"
            echo "  Then run: git cherry-pick --continue"
            echo "  Or abort:  git cherry-pick --abort"
            ((fail_count++))
            break
        fi
    done

    cd "$original_dir"

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    if [ $fail_count -eq 0 ]; then
        print_success "Successfully routed $success_count commit(s) to $target"
        echo ""
        echo "Next steps:"
        echo "  1. cd $worktree_path"
        echo "  2. Review the changes: git log -$success_count --oneline"
        echo "  3. Push when ready: git push origin $branch_name"
    else
        print_warning "Partial success: $success_count routed, $fail_count failed"
        echo ""
        echo "Resolve conflicts in: $worktree_path"
    fi
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Handle help flag
case "${1:-}" in
    --help|-h)
        show_usage
        exit 0
        ;;
esac

main "$@"
