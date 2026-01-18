# Claude Code Instructions for OrcPub

## Project Overview
OrcPub is a D&D 5e character builder written in ClojureScript (frontend) and Clojure (backend), using:
- **Frontend**: Reagent + Re-frame (ClojureScript/React)
- **Backend**: Pedestal (REST framework) + Datomic (database)
- **Build**: Leiningen + Figwheel (hot reload)

## E2E Testing

### Running Tests
```bash
# Quick start (assumes Datomic is running)
PORT=8890 lein run &  # Start backend
cd e2e && npm test    # Run all tests
```

### Important Route Notes
OrcPub routes use **`/dnd/5e/`** (not `/dnd/e5/`):
- Character builder: `/pages/dnd/5e/character-builder`
- Spells: `/pages/dnd/5e/spells`
- My Content: `/dnd/5e/my-content` (no /pages/ prefix)

### DOM Structure
- **Home page (`/`)**: Splash page with `.splash-button` elements, no traditional header
- **Interior pages**: Rendered into `#app` container

### Expected Errors to Ignore
In production mode (`lein run`), you'll see:
```
WebSocket connection to 'ws://localhost:3449/figwheel-ws/dev' failed
```
This is expected - the Figwheel client code is in the compiled JS but Figwheel isn't running.

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

## Code Locations

| Feature | Location |
|---------|----------|
| Frontend entry | `web/cljs/orcpub/core.cljs` |
| Re-frame events | `src/cljs/orcpub/dnd/e5/events.cljs` |
| Re-frame subs | `src/cljs/orcpub/dnd/e5/subs.cljs` |
| Backend routes | `src/clj/orcpub/routes.clj` |
| D&D 5e rules | `src/cljc/orcpub/dnd/e5/` |
| Tests (CLJ) | `test/clj/`, `test/cljc/` |
| E2E tests | `e2e/scenarios/` |

## Testing Checklist for Changes

Before committing:
1. Run E2E tests: `cd e2e && npm test`
2. Check for JS console errors (tests capture these automatically)
3. Verify routes if UI changes involved

## Branch Strategy

- `develop` - Main development branch
- `testing/develop` - Testing infrastructure (devcontainer, E2E, CI)
- `agents/develop` - AI agent configuration and documentation
