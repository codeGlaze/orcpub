# Theming System

> Theme architecture, SVG icon system, and Garden CSS conventions.
>
> **Not on integration yet.** This describes the theming branch, `claude/add-color-themes-gyRhI`
> (unmerged, last commit 2026-01-22). Integration has two themes, `light-theme` and `dark-theme`,
> toggled in `events.cljs`, and no `themes.clj` or `colors.clj`. The first sections are this page's
> own; "From CLAUDE.md" below is the fuller guidance that sat in CLAUDE.md until 2026-10-23, kept
> only where it adds to them. The theme list above matches the branch's `themes.clj`.

---

## Available Themes

| Theme | Description |
|-------|-------------|
| `light-theme` | Basic light mode |
| `nord-theme` | Nord dark palette |
| `nord-light-theme` | Nord light palette |
| `nord-theme-elevated` | Nord dark with shadows/depth |
| `nord-light-theme-elevated` | Nord light with modern card design |

## Style Architecture

```
src/clj/orcpub/styles/
├── core.clj      # Base styles, layout, utilities (~1400 lines)
├── themes.clj    # Theme definitions (light, nord variants)
└── colors.clj    # Color palettes (Nord, core app colors)
```

- **Adding a theme**: Define in `themes.clj`, add to `all-themes` vector
- **CSS variables**: Used for theme-aware values (e.g., `--header-icon-color`)
- **Themes use concat**: Each theme is a vector of rules, concatenated into `app`

## SVG Icon System

Icons use CSS mask technique for theme-aware coloring:

```clojure
;; In CLJS (with re-frame subscription)
(svg-icon "bookshelf" 32)              ; uses theme subscription
(svg-icon "bookshelf" 32 "")           ; empty string = use subscription
(svg-icon "bookshelf" 32 "nord-theme") ; explicit theme override

;; In CLJC (pure, no subscriptions) - for server-rendered pages
(svg-icon "bookshelf" 32 "dark-theme") ; theme required
```

**Key files:**
- CLJS component: `src/cljs/orcpub/dnd/e5/views.cljs` (line ~222)
- CLJC component: `src/cljc/orcpub/dnd/e5/views_2.cljc` (for splash page)
- CSS styles: `src/clj/orcpub/styles/core.clj` (`.svg-icon-wrapper`)

## Header Icon Colors

The header background stays dark across ALL themes. Header icons use
`--header-icon-color` CSS variable:

- Default: `white`
- Nord themes: `nord6` (#ECEFF4 - bright snow white)

**Important**: Light themes should NOT use dark header icon colors — the header
background doesn't change with theme.

## Gotchas

1. **`.svg-icon` class has `visibility: hidden`** — It's for the mask-based system
   where the img is hidden. Don't reuse this class for plain `<img>` tags.
2. **Splash page is server-rendered (CLJC)** — Uses pure `svg-icon` without
   re-frame. Theme must be passed explicitly.
3. **Garden CSS syntax**: Each theme rule must be inside a single vector. Multiple
   top-level vectors in a `def` causes "Too many arguments to def" error.

## CSS Compilation

Styles are written in Clojure using Garden:

```bash
lein garden once    # Compile once (also runs as prep-task)
lein garden auto    # Watch mode
```

Or via lein profiles:
```bash
lein with-profile +css-watch repl   # Auto-recompile during REPL
```

---

# From CLAUDE.md


The old CLAUDE.md listed 11 themes (`midnight`, `forest`, `crimson`, `sunset`, ...); none of them is
defined on the theming branch, which has the 5 above.

## Theme Toggle
The theme toggle is on the character builder page header. It shows "Theme: \<name\> ▾" and clicking cycles through all themes.

**Key files:**
- Toggle component: `src/cljs/orcpub/character_builder.cljs` (`theme-toggle` fn)
- Theme cycle logic: `src/cljs/orcpub/dnd/e5/events.cljs` (`:cycle-theme` event)
- Theme spec: `src/cljs/orcpub/dnd/e5/db.cljs` (`:theme` spec)
- Toggle styling: `src/clj/orcpub/styles/core.clj` (`.theme-toggle` rule)

**Styling approach:** Uses `currentColor` for borders so it works on both light and dark themes without hardcoded colors.

## SVG Icon System
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

## Icon Color System
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

## Theme Gotchas (beyond the three above)
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

## Playwright Theme Screenshots

> **The old commands here are gone:** there is no `dev-clean` profile (the dev build does not preload
> re-frame-10x, so screenshots have no debug panel anyway) and no `e2e/` directory, on integration or
> on the theming branch. Use `scripts/e2e/run.sh` with a script that clicks the theme toggle.

**What DOESN'T work:**
- Setting localStorage before navigation (theme loads from re-frame db, not localStorage)
- Using EDN format in localStorage (it's a webpage, everything is JavaScript)

**What DOES work:**
- Navigate to `/pages/dnd/5e/character-builder`
- Click the "Theme:" text to cycle through themes
- Take screenshot after each click

