#!/bin/bash
#############################################################################
# Run E2E Tests Against GitHub Codespace
#
# This script orchestrates the full testing workflow:
# 1. Connects to a Codespace (or creates one if needed)
# 2. Ensures the app is running
# 3. Runs Playwright tests
# 4. Collects and displays results
#
# Usage:
#   ./run-codespace-tests.sh [options]
#
# Options:
#   --scenarios <list>    Comma-separated list of scenarios to run
#                         (console-errors, ui-smoke, import-export, all)
#   --codespace <name>    Specific codespace to use
#   --patch <description> Description of what's being tested
#   --headed              Run tests in headed mode (visible browser)
#   --create-codespace    Create a new codespace if none exists
#   --skip-connect        Skip codespace connection (assume local app)
#
# Examples:
#   ./run-codespace-tests.sh --scenarios console-errors
#   ./run-codespace-tests.sh --scenarios all --patch "Fix modal styling"
#   ./run-codespace-tests.sh --skip-connect  # Test against localhost
#############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
E2E_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_DIR="$(cd "$E2E_DIR/.." && pwd)"

# Default values
SCENARIOS="all"
CODESPACE_NAME=""
PATCH_CONTEXT=""
HEADED=false
CREATE_CODESPACE=false
SKIP_CONNECT=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --scenarios)
            SCENARIOS="$2"
            shift 2
            ;;
        --codespace)
            CODESPACE_NAME="$2"
            shift 2
            ;;
        --patch)
            PATCH_CONTEXT="$2"
            shift 2
            ;;
        --headed)
            HEADED=true
            shift
            ;;
        --create-codespace)
            CREATE_CODESPACE=true
            shift
            ;;
        --skip-connect)
            SKIP_CONNECT=true
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Header
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  OrcPub E2E Test Runner"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
log_info "Scenarios: $SCENARIOS"
[ -n "$PATCH_CONTEXT" ] && log_info "Testing: $PATCH_CONTEXT"
echo ""

# Step 1: Connect to Codespace (unless skipped)
if [ "$SKIP_CONNECT" = false ]; then
    log_step "Connecting to Codespace..."

    if [ "$CREATE_CODESPACE" = true ] && [ -z "$CODESPACE_NAME" ]; then
        log_info "Creating new codespace..."
        CODESPACE_NAME=$(gh codespace create -r "$(git remote get-url origin | sed 's/.*github.com[:/]//' | sed 's/.git$//')" --json name -q '.name')
        log_info "Created: $CODESPACE_NAME"
    fi

    # Run connection script
    if [ -n "$CODESPACE_NAME" ]; then
        "$SCRIPT_DIR/connect-codespace.sh" "$CODESPACE_NAME"
    else
        "$SCRIPT_DIR/connect-codespace.sh"
    fi

    # Wait for port to be ready
    log_info "Verifying app is accessible..."
    max_wait=30
    waited=0
    while [ $waited -lt $max_wait ]; do
        if curl -s -o /dev/null -w '%{http_code}' http://localhost:8890 2>/dev/null | grep -q "200"; then
            log_info "App is accessible at http://localhost:8890"
            break
        fi
        sleep 2
        waited=$((waited + 2))
    done
else
    log_info "Skipping codespace connection (assuming local app)"
fi

# Step 2: Install dependencies if needed
log_step "Checking dependencies..."
cd "$E2E_DIR"

if [ ! -d "node_modules" ]; then
    log_info "Installing npm dependencies..."
    npm install
fi

if ! npx playwright --version &> /dev/null; then
    log_info "Installing Playwright browsers..."
    npx playwright install chromium
fi

# Step 3: Build test command
log_step "Running tests..."

TEST_CMD="npx playwright test"

# Add specific scenario files
case "$SCENARIOS" in
    all)
        # Run all tests
        ;;
    console-errors)
        TEST_CMD="$TEST_CMD scenarios/console-errors.spec.ts"
        ;;
    ui-smoke)
        TEST_CMD="$TEST_CMD scenarios/ui-smoke.spec.ts"
        ;;
    import-export)
        TEST_CMD="$TEST_CMD scenarios/import-export.spec.ts"
        ;;
    *)
        # Custom list - add each
        IFS=',' read -ra SCENARIO_LIST <<< "$SCENARIOS"
        for scenario in "${SCENARIO_LIST[@]}"; do
            TEST_CMD="$TEST_CMD scenarios/${scenario}.spec.ts"
        done
        ;;
esac

# Add headed mode if requested
if [ "$HEADED" = true ]; then
    TEST_CMD="$TEST_CMD --headed"
fi

# Set environment variables
export APP_URL="http://localhost:8890"
export PATCH_CONTEXT="$PATCH_CONTEXT"
export TEST_SCENARIOS="$SCENARIOS"

# Run tests
log_info "Executing: $TEST_CMD"
echo ""

$TEST_CMD || true  # Don't exit on test failure

# Step 4: Display results
echo ""
log_step "Test Results"
echo ""

RESULTS_FILE="$E2E_DIR/test-results/agent-report.json"

if [ -f "$RESULTS_FILE" ]; then
    # Parse and display key results
    if command -v jq &> /dev/null; then
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "  Summary"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        jq -r '.summary | "  Total: \(.total) | Passed: \(.passed) | Failed: \(.failed)"' "$RESULTS_FILE"
        jq -r '.summary | "  Status: \(.overallStatus | ascii_upcase)"' "$RESULTS_FILE"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

        # Show blocking issues if any
        BLOCKING=$(jq -r '.blockingIssues | length' "$RESULTS_FILE")
        if [ "$BLOCKING" -gt 0 ]; then
            echo ""
            log_warn "Blocking Issues:"
            jq -r '.blockingIssues[]' "$RESULTS_FILE" | while read -r issue; do
                echo "  - $issue"
            done
        fi

        # Show recommendations
        echo ""
        log_info "Recommendations:"
        jq -r '.recommendations[]' "$RESULTS_FILE" | while read -r rec; do
            echo "  - $rec"
        done

        # Show console errors if any
        ERRORS=$(jq -r '.consoleErrors | length' "$RESULTS_FILE")
        if [ "$ERRORS" -gt 0 ]; then
            echo ""
            log_warn "Console Errors ($ERRORS):"
            jq -r '.consoleErrors[] | "  [\(.type)] \(.text)"' "$RESULTS_FILE" | head -10
        fi
    else
        cat "$RESULTS_FILE"
    fi

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_info "Full report: $RESULTS_FILE"
    log_info "HTML report: npx playwright show-report"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
else
    log_warn "No results file found at $RESULTS_FILE"
fi

# Cleanup hint
if [ "$SKIP_CONNECT" = false ]; then
    echo ""
    log_info "To stop port forwarding:"
    log_info "  kill \$(cat /tmp/orcpub-port-forward.pid)"
fi
