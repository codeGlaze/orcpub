# Session Summary: E2E Testing Setup

**Date**: 2026-01-18
**Branch**: testing/develop
**Status**: Complete - all 20 tests passing

## What Was Done

1. Merged `testing/develop` into feature branch to get devcontainer setup
2. Installed Node.js, npm dependencies, and Playwright Chromium
3. Started app with Datomic transactor + `PORT=8890 lein run`
4. Fixed multiple test failures (routes, selectors, error filtering)
5. Got all 20 E2E tests passing
6. Created documentation and pushed to appropriate branches

## Key Discoveries

### Route Naming
- **Correct**: `/dnd/5e/` (five-e)
- **Wrong**: `/dnd/e5/` (e-five)
- Found by inspecting splash page links

### DOM Structure
- `#app` - main container (exists)
- `#app-header` - does NOT exist
- `.splash-button` - navigation on home page
- No traditional header on splash page

### Figwheel Errors
Production mode (`lein run`) still has Figwheel client code that tries to connect to `ws://localhost:3449`. These errors are expected and must be filtered:

```typescript
errors.filter(e =>
  !e.text.includes('figwheel-ws') &&
  !e.text.includes('ws://localhost:3449')
)
```

### App Startup
Full app needs two components:
```bash
# Terminal 1
./bin/transactor config/dev-transactor.properties

# Terminal 2
PORT=8890 lein run
```

The `PORT` env var is required.

## Files Modified

| File | Change |
|------|--------|
| `e2e/fixtures/test-utils.ts` | Fixed selector `#app-header` → `#app`, fixed routes |
| `e2e/scenarios/*.spec.ts` | Fixed routes `/dnd/e5/` → `/dnd/5e/`, added Figwheel filtering |
| `e2e/reporters/agent-reporter.ts` | Added expected error filtering |
| `e2e/playwright.config.ts` | Disabled Firefox/mobile (not installed) |
| `e2e/AGENT-GUIDE.md` | Created - quick start for agents |
| `CLAUDE.md` | Created - project overview |

## Quick Test Commands

```bash
cd e2e
npm run test:console  # 5 tests, fastest
npm test              # all 20 tests
cat test-results/agent-report.json | jq '.summary'
```

## Branch Strategy

- `testing/develop` - E2E tests, devcontainer, start scripts
- `agents/develop` - CLAUDE.md, agents.md, this summary
- Keep separate for selective merges to `develop`
