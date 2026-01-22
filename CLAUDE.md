# Claude Code Instructions for OrcPub

## Project Overview
OrcPub is a D&D 5e character builder written in ClojureScript (frontend) and Clojure (backend), using:
- **Frontend**: Reagent + Re-frame (ClojureScript/React)
- **Backend**: Pedestal (REST framework) + Datomic (database)
- **Build**: Leiningen + Figwheel (hot reload)

## Attribution Policy

**Do not add** `Co-Authored-By: Claude` or `Generated with Claude Code` lines to commits or PR descriptions.
If Claude Code ignores this setting, add the local hook described in SETUP.md.

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

#### Option 1: Using start.sh (recommended)
```bash
./start.sh              # Interactive menu
./start.sh all          # Start transactor + server
./start.sh transactor   # Start transactor only
./start.sh server       # Start server only
./start.sh status       # Show running processes
./start.sh kill         # Stop all processes
```
The script will prompt to unpack Datomic if not already done.

#### Option 2: Manual
```bash
# 1. Start Datomic transactor
lib/datomic-free-0.9.5703/bin/transactor lib/datomic-free-0.9.5703/config/working-transactor.properties &

# 2. Start app server (production mode)
PORT=8890 lein run

# OR for development with hot reload:
lein figwheel  # Frontend dev server on port 3449
PORT=8890 lein run  # Backend still needed
```

### Calva (VSCode)
For interactive development, use Calva's "Jack-in" command. Select profiles at the prompt:
- **start-server**: Auto-starts the web server on REPL launch
- **css-watch**: Auto-recompiles CSS (Garden) on file changes
- **dev**: Development mode with debugging tools

Example: Select both `start-server` and `css-watch` for full dev experience.

### Lein Profiles
```bash
# Start REPL with auto-start server
lein with-profile +start-server repl

# Start REPL with server AND CSS auto-recompile
lein with-profile +start-server,+css-watch repl

# Compile CSS once
lein garden once

# Watch CSS for changes (standalone)
lein garden auto
```

### CSS (Garden) Compilation
Styles are written in Clojure using Garden (`src/clj/orcpub/styles/`). To recompile:
- **Once**: `lein garden once` (also runs automatically as a prep-task)
- **Watch mode**: `lein garden auto` or use the `+css-watch` profile

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

### For Agents: Branch Configuration

**On session start**, read `.claude/branch-config` to know where to route source code:

```bash
# .claude/branch-config
FEATURE_BRANCH=claude/add-color-themes-gyRhI   # Route src/* here
INTEGRATION_BRANCH=integrate/themes-nordic      # You work here
```

If this file doesn't exist, ask the user which branch to use for source code PRs.

### For Agents: Starting a Feature

Use the dual-branch workflow to keep your PR clean:

```bash
./scripts/git/start-feature.sh my-feature
# Creates: feature/my-feature (clean, from develop)
# Creates: integrate/my-feature (work branch, from agents/develop)
```

You work in `integrate/my-feature` (has CLAUDE.md, agent tooling). Code commits get routed to `feature/my-feature` (clean, for PR).

**Branch type prefixes**: `feature/`, `fix/`, `bugfix/`, `hotfix/`, `patch/`, `enhancement/`, `claude/`

### For Agents: During Development

1. **Hooks protect you automatically** - Wrong files get blocked with clear fix instructions

2. **Check `.claude/branch-config`** for the feature branch name

3. **Route code commits to the feature branch**:
   ```bash
   ./scripts/git/route-commit.sh HEAD claude/add-color-themes-gyRhI
   # Or use the branch name from branch-config
   ```

3. **If blocked**, follow the error message guidance:
   - Unstage wrong file: `git reset HEAD <file>`
   - Route to correct branch: `./scripts/git/route-commit.sh HEAD <target>`
   - Switch worktrees: `cd ../orcpub-<target>`

### For Agents: Cherry-Pick Gotchas

**Cherry-pick conflict semantics are counterintuitive:**
- `--ours` = branch you're ON (target branch HEAD)
- `--theirs` = commit being cherry-picked (incoming changes)

This is **opposite** of merge semantics!

**When feature branch is messy** (many old commits, wrong files):
Don't cherry-pick. Instead, reset to clean state and copy files:
```bash
git checkout feature-branch
git reset --hard origin/develop
git checkout integration-branch -- src/ dev/ project.clj
git commit -m "Feature description"
git push --force origin feature-branch
```

### For Agents: Preparing for PR

PRs are **manually created by the user** as the final step. Agents should:

1. **Prepare the feature branch** (clean commits, force-pushed if needed)
2. **Provide the compare URL** for convenience
3. **Do NOT auto-create PRs** via `gh pr create`

When ready, ensure the feature branch is pushed:
```bash
git checkout feature/my-feature
git push -u origin feature/my-feature
```

Then provide the user with:
```
PR URL when ready: https://github.com/<org>/<repo>/compare/develop...<feature-branch>
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
