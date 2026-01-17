# Codespace Testing Guide

This document explains how to run E2E tests from within a GitHub Codespace.

## Quick Start (In Codespace)

```bash
# 1. Merge the E2E testing infrastructure
git fetch origin claude/review-testing-automation-aFeqE
git merge origin/claude/review-testing-automation-aFeqE --no-edit

# 2. Install dependencies
cd e2e
npm install
npx playwright install chromium --with-deps

# 3. Start the app (in another terminal or background)
cd /workspaces/orcpub
./start.sh &

# 4. Wait for app to be ready, then run tests
cd /workspaces/orcpub/e2e
APP_URL=http://localhost:8890 npm test
```

## Testing a Specific Patch

To test the `add-error-handling` patch:

```bash
# Checkout the patch branch
git checkout claude/add-error-handling-mk82zx2vzck9nv9m-IMm3C

# Merge in E2E tests
git fetch origin claude/review-testing-automation-aFeqE
git cherry-pick origin/claude/review-testing-automation-aFeqE

# Install and run
cd e2e && npm install && npx playwright install chromium --with-deps
APP_URL=http://localhost:8890 PATCH_CONTEXT="add-error-handling" npm test

# View results
cat test-results/agent-report.json
```

## Available Test Scenarios

| Command | What it tests |
|---------|---------------|
| `npm test` | All scenarios |
| `npm run test:console` | Console errors only |
| `npm run test:smoke` | UI smoke tests |
| `npm run test:import` | Import/export functionality |

## Using the Orchestration Script

```bash
# Full test run with context
./scripts/run-codespace-tests.sh --skip-connect --scenarios all --patch "Description of patch"

# Just console errors
./scripts/run-codespace-tests.sh --skip-connect --scenarios console-errors

# With visible browser
./scripts/run-codespace-tests.sh --skip-connect --headed
```

## Output Files

After running tests, check:

- `test-results/agent-report.json` - Structured JSON for automated analysis
- `test-results/results.json` - Standard Playwright JSON report
- `playwright-report/index.html` - Visual HTML report (`npx playwright show-report`)

## Agent Report Format

The `agent-report.json` contains:

```json
{
  "timestamp": "2026-01-17T...",
  "appUrl": "http://localhost:8890",
  "patchContext": "add-error-handling",
  "summary": {
    "total": 15,
    "passed": 14,
    "failed": 1,
    "overallStatus": "failed"
  },
  "consoleErrors": [...],
  "blockingIssues": ["Test X failed: reason"],
  "recommendations": ["Fix failing tests before proceeding"]
}
```

## Limitations

- **Claude Code on the web** cannot directly connect to Codespaces (domains blocked)
- Run tests FROM the Codespace and share results back
- Or run Claude Code inside the Codespace with full access

## Branch Information

- **E2E Infrastructure**: `claude/review-testing-automation-aFeqE`
- **Error Handling Patch**: `claude/add-error-handling-mk82zx2vzck9nv9m-IMm3C`
- **Devcontainer Setup**: `testing/develop`
