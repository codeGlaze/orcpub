#!/bin/bash
#
# prepare-pr.sh - Prepare a feature branch for PR to develop
#
# This script cleans agent-specific files from a feature branch that was
# branched from agents/develop, making it ready for a PR to develop.
#
# What it does:
#   1. Creates a new clean branch from develop
#   2. Cherry-picks your feature commits (excluding agent-only commits)
#   3. Removes any remaining agent files
#   4. Leaves you ready to push and create a PR
#
# Usage:
#   ./scripts/git/prepare-pr.sh [source-branch] [new-branch-name]
#   ./scripts/git/prepare-pr.sh                          # Uses current branch
#   ./scripts/git/prepare-pr.sh feature/my-feature       # Specific source
#   ./scripts/git/prepare-pr.sh feature/old pr/my-feature # Custom target name
#   ./scripts/git/prepare-pr.sh --strip-only             # Just remove agent files from current branch
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
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

# Agent files to remove (patterns)
AGENT_FILES=(
    "CLAUDE.md"
    "AGENTS.md"
    "agents.md"
    ".claude"
    "flow*.md"
)

# Directories to check for agent-only content
AGENT_DIRS=(
    ".claude"
    "agents"
)

show_usage() {
    cat << 'EOF'
Usage: prepare-pr.sh [source-branch] [new-branch-name]
       prepare-pr.sh --strip-only

Prepare a feature branch for PR to develop by removing agent files.

ARGUMENTS:
    source-branch     Branch to prepare (default: current branch)
    new-branch-name   Name for the clean branch (default: pr/<source-branch-name>)

OPTIONS:
    --strip-only      Just remove agent files from the current branch (no cherry-pick)
    -h, --help        Show this help message

EXAMPLES:
    # Prepare current branch (full workflow)
    ./scripts/git/prepare-pr.sh

    # Prepare specific branch
    ./scripts/git/prepare-pr.sh feature/add-themes

    # Prepare with custom target name
    ./scripts/git/prepare-pr.sh feature/add-themes pr/themes-clean

    # Quick strip: just remove agent files from current branch
    ./scripts/git/prepare-pr.sh --strip-only

WHAT THIS DOES (full workflow):
    1. Fetches latest develop
    2. Creates a new branch from develop
    3. Cherry-picks non-agent commits from your feature branch
    4. Removes any agent files that slipped through
    5. Creates a final "clean" commit if needed

WHAT --strip-only DOES:
    1. Removes agent files from current branch
    2. Creates a cleanup commit if any files were removed
    (Does NOT create a new branch or cherry-pick)

AGENT FILES REMOVED:
    - CLAUDE.md, AGENTS.md, agents.md
    - .claude/ directory
    - flow*.md (workflow notes)

EOF
}

# Get the merge base between a branch and agents/develop or develop
find_feature_commits() {
    local branch="$1"

    # Try to find where branch diverged from agents/develop
    local base
    base=$(git merge-base "$branch" origin/agents/develop 2>/dev/null || \
           git merge-base "$branch" origin/develop 2>/dev/null || \
           echo "")

    if [ -z "$base" ]; then
        print_error "Could not find merge base for $branch"
        exit 1
    fi

    echo "$base"
}

# Check if a commit only touches agent files
is_agent_only_commit() {
    local sha="$1"
    local files
    files=$(git diff-tree --no-commit-id --name-only -r "$sha")

    for file in $files; do
        local is_agent=false

        # Check against agent file patterns
        for pattern in "${AGENT_FILES[@]}"; do
            case "$file" in
                $pattern|$pattern/*) is_agent=true; break ;;
            esac
        done

        # Check against agent directories
        for dir in "${AGENT_DIRS[@]}"; do
            case "$file" in
                $dir|$dir/*) is_agent=true; break ;;
            esac
        done

        # If any file is NOT an agent file, this is not an agent-only commit
        if [ "$is_agent" = false ]; then
            return 1
        fi
    done

    return 0
}

# Remove agent files from working directory
remove_agent_files() {
    local removed=0

    for pattern in "${AGENT_FILES[@]}"; do
        if [ -e "$pattern" ]; then
            rm -rf "$pattern"
            print_info "Removed: $pattern"
            ((removed++)) || true
        fi
    done

    for dir in "${AGENT_DIRS[@]}"; do
        if [ -d "$dir" ]; then
            rm -rf "$dir"
            print_info "Removed: $dir/"
            ((removed++)) || true
        fi
    done

    echo "$removed"
}

# Strip-only mode: just remove agent files from current branch
strip_only() {
    print_header "Stripping Agent Files"

    local current_branch
    current_branch=$(git branch --show-current)

    echo ""
    print_info "Branch: $current_branch"
    echo ""

    # Ensure we're in the repo root
    cd "$REPO_ROOT"

    # Check for uncommitted changes
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        print_error "You have uncommitted changes. Please commit or stash them first."
        exit 1
    fi

    # Remove agent files
    local removed
    removed=$(remove_agent_files)

    if [ "$removed" -gt 0 ]; then
        # Check if there are changes to commit
        if ! git diff-index --quiet HEAD -- 2>/dev/null || [ -n "$(git ls-files --deleted)" ]; then
            git add -A
            git commit -m "chore: remove agent files for PR

Removed agent-specific files that were inherited from agents/develop.
These files are not needed in the main codebase.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
            print_success "Created cleanup commit"
        fi
    else
        print_info "No agent files found to remove"
    fi

    # Summary
    print_header "Summary"

    echo ""
    echo "  Agent files removed: $removed"
    echo "  Branch: $current_branch"
    echo ""

    if [ "$removed" -gt 0 ]; then
        print_success "Agent files stripped!"
        echo ""
        echo "Next steps:"
        echo ""
        echo "  1. Push the branch:"
        echo -e "     ${GREEN}git push origin $current_branch${NC}"
        echo ""
    else
        print_info "Branch was already clean"
    fi
}

main() {
    # Handle --strip-only flag
    if [ "${1:-}" = "--strip-only" ]; then
        strip_only
        exit 0
    fi

    # Parse arguments
    local source_branch="${1:-$(git branch --show-current)}"
    local target_branch="${2:-pr/${source_branch#*/}}"

    if [ "$source_branch" = "--help" ] || [ "$source_branch" = "-h" ]; then
        show_usage
        exit 0
    fi

    print_header "Preparing Branch for PR"

    echo ""
    print_info "Source branch: $source_branch"
    print_info "Target branch: $target_branch"
    echo ""

    # Ensure we're in the repo root
    cd "$REPO_ROOT"

    # Check for uncommitted changes
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        print_error "You have uncommitted changes. Please commit or stash them first."
        exit 1
    fi

    # Fetch latest
    print_info "Fetching latest from origin..."
    git fetch origin develop
    git fetch origin agents/develop 2>/dev/null || true
    print_success "Fetched latest"

    # Find the merge base
    print_info "Finding feature commits..."
    local merge_base
    merge_base=$(find_feature_commits "$source_branch")
    print_success "Merge base: $(git rev-parse --short "$merge_base")"

    # Get list of commits to cherry-pick
    local commits
    commits=$(git rev-list --reverse "$merge_base..$source_branch")
    local total_commits
    total_commits=$(echo "$commits" | grep -c . || echo 0)
    print_info "Found $total_commits commits to process"

    # Create new branch from develop
    print_info "Creating clean branch from develop..."
    if git show-ref --verify --quiet "refs/heads/$target_branch" 2>/dev/null; then
        print_warning "Branch $target_branch already exists"
        read -p "Delete and recreate? [y/N] " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            git branch -D "$target_branch"
        else
            print_error "Aborted"
            exit 1
        fi
    fi

    git checkout -b "$target_branch" origin/develop
    print_success "Created $target_branch from origin/develop"

    # Cherry-pick non-agent commits
    print_header "Cherry-picking Feature Commits"

    local picked=0
    local skipped=0

    for sha in $commits; do
        local short_sha
        short_sha=$(git rev-parse --short "$sha")
        local msg
        msg=$(git log -1 --format="%s" "$sha")

        if is_agent_only_commit "$sha"; then
            print_warning "Skipping agent-only: $short_sha $msg"
            ((skipped++)) || true
        else
            echo "Cherry-picking: $short_sha $msg"
            if git cherry-pick "$sha" 2>/dev/null; then
                print_success "Applied: $short_sha"
                ((picked++)) || true
            else
                print_error "Conflict in: $short_sha"
                echo ""
                echo "  Resolve the conflict, then run:"
                echo "    git cherry-pick --continue"
                echo ""
                echo "  Or abort with:"
                echo "    git cherry-pick --abort && git checkout $source_branch"
                exit 1
            fi
        fi
    done

    # Remove any remaining agent files
    print_header "Cleaning Agent Files"

    local removed
    removed=$(remove_agent_files)

    if [ "$removed" -gt 0 ]; then
        # Check if there are changes to commit
        if ! git diff-index --quiet HEAD -- 2>/dev/null; then
            git add -A
            git commit -m "chore: remove agent files for PR

Removed agent-specific files that were inherited from agents/develop.
These files are not needed in the main codebase.

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>"
            print_success "Created cleanup commit"
        fi
    else
        print_info "No agent files to remove"
    fi

    # Summary
    print_header "Summary"

    echo ""
    echo "  Commits cherry-picked: $picked"
    echo "  Commits skipped:       $skipped (agent-only)"
    echo "  Agent files removed:   $removed"
    echo ""
    echo "  You are now on branch: $target_branch"
    echo ""

    print_success "Branch is ready for PR!"
    echo ""
    echo "Next steps:"
    echo ""
    echo "  1. Review the commits:"
    echo -e "     ${GREEN}git log --oneline origin/develop..HEAD${NC}"
    echo ""
    echo "  2. Push the branch:"
    echo -e "     ${GREEN}git push -u origin $target_branch${NC}"
    echo ""
    echo "  3. Create a PR to develop:"
    echo -e "     ${GREEN}gh pr create --base develop${NC}"
    echo ""
}

main "$@"
