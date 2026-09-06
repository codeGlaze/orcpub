# Growable Option Menus + Builder Redesign — Continuity Record

The full state of `redesign/growable-option-menus` (off `develop`, on `codeGlaze/orcpub`):
goals, architecture, **validated lessons/gotchas**, per-builder status, what's left, and
how to verify. Written so this can be picked up mid-flight without restarting.
Design references (gitignored, in `design_handoff_character_menus/`): `Character Menus.dc.html`,
`OrcPub Background Builder Reference.dc.html`, `OrcPub Race Builder Reference.dc.html`,
`README.md` (tokens), `menu-logic.js` (the grouping spec).

## 2026-09-06 — lifted onto `feat/option-picker`, then cut

`option_menu_views.cljs`, `option_grouping.cljs` and `themes.cljs` were ported onto
`feat/option-picker` and Equipment was wired to `omv/option-menu` with a render cap. That was
reverted for a filtering combobox, and the namespaces were removed once nothing referenced
them — not because the menu is wrong, but because it arrives with theming, layout modes and
page structure, which is a site-wide redesign and was not going out with the Summer Patch.

Nothing is lost. `option_grouping` and `themes` were byte-identical to this branch;
`option_menu_views` diverged by 34 lines (the `:max-rendered` / `::show-all` capping work),
in `feat/option-picker` history at `2a671844` and `ec85c5ac`. Rewiring one selection cost 31
lines of `character_builder.cljs`, and the namespace self-registers its 13 subs and events.
See [equipment-option-picker.md](equipment-option-picker.md).


## Goal

Redesign the character-builder's multi-select menus AND the homebrew builders' whole UI
to match the references: contained section cards, parent/child nesting, a single global
layout toggle (grid/pills/A–Z), per-menu chrome (pattern banner, search, chips/count,
collapse), an elevated header band, dark-inset form controls, and **intrinsic
responsiveness** (no magic widths / no new breakpoints). Recreate the look with the app's
own tokens (`orange` = `#f0a100`), not the prototype's hex.

## Architecture

### Shared component — `src/cljs/orcpub/dnd/e5/option_menu_views.cljs` (alias `omv`)
Pure grouping logic stays in `orcpub.dnd.e5.option-grouping` (`dominant-prefix`, `classify`,
`group-by-letter`, `present-letters`). This ns is view + the menus' re-frame state.

- `option-menu` — the core. Two modes:
  - **panel** (`:title` set): header row = accent tab + title + count pill + Clear +
    chevron; then `:header-extra` (e.g. a "Choose N" dropdown), `:wildcards` ("Choose any"
    dashed group), search (only when `>= search-min` = 10 options), the body, `:trailer`.
  - **legacy** (no `:title`): a plain count line + chips tray (for not-yet-migrated callers).
  - Other opts: `:top-level?` (accent size), `:multiselect?` (gates count/Clear/chips),
    `:collapsible?`, `:on-clear`, `:cell-fn`, `:chip-fn`.
- `section-card` — standalone elevated card around one `option-menu` (collapsible if
  `>= search-min` options).
- `subsection` — a recessed child well around an `option-menu` (collapsible if large too,
  so a big child like Other Equipment tucks away without collapsing its parent).
- `parent-section` — a parent card with an accent heading + nested child wells; whole card
  collapses via `:collapse-id` + `:summary-labels`.
- `card` — generic flat card with an accent-tab heading wrapping arbitrary content
  (non-menu form sections: Size&Speed, Armor Class, Ability Scores).
- `checkbox-options items selected-fn toggle-fn` — items (`{:name :key}`) → normalized
  option vector. `layout-toggle` — the global grid/pills/A–Z control (sliding thumb).
  `summarize-selected` — the "Chosen: a, b, c +N more" / "Nothing selected yet" line.

### Factories — `views.cljs`
`map-prop-menu` (multi-select at `[:props kw (:key item)]`) and `value-choice-menu`
(single-select numeric at `[:props kw]`) — both now render `section-card`s, so every
builder menu using them gets card chrome + size-gated collapse. ~14 renderers collapse
into these. Plus `damage-type-items`, and the `*-wildcards` helpers for the Any-N groups.

### The two render-path families
- **Family A — SRD character creation** (`character_builder.cljs`): `default-selection-
  section-body` → rich `option-selector-base` cards. Per-option data from
  `views_aux/option-selector-data`. Re-toggling a multiselect option deselects it
  (`event_handlers.cljc update-multi-select`). NOT YET carded — the deferred piece.
- **Family B — homebrew builders** (`views.cljs`): the ~37 flex-wrap renderers, now routed
  through `omv` (mostly via the factories). This is where all the redesign work has landed.

### State model
- **Layout** — global + persisted at `[:user-data :menu-layout]` (default `:grid`),
  `::omv/layout`/`::omv/set-layout` (user→local-store interceptor, like `:theme`). One
  toggle re-renders every menu. NOT per-menu.
- **Collapse** — per-section + persisted at `[:user-data :menu-collapsed id]`,
  `::omv/collapsed`/`::omv/toggle-collapsed`. Default expanded; independent (not an
  accordion). Gated by size (`>= search-min`).
- **Search query + A–Z letter** — transient, per-menu, `[::omv/menu-state menu-id …]`.
- **`menu-id` must be stable + unique** — keys the per-menu search/letter/collapse. Convention:
  a vector off the toggle-event keyword (`[:option-skill-proficiency toggle-event]`);
  per-entity menus include the entity key. NEVER gensym.

## Validated lessons / gotchas (do not relearn these)

1. **Garden CSS minify strips spaces inside `url()`** — a `data:image/svg+xml,...` caret
   came out as `<svgxmlns…d='M11l555-5'…` (spaces gone) and rendered nothing. **Encode
   spaces as `%20`** in data URIs. (commit bc026286)
2. **Garden maps with >8 keys are unordered** (Clojure array-map → hash-map at 9+ entries),
   so a `:border :none` shorthand can emit AFTER `:border-bottom` and reset it to 0. **Don't
   mix a shorthand and a longhand of the same property in one rule with 9+ keys** — use
   per-side longhands (`:border-top/-right/-left :none`). (commit f14c1f5c)
3. **The homebrew builders are ALREADY width-capped at 1440** via content-page's `.content`
   (measured identical to the character builder: 1440px wide, left:180 centered at an 1800
   viewport). Don't add redundant `.container`/`.content` wrappers — it's a no-op. The 1440
   `content-style` max-width is the shared outer guardrail; leave it.
4. **Intrinsic responsiveness, not width-tuning.** No layout rule tied to a magic width;
   each section declares its own behavior; the page just stacks. Tools: grid
   `repeat(auto-fill, minmax(min(100%, X), 1fr))` (the `min(100%, …)` guard prevents
   overflow below X on small phones), flex `1 1 <comfortable-min>` + `max-width <cap>`
   (fills mid widths, caps wide, stacks narrow), `flex-wrap` everywhere, `clamp()` for
   type/spacing. **Verify by a continuous width sweep** (e.g. 360→1600 step 20) and assert
   ZERO horizontal-overflow frames — any overflow/stretch/strand frame is a wrong rule.
   Fixed sizes (390/834/1440) are checks, not targets. The win condition is FEWER
   hand-authored widths than before. (commit a77291b1)
5. **Single-source checkbox.** `comps/checkbox` (`components.cljc`) is THE rounded amber box
   app-wide; menu cells reuse it. The legacy white box is preserved under a `.classic-
   checkboxes` root class — wire that to a re-frame user pref (mirror `:theme`) for a
   "classic checkboxes" toggle if change-resistant users want it. (commit e3510077)
6. **Collapse animation:** body stays MOUNTED, reveals via `grid-template-rows 1fr↔0fr` on
   `.opt-section-body` (+ inner `.opt-section-body-inner` `overflow:hidden; min-height:0`).
   `>= 40` options or any parent card uses `.instant` (no animation) to dodge reflow jank on
   big grids (Other Equipment ~62). A `prefers-reduced-motion: reduce` `at-media` kills the
   thumb slide, chevron rotation, and the reveal. (commit f54ee592)
7. **Layout-toggle thumb** needs `left: 4px` — without it the absolutely-positioned thumb
   starts from its static auto position and `translateX(idx*100%)` overshoots. (commit 11c91968)
8. **Family A**: call `option-selector-data` ONCE per option (reuse for both chrome and the
   `cell-fn` card). Clear/chip-remove reuse each option's real `:select-fn` — never invent a
   separate deselect path. Filter prereq-hidden options so they leave no empty grid cell.
9. **dominant-prefix/classify are memoized** on the label vector (rerun every render
   otherwise). Derived data is NOT moved into `reg-sub`s — options arrive as component args,
   not normalized in app-db, so a sub keyed on a 90-item vector never caches.
10. **Don't ship the sample** "You can't be Charmed by fey creatures" — prototype-only.

## Design vocabulary (from the designer)

`#f0a100` amber accent; cards flat (`#1b232f`, 1px `rgba(255,255,255,.08)`, faint inset —
NO drop shadow; elevation is reserved for the header band / modals / stepper / popovers).
Dark insets: form controls + search + nested wells `#11161d`, 1px `rgba(255,255,255,.1)`,
radius 8. Muted uppercase 11px field labels (`.builder-field-label`). Native `<select>` =
`appearance:none` + an inline SVG caret. Header band = an APP BAR, not a card: full-bleed
(margin breakout of the builder `p-20`), lighter gradient, `z-index:5` + downward shadow,
2px `rgba(240,161,15,.55)` amber bottom rule, no radius.

## Per-builder status

- **Background Builder** — DONE: cards, parent/child nesting (Tool Profs, Starting
  Equipment), collapse (incl. large children), search, wildcard groups, polish.
- **Race Builder** — DONE: header band, every section carded (Size&Speed / Armor Class /
  Ability Scores via `omv/card`; proficiency menus via factories; Languages promoted to an
  option-menu), dark-inset controls + caret, intrinsic responsive header + cards.
- **Subrace / Monster** — menus carded via the shared renderers (no errors); still need the
  header band + form-section carding + intrinsic header (same conversion as race).
- **Feat** — partially carded; its bespoke sections (`feat-prereqs`, `feat-ability-increase-
  options`, `feat-weapon/armor-proficiency` with leading/trailing checkboxes) are NOT carded.
- **Item** — `item-modifier-toggles` (damage/condition) carded; `base-armor/weapon-selector`
  + `attunement-selector` left bespoke. Item-builder uses `content-page` directly (not
  `builder-page`), so it currently has NO layout toggle.
- **Spell** — only fixed V/S/M + Ritual/Attack toggles; nothing ragged. Optional.
- **Global harmonizers (cascade everywhere)** — DONE: `.input`/`.builder-option-dropdown`
  dark-inset + caret, flat `.opt-section`, muted `.builder-field-label`, single-source
  checkbox, sliding thumb, animated collapse, reduced-motion.

## What's left (priority order)

1. **Generalize the header band + intrinsic layout into a shared component/layout** so
   Subrace/Monster/Item/Feat inherit the band, the `.builder-header-row` flex model, and the
   in-card margin reset (instead of race-only markup). The band is currently inline in
   `race-builder`.
2. **Feat** — card its bespoke sections.
3. **Subrace / Monster / Item** — header band + form-section carding (mechanical once #1 is a
   shared component). Item also needs a layout toggle (it bypasses `builder-page`).
4. **SRD character-creation flow (Family A)** — the original deferral. Options that expand
   into nested UI (ability/spell/feature sub-choices, help, edit) can't become a lightweight
   cell. Decide: lightweight cell for plain options + card fallback for rich ones. SHOW the
   user real before/after screenshots of rich options before deciding.
5. Optional: the "classic checkboxes" user-pref toggle (CSS hook already exists).

## Verify live (headless)

No system node; use the VSCode-server node + the cached FULL Chromium (bundled
headless-shell yields blank shots — see `orcpub-testing/e2e/AGENT-GUIDE.md`):
- node: `~/.vscode-server/bin/<hash>/node`
- Playwright: require from `orcpub-testing/e2e/node_modules`, launch with
  `executablePath: ~/.cache/ms-playwright/chromium-1200/chrome-linux64/chrome`
- Builder URLs: `http://localhost:8890/pages/dnd/5e/<name>-builder`
- Build first: `lein garden once` then `lein fig:build` (CSS is gitignored, regenerated).
- Selectors: `.opt-section` (card), `.opt-section-chevron` (collapsible), `.opt-menu-grid`,
  `.opt-menu-layout-seg`/`.opt-menu-layout-thumb`, `.builder-header-band`, `.checkbox-box`.
- For responsive work: sweep the viewport continuously and assert
  `document.documentElement.scrollWidth - clientWidth === 0` at every step.

## Commit map (25 commits, develop..HEAD)

Component+Family B: 23449448, f94acec1, 6cf95894, 470cbc4a, a9d3673f, e3510077, 2ebd7b40.
Background containment+collapse: 2b2653fb, 78634a01, 7a9a0d55, c2deebab, 1c96e830, a7b219ae,
0572836c. Toggle/anim/reduced-motion: f54ee592, 11c91968. Race carding: 1cba9255, dd88ab2f,
9fc7dc0a, f14c1f5c, a77291b1. Harmonizers: b7c34786, bc026286. (Plus a21a44af seed,
495c7816 env doc.)
