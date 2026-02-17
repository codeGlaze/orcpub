# OrcPub E2E Tests

Playwright-based end-to-end tests for OrcPub, designed for automated testing with GitHub Codespaces.

## Quick Start

### Local Testing (app running locally)

```bash
# Install dependencies
cd e2e
npm install
npx playwright install chromium

# Run all tests
npm test

# Run specific scenario
npm run test:console   # Console errors only
npm run test:smoke     # UI smoke tests
npm run test:import    # Import/export tests
```

### Codespace Testing

```bash
# Connect to Codespace and run tests
./scripts/run-codespace-tests.sh

# With specific options
./scripts/run-codespace-tests.sh --scenarios console-errors --patch "Fix modal issue"

# Run in headed mode (see browser)
./scripts/run-codespace-tests.sh --headed

# Skip codespace connection (use local app)
./scripts/run-codespace-tests.sh --skip-connect
```

## Test Scenarios

| Scenario | File | Description |
|----------|------|-------------|
| `console-errors` | `scenarios/console-errors.spec.ts` | Captures JavaScript errors and warnings |
| `ui-smoke` | `scenarios/ui-smoke.spec.ts` | Verifies basic UI rendering and navigation |
| `import-export` | `scenarios/import-export.spec.ts` | Tests .orcbrew file import and data handling |

## Output

Tests generate structured JSON output for automated processing:

- `test-results/agent-report.json` - Structured report for Claude/agents
- `test-results/results.json` - Standard Playwright JSON report
- `playwright-report/` - HTML report (run `npx playwright show-report`)

### Agent Report Format

```json
{
  "timestamp": "2026-01-17T10:00:00Z",
  "appUrl": "http://localhost:8890",
  "patchContext": "Fix modal styling",
  "summary": {
    "total": 10,
    "passed": 9,
    "failed": 1,
    "overallStatus": "failed"
  },
  "consoleErrors": [...],
  "blockingIssues": ["Test 'modal closes' failed: timeout"],
  "recommendations": ["Fix failing tests before proceeding"]
}
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `APP_URL` | Base URL of the app | `http://localhost:8890` |
| `PATCH_CONTEXT` | Description of what's being tested | - |
| `TEST_SCENARIOS` | Comma-separated list of scenarios | `all` |
| `HEADLESS` | Run in headless mode | `true` |

### Test Config File

Copy `test-config.example.json` to `test-config.json` and customize:

```json
{
  "scenarios": ["console-errors", "ui-smoke"],
  "patchContext": "Fix modal styling issue #123",
  "appUrl": "http://localhost:8890"
}
```

## Writing New Tests

1. Create a new file in `scenarios/` directory
2. Use the shared utilities from `fixtures/test-utils.ts`
3. Attach console errors for agent reporting

```typescript
import { test, expect } from '@playwright/test';
import { setupConsoleCapture, attachConsoleErrors, waitForAppReady } from '../fixtures/test-utils';

test('my new test', async ({ page }, testInfo) => {
  const errors = setupConsoleCapture(page);

  await page.goto('/');
  await waitForAppReady(page);

  // Your test logic here

  await attachConsoleErrors(testInfo, errors);
});
```

## Codespace Integration

### Prerequisites

- GitHub CLI (`gh`) installed and authenticated
- Access to create/use Codespaces

### Manual Connection

```bash
# Connect to existing codespace
./scripts/connect-codespace.sh my-codespace-name

# Or let it find the first available
./scripts/connect-codespace.sh
```

### GitHub Actions

See `.github/workflows/e2e-tests.yml` for CI integration example.

## Troubleshooting

### "App not accessible"

1. Ensure the app is running: `lein figwheel` or `./start.sh`
2. Check port 8890 is available: `lsof -i :8890`
3. For Codespace: verify port forwarding is active

### "Playwright not found"

```bash
npm install
npx playwright install chromium
```

### "Tests timeout"

- Increase timeout in `playwright.config.ts`
- Check if app is loading slowly (ClojureScript compilation)
- Try running with `--headed` to see what's happening

### "Console errors in report but tests pass"

Tests only fail on JavaScript errors by default. Warnings are captured but don't fail tests. Review the `agent-report.json` for full details.

## Theme Screenshot Testing

### How Theme Testing Works

The theme toggle is on the character builder page (`/pages/dnd/5e/character-builder`). It shows "Theme: <name> ▾" and clicking it cycles through all themes.

```typescript
// Navigate once, then click to cycle
await page.goto('/pages/dnd/5e/character-builder');
await waitForAppReady(page);

// Click "Theme:" text to cycle to next theme
await page.getByText('Theme:').click();
```

### Disabling re-frame-10x

The re-frame-10x devtools panel can interfere with screenshots. Use the `dev-clean` profile:

```bash
# Build JS without 10x panel
lein with-profile dev-clean cljsbuild once dev

# Then start server
PORT=8890 lein run
```

**Note:** `lein run` serves pre-compiled JS. The 10x panel is baked into the JS build, not the server.

### Screenshot Location

Screenshots are saved to `e2e/screenshots/`.
