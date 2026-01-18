# OrcPub E2E Testing - Agent Quick Start Guide

This guide helps AI agents quickly run and understand the E2E test infrastructure.

## Quick Commands

```bash
# Start the app (from project root)
cd /workspaces/orcpub/lib/datomic-free-0.9.5703 && ./bin/transactor config/working-transactor.properties &
sleep 5
PORT=8890 lein run &

# Wait for app to be ready
curl -s http://localhost:8890 >/dev/null && echo "App ready"

# Run E2E tests (from e2e directory)
cd /workspaces/orcpub/e2e
npm test

# Quick smoke test only
npm run test:console
```

## Key Learnings (Lessons Learned)

### 1. Route Paths
OrcPub uses `/dnd/5e/` NOT `/dnd/e5/`:
- ✅ Correct: `/pages/dnd/5e/character-builder`
- ❌ Wrong: `/pages/dnd/e5/character-builder`

### 2. DOM Selectors
The app has a **splash page** at `/` without a traditional header:
- Home page: Use `.splash-button` for navigation buttons
- App container: Use `#app` (not `#app-header`)
- Interior pages: May have different layouts

### 3. Expected Console Errors
When running `lein run` (production mode), you'll see Figwheel WebSocket errors:
```
WebSocket connection to 'ws://localhost:3449/figwheel-ws/dev' failed
```
**These are expected** - the compiled JS includes Figwheel client code, but Figwheel isn't running. The test reporter filters these automatically.

### 4. Test Timeouts
- Default timeout: 30s per test
- Some pages load slowly - use `page.waitForLoadState('networkidle')` instead of `waitForAppReady()` when needed
- Multi-page navigation tests should use `test.setTimeout(60000)`

## Test Structure

```
e2e/
├── scenarios/
│   ├── console-errors.spec.ts  # JS error detection (most critical)
│   ├── ui-smoke.spec.ts        # Basic UI rendering
│   └── import-export.spec.ts   # File import/export features
├── fixtures/
│   ├── test-utils.ts           # Shared utilities (waitForAppReady, etc.)
│   └── test-content.orcbrew    # Test data file
└── reporters/
    └── agent-reporter.ts       # Outputs JSON for agent parsing
```

## Understanding Test Results

The agent reporter outputs to `e2e/test-results/agent-report.json`:

```json
{
  "summary": {
    "total": 20,
    "passed": 20,
    "failed": 0,
    "overallStatus": "passed"
  },
  "recommendations": ["All tests passed with no unexpected errors - ready to proceed"]
}
```

### Status Meanings
- `overallStatus: "passed"` - All tests pass, no unexpected errors → Safe to proceed
- `overallStatus: "failed"` - Has failures or unexpected JS errors → Investigate before proceeding

## Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Tests timeout waiting for `#app` | Wrong route or page not loading | Check route path, use `/pages/dnd/5e/...` |
| "Not Found" page | Incorrect route | Verify route exists (check homepage links) |
| Firefox/mobile tests fail | Browser not installed | Only Chromium is installed by default |
| Console errors about WebSocket | Figwheel not running | Expected in production mode, filtered out |

## Adding New Tests

1. Check actual DOM structure first (use browser DevTools)
2. Use correct routes (look at homepage links for reference)
3. Use `filterFigwheelErrors()` helper if checking console errors
4. Keep tests focused - one assertion per test when possible
