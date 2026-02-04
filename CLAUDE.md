# Claude Code Instructions for OrcPub

## Project Overview
OrcPub is a D&D 5e character builder written in ClojureScript (frontend) and Clojure (backend), using:
- **Frontend**: Reagent + Re-frame (ClojureScript/React)
- **Backend**: Pedestal (REST framework) + Datomic (database)
- **Build**: Leiningen + Figwheel (hot reload)

## E2E Testing

See **[e2e/README.md](e2e/README.md)** for full documentation.

### Quick Start
```bash
# Assumes Datomic is running
lein cljsbuild once dev  # REQUIRED: compile CLJS first!
PORT=8890 lein run &     # Start backend
cd e2e && npm test       # Run all tests
```

**CRITICAL**: `lein run` does NOT compile ClojureScript. After any `.cljs` changes, you MUST run `lein cljsbuild once dev` before `lein run` or the server will use stale JS.

### Important Route Notes
OrcPub routes use **`/dnd/5e/`** (not `/dnd/e5/`):
- Character builder: `/pages/dnd/5e/character-builder`
- Spells: `/pages/dnd/5e/spells`
- My Content: `/dnd/5e/my-content` (no /pages/ prefix)

## Development Workflow

### Starting the App
```bash
# 1. Start Datomic transactor
cd lib/datomic-free-0.9.5703
./bin/transactor config/working-transactor.properties &

# 2. Start app server (production mode)
PORT=8890 lein run

# OR for development with hot reload:
lein figwheel  # Frontend dev server on port 3449
PORT=8890 lein run  # Backend still needed
```

### Calva (VSCode)
For interactive development, use Calva's "Jack-in" command which starts the REPL with Figwheel.

### REPL Helper Functions (dev/user.clj)
Available in any dev REPL:
- `(start-server)` / `(stop-server)` - Start/stop the web server
- `(ensure-test-accounts!)` - Create test accounts from `dev/test-accounts.edn`
- `(list-test-accounts)` - Check which test accounts exist
- `(verify-new-user "email")` - Mark a user as verified (skip email flow)
- `(init-database)` - Initialize/reset the Datomic schema
- `(fig-start)` / `(fig-stop)` - Start/stop Figwheel
- `(cljs-repl)` - Connect to ClojureScript REPL (after fig-start)

## Code Locations

| Feature | Location |
|---------|----------|
| Frontend entry | `web/cljs/orcpub/core.cljs` |
| Re-frame events | `src/cljs/orcpub/dnd/e5/events.cljs` |
| Re-frame subs | `src/cljs/orcpub/dnd/e5/subs.cljs` |
| Backend routes | `src/clj/orcpub/routes.clj` |
| D&D 5e rules | `src/cljc/orcpub/dnd/e5/` |
| REPL dev helpers | `dev/user.clj` |
| Test accounts | `dev/test-accounts.edn` |
| Tests (CLJ) | `test/clj/`, `test/cljc/` |
| E2E tests | `e2e/scenarios/` |

## Testing Checklist for Changes

Before committing:
1. Compile CLJS: `lein cljsbuild once dev`
2. Restart server: `PORT=8890 lein run`
3. Run E2E tests: `cd e2e && npm test`
4. Check for JS console errors (tests capture these automatically)
5. Verify routes if UI changes involved

### Test Accounts
- Credentials in `dev/test-accounts.edn`
- **Preferred**: Run `./add-testers.sh` (checks Datomic, skips Garden, works without server)
- Alternative: From REPL, call `(ensure-test-accounts!)` or `(list-test-accounts)`
- Default: `tester1@example.com` / `Testing123!`

The `add-testers.sh` script:
1. Checks Datomic is running (required)
2. Tries existing nREPL on port 7888 (fast path)
3. Falls back to fresh REPL with `+no-prep` profile (skips Garden)
4. Works without `(start-server)` - connects directly to Datomic
5. Shows spinner in TTY, static message in CI/pipes
6. Filters REPL noise from display, keeps full log in `/tmp/add-testers-*.log`
7. Detects and surfaces real errors (not expected JVM shutdown noise)
8. Exit code 0 = success, 1 = error (usable in CI)

### Gotchas
- **re-frame-10x**: Debug panel blocks clicks in dev mode. Hide with `page.evaluate()`.
- **Port conflicts**: Use `PORT=8891` for seeding if 8890 in use.
- **Class dropdowns**: In character builder, classes are selected via `<select>` dropdown, not clickable tiles like races. Use `page.selectOption('artificer')` not `page.click('text=Artificer')`.
- **Delete All confirmation**: The confirmation button is a `.link-button` with text "delete" (lowercase), not a regular button with "Yes/Confirm".
- **CLJS compilation**: `lein run` does NOT compile ClojureScript. Always run `lein cljsbuild once dev` first after .cljs changes.
- **Garden CSS**: `lein repl` runs Garden by default (slow). Use `lein with-profile +no-prep repl` or `SKIP_GARDEN=1 lein repl` to skip it.
- **with-db macro**: Works with or without server running - connects directly to Datomic if needed.
- **SocketException on REPL exit**: Expected JVM shutdown noise when using `System/exit` - not a real error. The `add-testers.sh` script filters this automatically.

### UI Layout Patterns
- **Race selection**: Clickable tiles - click on race name directly
- **Class selection**: `<select>` dropdown - NOT tiles (use `selectOption()`)
- **Subclass**: Appears after class selected (level-dependent)
- **Button styles**: `.form-button` (standard), `.link-button` (text-styled, used in confirmations)
- **Routes**: URLs use `/dnd/5e/`, namespaces use `orcpub.dnd.e5` - don't confuse them

### Key UI Selectors for E2E Tests
- `#missing-content-warning` - The missing content warning banner
- `#missing-content-details` - Expanded details container
- `.missing-content-item` - Individual missing items with `data-key` and `data-type` attributes

## Linter Configuration

- **clj-kondo**: Config in `.clj-kondo/config.edn`
- **clojure-lsp**: Also reads `.clj-kondo/config.edn` (no separate `.lsp/config.edn` needed)
- Custom macros like `with-db` are excluded from unresolved-symbol warnings
- REPL helper functions in `user` namespace are excluded from unused-public-var warnings

## Branch Strategy

- `develop` - Main development branch
- `testing/develop` - Testing infrastructure (devcontainer, E2E, CI)
- `agents/develop` - AI agent configuration and documentation
