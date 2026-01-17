#!/bin/bash
set -euo pipefail

#############################################################################
# OrcPub Automated Test Runner
#
# Usage:
#   ./.claude/test-runner.sh [--focus TARGET] [--custom-tests TEST1,TEST2]
#
# Examples:
#   ./.claude/test-runner.sh --focus modals
#   ./.claude/test-runner.sh --focus all
#   ./.claude/test-runner.sh --custom-tests "test_modal_display,test_spell_selection"
#
# Output:
#   .claude/test-results.json - structured test results for agent consumption
#############################################################################

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_FILE="$SCRIPT_DIR/test-results.json"
TEST_FOCUS="${1:-all}"
CUSTOM_TESTS=""

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --focus)
      TEST_FOCUS="$2"
      shift 2
      ;;
    --custom-tests)
      CUSTOM_TESTS="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Initialize counters
BACKEND_PASSED=0
BACKEND_FAILED=0
BACKEND_ERROR=0
BACKEND_DURATION=0

FRONTEND_ERRORS=0
FRONTEND_WARNINGS=0

START_TIME=$(date +%s%3N)
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
COMMIT=$(cd "$PROJECT_DIR" && git rev-parse --short HEAD 2>/dev/null || echo "unknown")
BRANCH=$(cd "$PROJECT_DIR" && git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
RUN_ID="${TIMESTAMP}-${COMMIT}"

echo "================================================"
echo "OrcPub Test Runner - $(date)"
echo "================================================"
echo "Branch: $BRANCH | Commit: $COMMIT"
echo "Focus: $TEST_FOCUS"
echo ""

#############################################################################
# BACKEND TESTS
#############################################################################
echo "[1/3] Running backend tests..."
BACKEND_START=$(date +%s%3N)
BACKEND_OUTPUT=""

cd "$PROJECT_DIR"

if lein test > /tmp/backend_test_output.txt 2>&1; then
  BACKEND_STATUS="passed"
  # Parse output for pass/fail counts (lein test output format)
  BACKEND_PASSED=$(grep -oP '(?<=Ran )\d+(?= tests)' /tmp/backend_test_output.txt | head -1 || echo "0")
  BACKEND_FAILED=0
  BACKEND_ERROR=0
else
  BACKEND_STATUS="failed"
  BACKEND_FAILED=1
  BACKEND_ERROR=0
fi

BACKEND_OUTPUT=$(cat /tmp/backend_test_output.txt)
BACKEND_DURATION=$(($(date +%s%3N) - BACKEND_START))

echo "✓ Backend tests completed in ${BACKEND_DURATION}ms (Status: $BACKEND_STATUS)"
echo ""

#############################################################################
# FRONTEND BUILD CHECK
#############################################################################
echo "[2/3] Checking frontend build artifacts..."
FRONTEND_BUILD_STATUS="not_checked"

if [ -f "$PROJECT_DIR/resources/public/js/compiled/orcpub.js" ]; then
  FRONTEND_BUILD_STATUS="present"
  FRONTEND_BUILD_SIZE=$(stat -f%z "$PROJECT_DIR/resources/public/js/compiled/orcpub.js" 2>/dev/null || stat -c%s "$PROJECT_DIR/resources/public/js/compiled/orcpub.js" 2>/dev/null || echo "0")
  FRONTEND_BUILD_MODIFIED=$(stat -f "%Sm" -t "%Y-%m-%dT%H:%M:%SZ" "$PROJECT_DIR/resources/public/js/compiled/orcpub.js" 2>/dev/null || stat -c%y "$PROJECT_DIR/resources/public/js/compiled/orcpub.js" 2>/dev/null || echo "unknown")
else
  FRONTEND_BUILD_STATUS="missing"
  FRONTEND_BUILD_SIZE=0
  FRONTEND_BUILD_MODIFIED="unknown"
fi

echo "✓ Frontend build artifacts checked (Status: $FRONTEND_BUILD_STATUS)"
echo ""

#############################################################################
# TARGETED UI TESTS
#############################################################################
echo "[3/3] Preparing targeted UI test info..."
echo "✓ Test focus: $TEST_FOCUS"
if [ ! -z "$CUSTOM_TESTS" ]; then
  echo "✓ Custom tests: $CUSTOM_TESTS"
fi
echo ""

#############################################################################
# GENERATE RESULTS JSON
#############################################################################
echo "Generating results JSON..."

# Build the JSON dynamically
cat > "$RESULTS_FILE" << 'EOFJ'
{
  "timestamp": "TIMESTAMP_PLACEHOLDER",
  "branch": "BRANCH_PLACEHOLDER",
  "commit": "COMMIT_PLACEHOLDER",
  "test_run_id": "RUN_ID_PLACEHOLDER",
  "test_focus": {
    "target": "FOCUS_PLACEHOLDER",
    "custom_tests": CUSTOM_TESTS_PLACEHOLDER
  },
  "backend_tests": {
    "status": "BACKEND_STATUS_PLACEHOLDER",
    "total": BACKEND_PASSED_PLACEHOLDER,
    "passed": BACKEND_PASSED_PLACEHOLDER,
    "failed": BACKEND_FAILED_PLACEHOLDER,
    "error": BACKEND_ERROR_PLACEHOLDER,
    "duration_ms": BACKEND_DURATION_PLACEHOLDER,
    "summary": "BACKEND_OUTPUT_PLACEHOLDER"
  },
  "frontend_build": {
    "status": "FRONTEND_BUILD_STATUS_PLACEHOLDER",
    "main_js_size_bytes": FRONTEND_BUILD_SIZE_PLACEHOLDER,
    "main_js_modified": "FRONTEND_BUILD_MODIFIED_PLACEHOLDER"
  },
  "frontend_console": {
    "status": "not_captured_yet",
    "note": "Console errors captured when app is running in browser"
  },
  "targeted_ui_tests": {
    "status": "not_run",
    "note": "Specify --custom-tests to define UI tests for this patch"
  },
  "summary": {
    "overall_status": "SUMMARY_STATUS_PLACEHOLDER",
    "backend_passed": true,
    "all_tests_passed": TESTS_PASSED_PLACEHOLDER,
    "blocking_issues": BLOCKING_ISSUES_PLACEHOLDER,
    "recommendations": [
      "Review backend test output above",
      "Manual UI testing needed - run 'lein figwheel' and test in browser"
    ]
  }
}
EOFJ

# Function to escape JSON strings
escape_json() {
  printf '%s\n' "$1" | sed -e 's/[\"]/\\&/g' -e 's/$/\\/' | tr -d '\n' | sed -e 's/\\$//'
}

# Replace placeholders
ESCAPED_OUTPUT=$(escape_json "$BACKEND_OUTPUT")
TESTS_PASSED="true"
SUMMARY_STATUS="passed"
BLOCKING_ISSUES="[]"

if [ "$BACKEND_STATUS" = "failed" ]; then
  TESTS_PASSED="false"
  SUMMARY_STATUS="failed"
  BLOCKING_ISSUES='["Backend tests failed - review output above"]'
fi

CUSTOM_TESTS_JSON="[]"
if [ ! -z "$CUSTOM_TESTS" ]; then
  CUSTOM_TESTS_JSON=$(echo "\"$CUSTOM_TESTS\"" | tr ',' '\n' | sed 's/^ *//;s/ *$//' | sed 's/^/"/;s/$/"/' | paste -sd ',' | sed 's/,/,/g;s/^/[/;s/$/]/')
fi

sed -i.bak \
  -e "s/TIMESTAMP_PLACEHOLDER/$TIMESTAMP/g" \
  -e "s/BRANCH_PLACEHOLDER/$BRANCH/g" \
  -e "s/COMMIT_PLACEHOLDER/$COMMIT/g" \
  -e "s/RUN_ID_PLACEHOLDER/$RUN_ID/g" \
  -e "s/FOCUS_PLACEHOLDER/$TEST_FOCUS/g" \
  -e "s/BACKEND_STATUS_PLACEHOLDER/$BACKEND_STATUS/g" \
  -e "s/BACKEND_PASSED_PLACEHOLDER/$BACKEND_PASSED/g" \
  -e "s/BACKEND_FAILED_PLACEHOLDER/$BACKEND_FAILED/g" \
  -e "s/BACKEND_ERROR_PLACEHOLDER/$BACKEND_ERROR/g" \
  -e "s/BACKEND_DURATION_PLACEHOLDER/$BACKEND_DURATION/g" \
  -e "s/FRONTEND_BUILD_STATUS_PLACEHOLDER/$FRONTEND_BUILD_STATUS/g" \
  -e "s/FRONTEND_BUILD_SIZE_PLACEHOLDER/$FRONTEND_BUILD_SIZE/g" \
  -e "s/FRONTEND_BUILD_MODIFIED_PLACEHOLDER/$FRONTEND_BUILD_MODIFIED/g" \
  -e "s/TESTS_PASSED_PLACEHOLDER/$TESTS_PASSED/g" \
  -e "s/SUMMARY_STATUS_PLACEHOLDER/$SUMMARY_STATUS/g" \
  -e "s|BLOCKING_ISSUES_PLACEHOLDER|$BLOCKING_ISSUES|g" \
  "$RESULTS_FILE"

rm -f "$RESULTS_FILE.bak"

# Handle the backend output (escape for JSON)
ESCAPED_OUTPUT=$(printf '%s\n' "$BACKEND_OUTPUT" | python3 -c "import sys, json; print(json.dumps(sys.stdin.read()))" 2>/dev/null || echo '""')
python3 << 'EOFP'
import json
import sys

with open("$RESULTS_FILE", "r") as f:
    data = json.load(f)

data["backend_tests"]["summary"] = "$BACKEND_OUTPUT"

with open("$RESULTS_FILE", "w") as f:
    json.dump(data, f, indent=2)
EOFP

END_TIME=$(date +%s%3N)
TOTAL_DURATION=$((END_TIME - START_TIME))

echo "================================================"
echo "Test Run Complete"
echo "================================================"
echo "Results written to: $RESULTS_FILE"
echo "Total duration: ${TOTAL_DURATION}ms"
echo ""
echo "Summary:"
echo "  Backend tests: $BACKEND_STATUS ($BACKEND_PASSED passed, $BACKEND_FAILED failed)"
echo "  Frontend build: $FRONTEND_BUILD_STATUS"
echo "  Overall status: $SUMMARY_STATUS"
echo ""
echo "Next steps:"
if [ "$BACKEND_STATUS" = "failed" ]; then
  echo "  ⚠️  Backend tests failed - review test output"
fi
echo "  → Run 'lein figwheel' to start dev server"
echo "  → Open http://localhost:8890 in browser"
echo "  → Check console for errors"
echo ""
echo "Results available for agent consumption at: $RESULTS_FILE"
