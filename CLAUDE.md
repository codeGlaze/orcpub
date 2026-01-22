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

- `develop` - Main development branch (PRs only, no direct push)
- `testing/develop` - Testing infrastructure (devcontainer, E2E, CI)
- `agents/develop` - AI agent configuration and documentation
- `feature/*`, `integrate/*` - Feature and integration branches

### Branch Protection

Git hooks automatically enforce branch rules:

| Branch | Allowed Files | Blocked |
|--------|---------------|---------|
| `develop` | N/A | Direct pushes (use PR) |
| `testing/develop` | `e2e/*`, `.devcontainer/*`, `.github/*` | Source code |
| `agents/develop` | `*.md`, `.claude/*`, `docs/*` | Source code, tests |
| `feature/*` | Everything | Nothing |

### For Agents: Workflow

1. **Hooks protect you automatically** - If you try to commit wrong files, you'll see a clear error with fix instructions

2. **If blocked**, follow the guidance in the error message:
   - Unstage the wrong file: `git reset HEAD <file>`
   - Route to correct branch: `./scripts/git/route-commit.sh HEAD <target>`
   - Switch worktrees: `cd ../orcpub-<target>`

3. **Preparing a PR to develop**:
   - Feature branches from `agents/develop` contain agent files
   - Before PR, run: `./scripts/git/prepare-pr.sh`
   - This creates a clean branch without agent files

4. **Worktrees** (if set up):
   ```
   /workspaces/orcpub/          # Your working branch
   /workspaces/orcpub-develop/  # develop
   /workspaces/orcpub-testing/  # testing/develop
   /workspaces/orcpub-agents/   # agents/develop
   ```

See `scripts/git/README.md` for full documentation.
