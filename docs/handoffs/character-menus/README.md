# Handoff: Growable Multi-Select Character Menus

## Overview
A redesign of the character-builder's multi-select menus (Other Equipment, Tool
Proficiencies, Saving Throw Advantage, …). These menus are **open-ended**: options
are added over time, sorted alphabetically, and their label lengths are unknown to
the app ahead of time. The redesign keeps an easy multi-select interaction while
making long, ragged, ever-growing lists scannable.

It solves three problems:
1. **Ragged wrapping** → an aligned, auto-fitting column grid (and an alternate
   large-target "pill" layout the user can toggle to).
2. **Long lists** → optional A–Z grouping with a quick-jump letter bar.
3. **Repetitive boilerplate labels** (e.g. every saving-throw option starts with
   "You have advantage on saving throws against being …") → the shared wording is
   detected automatically, shown ONCE as a quoted pattern banner, and each option
   collapses to just its keyword. Crucially, an option that does **not** follow the
   dominant wording is surfaced in full and flagged, so divergent phrasing is never
   silently hidden.

## About the Design Files
The files in `prototype/` are a **design reference created in HTML** — a working
prototype showing the intended look and behavior, **not production code to copy
directly**. `Character Menus.dc.html` runs on a small prototype runtime
(`support.js`); do not port that runtime. The task is to **recreate this design in
the existing codebase** using its established framework, component library, state
management, and styling conventions.

The one piece meant to be reused close to as-is is **`menu-logic.js`** — the three
algorithms (dominant-prefix detection, divergence classification, A–Z bucketing)
are pure, framework-agnostic functions with no UI dependencies. Adapt their style
to the repo, but the logic is the spec.

## Fidelity
**High-fidelity.** Final colors, typography, spacing, and interactions are all
specified below and present in the prototype. Recreate the UI faithfully using the
codebase's existing primitives (its own checkbox/pill/input components, tokens, and
list/grid utilities) rather than reproducing the inline styles verbatim. Where the
repo already has a menu component for these lists, **extend it** rather than adding
a parallel one.

## The three layout modes (user-toggleable)
A segmented control switches all menus between layouts at once; selection persists
across switches.

1. **Aligned grid** (default, recommended) — CSS grid,
   `grid-template-columns: repeat(auto-fill, minmax(150px, 1fr))`, `gap: 6px`,
   `align-items: start`. Each option is a row: a square checkbox + label. Columns
   stay aligned regardless of label length or option count. `minmax` min-width per
   menu: Equipment 150px, Tools 178px, Saves 150px (roughly the longest expected
   item; safe default 150–180px).
2. **Keyword pills** — `display:flex; flex-wrap:wrap; gap:9px`. Each option is a
   rounded toggle pill (the whole pill is the hit target, large/comfortable). Reads
   as "tighter"; offered as an alternative for users who prefer it, not the default.
3. **A–Z groups** — options bucketed by first letter of their DISPLAY text, each
   group under a letter heading, plus a quick-jump letter bar that filters to one
   letter. Best for the long Equipment list.

## Screens / Views

### Menu card (one per menu, repeated)
- **Layout**: vertical stack inside a card.
  - Card: `background:#161c25; border:1px solid rgba(255,255,255,.06);
    border-radius:16px; padding:22px 22px 24px`.
  - **Header row**: space-between. Left = title (+ pattern banner for collapsible
    menus). Right = count label + Clear button.
  - **Pattern banner** (only when a dominant prefix is detected — see below).
  - **Search input**.
  - **Selected-chips tray** (only when ≥1 selected).
  - **A–Z jump bar** (only in A–Z mode).
  - **Option groups** (grid or pills depending on mode).
- **Title**: 18px / weight 600 / `#eef2f7` / letter-spacing -.005em.
- **Count label**: e.g. `7 of 96 selected`, 13px, `#9aa4b2`, tabular-nums.
- **Clear button**: text button, 12.5px, `#aab3c0`, `1px solid rgba(255,255,255,.12)`,
  radius 8px, padding 5×11. Hover: border `rgba(230,162,60,.6)`, text `#e6a23c`.
  Only rendered when something is selected.

### Pattern banner (the "quoted example")
Shown above the search box **only** when `dominantPrefix()` returns a non-empty
string. Communicates "every option is this sentence with one keyword swapped in",
styled unmistakably as a quoted example so non-native speakers grasp it instantly.
- Caption above: `EVERY OPTION READS` — 10.5px, weight 700, letter-spacing .1em,
  uppercase, `#7e8897`, margin-bottom 7px.
- Blockquote: `border-left:3px solid #e6a23c; background:rgba(230,162,60,.06);
  border-radius:10px; padding:13px 16px 13px 40px; font-style:italic; font-size:
  15.5px; color:#e3e8ee; line-height:1.65`.
  - Decorative open-quote glyph “ absolutely positioned top-left, ~30px,
    `Georgia, serif`, `rgba(230,162,60,.5)`, non-italic.
  - Text = the shared prefix (trimmed), followed inline by a **slot chip** naming
    the keyword: `condition` for Saving Throws (configurable per menu; default
    "keyword"). Slot chip: pill, `background:rgba(230,162,60,.18);
    border:1px dashed rgba(230,162,60,.7); color:#f0c071; font-weight:600;
    font-style:normal; font-size:13px; padding:1px 11px; border-radius:999px`.

### Search input
- `width:100%; background:#11161d; border:1px solid rgba(255,255,255,.09);
  border-radius:10px; color:#e7ecf2; font-size:14px; padding:10px 13px 10px 34px`.
- Leading `⌕` glyph absolutely positioned left, `#5d6776`.
- Focus: border `rgba(230,162,60,.55)`. Placeholder `#6b7585`.
- Filters on BOTH collapsed display text and full label (see `filterOptions`).

### Selected-chips tray
Shown only when ≥1 option selected, directly under the search box, with a bottom
divider.
- `UPPERCASE "Chosen"` label, 12px, `#6b7585`, then wrapping chips, `gap:7px`.
- Chip: `background:rgba(230,162,60,.14); border:1px solid rgba(230,162,60,.4);
  color:#f0d6a6; border-radius:999px; padding:5px 8px 5px 11px; font-size:13px;
  white-space:nowrap`, with a trailing `×`. Click removes that selection.
- Chips show the COLLAPSED display text and are sorted alphabetically by it.

### A–Z jump bar (A–Z mode only)
- Wrapping row of letter buttons, `gap:4px`. First button `All`, then only the
  letters actually present in the (unfiltered) option set.
- Button: `min-width:28px; padding:5px 8px; radius:7px; font-size:12.5px;
  weight:600; border:1px solid rgba(255,255,255,.07); color:#8893a2`.
- Active: `background:rgba(230,162,60,.16); color:#e6a23c;
  border:1px solid rgba(230,162,60,.45)`.

### Option — grid cell (grid & A–Z modes)
- Row: `display:flex; align-items:flex-start; gap:10px; padding:8px 10px;
  border-radius:9px; font-size:14px; line-height:1.3; cursor:pointer`.
- Checkbox box: 18×18, `border-radius:5px`, unselected
  `1px solid #5b6576` (dashed for wildcard/"Any N" meta options), selected
  `border:1px solid #e6a23c; background:#e6a23c` with a `#15202e` check glyph.
- Unselected hover: `background:rgba(255,255,255,.05)`.
- Selected row: `background:rgba(230,162,60,.13); border:1px solid
  rgba(230,162,60,.5); color:#f3e7d2`.
- Title attribute = full label (so the official wording is available on hover too).

### Option — pill (pills mode)
- `display:inline-flex; align-items:center; gap:6px; padding:8px 15px;
  border-radius:999px; font-size:14px; line-height:1.15; white-space:nowrap`.
- Unselected: `background:rgba(255,255,255,.025); color:#cdd4de;
  border:1px solid #303a49` (dashed `#3a4452` for meta/wildcard). Hover: border
  `#4a5666`, bg `rgba(255,255,255,.05)`.
- Selected: `background:#e6a23c; color:#15202e; border:1px solid #e6a23c;
  font-weight:600`, leading `✓`.

### Non-standard option treatment (the divergence case)
When a dominant prefix exists but an option does NOT start with it
(`nonStandard === true`):
- **Grid/A–Z**: the cell spans the full row (`grid-column: 1 / -1`), gets a
  `border-left:2px solid #e6a23c` and faint `rgba(230,162,60,.05)` background, and a
  small amber **`≠ NON-STD`** badge (`background:#e6a23c; color:#15202e;
  font-size:10px; weight:700; radius:4px; padding:1px 5px`). The label is shown in
  full, with the shared portion (up to `divergeAt`) muted `#7e8897` and the
  divergent tail highlighted `#e6a23c` weight 600.
- **Pills**: dashed amber border, `color:#f0d6a6`, and a `≠` marker glyph.
- Purpose: a future option with different phrasing is impossible to miss and easy
  to compare against the standard wording — nothing is silently stripped.

## Interactions & Behavior
- **Toggle option**: click anywhere on the cell/pill toggles selection. Selection
  is per-menu.
- **Clear**: empties that menu's selection. Hidden when nothing is selected.
- **Search**: live filter, case-insensitive, matches display text OR full label.
  Empty-state message `No options match "<query>".` when nothing matches.
- **Layout toggle**: segmented control (Aligned grid / Keyword pills / A–Z groups);
  changes all menus; selection persists across switches.
- **A–Z jump**: clicking a letter filters that menu to options whose display text
  starts with it; `All` clears the letter filter.
- **Chip remove**: clicking a chip in the tray deselects that option.
- Transitions are subtle: `transition: all .12s` on interactive elements; no large
  motion.
- **Responsive**: the grid uses `auto-fill minmax(...)`, so column count adapts to
  container width with no breakpoints. Pills and chips wrap naturally.

## State Management
Per menu:
- `selectedIds: Set<string>` (or `{ [id]: true }`) — which options are selected.
- `query: string` — search text.
- `letter: string | null` — active A–Z filter (A–Z mode only).
Global:
- `layout: 'grid' | 'pills' | 'az'` — shared across all menus.

Derived (recompute on render; see `menu-logic.js`):
- `prefix = dominantPrefix(labels)`
- `classified = classifyOptions(labels, prefix)`
- filtered via `filterOptions`, grouped via `groupByLetter` in A–Z mode.

Data: options come from the existing menu data source (the builder's option lists).
IDs can be a slug of the label (`label.toLowerCase().replace(/[^a-z0-9]+/g,'-')`).
**Wildcard/meta options** (e.g. Tools' "Any 1 / Any 2 / Any 3") render with a dashed
box/border and should sort/group separately (shown under a small "Wildcards" heading
in the prototype) — preserve whatever semantics the current builder gives them.

## Design Tokens
Colors:
- Page bg `#11161d`; card bg `#161c25`; input/quote-inset bg `#11161d`.
- Card border `rgba(255,255,255,.06)`; hairline dividers `rgba(255,255,255,.05–.07)`.
- **Accent (amber)** `#e6a23c`; on-amber text `#15202e`; amber tints
  `rgba(230,162,60,.05 / .06 / .13 / .14 / .16 / .18)`; amber-light text `#f0c071`,
  `#f0d6a6`, `#f3e7d2`.
- Text: primary `#eef2f7` / `#e7ecf2`; body `#cdd4de` / `#d7dde6`; secondary
  `#9aa4b2`; muted `#7e8897` / `#6b7585`; faint/border-gray `#5b6576` / `#303a49`.
Radii: cards 16px; inputs/buttons 9–10px; checkbox 5px; small letter btns 7px;
chips/pills 999px. Borders: 1px hairlines; 2–3px amber accent on quote/divergent.
Type: system UI stack; sizes 10.5 (caption) · 12–13 (meta/chips) · 14 (options/
input) · 15.5 (quote) · 18 (menu title) · 30 (page H1). Weights 500/600/650/700.
Spacing: card padding 22px; option padding 8×10; grid gap 6px; pill gap 9px.

## Assets
None — no images or icon files. Glyphs used are plain Unicode: `⌕` (search),
`✓` (check), `×` (remove), `“` (quote), `≠` (non-standard marker). Swap for the
repo's existing icon set if it has equivalents.

## Files
- `prototype/Character Menus.dc.html` — the high-fidelity design reference (open in
  a browser to interact). Built on a prototype runtime; **reference only**.
- `prototype/support.js` — that prototype runtime. **Do not port.**
- `menu-logic.js` — the reusable, framework-agnostic algorithms. **This is the
  spec for the three behaviors** — adapt to the repo's conventions.

## Notes for implementation
- The prototype includes one illustrative non-standard saving-throw option
  ("You can't be Charmed by fey creatures") purely to demonstrate the divergence
  treatment — it is **not real data**; do not seed it into the app.
- `dominantPrefix` thresholds (`minWords: 4`, `ratio: 0.5`) are tunable; they're
  chosen so a menu collapses only when boilerplate is genuinely shared, and a
  single odd entry can't trigger or block collapse.
- Keep the menus sort-agnostic and length-agnostic — no behavior should assume a
  fixed set of options.
