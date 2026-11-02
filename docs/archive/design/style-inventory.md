# OrcPub Style Inventory

**As of 2026-09-29**

## Summary

- **Distinct colours**: 38 (dark theme, light theme, accents, portrait drawer)
- **Font families**: 3 (Open Sans primary, Vollkorn for portrait credit, ui-monospace for code)
- **Font sizes**: 15 (10px to 48px)
- **Spacing values**: 35+ (1px to 80px margins/padding)
- **Top 3 contradictions**: 
  1. #f0a100 vs #f1a20f (orange button gradient; near-identical variants)
  2. Two different red definitions: #9a031e (light theme) and #ff6b6b (dark theme default)
  3. 21px margin-top is an isolated odd value in an otherwise 5/10/20/30/40 sequence

---

## 1. Colours

### Dark Theme (Default)

| Value | Name in Code | Where | Count | Usage |
|-------|--------------|-------|-------|-------|
| #f0a100 | `orange` / `button-color` | core.clj:8,9,241,548; portrait.cljs:311,330,338,441,451,485 | 13+ | Primary accent: buttons, borders, text highlights, UI focus states |
| #f1a20f | (unnamed) | core.clj:1330,1343 | 2 | Button gradient lighter shade (builder tabs) |
| #dbab50 | (unnamed) | core.cljs:1428,1475 | 2 | Button gradient darker shade, text in portrait drawer |
| #ff6b6b | `red-on-dark` | core.clj:14,359,718 | 3+ | Error/alert text in dark theme, conflict modal rail |
| #9a031e | `red` | core.clj:10,678 | 2 | Alert backgrounds; overridden in light theme |
| #ffcc5e | (unnamed) | portrait.cljs:338,416,425,451,469,485,503,527,528,571,572 | 11+ | Focus states, hover highlights in portrait drawer |
| #ffd21a | `warning-yellow` | core.clj:20,717,1592,1596,2000,2001,2005 | 7+ | Library health warnings, export unfilled fields, conflict attention |
| #e5637a | `broken-red` | core.clj:21,601,1601 | 3+ | Severity indicator for broken data (darker red) |
| #f5b942 | `amber-on-dark` | core.clj:15,594 | 2 | Field notice warnings in dark theme |
| #70a800 | `green` | core.clj:17,352,2050 | 3+ | Conflict "keep existing" action, text color |
| #47eaf8 | `cyan` | core.clj:18,1046,2099,2124 | 4+ | Import log, conflict rename option, code blocks |
| #8b7ec8 | `purple` | core.clj:19,2113 | 2 | Conflict skip option |
| #9fb0c3 | `muted-on-dark` | core.clj:16,308,595,2370 | 4+ | Muted text, secondary info ("What's New" subtitle) |
| #ebeef4 | (unnamed) | portrait.cljs:317,382,393,452,519,553 | 6+ | Main text color in portrait drawer |
| #8b95a5 | (unnamed) | portrait.cljs:335,407,575 | 3 | Secondary text in portrait drawer, disabled state |
| #616a7a | (unnamed) | portrait.cljs:382,430,483 | 3 | Label text, muted elements in portrait drawer |
| #131924 | (unnamed) | portrait.cljs:310,354,436,525,548,562 | 6+ | Dark background, portrait picker base |
| #0e131a | (unnamed) | portrait.cljs:326,345,467 | 3 | Darker background sections (portrait drawer header, canvas side) |
| #1a1e28 | (unnamed) | core.clj:1753,2212,2278,2490 | 7+ | Panel/modal background (conflict, export, what's new) |
| #1b2230 | (unnamed) | portrait.cljs:256 | 1 | Empty portrait frame gradient start |
| #141a25 | (unnamed) | portrait.cljs:256 | 1 | Empty portrait frame gradient end |
| #0f141c | (unnamed) | portrait.cljs:374 | 1 | Portrait frame radial gradient end |
| #202939 | (unnamed) | portrait.cljs:374 | 1 | Portrait frame radial gradient middle |
| #1e2635 | (unnamed) | portrait.cljs:492,536,548 | 3 | Portrait panel background, layer panel |
| #171e29 | (unnamed) | portrait.cljs:425,473,548 | 3+ | Portrait color strip background, picker background |
| #2c3445 | (unnamed) | core.clj:1710,2212,2291 | 3+ | Dropdown/filter background, sticky header stuck state, what's new header |
| #313A4D | (unnamed) | core.clj:1215,1054 | 3 | App header background, app gradient base |
| #080A0D | (unnamed) | core.clj:2427,index.clj:172,174 | 2+ | Base body/app background, export busy page background |
| #191919 | (unnamed) | core.clj:344 | 1 | Black text class (`.black`) |
| #15202e | (unnamed) | portrait.cljs:401 | 1 | Portrait button text color (primary) |
| #b57500 | (unnamed) | portrait.cljs:402 | 1 | Portrait button border color |
| #6fbb5a | (unnamed) | portrait.cljs:504 | 1 | Green in custom color preset gradient |
| #78d0d4 | (unnamed) | portrait.cljs:504 | 1 | Cyan in custom color preset gradient |
| #c85c5c | (unnamed) | portrait.cljs:504 | 1 | Red in custom color preset gradient |
| #7a94b8 | (unnamed) | portrait.cljs:504 | 1 | Blue in custom color preset gradient |
| #33658A | (unnamed) | core.clj:1832,1856,1459 | 3+ | Light theme form button background, library badge (light mode) |

### Light Theme (`.app.light-theme`)

| Value | Name in Code | Where | Count | Usage |
|-------|--------------|-------|-------|-------|
| #9a031e | `red` | core.clj:1784 | 1 | Error text color in light theme (same as dark `red` def) |
| #363636 | (unnamed) | core.clj:1814,1815,1817,1808 | 4+ | Main text, link color in light theme |
| #282828 | (unnamed) | core.clj:1802,1822,1863,1871 | 4+ | Input/form border, option text in light theme |
| white | keyword | core.clj:1801,1870,1874 | 3+ | Option/sticky header background in light |
| #8a5a00 | (unnamed) | core.clj:1791,1858 | 2+ | Warning notice color, library badge text (light mode) |
| #55637a | (unnamed) | core.clj:1792 | 1 | Note notice color in light theme |
| #b47800 | (unnamed) | core.clj:1859 | 1 | Library badge dot color (compat badge, light) |
| #2b567a | (unnamed) | core.clj:1855 | 1 | Library badge text (benign, light) |
| white (rgba) | (unnamed) | core.clj:1779 | 1 | Light theme background gradient |
| #DDDDDD | (unnamed) | core.clj:1779 | 1 | Light theme background gradient end |

### Shared / Borders / Backgrounds

| Value | Name in Code | Where | Count | Usage |
|-------|--------------|-------|-------|-------|
| rgba(255,255,255,0.05) | (unnamed) | core.clj:370; portrait.cljs:263 | 2+ | Subtle white overlay, gradient masks |
| rgba(255,255,255,0.2) | (unnamed) | core.clj:460 | 1 | Transparent border color (gray) |
| rgba(72,72,72,0.2) | (unnamed) | core.clj:672 | 1 | Light background opacity |
| rgba(0,0,0,0.15) | (unnamed) | core.clj:674 | 1 | Lighter background opacity |
| rgba(255,255,255,0.06) | (unnamed) | portrait.cljs:325,346,359,426,437,493 | 6+ | Subtle borders, backgrounds in portrait drawer |
| rgba(240,161,0,0.16) | (unnamed) | portrait.cljs:311,417,441,441,467 | 5+ | Accent tint backgrounds, hover states |
| rgba(0,0,0,0.55) | (unnamed) | portrait.cljs:303 | 1 | Backdrop opacity for portrait drawer |

### Portrait Drawer Specific (`.lk-*` classes)

| Value | Name in Code | Where | Count | Usage |
|-------|--------------|-------|-------|-------|
| (See drawer-styles) | `.pl-*` classes | portrait.cljs:278+ | 50+ | All portrait drawer styling; uses #f0a100, #0e131a, #ebeef4, #131924, etc. |

---

## 2. Typefaces

| Font Family | Where Loaded | Where Used | Notes |
|-------------|-------------|-----------|-------|
| **Open Sans, sans-serif** | Google Fonts URL in index.clj:265 | core.clj:64 (def font-family); used in .sans class; tooltip, form elements, portrait drawer | Primary typeface for entire app. Loaded from `https://fonts.googleapis.com/css?family=Open+Sans` |
| **Vollkorn** (italic) | drawer-styles in portrait.cljs:293-299 | Portrait credit text overlay (canvas rendering) | Subset WOFF2 + TTF fallback; used only for portrait export captions; loaded on-demand for PDF export |
| **ui-monospace, Menlo, monospace** | Inline in drawer-styles | portrait.cljs:416,560 (code blocks, seed values) | Used for monospace text in portrait drawer (seed row code display) |

---

## 3. Type Sizes and Weights

### Font Sizes

| Size | Utility Class | Count | Usage |
|------|--------------|-------|-------|
| 10px | `.f-s-10` | 1 | Option help circle |
| 11px | `.f-s-11` | 1 | Option group title, portrait labels |
| 12px | `.f-s-12` | 1 | Form button, export edit label, portrait drawer text |
| 14px | `.f-s-14` | 1+ | Form field label, filter dropdown, portrait text |
| 16px | `.f-s-16` | 1 | Display section text |
| 18px | `.f-s-18` | 1+ | Selection stepper title, portrait drawer title |
| 20px | `.f-s-20` | 1 | Page headings |
| 24px | `.f-s-24` | 1 | What's New panel title |
| 28px | `.f-s-28` | 1 | (unused in included excerpts) |
| 32px | `.f-s-32` | 1 | Large headings |
| 36px | `.f-s-36` | 1 | Major section headings |
| 48px | `.f-s-48` | 1 | Hero/splash text |
| 13px | (unnamed) | 1 | Export bug toggle |
| 15px | (unnamed) | 1 | Message title |

### Font Weights

| Weight | Utility Class | Where | Count | Notes |
|--------|--------------|-------|-------|-------|
| bold | `.f-w-bold` or `:bold` | core.clj:108,126,1421,1465 | 4+ | Headings, button text, labels |
| 600 | `.f-w-600` | core.clj:128,1317,1442,1465,1502,1531 | 6+ | Bold variant for buttons, headers |
| 700 | (inline value) | core.clj:545,1523,1594,1727 | 4+ | Extra bold for badges, labels |
| normal | `.f-w-n` | core.clj:123 | 1 | Body text default |
| 500 | (inline value) | portrait.cljs:412,519,560 | 3+ | Medium weight in portrait drawer |
| 400 | (inline value) | portrait.cljs:297,500 | 2+ | Normal weight, font-face fallback |

### Text Transform & Spacing

| Property | Values | Count | Usage |
|----------|--------|-------|-------|
| text-transform | uppercase | 6+ | `.uppercase`, option group title, builder tabs, buttons |
| letter-spacing | 0.08em, 0.5px, 1px, 1.2px, 0.04em, 0.06em, 0.14em | 8+ | PDF option titles, portrait drawer labels, uppercase text emphasis |
| line-height | 1, 1.3, 1.4, 1.45, 1.5, 1.6, 19px, 20px | 8+ | Text readability, message boxes |

---

## 4. Spacing (Margins & Padding)

### Margin Values

| Value | Class | Count | Usage |
|-------|-------|-------|-------|
| 2px | `.m-t-2`, `.m-b-2`, `.m-r-2` | 3 | Fine adjustments |
| 5px | `.m-5`, `.m-r-5`, `.m-t-5` (note: `.m-t-5` is in range) | 3+ | Subtle spacing |
| 10px | `.m-t--10`, `.m-b-10`, `.m-r-10`, `.m-l--10` | 4+ | Standard small gap |
| 18px | `.m-r-18` | 1 | Item spacing |
| 19px | `.m-b-19`, `.m-t-21` (see contradiction) | 1+ | Character builder header (odd value) |
| 20px | `.m-t-20`, `.m-b-20`, `.m-r-20`, `.m-l-30` | 4+ | Standard medium gap |
| 21px | `.m-t-21` | 1 | **Contradiction**: Isolated odd value in sequence |
| 30px | `.m-t-30`, `.m-b-30`, `.m-r-30`, `.m-l-30` | 4+ | Larger content spacing |
| 40px | `.m-t-40`, `.m-b-40` | 2 | Large section spacing |
| 80px | `.m-r-80` | 1 | Header buttons |
| Negative values | `.m-t--5`, `.m-t--10`, `.m-t--20`, `.m-b--1`, `.m-b--2` | 5+ | Overlapping elements, visual adjustments |

### Padding Values

| Value | Class | Count | Usage |
|-------|-------|-------|-------|
| 0px | `.p-0` | 1 | Reset padding |
| 1px | `.p-1` | 1 | Minimal padding |
| 2px | `.p-2` | 1 | Subtle padding |
| 3px | (inline) | 2 | Fine adjustments |
| 4px | (inline) | 1+ | Small control padding |
| 5px | `.p-5`, `.p-t-5`, `.p-b-5`, `.p-r-5`, `.p-l-5` | 5+ | Base unit padding |
| 10px | `.p-10`, `.p-t-10`, `.p-b-10`, `.p-r-10`, `.p-l-10` | 5+ | Standard padding |
| 15px | `.p-l-15` | 1 | Left padding |
| 20px | `.p-20`, `.p-t-20`, `.p-b-20`, `.p-r-20`, `.p-l-20` | 5+ | Larger padding |
| 30px | `.p-30` | 1 | Large padding |
| 40px | `.p-b-40` | 1 | Bottom padding |
| 5px 10px | `.p-5-10` | 1 | Vertical/horizontal combo |

---

## 5. Radii, Borders, Shadows

### Border Radius

| Value | Class | Count | Usage |
|-------|-------|-------|-------|
| 50% | `.b-rad-50-p` | 1+ | Circular elements (buttons, badges) |
| 5px | `.b-rad-5` | 4+ | Buttons, panels, standard rounding |
| 10px | `.b-rad-10` | 1+ | Larger panels, portrait frame |
| 3px | (inline) | 2+ | Small controls, code blocks |
| 4px | (inline) | 3+ | Input fields, controls |
| 6px | (inline) | 2+ | Panels, modals |
| 8px | (inline) | 3+ | Portrait drawer controls, panels |
| 999px | (inline) | 4+ | Pills, badges, fully rounded |

### Borders

| Value | Class | Count | Usage |
|-------|-------|-------|-------|
| 1px solid | `.b-1` | 1 | Standard border |
| 3px solid | `.b-3` | 1 | Thick border |
| 2px solid | `.b-b-2` | 1 | Bottom border |
| 1px dashed | (inline) | 2+ | Dashed borders in conflict modal |
| Color-specific | `.b-orange`, `.b-red`, `.b-gray` | 3+ | Borders using accent colors |
| rgba(255,255,255,0.2) | (inline) | 3+ | Subtle light borders |

### Box Shadows

| Value | Class/Context | Count | Usage |
|-------|--------------|-------|-------|
| 0 2px 6px rgba(0,0,0,0.5) | `.hover-shadow:hover`, `.form-button:hover` | 4+ | Hover/elevation effect |
| 0 1px 0 0 #f0a100 | `.orange-shadow`, `.checkbox` | 2+ | Accent underline shadow |
| 1px 2px 1px black | `.text-shadow` | 1 | Text shadow effect |
| 1px 2px 1px white | `.white-text-shadow` | 1 | White text shadow |
| 1px 1px 1px rgba(0,0,0,0.8) | `.slight-text-shadow` | 1 | Subtle text shadow |
| 0 4px 12px rgba(0,0,0,0.4) | Dropdown filter menu | 1 | Dropdown shadow |
| 0 8px 24px rgba(0,0,0,0.55) | Inventory picker popover | 1 | Larger shadow |
| 0 14px 34px rgba(0,0,0,0.55) | Conflict modal, combo dropdown | 2+ | Deep shadow for modals |
| 0 0 0 2px rgba(241,162,15,0.35) | `.mc-primary` | 1+ | Accent ring for primary actions |
| inset + external | Portrait controls | 3+ | Combination shadows for depth |

---

## 6. Components

### Buttons (`.form-button`, `.roll-button`, `.link-button`, `.mc-btn`)

**Dark Theme (Default)**
```
.form-button:
  color: white
  font-weight: 600
  font-size: 12px
  border: none
  border-radius: 5px
  padding: 10px 15px
  background: linear-gradient(to bottom, #f1a20f, #dbab50)
  cursor: pointer
```
| Location | Count | Variant |
|----------|-------|---------|
| core.clj:1419-1428 | 1 | Primary (main call-to-action) |
| core.clj:1477 | 1 | :hover shadow |
| core.clj:1480 | 1 | .disabled (opacity 0.5) |

**Light Theme Override**
```
.app.light-theme .form-button:
  background: linear-gradient(to bottom, #33658A, #33658A)
```
| core.clj:1831-1832 | 1 | Solid blue (no gradient) |

### Links (`.orange`, `.a-white`, `.link-button`)

| Class | Dark Theme | Light Theme | File:Line |
|-------|-----------|------------|-----------|
| `.orange` a:visited | color: #f0a100 | color: rgba(0,0,0,0.8) | core.clj:345-348, 1834-1835 |
| `.a-white` | color: white !important | unchanged | core.clj:349-351 |
| `.link-button` | color: #f0a100; text-decoration: underline | color: #363636 | core.clj:1647-1656, 1807-1808 |

### Inputs (`.input`, select, option)

**Dark Theme**
```
.input:
  background-color: transparent
  color: white
  border: 1px solid white
  border-radius: 5px
  padding: 10px
  font-size: 14px

select:
  background-color: transparent
  color: white
  color-scheme: dark

option:
  background-color: #1a1e28
  color: rgba(255,255,255,0.9)
```
| Location | File:Line |
|----------|-----------|
| Input styles | core.clj:1671-1681 |
| Select/option | core.clj:1192-1200 |

**Light Theme Override**
```
.app.light-theme .input:
  color: black
  border: 1px solid #282828

.app.light-theme select:
  color: black
  color-scheme: light

.app.light-theme option:
  background-color: white
  color: #282828
```
| Location | File:Line |
|----------|-----------|
| Input/select/option | core.clj:1819-1802 |

### Cards/Panels (`.builder-option`, `.selection-stepper-main`, `.conflict-modal`)

| Component | Dark Theme | File:Line | Notes |
|-----------|-----------|-----------|-------|
| `.builder-option` | border: 1px solid rgba(255,255,255,0.5); border-radius: 5px; padding: 10px | core.clj:1298-1305 | Light border on transparent |
| `.selection-stepper-main` | background: #1a1e28; border: 1px solid white; border-radius: 5px; box-shadow: 0 2px 6px rgba(0,0,0,0.5) | core.clj:1748-1754 | Dark panel with shadow |
| `.conflict-modal` | background: #1a1e28; border-radius: 5px; max-width: 860px; box-shadow: 0 2px 6px rgba(0,0,0,0.5) | core.clj:1938-1945 | Conflict resolution modal |
| `.whats-new-panel` | background: #1a1e28; color: rgba(255,255,255,0.85); border-radius: 6px; box-shadow: 0 4px 18px rgba(0,0,0,0.6) | core.clj:2278-2287 | What's New overlay panel |

### Header (`.app-header`, `.sticky-header`)

| Element | Style | File:Line |
|---------|-------|-----------|
| `.app-header` | background-color: black; background-image: url(/../../image/header-background.jpg); background-size: cover | core.clj:1224-1229 |
| `.header-tab` | background: rgba(0,0,0,0.5); backdrop-filter: blur(5px); border-radius: 5px | core.clj:1231-1235 |
| `.sticky-header` | position: sticky; top: 0; z-index: 100 | core.clj:1208-1211 |
| `.sticky-header.stuck` | background-color: #313A4D; box-shadow: 0 2px 6px rgba(0,0,0,0.5) | core.clj:1213-1215 |

**Light Theme Override**
```
.app.light-theme .sticky-header.stuck:
  background-color: white
```
| core.clj:1873-1874 | 1 |

### Tabs (`.builder-tab`, `.builder-tabs`, `.selected-builder-tab`)

| Element | Style | File:Line |
|---------|-------|-----------|
| `.builder-tabs` | display: flex; gap: 10px; padding: 10px; text-transform: uppercase; font-weight: 600 | core.clj:1311-1317 |
| `.builder-tab` | border-bottom: 5px solid rgba(72,72,72,0.37); padding-bottom: 13px; text-align: center; cursor: pointer | core.clj:1319-1327 |
| `.selected-builder-tab` | border-bottom-color: #f1a20f | core.clj:1329-1332 |

### Theme Toggle (`.dev-mode-switch`)

| State | Style | File:Line |
|-------|-------|-----------|
| Default | background-color: rgba(255,255,255,0.16); width: 34px; height: 18px; border-radius: 9px; cursor: pointer | core.clj:2194-2203 |
| .on | background-color: #f0a100; `&:after` transforms to right | core.clj:2214-2216 |
| :focus-visible | outline: 2px solid #f0a100; outline-offset: 3px | core.clj:2217-2219 |

### Portrait Drawer (`.pl-*` classes)

See full drawer-styles CSS in portrait.cljs:278-577. Key styles:

| Class | Style | File:Line |
|-------|-------|-----------|
| `.pl-drawer` | position: fixed; width: 600px; background: #131924; border-left: 1px solid rgba(240,161,0,0.16); animation: pl-slide 220ms | portrait.cljs:307-318 |
| `.pl-btn-primary` | background: linear-gradient(to bottom, #f1a20f, #dbab50); color: #15202e; font-weight: 700; box-shadow: 0 6px 14px -8px rgba(240,161,0,0.32) | portrait.cljs:399-404 |
| `.pl-btn-ghost` | border: 1px solid rgba(255,255,255,0.10); color: #8b95a5 | portrait.cljs:407-409 |
| `.pl-slot` | background: #131924; border: 1px solid rgba(255,255,255,0.06); border-radius: 999px | portrait.cljs:434-440 |
| `.pl-slot.on` | border-color: #f0a100; box-shadow: 0 0 0 1px rgba(240,161,0,0.24) | portrait.cljs:441 |
| `.pl-preset.custom` | background: conic-gradient(#f0a100, #78d0d4, #c85c5c, #6fbb5a, #7a94b8, #f0a100) | portrait.cljs:504 |

---

## 7. Themes

### How Theme Switching Works

**Dark Theme (Default)**
- Applied to root: `<div class="app">` or `<div class="app.dark-theme">` (dark is default, no class needed)
- Defined throughout core.clj lines 1-2000+
- All color values use dark tints and high-contrast text

**Light Theme**
- Applied by adding class: `<div class="app light-theme">`
- Overrides defined in core.clj:1778-1879 (`.app.light-theme { ... }`)
- Primary selector: `.app.light-theme` affects background, text colors, form elements, badges

### Properties That Change Between Themes

| Property | Dark Theme | Light Theme | File |
|----------|-----------|-------------|------|
| Background | `linear-gradient(182deg, #313A4D, #080A0D)` | `linear-gradient(182deg, #FFFFFF, #DDDDDD)` | core.clj:1054, 1779 |
| Body text | `.main-text-color: white` | `.main-text-color: #363636` | core.clj:336-338, 1813-1815 |
| Input border | `1px solid white` | `1px solid #282828` | core.clj:1674, 1822 |
| Input text | `color: white` | `color: black` | core.clj:1673, 1821 |
| Select color-scheme | `dark` | `light` | core.clj:1195, 1797 |
| Option background | `#1a1e28` | `white` | core.clj:1199, 1801 |
| Option text | `rgba(255,255,255,0.9)` | `#282828` | core.clj:1200, 1802 |
| Form button gradient | `#f1a20f → #dbab50` | `#33658A → #33658A` (solid blue) | core.clj:1428, 1832 |
| `.red` text | `#ff6b6b` | `#9a031e` | core.clj:359, 1784 |
| `.orange` text | `#f0a100` | `rgba(0,0,0,0.8)` | core.clj:346, 1834 |
| Link button | `#f0a100` | `#363636` | core.clj:1648, 1808 |
| Field notice bg | `rgba(255,255,255,0.06)` | `rgba(0,0,0,0.05)` | core.clj:573, 1787 |
| Warning notice color | `#f5b942` | `#8a5a00` | core.clj:594, 1791 |
| Field remedy border | `rgba(255,255,255,0.12)` | `rgba(0,0,0,0.18)` | core.clj:607, 1789 |
| Sticky header | `#313A4D` | `white` | core.clj:1215, 1874 |
| Item list borders | `rgba(255,255,255,0.5)` | `rgba(0,0,0,0.5)` | core.clj:1246, 1805 |
| Builder dropdown | `transparent bg` | `white bg, #282828 text` | core.clj:1358, 1861-1864 |
| Library badges (benign) | `rgba(110,168,220,0.18)` text `#9ec7ea` | `rgba(51,101,138,0.14)` text `#2b567a` | core.clj:1450, 1854 |
| Library badges (compat) | `rgba(217,165,32,0.20)` text `#e5c169` | `rgba(180,120,0,0.16)` text `#8a5a00` | core.clj:1453, 1857 |
| Striped table rows | `rgba(255,255,255,0.1)` | `rgba(0,0,0,0.1)` | core.clj:1296, 1878 |

---

## 8. Contradictions

### Explicit Contradictions

1. **Orange Button Gradient Variants** (core.clj:1428 vs 1330)
   - `#f1a20f` (lighter) used in button gradient (core.clj:1428, 1475) and selected builder tab (core.clj:1330)
   - `#f0a100` (defined as `button-color`, core.clj:9) is the primary accent
   - Visually near-identical; unclear if intentional or artifact of gradient mathematics

2. **Two Red Definitions with No Documented Fallback** (core.clj:10,14)
   - `red: #9a031e` (dark/saturated) — documented as "one red cannot serve both themes" (line 11-13)
   - `red-on-dark: #ff6b6b` (bright) — used in dark theme by default (line 359)
   - Light theme explicitly overrides to use `#9a031e` (line 1784)
   - This forces the `.red` class to be re-scoped in light-theme block, making dark-theme-only code depend on late override

3. **Margin-Top 21px Isolation** (core.clj:51, 169-170)
   - Defined in range generator: `(concat (range 0 10) [21] (range 10 30 5))`
   - Only odd value in margin sequence (all others are 0,1–9, 10,15,20,25 etc.)
   - Used once in `.character-builder-header` (line 1150) and `.m-t-21` class (line 169-170)
   - No explanation for why 21 instead of 20 or 25

### Minor Inconsistencies

4. **Button Styling Spread Across Variants** (core.clj:1419, 1462, 1647, portrait.cljs:389)
   - `.form-button`: gradient, uppercase, 12px, 600 weight
   - `.roll-button`: identical gradient and layout but separate definition (1462)
   - `.link-button`: similar padding/radius but different color handling (1647)
   - `.pl-btn` (portrait): 600 12px uppercase, matches `.form-button` but defined separately (portrait.cljs:389)
   - No shared mixin or variable; each duplicates the same values

5. **Text Color Opacity Inconsistency**
   - Input text: `#484848` (dark gray, views.cljs:68)
   - Main dark-theme text: `white` (core.clj:32)
   - Muted text: `#9fb0c3`, `#8b95a5` (various opacities)
   - No consistent opacity scale defined; values chosen per-context

6. **Border Radius Variety** (15+ distinct values)
   - Buttons: 5px (core.clj:1424, portrait.cljs:391)
   - Portrait frame: 10px (portrait.cljs:375)
   - Pills/badges: 999px (portrait.cljs:438)
   - Small controls: 3px, 4px, 8px (inline values)
   - No established scale; each component picks a value

7. **Box Shadow Inconsistency**
   - Standard hover shadow: `0 2px 6px 0 rgba(0,0,0,0.5)` (core.clj:232, 1478)
   - Dropdown: `0 4px 12px rgba(0,0,0,0.4)` (core.clj:1721) — different alpha
   - Modal: `0 2px 6px 0 rgba(0,0,0,0.5)` vs deeper `0 14px 34px rgba(0,0,0,0.55)` (core.clj:1945, 2287)
   - Portrait drawer: `0 6px 14px -8px rgba(240,161,0,0.32)` — accent-colored shadow (portrait.cljs:403)

---

## Notes on Sources

- **core.clj**: 2676 lines of Garden CSS; main source for utility classes, theme definitions, component styles
- **index.clj**: Server-rendered HTML head; inline base styles, font loads, meta tags
- **views_2.cljc**: Splash page; limited inline styles for buttons (uses `#f0a100`)
- **portrait.cljs**: Portrait drawer component; ~350 lines of plain CSS strings (drawer-styles + empty-slot-styles); self-contained design language
- **views.cljs**: Not fully read (2,000+ lines); sampled for color constants at top; found 21 hex color references, most duplicating core.clj values

All color names and hex values are exactly as written in source; no inference or normalization applied. Counts reflect grep matches across specified source files only.
