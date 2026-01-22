# Session Summary: Theme Integration & Styles Refactoring

**Date**: 2026-01-22
**Branch**: integrate/themes-nordic
**Status**: Implementation complete - awaiting commit

## What Was Done

1. Fixed bracket syntax errors in theme definitions (5 themes affected)
2. Implemented CSS variable `--header-icon-color` for theme-aware header icons
3. Fixed landing page icons not showing (regression from `.svg-icon` visibility)
4. Created pure `svg-icon` component in CLJC for server-rendered pages
5. Refactored monolithic `core.clj` (2093 lines) into modular files:
   - `colors.clj` (39 lines) - Color palette definitions
   - `themes.clj` (739 lines) - All 5 theme definitions
   - `core.clj` (1390 lines) - Base styles
6. Updated CLAUDE.md with style architecture documentation

## Key Technical Decisions

### Header Icon Colors
The header background (`/image/header-background.jpg` with black fallback) stays **dark across ALL themes**. This means:
- Header icons must always be light-colored for visibility
- Nord-light themes were incorrectly using `nord0` (dark) - fixed to `nord6` (bright)
- CSS variable approach allows future flexibility if header background changes

```clojure
;; Base rule
[:.app-header
 {:--header-icon-color :white}
 [:.svg-icon-wrapper
  {:color "var(--header-icon-color, white)"}]]

;; Theme override
[:.app.nord-theme
 [:.app-header
  {:--header-icon-color colors/nord6}]]
```

### SVG Icon System
Icons use CSS mask technique for theme-aware coloring:

| Context | File | Re-frame? | Theme Source |
|---------|------|-----------|--------------|
| CLJS | `views.cljs` | Yes | `@(subscribe [:theme])` |
| CLJC (splash) | `views_2.cljc` | No | Passed as parameter |

```clojure
;; CLJS (subscription-based)
(svg-icon "bookshelf" 32)           ; uses theme subscription
(svg-icon "bookshelf" 32 "")        ; empty string = use subscription

;; CLJC (pure, no subscriptions)
(svg-icon "bookshelf" 32 "dark-theme") ; theme required
```

### Garden CSS Syntax Gotcha
**Problem**: "Too many arguments to def" error
**Cause**: Each theme def had multiple top-level vectors
```clojure
;; WRONG - multiple top-level vectors
(def light-theme
  [:.rule1 ...]
  [:.rule2 ...]  ; <- second argument to def!
  [:.rule3 ...])

;; CORRECT - wrapped in outer vector
(def light-theme
  [[:.rule1 ...]
   [:.rule2 ...]
   [:.rule3 ...]])
```

## Files Created

| File | Purpose |
|------|---------|
| `src/clj/orcpub/styles/colors.clj` | Nord palette (nord0-15), core app colors |
| `src/clj/orcpub/styles/themes.clj` | 5 theme definitions, exports `all-themes` |

## Files Modified

| File | Change |
|------|--------|
| `src/clj/orcpub/styles/core.clj` | Extracted themes/colors, added `--header-icon-color` |
| `src/cljc/orcpub/dnd/e5/views_2.cljc` | Replaced `svg-icon-2` with proper mask-based `svg-icon` |
| `CLAUDE.md` | Added style architecture, theming docs, gotchas |

## Errors Fixed

### Landing Page Icons Invisible
- **Cause**: `.svg-icon` class has `visibility: hidden` (for mask system)
- **Symptom**: Splash page used `<img class="svg-icon">` which was hidden
- **Fix**: Created proper `svg-icon` component using CSS mask technique
- **User guidance**: "go with the best solutions not dirty fixes"

### Light Theme Header Icons Too Dark
- **Cause**: Nord-light themes set `--header-icon-color` to `nord0` (dark)
- **Reality**: Header stays dark, so icons need to be light
- **Fix**: All themes use `nord6` (#ECEFF4) for header icons

### Broken core.clj During Refactor
- **Cause**: Partial edit left orphaned content
- **Fix**: Careful sequential edits to remove remaining theme code

## Style Architecture

```
src/clj/orcpub/styles/
├── core.clj      # Base styles, layout, utilities (~1390 lines)
├── themes.clj    # Theme definitions (light, nord variants)
└── colors.clj    # Color palettes (Nord, core app colors)
```

Theme integration in `core.clj`:
```clojure
(ns orcpub.styles.core
  (:require [orcpub.styles.colors :as colors]
            [orcpub.styles.themes :as themes]))

(def app
  (concat
    [[:html ...] [:body ...] ...]  ; base styles
    themes/all-themes))            ; all themes concatenated
```

## Available Themes

| Theme | Description |
|-------|-------------|
| `light-theme` | Basic light mode |
| `nord-theme` | Nord dark palette |
| `nord-light-theme` | Nord light palette |
| `nord-theme-elevated` | Nord dark with shadows/depth |
| `nord-light-theme-elevated` | Nord light with modern card design |

## Verification Steps

1. Recompile CSS: `lein garden once`
2. Start the app: `PORT=8890 lein run`
3. Verify:
   - Landing page icons visible (dark background, light icons)
   - Header icons visible across all themes
   - Theme switching works in character builder

## Git Workflow Lessons Learned

This session also involved significant work on the git workflow scripts. Key lessons:

### File Routing Rules
| File Type | Destination | NOT |
|-----------|-------------|-----|
| Root `*.sh` (`pull.sh`, `start.sh`) | `testing/develop` | `develop` |
| `scripts/git/*` | `testing/develop` | `agents/develop` |
| `CLAUDE.md`, `*.md` | `agents/develop` | - |
| Source code (`src/*`) | Feature branch | Direct to `develop` |

### Common Mistakes
1. **Bundling files for different destinations** - Commit separately by destination
2. **Assuming root scripts go to `develop`** - They go to `testing/develop`
3. **Forgetting pre-push hook** - Both `pre-commit` AND `pre-push` need pattern updates

### Hook Update Chicken-and-Egg
When adding new allowed patterns AND files using those patterns:
1. Push hook update first: `git push origin <hook-commit>:<branch>`
2. Then push remaining: `git push origin <branch>`

## Git Status (Current Session - Uncommitted)

**Branch**: `integrate/themes-nordic`

```
M .claude/summaries/2026-01-22-theme-integration.md  # → agents/develop
M CLAUDE.md                                          # → agents/develop
M src/clj/orcpub/styles/core.clj                     # → feature/themes-nordic
M src/clj/orcpub/styles/themes.clj                   # → feature/themes-nordic
M src/cljc/orcpub/dnd/e5/views_2.cljc                # → feature/themes-nordic
M src/cljs/orcpub/dnd/e5/views.cljs                  # → feature/themes-nordic
```

### Routing Guide
Per the dual-branch workflow:
1. **Documentation** (*.md, .claude/*) → `agents/develop`
2. **Source code** (src/*) → `feature/themes-nordic` (clean branch for PR)

### To Commit Properly
```bash
# 1. Commit source code changes
git add src/
git commit -m "Theme fixes: header icons, flyout menus, opacity"

# 2. Route to feature branch
./scripts/git/route-commit.sh HEAD themes-nordic

# 3. Commit documentation separately
git add CLAUDE.md .claude/
git commit -m "docs: Update theme documentation with lessons learned"
# This stays in integrate/ branch or routes to agents/develop
```

## Key Gotchas Documented

1. **`.svg-icon` class has `visibility: hidden`** - for mask system, don't reuse for plain `<img>`
2. **Splash page is server-rendered (CLJC)** - no re-frame, theme must be passed explicitly
3. **Garden CSS syntax** - each theme must be inside a single outer vector
4. **Vendor prefixes in Reagent/React styles** - Use camelCase, not kebab-case:
   - WRONG: `:-webkit-mask-image` (React silently drops this!)
   - RIGHT: `:WebkitMaskImage` (React renders as `-webkit-mask-image`)
   - The CLJC `style` function in `views_2.cljc` converts camelCase back to CSS format for server rendering

---

## Firefox Icon Disappearing Bug Fix (Session 2)

### Problem
SVG icons on the splash/landing page loaded correctly on initial server render, then disappeared when re-frame's debug panel appeared (client hydration). Issue was specific to modern Firefox.

### Root Cause
React/Reagent **silently drops** kebab-case vendor prefixes in style maps:
- Server-rendered HTML: `style="-webkit-mask-image: url(...); mask-image: url(...)"`
- Client-rendered DOM: `style="mask-image: url(...);"` ← webkit missing!

Firefox requires the unprefixed `mask-image`, but the hydration mismatch caused issues.

### Solution
Changed vendor prefix from kebab-case to camelCase in both components:

**In `views.cljs` (CLJS component):**
```clojure
;; Before (WRONG)
:-webkit-mask-image (str "url(" icon-url ")")

;; After (CORRECT)
:WebkitMaskImage (str "url(" icon-url ")")
```

**In `views_2.cljc` (CLJC component):**
Added helper functions to convert camelCase back to CSS format for server rendering:
```clojure
(defn camel->kebab
  "Converts camelCase to kebab-case, with special handling for vendor prefixes.
   WebkitMaskImage -> -webkit-mask-image"
  [s]
  (-> s
      (s/replace #"([A-Z])" "-$1")
      s/lower-case
      (s/replace #"^-" "")))

(defn css-property-name
  "Converts a ClojureScript style keyword to CSS property name.
   Handles both kebab-case (:mask-image) and camelCase (:WebkitMaskImage)."
  [k]
  (let [n (name k)]
    (if (re-find #"[A-Z]" n)
      (let [kebab (camel->kebab n)]
        (if (re-find #"^(webkit|moz|ms|o)-" kebab)
          (str "-" kebab)  ; Add leading hyphen for vendor prefix
          kebab))
      n)))
```

### Why This Works
- **CLJS**: React expects camelCase (`WebkitMaskImage`) and outputs `-webkit-mask-image`
- **CLJ (server)**: Custom `style` function converts `:WebkitMaskImage` → `-webkit-mask-image`
- Both server and client now produce identical HTML

### Testing
Playwright E2E tests verified fix works in both Chromium and Firefox:
- Icons visible on page load (14/14 detected)
- Icons remain visible after re-frame hydration
- Server-rendered HTML includes both `-webkit-mask-image` and `mask-image`

---

## Header Element Theming Fixes (Session 3)

### Problem
Three issues with header elements when switching themes:
1. **Header icons flipping to dark** - SVG icons in header inherited dark color from theme's `.main-text-color` override
2. **Logo inverting on dark themes** - Dark themes applied `filter: invert(1) brightness(2.5)` to the logo
3. **Header text not matching icon color** - Tab labels didn't use the same `--header-icon-color` variable

### Root Cause: CSS Specificity
The header background stays **dark across ALL themes**, but theme rules were overriding header element colors:
- Theme rules: `.app.nord-theme .main-text-color` = 3 classes (wins due to specificity + order)
- Header rules: `.app-header .svg-icon-wrapper` = 2 classes (loses)

### Solution

**1. Use `!important` for header overrides** (in [core.clj:1055-1065](src/clj/orcpub/styles/core.clj#L1055-L1065)):
```clojure
[:.app
 [:.app-header
  ;; Icons: Force light color regardless of theme's .main-text-color
  [:.svg-icon-wrapper.main-text-color
   {:color "var(--header-icon-color, white) !important"}]
  ;; Logo: Never filter - designed for dark backgrounds
  [:img.h-60
   {:filter "none !important"}]]]
```

**2. Remove conflicting theme rules** from [themes.clj](src/clj/orcpub/styles/themes.clj):
- Deleted `[:img.h-60 {:filter "invert(1) brightness(2.5)"}]` from dark themes
- Deleted `[:img.h-60 {:filter "none"}]` from light themes (now redundant)

**3. Add text color to header tabs** (in [core.clj:1067-1078](src/clj/orcpub/styles/core.clj#L1067-L1078)):
```clojure
[:.header-tab
 {:color "var(--header-icon-color, white)"
  :--header-active-bg "rgba(136, 192, 208, 0.6)"}  ; nord8 frost cyan
 [:&.active
  {:background-color "var(--header-active-bg)"}]]
```

**4. Update active tab to use CSS class** (in [views.cljs](src/cljs/orcpub/dnd/e5/views.cljs)):
- Changed from inline `:style active-style` to `:class-name "active"`
- Allows themes to customize active background via CSS variable

### Key Lesson: When to Use `!important`
Using `!important` is appropriate when:
- A property should **never** be overridden by any theme
- Specificity wars would require increasingly complex selectors
- The rule represents a fundamental constraint (dark header = light content)

### CSS Variables for Header Theming
| Variable | Default | Purpose |
|----------|---------|---------|
| `--header-icon-color` | `white` | Icon and text color in header |
| `--header-active-bg` | `rgba(136, 192, 208, 0.6)` | Active tab background |

Themes can override these in their `.app-header` rules.

---

## Aurora Colors in Light Themes (Session 3 continued)

### Problem
Light themes had plain dark icons (#191919) which felt flat and lacked personality compared to the colorful Nord palette.

### Solution
Added CSS variables `--icon-color` and `--icon-active-color` to the icon system, allowing themes to customize icon colors.

**Base icon classes** (in [core.clj](src/clj/orcpub/styles/core.clj)):
```clojure
[:.svg-icon-dark
 {:--icon-color :white
  :--icon-active-color :white
  :color "var(--icon-color, white)"}]

[:.svg-icon-light
 {:--icon-color "#191919"
  :--icon-active-color "#191919"
  :color "var(--icon-color, #191919)"}]
```

**Light theme overrides** (in [themes.clj](src/clj/orcpub/styles/themes.clj)):

| Theme | Body Icons | Active Icon | Header Icons | Header Active |
|-------|------------|-------------|--------------|---------------|
| `nord-light-theme` | nord10 (frost blue) | nord14 (aurora green) | nord6 (snow white) | nord14 (aurora green) |
| `nord-light-theme-elevated` | nord15 (aurora purple) | nord14 (aurora green) | nord6 (snow white) | nord14 (aurora green) |

### Aurora Color Usage
- **nord10 (frost blue)** - Default body icons for standard light theme
- **nord14 (green)** - Universal active/selected state across light themes
- **nord15 (purple)** - Default body icons for elevated light theme (adds personality)

**Important**: Header icons must ALWAYS be light (nord6 or white) because the header background is dark across all themes. Mid-tone Aurora colors (nord12 orange, nord13 yellow) don't have enough contrast on dark backgrounds.

### CSS Variables Summary
| Variable | Scope | Purpose |
|----------|-------|---------|
| `--icon-color` | `.svg-icon-*` | Default icon color |
| `--icon-active-color` | `.svg-icon-*` | Selected/active icon color |
| `--header-icon-color` | `.app-header` | Header icon and text color |
| `--header-active-bg` | `.header-tab` | Active tab background |

---

## Light Theme Readability Fixes (Session 3 continued)

### Problems Identified
1. **Header icons unreadable** - Mid-tone Aurora colors (nord13 yellow, nord12 orange) don't have enough contrast on dark header
2. **Selected tab icons not visually distinct** - `--icon-active-color` was set but never applied
3. **Non-selected tabs too faint** - `opacity-6` (60%) made icons hard to see
4. **Flyout menus unreadable** - Light background with light text (inherited from header)

### Solutions

**1. Header icons must be LIGHT** (nord6 snow white):
```clojure
[:.app-header
 {:--header-icon-color colors/nord6}]  ; NOT aurora mid-tones!
```

**2. Active tab icon color** - Added CSS rule in [core.clj](src/clj/orcpub/styles/core.clj):
```clojure
[:&.active
 {:background-color "var(--header-active-bg)"}
 [:.svg-icon-wrapper
  {:color "var(--icon-active-color, var(--header-icon-color, white))"}]]
```

**3. Increased non-active opacity** - Changed from `opacity-6` to `opacity-8` in [views.cljs](src/cljs/orcpub/dnd/e5/views.cljs):
```clojure
;; Before: "opacity-6 hover-opacity-full"
;; After:  "opacity-8 hover-opacity-full"
```
Also added `.opacity-8` class to core.clj.

**4. Flyout menu text color** - Added dark text to light theme `.shadow` rules:
```clojure
[:.shadow
 {:background-color "rgba(236, 239, 244, 0.98) !important"
  :color colors/nord0}]  ; Dark text on light background
```

### Key Lesson: Color Contrast on Dark Backgrounds
When element backgrounds are dark (like the header), text/icons must be LIGHT:
- ✅ nord6 (#ECEFF4) - bright snow white
- ✅ white
- ❌ nord13 (#EBCB8B) - aurora yellow (mid-tone, poor contrast)
- ❌ nord12 (#D08770) - aurora orange (mid-tone, poor contrast)

Aurora colors work great for body content on light backgrounds, but NOT for elements on dark backgrounds.

## Color Reference

### Nord Palette
```clojure
;; Polar Night (dark backgrounds)
nord0 "#2E3440"  nord1 "#3B4252"  nord2 "#434C5E"  nord3 "#4C566A"

;; Snow Storm (light foregrounds)
nord4 "#D8DEE9"  nord5 "#E5E9F0"  nord6 "#ECEFF4"

;; Frost (bluish accents)
nord7 "#8FBCBB"  nord8 "#88C0D0"  nord9 "#81A1C1"  nord10 "#5E81AC"

;; Aurora (colorful accents)
nord11 "#BF616A"  nord12 "#D08770"  nord13 "#EBCB8B"  nord14 "#A3BE8C"  nord15 "#B48EAD"
```

### Core App Colors
```clojure
orange "#f0a100"  ; primary accent, button color
red    "#9a031e"  ; errors, danger
green  "#70a800"  ; success
```
