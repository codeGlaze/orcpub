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

## Git Status

**Branch**: `integrate/themes-nordic`
**Status**: Clean working directory (all previous changes committed and routed)

### Committed Work (Sessions 1-3)
- Source code fixes → `feature/themes-nordic` (ready for PR when complete)
- Documentation → `agents/develop`
- E2E artifacts gitignore → `testing/develop`

### Current Session (Session 4)
Accessibility audit and color enhancement planning - documentation only so far.

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

---

## Theme Accessibility Audit (Session 4)

### Contrast Ratio Analysis

| Theme | Element | Foreground | Background | Ratio | WCAG |
|-------|---------|------------|------------|-------|------|
| Light | Text | #363636 | #FFFFFF | ~7:1 | AAA |
| Light | Links | #363636 | #FFFFFF | ~7:1 | ⚠️ Same as text |
| Nord Dark | Text | nord4 | nord0 | ~10:1 | AAA |
| Nord Dark | Links | nord8 | nord0 | ~7:1 | AAA |
| Nord Light | Text | nord0 | nord6 | ~13:1 | AAA |
| Nord Light | Body icons | nord10 | nord6 | ~4.5:1 | AA (borderline) |
| **Nord Light** | **Active icons** | **nord14** | **nord6** | **~3:1** | **FAIL** |
| **Nord Light Elev.** | **Body icons** | **nord15** | **nord6** | **~3.5:1** | **FAIL** |
| All | Header icons | nord6/white | dark bg | ~10:1+ | AAA |

### Issues Identified

1. **nord14 (Aurora Green) Active Icons** - Fails WCAG AA (4.5:1) on light backgrounds
2. **nord15 (Aurora Purple) Body Icons** - Borderline fail for functional UI elements
3. **Washed Out Aesthetic** - Nord is intentionally muted; light themes feel flat
4. **Basic Light Theme** - Links indistinguishable from text, no personality

### Design Philosophy: Bold Over Boring

The Nord palette prioritizes eye comfort over visual excitement. For OrcPub's D&D character builder aesthetic, we want:
- **Bold, distinct colors** that pop for interactive elements
- **Maintained contrast** for accessibility (WCAG AA minimum: 4.5:1)
- **Personality** - this is a fantasy RPG tool, not a minimalist note app

### Proposed "OrcPub Bold" Color Variants

These maintain Nord's hue/saturation family but are deepened for contrast:

| Nord Original | Hex | Issue | OrcPub Bold | New Hex | New Contrast |
|---------------|-----|-------|-------------|---------|--------------|
| nord10 (frost blue) | #5E81AC | Borderline (~4.5:1) | Deep Frost | #3D6A99 | ~6:1 |
| nord14 (green) | #A3BE8C | Fails (~3:1) | Aurora Verdant | #6B9352 | ~5:1 |
| nord15 (purple) | #B48EAD | Fails (~3.5:1) | Aurora Amethyst | #8B5C84 | ~5:1 |
| nord13 (yellow) | #EBCB8B | Mid-tone | Aurora Amber | #D4A84A | ~4.5:1 on dark |
| nord11 (red) | #BF616A | Good | Keep | - | - |
| nord12 (orange) | #D08770 | Mid-tone | Aurora Ember | #C06A50 | ~5:1 |

### Implementation Strategy

**Phase 1: Fix Accessibility Failures**
1. Replace nord14 with #6B9352 (Aurora Verdant) for active icons on light themes
2. Replace nord15 with #8B5C84 (Aurora Amethyst) for elevated light theme body icons
3. Update nord10 to #3D6A99 (Deep Frost) for standard light theme body icons

**Phase 2: Add Personality**
1. Use app orange (#f0a100) or Aurora Amber for call-to-action buttons
2. Add hover states with brightness/saturation boost
3. Consider gradient accents using Frost + Aurora combinations

**Phase 3: Theme-Specific Polish**
1. Basic light theme: needs complete personality overhaul
2. Nord light: frost blues work, fix greens/purples
3. Nord light elevated: most sophisticated, tweak for boldness

### Color Context Rules

**On Dark Backgrounds (header, dark themes)**
- Use LIGHT colors: nord6, nord4, white
- Aurora colors work well (they were designed for dark backgrounds)
- Mid-tones like nord13/nord12 are acceptable

**On Light Backgrounds (light themes body)**
- Use DARK/SATURATED colors: nord0, deepened Aurora variants
- Raw Aurora colors (nord11-15) are too pale - they need darkening
- Frost colors (nord7-10) need deepening for body use

### CSS Variables for Theming

Current variables (defined in core.clj, overridden per-theme):

| Variable | Scope | Default | Purpose |
|----------|-------|---------|---------|
| `--icon-color` | `.svg-icon-*` | white/dark | Body icon default |
| `--icon-active-color` | `.svg-icon-*` | same | Active/selected icon |
| `--header-icon-color` | `.app-header` | white | Header icon/text |
| `--header-active-bg` | `.header-tab` | frost cyan | Active tab background |

**Proposed additions:**
| Variable | Scope | Purpose |
|----------|-------|---------|
| `--accent-primary` | `.app` | Main accent (buttons, links) |
| `--accent-success` | `.app` | Success states (green variant) |
| `--accent-warning` | `.app` | Warning states (amber variant) |
| `--text-muted` | `.app` | Secondary text |

---

## Icon System Decoupling Fix (Session 5)

### Problem
Icons were coupled to text color via `.main-text-color` class, causing issues:
1. White icons on white background in Ability Scores preview
2. Icons looked black instead of intended theme colors
3. Changing icon colors in themes changed ALL text on page

### Root Cause
The icon wrapper had BOTH classes: `div.main-text-color.svg-icon-wrapper.svg-icon-light`
- Themes set `.main-text-color { color: #363636 }` for text
- `.svg-icon-light` tries to set `color: var(--icon-color)` for icons
- Same specificity = last one wins = text color applied to icons

### Solution
**Remove `.main-text-color` from icon wrapper** - icons should ONLY use `.svg-icon-light`/`.svg-icon-dark`.

```clojure
;; Before (views.cljs)
[:div.main-text-color.svg-icon-wrapper ...]

;; After
[:div.svg-icon-wrapper ...]
```

Changed in both:
- `src/cljs/orcpub/dnd/e5/views.cljs` (CLJS component)
- `src/cljc/orcpub/dnd/e5/views_2.cljc` (CLJC server-rendered component)

Also updated `core.clj` header rules from `.svg-icon-wrapper.main-text-color` to just `.svg-icon-wrapper`.

### Additional Fixes
- **Theme selector overflow**: Changed `flex-shrink: 0` to `min-width: 0` to allow shrinking
- **Page wobble**: Added `.app { overflow-x: hidden }` and content padding
- **Nord Light+ sticky header**: Changed from semi-transparent gradient to solid opaque `nord6`

---

## Sunset Beach Theme (Session 5)

### Motivation
Nord themes felt "one note" - cool blue-gray palette with minor variations between 4 themes. User wanted something warm, inviting, readable - "like sunset on a beach."

### Design Philosophy: Warmth Over Cool
Instead of the muted, eye-comfort-focused Nord palette, Sunset Beach uses:
- **Warm earth tones** instead of cool blue-grays
- **High contrast** for excellent readability
- **Complementary accents** (ocean teal) for interactive elements
- **Personality** - feels inviting and distinct

### Sunset Beach Palette

| Role | Name | Hex | Purpose |
|------|------|-----|---------|
| Background | Sand Light | `#FDF6E8` | Warm cream, main bg |
| Background | Sand | `#F5E6D3` | Beach tan, secondary bg |
| Borders | Sand Dark | `#E8D5BE` | Wet sand, dividers |
| Text | Driftwood | `#5D4E3C` | Primary text (~10:1 contrast) |
| Text | Driftwood Dark | `#3D3229` | Headers, strong emphasis |
| Accent | Coral | `#E07A5F` | Primary accent, buttons |
| Accent | Amber | `#E9B44C` | Golden highlights |
| Accent | Rose | `#C17C74` | Secondary accent |
| Links | Teal | `#2A7C6F` | Ocean teal (~5:1 contrast) |
| Links | Teal Dark | `#1D5B52` | Hover states |

### Color Context
- **Gradient background**: Sand Light → Sand (warm, not flat)
- **Text color**: Driftwood brown provides excellent readability
- **Links/Active**: Ocean teal as complementary contrast to warm tones
- **Primary action**: Coral for buttons (warm, inviting)
- **Header icons**: Sand Light (warm cream on dark header)

### Theme Cycle (8 themes)
Dark → Light → Light+ → **Sunset** → Nord → Nord Light → Nord+ → Nord Light+

### Key Lesson: Variety Over Similarity
Having multiple themes from the same palette (4 Nord variants) provides less user value than having distinct themes with different personalities. Better to have:
- 1-2 cool themes (Nord)
- 1-2 warm themes (Sunset)
- 1-2 high-contrast accessibility themes

Than 6 variations of the same color family.

---

## Current Theme Inventory

| Theme | Palette | Character | Best For |
|-------|---------|-----------|----------|
| `dark-theme` | Original | Dark default | Default |
| `light-theme` | Basic | Plain, minimal | Light mode baseline |
| `light-plus-theme` | Bold | Blue accents | Light mode with personality |
| `sunset-theme` | Sunset Beach | Warm, inviting | Cozy reading experience |
| `nord-theme` | Nord | Cool, muted dark | Eye comfort (dark) |
| `nord-light-theme` | Nord | Cool, muted light | Eye comfort (light) |
| `nord-theme-elevated` | Nord | Dark + shadows | Modern dark |
| `nord-light-theme-elevated` | Nord | Light + shadows | Modern light |

---

## Git Status (Session 5)

**Branch**: `integrate/themes-nordic`
**Status**: Changes pending commit

### Files Modified
- `src/clj/orcpub/styles/colors.clj` - Added Sunset Beach palette
- `src/clj/orcpub/styles/core.clj` - Icon decoupling, overflow fix, content padding
- `src/clj/orcpub/styles/themes.clj` - Added sunset-theme
- `src/cljc/orcpub/dnd/e5/views_2.cljc` - Removed main-text-color from icons
- `src/cljs/orcpub/character_builder.cljs` - Theme display name, toggle fix
- `src/cljs/orcpub/dnd/e5/db.cljs` - Theme spec
- `src/cljs/orcpub/dnd/e5/events.cljs` - Theme cycle
- `src/cljs/orcpub/dnd/e5/views.cljs` - Icon decoupling, light-theme? detection

---

## Theme Refinement & Visual Polish (Session 6)

### User Feedback: "Early 00s MySpace"
Initial bold theme implementations were too garish - heavy solid colors, hard edges, flat buttons. User wanted:
- **Subtle gradients** instead of flat colors
- **Restrained accents** - not every element needs to pop
- **Visual depth** through shadows and transparency
- **Texture** to break up heavy solid backgrounds

### Themes Expanded to 11

| Theme | Type | Character |
|-------|------|-----------|
| `dark-theme` | Dark | Default dark, calm for night use |
| `nord-theme` | Dark | Calm Nordic palette |
| `midnight-theme` | Dark | Deep blue twilight |
| `forest-theme` | Dark | Woodland green with texture |
| `slate-theme` | Dark | Professional gray, purple/cyan accents |
| `crimson-theme` | Dark | Rich burgundy and gold |
| `light-theme` | Light | Basic light mode |
| `light-plus-theme` | Light | Enhanced contrast, blue accents |
| `sunset-theme` | Light | Warm beach colors |
| `arctic-aurora-theme` | Light | Teal/cyan aurora aesthetic |
| `parchment-theme` | Light | Warm paper/parchment feel |

### Button Gradient Bug Fix

**Problem**: Buttons showed flat colors instead of gradients
**Cause**: Using `background` shorthand then `background-image: none` later

```clojure
;; WRONG - cancels the gradient!
{:background "linear-gradient(...)"}
;; Later in cascade or pseudo:
{:background-image :none}  ; ← This wipes out the gradient!

;; RIGHT - use background-image directly
{:background-image "linear-gradient(135deg, #color1 0%, #color2 100%)"}
;; No background-image: none anywhere
```

### Theme Toggle Visibility Fix

**Problem**: Toggle borders invisible on light themes (rgba white borders on light bg)
**Solution**: Use `currentColor` which inherits from text color

```clojure
[:.theme-toggle
 {:padding "4px 10px"
  :border-radius "4px"
  :border "1px solid currentColor"  ; ← Adapts to any theme
  :opacity 0.6
  :transition "all 0.15s ease"}
 [:&:hover
  {:opacity 1
   :background "rgba(128, 128, 128, 0.12)"}]]
```

### SVG Pattern Texture for Forest Theme

**Problem**: Forest theme's dark green background felt "heavy" and bland
**Solution**: Layer an SVG dot pattern with the gradient using data URI

```clojure
{:background-image "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='40' height='40' viewBox='0 0 40 40'%3E%3Cg fill='%235a8a5a' fill-opacity='0.4'%3E%3Ccircle cx='5' cy='5' r='3'/%3E%3Ccircle cx='25' cy='25' r='3'/%3E%3Ccircle cx='35' cy='10' r='2'/%3E%3Ccircle cx='10' cy='30' r='2'/%3E%3C/g%3E%3C/svg%3E\"), linear-gradient(180deg, #2D2810 0%, #1F2810 15%, #182418 35%, #142014 60%, #0F1F0F 100%)"
 :background-attachment "fixed"}
```

Key points:
- SVG pattern is FIRST in the `background-image` list (renders on top)
- Gradient is SECOND (renders behind pattern)
- `background-attachment: fixed` keeps pattern stable during scroll
- Use `fill-opacity` in SVG for subtle texture

### Playwright Theme Screenshot Testing

**What DOESN'T Work**:
- Setting localStorage before navigation (theme loads from re-frame db, not localStorage)
- Using EDN format in localStorage (it's a webpage, everything is JavaScript)

**What DOES Work**:
1. Navigate to `/pages/dnd/5e/character-builder`
2. Click the "Theme:" text to cycle through themes
3. Take screenshot after each click

```typescript
// Navigate once, then click to cycle
await page.goto('/pages/dnd/5e/character-builder');
await waitForAppReady(page);

for (let i = 0; i < THEMES.length; i++) {
  await page.waitForTimeout(500);
  await page.screenshot({ path: `screenshots/${theme}.png` });
  if (i < THEMES.length - 1) {
    await page.getByText('Theme:').click();  // Cycles to next theme
  }
}
```

**Disabling re-frame-10x for Clean Screenshots**:
```bash
# Build JS without 10x panel
lein with-profile dev-clean cljsbuild once dev

# Start server (serves pre-compiled JS)
PORT=8890 lein run

# Run tests
cd e2e && npm test -- --grep "Theme Screenshots"
```

### Theme Design Principles Refined

1. **Subtle gradients** - Multi-stop gradients with soft transitions, not flat colors
2. **Restrained accents** - Accent colors for interactive elements only, not backgrounds
3. **Transparency** - Use rgba() for borders and overlays instead of solid colors
4. **Hover = brightness** - Show interactivity through brightness/glow, not color change
5. **Texture for dark themes** - SVG patterns prevent "wall of color" feeling
6. **Consistent transitions** - `transition: all 0.2s ease` for smooth state changes

### Files Modified (Session 6)

| File | Change |
|------|--------|
| `src/clj/orcpub/styles/themes.clj` | Added 6 new themes, refined existing |
| `src/clj/orcpub/styles/colors.clj` | New palettes (forest, slate, crimson, arctic, parchment) |
| `src/clj/orcpub/styles/core.clj` | Theme toggle styling with currentColor |
| `src/cljs/orcpub/dnd/e5/events.cljs` | Extended theme cycle to 11 themes |
| `src/cljs/orcpub/dnd/e5/db.cljs` | Theme spec updated |
| `e2e/scenarios/theme-screenshots.spec.ts` | New test file |
| `e2e/README.md` | Theme testing documentation |
| `CLAUDE.md` | Comprehensive theming documentation |
| `project.clj` | Added `dev-clean` profile |

### New Theme Gotchas

6. **Button gradient fix**: Never use `background-image: none` - it cancels out `background` shorthand gradients. Always use `background-image` directly with the gradient value.

7. **Breaking up heavy solid colors**: Use SVG data URI patterns in `background-image` layered with gradients.

8. **Theme toggle visibility**: Use `currentColor` for borders instead of hardcoded colors so it works on both light and dark themes.

---

## Current Theme Inventory (Updated)

| Theme | Palette | Character | Best For |
|-------|---------|-----------|----------|
| `dark-theme` | Original | Calm dark default | Night use |
| `nord-theme` | Nord | Calm Nordic dark | Eye comfort (dark) |
| `midnight-theme` | Midnight | Deep twilight blue | Atmospheric |
| `forest-theme` | Forest | Woodland green + texture | Nature lovers |
| `slate-theme` | Slate | Modern gray + purple | Professional |
| `crimson-theme` | Crimson | Burgundy + gold | Dramatic fantasy |
| `light-theme` | Basic | Plain light | Baseline light |
| `light-plus-theme` | Enhanced | Blue accents | Modern light |
| `sunset-theme` | Sunset Beach | Warm coral + teal | Cozy reading |
| `arctic-aurora-theme` | Arctic | Teal/cyan aurora | Cool bright |
| `parchment-theme` | Parchment | Warm paper aesthetic | Fantasy document |
