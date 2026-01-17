#!/bin/bash
#############################################################################
# OrcPub Test Command
#
# Simple wrapper to run tests and generate structured results
#
# Usage:
#   ./.claude/test.sh [--focus TARGET] [--custom-tests TEST1,TEST2]
#
# Examples:
#   ./.claude/test.sh                    # Run all tests
#   ./.claude/test.sh --focus modals      # Focus on modal testing
#   ./.claude/test.sh --focus spell-selection --custom-tests "test_spell_ui"
#
# Output:
#   .claude/test-results.json - readable by Claude and other agents
#############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Check prerequisites
if ! command -v python3 &> /dev/null; then
  echo "Error: python3 is required"
  exit 1
fi

# Run the Python test generator
python3 "$SCRIPT_DIR/test-generator.py" "$@"
