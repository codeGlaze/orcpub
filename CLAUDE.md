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

#### Style Architecture
```
src/clj/orcpub/styles/
├── core.clj      # Base styles, layout, utilities (~1400 lines)
├── themes.clj    # Theme definitions (light, nord variants)
└── colors.clj    # Color palettes (Nord, core app colors)
```

- **Adding a theme**: Define in `themes.clj`, add to `all-themes` vector
- **CSS variables**: Used for theme-aware values (e.g., `--header-icon-color`)
- **Themes use concat**: Each theme is a vector of rules, concatenated into `app`

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
| **Styles (Garden)** | `src/clj/orcpub/styles/` |
| - Core styles | `src/clj/orcpub/styles/core.clj` |
| - Theme definitions | `src/clj/orcpub/styles/themes.clj` |
| - Color palettes | `src/clj/orcpub/styles/colors.clj` |
| Splash page (CLJC) | `src/cljc/orcpub/dnd/e5/views_2.cljc` |

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

### For Agents: Starting a Feature

Use the dual-branch workflow to keep your PR clean:

```bash
./scripts/git/start-feature.sh my-feature
# Creates: feature/my-feature (clean, from develop)
# Creates: integrate/my-feature (work branch, from agents/develop)
```

You work in `integrate/my-feature` (has CLAUDE.md, agent tooling). Code commits get routed to `feature/my-feature` (clean, for PR).

**Branch type prefixes**: `feature/`, `fix/`, `bugfix/`, `hotfix/`, `patch/`, `enhancement/`

### For Agents: During Development

1. **Hooks protect you automatically** - Wrong files get blocked with clear fix instructions

2. **Route code commits to the clean branch**:
   ```bash
   ./scripts/git/route-commit.sh HEAD my-feature
   # Cherry-picks to feature/my-feature
   ```

3. **If blocked**, follow the error message guidance:
   - Unstage wrong file: `git reset HEAD <file>`
   - Route to correct branch: `./scripts/git/route-commit.sh HEAD <target>`
   - Switch worktrees: `cd ../orcpub-<target>`

### For Agents: Creating the PR

When ready, your `feature/my-feature` branch is already clean:
```bash
git checkout feature/my-feature
git push -u origin feature/my-feature
gh pr create --base develop
```

### For Agents: Pulling Updates

Use `pull.sh` to merge updates from multiple branches into your integration branch:

```bash
./pull.sh
# Merges: testing/develop, agents/develop, and a working branch you select
```

The script remembers your last selections and handles conflicts gracefully.

### Worktrees (for routing to develop/testing/agents)

```
/workspaces/orcpub/          # Your working branch
/workspaces/orcpub-develop/  # develop
/workspaces/orcpub-testing/  # testing/develop
/workspaces/orcpub-agents/   # agents/develop
```

See `scripts/git/README.md` for full documentation.

## Theming System

### Available Themes
- `light-theme` - Basic light mode
- `nord-theme` - Nord dark palette
- `nord-light-theme` - Nord light palette
- `nord-theme-elevated` - Nord dark with shadows/depth
- `nord-light-theme-elevated` - Nord light with modern card design

### SVG Icon System
Icons use CSS mask technique for theme-aware coloring:

```clojure
;; In CLJS (with re-frame subscription)
(svg-icon "bookshelf" 32)           ; uses theme subscription
(svg-icon "bookshelf" 32 "")        ; empty string = use subscription
(svg-icon "bookshelf" 32 "nord-theme") ; explicit theme override

;; In CLJC (pure, no subscriptions) - for server-rendered pages
(svg-icon "bookshelf" 32 "dark-theme") ; theme required
```

**Key files:**
- CLJS component: `src/cljs/orcpub/dnd/e5/views.cljs` (line ~222)
- CLJC component: `src/cljc/orcpub/dnd/e5/views_2.cljc` (for splash page)
- CSS styles: `src/clj/orcpub/styles/core.clj` (`.svg-icon-wrapper`)

### Header Icon Colors
The header background stays dark across ALL themes. Header icons use `--header-icon-color` CSS variable:
- Default: `white`
- Nord themes: `nord6` (#ECEFF4 - bright snow white)

**Important**: Light themes should NOT use dark header icon colors - the header background doesn't change with theme.

### Theme Gotchas
1. **`.svg-icon` class has `visibility: hidden`** - It's for the mask-based system where the img is hidden. Don't reuse this class for plain `<img>` tags.
2. **Splash page is server-rendered (CLJC)** - Uses pure `svg-icon` without re-frame. Theme must be passed explicitly.
3. **Garden CSS syntax**: Each theme rule must be inside a single vector. Multiple top-level vectors in a `def` causes "Too many arguments to def" error.
