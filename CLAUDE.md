@AGENTS.md
@BRANCH.md

## Claude-Specific Settings

### Commit signing — Verified badge as codeGlaze

Commits should show GitHub's green **Verified** badge under the **codeGlaze** identity, not
Anthropic's. This needs setup **per environment** — the signing key is ephemeral, so
regenerate it in each new container.

Why it isn't automatic: the remote environment signs by default with Claude Code Remote's
key (via `/tmp/code-sign`, `gpg.format=ssh`), registered to `noreply@anthropic.com`. A
commit authored as codeGlaze but signed with that key shows **Unverified** (identity
mismatch). `ssh-keygen` is **not installed**, so the SSH route can't be redirected — use
**GPG** (installed) instead.

```bash
# 1. generate a per-environment GPG signing key for codeGlaze
gpg --batch --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'codeGlaze <github@codeglaze.com>' ed25519 sign never

# 2. point this repo's git at it (openpgp, not the env ssh signer)
KEYID=$(gpg --list-secret-keys --with-colons github@codeglaze.com | awk -F: '/^fpr:/{print $10; exit}')
git config gpg.format openpgp
git config user.signingkey "$KEYID"
git config user.name  codeGlaze
git config user.email github@codeglaze.com
git config commit.gpgsign true

# 3. hand the user the public key to add on GitHub (Settings > SSH and GPG keys > New GPG key)
gpg --armor --export github@codeglaze.com
```

Notes:
- `github@codeglaze.com` must be a **verified email** on the codeGlaze GitHub account, or the
  badge stays Unverified even with a good signature (fall back to the GitHub `noreply` email).
- The Stop hook (`~/.claude/stop-hook-git-check.sh`) is hardcoded to want
  `noreply@anthropic.com`, so it keeps calling codeGlaze commits "Unverified" — that's the
  hook being wrong; GitHub's badge is the real signal. **Do not** reset the author to
  Anthropic. The harness restores the hook if edited; `git config commit.gpgsign false`
  silences its attribution block but disables signing (losing the badge), so prefer to just
  ignore the message.
- Commits already made unsigned or with the env key stay Unverified; don't rewrite history.

## E2E Testing

### Running Tests
```bash
# Quick start (assumes Datomic is running)
PORT=8890 lein run &  # Start backend
cd e2e && npm test    # Run all tests
```

### Browser probes (`test/browser/`)

Separate from the `e2e/` suite above, and **neither `lein test` nor the CLJS runner touches
them** — so "both suites green" says nothing about these. One sat failing and exiting 1 for
several commits because nothing ran it.

Note this branch's tree does not carry `test/browser/` or `scripts/test/` — it is a docs
branch on an old code snapshot. The paths below are on the code branches (`integration` and
its feature branches), which is where you will be running them.

```bash
lein fig:build && lein e2e-server        # in another shell
node scripts/test/run-browser-probes.js  # every asserting probe; non-zero if any fails
```

`ORCBREW_PACK=<pack>.orcbrew` enables the probes that need imported homebrew; a probe that
cannot run reports `SKIP` with its reason rather than passing quietly.

It also catches the two ways a probe lies rather than fails:

- **It stops asserting.** A control renamed out from under an `if (await x.count())` guard
  takes its checks with it and the probe still exits 0. Per-probe assertion counts live in
  `scripts/test/probe-baseline.json`; running fewer is a failure.
- **It sits there.** Stuck is detected by *silence* (180s), not total runtime — the slowest
  probe legitimately runs 393s, so no runtime limit short enough to catch a hang would spare
  it. A 30s heartbeat reports how long a probe has been silent, separately from how long it
  has been running.

Write assertions that can fail: `after <= opened` under the name "filtering narrows the list"
passes when filtering does nothing, and a missing control is the failure, not a reason to
print SKIP and carry on. Both were real, in this repo.

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

#### Option 1: Using menu (recommended)
```bash
./menu start            # Start Datomic + Server (background)
./menu                  # Interactive menu (start, stop, status, add user)
./menu stop             # Stop all services
./menu status           # Show running processes
```

#### Option 2: Using start.sh directly
```bash
./start.sh              # All-in-one (Datomic + Server)
./start.sh --tmux       # All services in tmux session 'orcpub'
./start.sh datomic      # Start transactor only
./start.sh server       # Start server only (after Datomic ready)
./start.sh figwheel     # Hot-reload frontend dev server
./start.sh --install    # First-time: downloads Datomic Pro
```

#### Option 3: Manual (fallback)
```bash
lib/com/datomic/datomic-pro/1.0.7482/bin/transactor \
  lib/com/datomic/datomic-pro/1.0.7482/config/working-transactor.properties &
sleep 5
PORT=8890 lein run
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
