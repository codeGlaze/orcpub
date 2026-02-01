# Theming System

> Theme architecture, SVG icon system, and Garden CSS conventions.

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
