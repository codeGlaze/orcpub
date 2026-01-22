#!/bin/bash
#
# start-feature.sh - Create paired feature + integration branches
#
# This script creates two branches for a new feature:
#   1. feature/<name> from develop (clean, for PR)
#   2. integrate/<name> from agents/develop (has tooling, for work)
#
# The integration branch is where all work happens. Code commits are
# routed to the feature branch, which stays clean for the PR.
#
# Usage:
#   ./scripts/git/start-feature.sh <name> [type]
#   ./scripts/git/start-feature.sh add-dark-mode
#   ./scripts/git/start-feature.sh fix-login-bug fix
#   ./scripts/git/start-feature.sh update-docs enhancement
#
# Types: feature (default), fix, bugfix, hotfix, patch, enhancement
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_success() { echo -e "${GREEN}✓${NC} $1"; }
print_warning() { echo -e "${YELLOW}⚠${NC} $1"; }
print_error() { echo -e "${RED}✗${NC} $1"; }
print_info() { echo -e "${BLUE}ℹ${NC} $1"; }

# Valid branch type prefixes
VALID_TYPES="feature fix bugfix bug-fix hotfix hot-fix patch hotpatch hot-patch enhancement"

show_usage() {
    cat << 'EOF'
Usage: start-feature.sh <name> [type]

Create paired branches for feature development.

ARGUMENTS:
    name    The feature name (e.g., "add-dark-mode", "fix-login-bug")
    type    Branch type prefix (default: feature)
            Valid types: feature, fix, bugfix, bug-fix, hotfix, hot-fix,
                        patch, hotpatch, hot-patch, enhancement

CREATES:
    <type>/<name>      Clean branch from develop (for PR)
    integrate/<name>   Work branch from agents/develop (has tooling)

EXAMPLES:
    ./scripts/git/start-feature.sh add-themes
    # Creates: feature/add-themes, integrate/add-themes

    ./scripts/git/start-feature.sh login-crash fix
    # Creates: fix/login-crash, integrate/login-crash

    ./scripts/git/start-feature.sh perf-optimization enhancement
    # Creates: enhancement/perf-optimization, integrate/perf-optimization

WORKFLOW:
    1. Work in integrate/<name> (has CLAUDE.md, agent tooling)
    2. Route code commits: ./scripts/git/route-commit.sh HEAD <name>
    3. When ready, PR from <type>/<name> to develop

EOF
}

normalize_type() {
    local type="$1"
    case "$type" in
        feature|fix|bugfix|bug-fix|hotfix|hot-fix|patch|hotpatch|hot-patch|enhancement)
            echo "$type"
            ;;
        bug)
            echo "bugfix"
            ;;
        *)
            echo ""
            ;;
    esac
}

main() {
    local name="${1:-}"
    local type="${2:-feature}"

    if [ -z "$name" ] || [ "$name" = "--help" ] || [ "$name" = "-h" ]; then
        show_usage
        exit 0
    fi

    # Normalize and validate type
    local normalized_type
    normalized_type=$(normalize_type "$type")
    if [ -z "$normalized_type" ]; then
        print_error "Invalid branch type: $type"
        echo ""
        echo "Valid types: $VALID_TYPES"
        exit 1
    fi

    local clean_branch="${normalized_type}/${name}"
    local work_branch="integrate/${name}"

    print_header "Starting Feature: $name"

    echo ""
    print_info "Clean branch: $clean_branch (from develop)"
    print_info "Work branch:  $work_branch (from agents/develop)"
    echo ""

    cd "$REPO_ROOT"

    # Check for uncommitted changes
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        print_error "You have uncommitted changes. Please commit or stash them first."
        exit 1
    fi

    # Fetch latest
    print_info "Fetching latest branches..."
    git fetch origin develop
    git fetch origin agents/develop 2>/dev/null || true
    git fetch origin testing/develop 2>/dev/null || true
    print_success "Fetched latest"

    # Check if branches already exist
    if git show-ref --verify --quiet "refs/heads/$clean_branch" 2>/dev/null; then
        print_error "Branch already exists: $clean_branch"
        exit 1
    fi
    if git show-ref --verify --quiet "refs/heads/$work_branch" 2>/dev/null; then
        print_error "Branch already exists: $work_branch"
        exit 1
    fi

    # Create clean branch from develop
    print_info "Creating clean branch from develop..."
    git checkout -b "$clean_branch" origin/develop
    print_success "Created $clean_branch"

    # Create work branch from agents/develop
    print_info "Creating work branch from agents/develop..."
    if git show-ref --verify --quiet "refs/remotes/origin/agents/develop" 2>/dev/null; then
        git checkout -b "$work_branch" origin/agents/develop
    else
        # Fallback: create from develop if agents/develop doesn't exist
        print_warning "agents/develop not found, creating from develop"
        git checkout -b "$work_branch" origin/develop
    fi
    print_success "Created $work_branch"

    # Optionally merge in testing/develop
    if git show-ref --verify --quiet "refs/remotes/origin/testing/develop" 2>/dev/null; then
        echo ""
        read -p "Merge testing/develop for E2E/devcontainer tooling? [Y/n] " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Nn]$ ]]; then
            print_info "Merging testing/develop..."
            if git merge origin/testing/develop -m "Merge testing/develop for tooling" --no-edit; then
                print_success "Merged testing/develop"
            else
                print_warning "Merge conflict - resolve manually or abort with: git merge --abort"
            fi
        fi
    fi

    # Summary
    print_header "Setup Complete"

    echo ""
    echo "  Created branches:"
    echo -e "    ${GREEN}$clean_branch${NC} ← Clean, for PR to develop"
    echo -e "    ${GREEN}$work_branch${NC} ← Work here (has agent tooling)"
    echo ""
    echo "  You are now on: $work_branch"
    echo ""

    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}  WORKFLOW${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "  1. Work normally in this branch (integrate/${name})"
    echo ""
    echo "  2. Route code commits to the clean branch:"
    echo -e "     ${GREEN}./scripts/git/route-commit.sh HEAD $name${NC}"
    echo ""
    echo "  3. When ready, create PR from clean branch:"
    echo -e "     ${GREEN}git checkout $clean_branch${NC}"
    echo -e "     ${GREEN}git push -u origin $clean_branch${NC}"
    echo -e "     ${GREEN}gh pr create --base develop${NC}"
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    # Store the feature info for route-commit.sh
    echo "$clean_branch" > "$REPO_ROOT/.git/FEATURE_CLEAN_BRANCH"
    echo "$work_branch" > "$REPO_ROOT/.git/FEATURE_WORK_BRANCH"
    print_info "Feature info saved for route-commit.sh"
}

main "$@"
