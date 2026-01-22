#!/bin/bash
#
# route-commit.sh - Cherry-pick commits to the appropriate worktree/branch
#
# This script helps route commits from an integration branch to the correct
# destination branch.
#
# Usage:
#   ./scripts/git/route-commit.sh <commit-or-range> <target>
#   ./scripts/git/route-commit.sh HEAD develop
#   ./scripts/git/route-commit.sh HEAD~3..HEAD testing
#   ./scripts/git/route-commit.sh abc123 agents
#   ./scripts/git/route-commit.sh HEAD my-feature    # Routes to feature/my-feature
#
# Targets:
#   develop     → routes to develop branch (orcpub-develop worktree)
#   testing     → routes to testing/develop branch (orcpub-testing worktree)
#   agents      → routes to agents/develop branch (orcpub-agents worktree)
#   <name>      → routes to feature/<name> or similar (paired clean branch)
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
    echo "    develop    - Code fixes, features → develop branch"
    echo "    testing    - E2E tests, devcontainer, CI → testing/develop"
    echo "    agents     - CLAUDE.md, docs, agent config → agents/develop"
    echo "    <name>     - Feature name → paired clean branch (feature/<name>)"
    echo ""
    echo "  Examples:"
    echo "    ./scripts/git/route-commit.sh HEAD develop"
    echo "    ./scripts/git/route-commit.sh HEAD~2..HEAD testing"
    echo "    ./scripts/git/route-commit.sh HEAD my-feature  # → feature/my-feature"
    echo ""
}

show_usage() {
    cat << 'EOF'
Usage: route-commit.sh <commit-or-range> <target>

Route commits from your current branch to the appropriate destination.

ARGUMENTS:
    <commit-or-range>   A commit SHA, HEAD, HEAD~N, or range (HEAD~3..HEAD)
    <target>            One of: develop, testing, agents, or a feature name

TARGETS:
    develop   Code fixes and features      → develop branch (worktree)
    testing   E2E tests, CI, devcontainer  → testing/develop branch (worktree)
    agents    Docs, CLAUDE.md, agent config → agents/develop branch (worktree)
    <name>    Feature name                 → paired clean branch (local)

EXAMPLES:
    # Route the last commit to develop worktree
    route-commit.sh HEAD develop

    # Route a specific commit to testing worktree
    route-commit.sh abc1234 testing

    # Route the last 3 commits to agents worktree
    route-commit.sh HEAD~3..HEAD agents

    # Route to paired feature branch (created by start-feature.sh)
    route-commit.sh HEAD add-themes    # → feature/add-themes

PREREQUISITES:
    For worktree targets (develop, testing, agents):
        ./scripts/git/setup-worktrees.sh

    For feature targets:
        ./scripts/git/start-feature.sh <name>

EOF
}

# Feature branch type prefixes (order matters - checked first to last)
FEATURE_PREFIXES="feature fix bugfix bug-fix hotfix hot-fix patch hotpatch hot-patch enhancement"

# Find a feature branch by name
find_feature_branch() {
    local name="$1"

    # Check each prefix
    for prefix in $FEATURE_PREFIXES; do
        local branch="${prefix}/${name}"
        if git show-ref --verify --quiet "refs/heads/$branch" 2>/dev/null; then
            echo "$branch"
            return 0
        fi
    done

    # Also check if saved from start-feature.sh
    if [ -f "$REPO_ROOT/.git/FEATURE_CLEAN_BRANCH" ]; then
        local saved_branch
        saved_branch=$(cat "$REPO_ROOT/.git/FEATURE_CLEAN_BRANCH")
        if [[ "$saved_branch" == *"/$name" ]] && git show-ref --verify --quiet "refs/heads/$saved_branch" 2>/dev/null; then
            echo "$saved_branch"
            return 0
        fi
    fi

    return 1
}

# Resolve target to worktree path and branch name
# Returns: "path:branch" for worktrees, ":branch" for local branches
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
            # Try to find a feature branch with this name
            local feature_branch
            if feature_branch=$(find_feature_branch "$target"); then
                # Local branch, not worktree - use empty path
                echo ":$feature_branch"
            else
                print_error "Unknown target: $target"
                echo ""
                echo "Valid targets: develop, testing, agents, or a feature name"
                echo ""
                echo "For feature branches, the branch must exist. Create with:"
                echo "  ./scripts/git/start-feature.sh $target"
                exit 1
            fi
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
    local is_local_branch=false

    # Check if this is a worktree or local branch
    if [ -z "$worktree_path" ]; then
        is_local_branch=true
        print_info "Target is a local branch: $branch_name"
    elif [ ! -d "$worktree_path" ]; then
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
    print_info "Routing $commit_count commit(s) to $branch_name"
    echo ""

    # Show what we're about to do
    echo "Commits to route:"
    for sha in $commits; do
        local msg
        msg=$(git -C "$REPO_ROOT" log -1 --format="%h %s" "$sha")
        echo "  $msg"
    done
    echo ""

    local original_dir
    original_dir=$(pwd)
    local original_branch
    original_branch=$(git branch --show-current)

    if [ "$is_local_branch" = true ]; then
        # Local branch: checkout, cherry-pick, checkout back
        echo "Destination: $branch_name (local branch)"
        echo ""

        # Save current branch
        print_info "Switching to $branch_name..."
        git checkout "$branch_name"

        # Cherry-pick each commit
        local success_count=0
        local fail_count=0

        for sha in $commits; do
            local short_sha
            short_sha=$(git rev-parse --short "$sha")
            local msg
            msg=$(git log -1 --format="%s" "$sha")

            echo "Cherry-picking: $short_sha $msg"

            if git cherry-pick "$sha" 2>/dev/null; then
                print_success "Applied: $short_sha"
                ((success_count++)) || true
            else
                print_error "Conflict in: $short_sha"
                echo ""
                echo "  Resolve the conflict, then run:"
                echo "    git cherry-pick --continue"
                echo "    git checkout $original_branch"
                echo ""
                echo "  Or abort with:"
                echo "    git cherry-pick --abort"
                echo "    git checkout $original_branch"
                ((fail_count++)) || true
                break
            fi
        done

        # Switch back to original branch
        if [ $fail_count -eq 0 ]; then
            git checkout "$original_branch"
        fi

        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        if [ $fail_count -eq 0 ]; then
            print_success "Successfully routed $success_count commit(s) to $branch_name"
            echo ""
            echo "Next steps:"
            echo "  1. Review: git log $branch_name -$success_count --oneline"
            echo "  2. Push when ready: git push origin $branch_name"
            echo "  3. Create PR: gh pr create --base develop --head $branch_name"
        else
            print_warning "Partial success: $success_count routed, $fail_count failed"
            echo ""
            echo "You are now on branch: $branch_name"
            echo "Resolve conflicts, then checkout $original_branch"
        fi
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    else
        # Worktree: cd to worktree, cherry-pick, cd back
        echo "Destination: $worktree_path"
        echo ""

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
                ((success_count++)) || true
            else
                print_error "Conflict in: $short_sha"
                echo ""
                echo "  Resolve the conflict in: $worktree_path"
                echo "  Then run: git cherry-pick --continue"
                echo "  Or abort:  git cherry-pick --abort"
                ((fail_count++)) || true
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
    fi
}

# Handle help flag
case "${1:-}" in
    --help|-h)
        show_usage
        exit 0
        ;;
esac

main "$@"
