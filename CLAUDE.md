@AGENTS.md
@BRANCH.md

## Claude-Specific Settings

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

# Build JS without re-frame-10x panel (for cleaner screenshots/E2E)
# The re-frame-10x panel is baked into the JS build, not the server
# Use this when you need clean UI for visual testing
lein with-profile dev-clean figwheel
# Or just build once:
lein with-profile dev-clean cljsbuild once dev
# Then start server normally: PORT=8890 lein run
```

### CSS (Garden) Compilation
Styles are written in Clojure using Garden (`src/clj/orcpub/styles/`). To recompile:
- **Once**: `lein garden once` (also runs automatically as a prep-task)
- **Watch mode**: `lein garden auto` or use the `+css-watch` profile

#### Style Architecture
```
src/clj/orcpub/styles/
├── core.clj      # Base styles, layout, utilities (~1400 lines)
├── themes.clj    # Theme definitions (11 themes, ~1000 lines)
└── colors.clj    # Color palettes (Nord, Midnight, Forest, Crimson, etc.)
```

- **Adding a theme**: Define in `themes.clj`, add to `all-themes` vector, update cycle in `events.cljs`
- **CSS variables**: Used for theme-aware values (e.g., `--header-icon-color`)
- **Themes use concat**: Each theme is a vector of rules, concatenated into `app`
- **Theme backgrounds**: Can layer SVG patterns with gradients using `background-image`

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
| `testing/develop` | `e2e/*`, `.devcontainer/*`, `test/*`, `.github/*`, `scripts/*`, `.githooks/*`, `.gitignore`, `Dockerfile*`, `docker-compose*`, `*.sh` | Source code |
| `agents/develop` | `*.md`, `.claude/*`, `agents/*`, `docs/*`, `scripts/git/*`, `.githooks/*` | Source code, tests |
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

### Available Themes (11 total)
- `dark-theme` - Default dark mode (calm, good for night use)
- `nord-theme` - Nord dark palette (calm Nordic)
- `midnight-theme` - Deep blue midnight
- `forest-theme` - Forest green with dot pattern texture
- `slate-theme` - Cool gray slate
- `crimson-theme` - Deep red/burgundy
- `light-theme` - Basic light mode
- `light-plus-theme` - Enhanced light with better contrast
- `sunset-theme` - Warm sunset colors
- `arctic-aurora-theme` - Teal/cyan aurora colors
- `parchment-theme` - Warm parchment/paper aesthetic

### Theme Toggle
The theme toggle is on the character builder page header. It shows "Theme: \<name\> ▾" and clicking cycles through all themes.

**Key files:**
- Toggle component: `src/cljs/orcpub/character_builder.cljs` (`theme-toggle` fn)
- Theme cycle logic: `src/cljs/orcpub/dnd/e5/events.cljs` (`:cycle-theme` event)
- Theme spec: `src/cljs/orcpub/dnd/e5/db.cljs` (`:theme` spec)
- Toggle styling: `src/clj/orcpub/styles/core.clj` (`.theme-toggle` rule)

**Styling approach:** Uses `currentColor` for borders so it works on both light and dark themes without hardcoded colors.

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

**How CSS mask icons work:**
1. `.svg-icon-wrapper` div has `background-color: currentColor` and `mask-image: url(icon.svg)`
2. The SVG acts as a stencil - only the SVG shape is visible
3. Color comes from CSS `color` property (inherited from `.main-text-color` or `.svg-icon-dark`/`.svg-icon-light`)
4. Both `mask-image` AND `-webkit-mask-image` must be set for cross-browser support

**Key files:**
- CLJS component: `src/cljs/orcpub/dnd/e5/views.cljs` (line ~222)
- CLJC component: `src/cljc/orcpub/dnd/e5/views_2.cljc` (for splash page)
- CSS styles: `src/clj/orcpub/styles/core.clj` (`.svg-icon-wrapper`)

### Icon Color System
Icons use CSS variables for theme customization:
- `--icon-color`: Default body icon color (dark themes: white, light themes: Aurora colors)
- `--icon-active-color`: Selected/active icon color
- `--header-icon-color`: Header icon and text color (default: `white`)
- `--header-active-bg`: Active tab background (default: frost cyan)

**Light theme colors:**
- `nord-light-theme`: frost blue body icons, **white header icons**, aurora green active
- `nord-light-theme-elevated`: aurora purple body icons, **white header icons**, aurora green active

**Critical**: Header icons must ALWAYS be light (nord6/white) because header background is dark. Mid-tone Aurora colors don't have enough contrast on dark backgrounds.

**Important**: Header overrides use `!important` in `core.clj` to beat theme specificity. Never add header styling in theme files.

### Theme Gotchas
1. **`.svg-icon` class has `visibility: hidden`** - It's for the mask-based system where the img is hidden. Don't reuse this class for plain `<img>` tags.
2. **Splash page is server-rendered (CLJC)** - Uses pure `svg-icon` without re-frame. Theme must be passed explicitly.
3. **Garden CSS syntax**: Each theme rule must be inside a single vector. Multiple top-level vectors in a `def` causes "Too many arguments to def" error.
4. **Vendor prefixes in Reagent/React styles** - Use camelCase, not kebab-case:
   - WRONG: `:-webkit-mask-image` (React silently drops this!)
   - RIGHT: `:WebkitMaskImage` (React renders as `-webkit-mask-image`)
   - The CLJC `style` function in `views_2.cljc` converts camelCase back to CSS format for server rendering.
5. **Header elements need `!important`** - Theme rules like `.app.theme .main-text-color` have 3-class specificity. Header overrides in `core.clj` use `!important` to ensure header icons/logo/text stay light regardless of theme.
6. **Button gradient fix**: Never use `background-image: none` - it cancels out `background` shorthand gradients. Always use `background-image` directly with the gradient value.
7. **Breaking up heavy solid colors**: Use SVG data URI patterns in `background-image` layered with gradients. Example from Forest theme:
   ```clojure
   {:background-image "url(\"data:image/svg+xml,...\"), linear-gradient(...)"}
   ```
8. **Theme toggle visibility**: Use `currentColor` for borders instead of hardcoded colors so it works on both light and dark themes.

### Playwright Theme Screenshots

**How to test themes visually:**
```bash
# Build JS without re-frame-10x panel (cleaner screenshots)
lein with-profile dev-clean cljsbuild once dev

# Start server
PORT=8890 lein run

# Run theme screenshot test
cd e2e && npm test -- --grep "Theme Screenshots"
```

**What DOESN'T work:**
- Setting localStorage before navigation (theme loads from re-frame db, not localStorage)
- Using EDN format in localStorage (it's a webpage, everything is JavaScript)

**What DOES work:**
- Navigate to `/pages/dnd/5e/character-builder`
- Click the "Theme:" text to cycle through themes
- Take screenshot after each click

Screenshots are saved to `e2e/screenshots/` with format `01-dark-theme.png`, `02-nord-theme.png`, etc.

See `e2e/scenarios/theme-screenshots.spec.ts` for the working implementation.
