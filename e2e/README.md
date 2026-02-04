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

## Test Fixtures

Located in `/test/`:
- `duplicate-external-a.orcbrew` - Homebrew with artificer, blood-hunter, custom-lineage
- `duplicate-external-b.orcbrew` - Similar content for duplicate testing

**Important**: Do NOT modify these fixture files. They are properly formatted orcbrew files.

## Key UI Selectors

```typescript
// Missing Content Warning
'#missing-content-warning'        // Warning banner
'#missing-content-details'        // Expanded details
'.missing-content-item'           // Individual items (data-key, data-type attrs)

// Character Builder
'text=Class / Level'              // Class section header (click to navigate)
'select'                          // Class dropdown (use selectOption())
'text=Custom Lineage (Variant)'   // Clickable race option

// My Content
'button:has-text("Delete All")'   // Delete all button
'.link-button:has-text("delete")' // Confirmation (NOT "Yes/Confirm"!)
```

## Critical Gotchas

### 1. CLJS Compilation is NOT Automatic

`lein run` does NOT compile ClojureScript. The server will use stale JS.

```bash
# WRONG - uses stale code
lein run

# CORRECT - compile first
lein cljsbuild once dev && PORT=8890 lein run
```

### 2. re-frame-10x Debug Panel Blocks Clicks

In dev mode, the debug panel intercepts click events. Hide it before clicking:

```typescript
await page.evaluate(() => {
  const panel = document.getElementById('--re-frame-10x--');
  if (panel) panel.style.display = 'none';
});
```

### 3. Class Selection Uses Dropdown, Not Tiles

Classes are in a `<select>` dropdown, unlike races which are clickable tiles:

```typescript
// WRONG - times out waiting for visible element
const artificer = page.locator('text=Artificer').first();
await artificer.click();

// CORRECT - use selectOption with the class key
const classDropdown = page.locator('select').filter({ hasText: /Artificer|Barbarian/i });
await classDropdown.selectOption('artificer');
```

### 4. Delete All Confirmation Button

The confirmation is a `.link-button` with text "delete", not a regular button:

```typescript
// WRONG - won't find confirmation
const confirmBtn = page.locator('button', { hasText: /yes|confirm|ok/i });

// CORRECT - link-button with "delete"
const confirmBtn = page.locator('.link-button, button, span', { hasText: /^delete$/i });
```

### 5. Test Accounts

- **Credentials**: `tester1@example.com` / `Testing123!`
- **Seed command**: `lein with-profile +no-prep run -m orcpub.seed-test-accounts`
- **Port conflicts**: Use `PORT=8891` if 8890 is in use

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
- **Class selection**: Make sure you're using `selectOption()` not `click()`

### "Console errors in report but tests pass"

Tests only fail on JavaScript errors by default. Warnings are captured but don't fail tests. Review the `agent-report.json` for full details.

### Debugging Failed Tests

1. **Check screenshots**: `e2e/test-results/*/test-failed-*.png`
2. **Check videos**: `e2e/test-results/*/*.webm`
3. **Check error context**: `e2e/test-results/*/error-context.md`
4. **Run single test**: `npx playwright test --grep "test name"`
5. **Run headed**: `npx playwright test --headed`

### Port Conflicts

```bash
# Kill stuck processes
pkill -f "lein"
# or
killall -9 java
```
