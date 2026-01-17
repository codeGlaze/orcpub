#!/bin/bash
#############################################################################
# Connect to GitHub Codespace for E2E Testing
#
# This script connects to a running Codespace and sets up port forwarding
# so that Playwright tests can run against the app.
#
# Usage:
#   ./connect-codespace.sh [codespace-name]
#
# If no codespace name is provided, it will use the first available one.
#
# Environment variables:
#   GH_TOKEN - GitHub token (optional if gh is already authenticated)
#   CODESPACE_NAME - Override the codespace to connect to
#
# Prerequisites:
#   - GitHub CLI (gh) installed and authenticated
#   - A running Codespace with the OrcPub app
#############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    if ! command -v gh &> /dev/null; then
        log_error "GitHub CLI (gh) is not installed"
        log_info "Install it from: https://cli.github.com/"
        exit 1
    fi

    if ! gh auth status &> /dev/null; then
        log_error "GitHub CLI is not authenticated"
        log_info "Run: gh auth login"
        exit 1
    fi
}

# Get codespace name
get_codespace_name() {
    local name="${1:-$CODESPACE_NAME}"

    if [ -z "$name" ]; then
        # Get the first available codespace for this repo
        name=$(gh codespace list --json name,repository -q '.[] | select(.repository | contains("orcpub")) | .name' 2>/dev/null | head -1)
    fi

    if [ -z "$name" ]; then
        log_error "No codespace found. Create one first:"
        log_info "  gh codespace create -r <owner>/orcpub"
        exit 1
    fi

    echo "$name"
}

# Check if codespace is running
ensure_codespace_running() {
    local name="$1"
    local state=$(gh codespace list --json name,state -q ".[] | select(.name==\"$name\") | .state" 2>/dev/null)

    if [ "$state" != "Available" ]; then
        log_info "Starting codespace $name..."
        gh codespace start -c "$name" --wait
    fi
}

# Start the app in the codespace
start_app_in_codespace() {
    local name="$1"

    log_info "Checking if app is already running..."

    # Check if app is responding
    if gh codespace ssh -c "$name" -- "curl -s -o /dev/null -w '%{http_code}' http://localhost:8890" 2>/dev/null | grep -q "200"; then
        log_info "App is already running"
        return 0
    fi

    log_info "Starting OrcPub app in codespace..."

    # Start the app in background
    gh codespace ssh -c "$name" -- "cd /workspaces/orcpub && nohup ./start.sh > /tmp/orcpub.log 2>&1 &" || true

    # Wait for app to be ready
    log_info "Waiting for app to start..."
    local max_wait=120
    local waited=0

    while [ $waited -lt $max_wait ]; do
        if gh codespace ssh -c "$name" -- "curl -s -o /dev/null -w '%{http_code}' http://localhost:8890" 2>/dev/null | grep -q "200"; then
            log_info "App is ready!"
            return 0
        fi
        sleep 5
        waited=$((waited + 5))
        echo -n "."
    done

    log_warn "App may not be fully ready yet, but proceeding..."
}

# Set up port forwarding
setup_port_forwarding() {
    local name="$1"
    local port="${2:-8890}"

    log_info "Setting up port forwarding (port $port)..."

    # Kill any existing port forwarding
    pkill -f "gh codespace ports forward" 2>/dev/null || true

    # Start port forwarding in background
    gh codespace ports forward "$port:$port" -c "$name" &
    local forward_pid=$!

    # Wait for port to be available
    sleep 3

    if kill -0 $forward_pid 2>/dev/null; then
        log_info "Port forwarding active (PID: $forward_pid)"
        echo "$forward_pid" > /tmp/orcpub-port-forward.pid
        return 0
    else
        log_error "Port forwarding failed to start"
        return 1
    fi
}

# Main function
main() {
    log_info "OrcPub Codespace Connection Script"
    echo ""

    check_prerequisites

    local codespace_name=$(get_codespace_name "$1")
    log_info "Using codespace: $codespace_name"

    ensure_codespace_running "$codespace_name"
    start_app_in_codespace "$codespace_name"
    setup_port_forwarding "$codespace_name"

    echo ""
    log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_info "  Codespace connected and app running!"
    log_info "  App URL: http://localhost:8890"
    log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    log_info "Run tests with:"
    log_info "  cd e2e && npm test"
    echo ""
    log_info "To stop port forwarding:"
    log_info "  kill \$(cat /tmp/orcpub-port-forward.pid)"
}

main "$@"
