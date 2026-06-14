# Growable Option Menus

How the character-builder's multi-select menus were redesigned, where every menu
lives, and how to verify the work. Implemented on `redesign/growable-option-menus`
off `develop`. Design handoff: `docs/handoffs/character-menus/`.

## The problem

The builder's multi-select menus rendered as ragged `[:div.flex.flex-wrap (map …
labeled-checkbox)]` lists: nothing aligned, long lists were unscannable, and every
saving-throw/condition option repeated the same boilerplate wording. The redesign
gives every menu a **single global layout toggle** (aligned grid / keyword pills /
A–Z groups) plus per-menu chrome (a quoted "pattern banner" when boilerplate is
detected, a search box, a selected-chips tray, an "N of M selected" count, and a
Clear). The amber/slate prototype palette was mapped onto the app's existing
`orange` token rather than copied.

## The two render-path families

Multi-select menus render through two unrelated code paths. The original handoff
only pointed at the first; missing the second is the easy mistake.

- **Family A — SRD character-building selections** (`src/cljs/orcpub/character_builder.cljs`).
  `default-selection-section-body` → `new-option-selector`/`option-selector-base`
  rich bordered cards. Per-option data comes from `views_aux/option-selector-data`
  (`src/cljc/orcpub/views_aux.cljc`): `:selected? :selectable? :multiselect? :select-fn`.
  Re-toggling a selected multiselect option DESELECTS it (`event_handlers.cljc`
  `update-multi-select`). One render path; the section title / "select N / remaining"
  / lock / homebrew chrome is drawn by `selection-section-base`.
- **Family B — homebrew Builder authoring tools** (`src/cljs/orcpub/dnd/e5/views.cljs`).
  ~37 renderers, all the same flex-wrap + `comps/labeled-checkbox` shape, dispatching
  `::bg/`/`::races/`/`::feats/`/`::monsters/`/`::mi/`/`::spells/` toggle events on a
  homebrew map (background/race/feat/monster/etc.). Spread across the Background,
  Race, Subrace, Feat, Spell, Monster, and Item builders.

## The shared component

`src/cljs/orcpub/dnd/e5/option_menu_views.cljs` (aliased `omv`). Pure grouping
logic stays in `orcpub.dnd.e5.option-grouping` (`dominant-prefix`, `classify`,
`group-by-letter`, `present-letters`); this ns is view + the small re-frame state
the menus need.

- `(omv/option-menu {:keys [menu-id title slot-label options multiselect? on-clear
  cell-fn chip-fn trailer]})` — renders banner + count + search + chips + the body
  in the current global layout. `multiselect?` (default true) gates the chips/count/
  Clear. The default cell renders an option's pre-built `:card` (Family A) or, failing
  that, a checkbox cell (Family B).
- `(omv/checkbox-options items selected-fn toggle-fn)` — adapts a list of
  `{:name :key}` items into the normalized option vector. `selected-fn`/`toggle-fn`
  each receive the item.
- `(omv/layout-toggle)` — the segmented grid/pills/A–Z control. Reads/writes the
  global state, so multiple instances stay in sync.

Two factories in `views.cljs` collapse the bulk of Family B (each is then a one-liner):
- `map-prop-menu` — multi-select checkboxes for a builder map-property; a choice is
  on when `[:props kw (:key item)]` is truthy, toggled by `[toggle-event kw key]`.
- `value-choice-menu` — single-select over a numeric property at `[:props kw]`.

## State model (the load-bearing rule)

- **Layout is global and persisted**: `[:user-data :menu-layout]` (default `:grid`),
  via `::omv/layout` / `::omv/set-layout` (with the user→local-store interceptor, same
  as `:theme`). Flipping it re-renders every menu at once. This is intentional — it is
  NOT a per-menu choice.
- **Search query + active A–Z letter are transient and per-menu**: kept under
  `[::omv/menu-state menu-id …]`, never persisted.
- **`menu-id` must be stable and unique.** It keys the per-menu search/letter, so an
  unstable id resets state and a duplicated id cross-links two menus. The convention
  for shared renderers is a vector keyed off the toggle-event keyword
  (e.g. `[:option-skill-proficiency toggle-event]`); per-entity menus must include the
  entity key. Never gensym.

## What's converted vs. left bespoke vs. not done

- **Converted via factories** (14): damage resistance/immunity/vulnerability,
  condition immunity, saving-throw advantage, skill/tool proficiency (+or-expertise),
  armor proficiency, hit points, and the feat number-range menus (skill, speed,
  initiative, languages).
- **Converted but hand-written** (extra parts make a factory awkward): the Background
  Builder sections, `option-proficiency-choice` (already a HOF with 4 partials — the
  good precedent), `option-languages` (Add-Language trailer), `option-weapon-proficiency`
  (concat of "All X weapons" + the weapon list), `feat-prereqs` (leading checkbox + 3
  concat'd groups), `feat-weapon/armor-proficiency` and `feat-ability-increase-options`
  (leading/trailing extra toggles).
- **Intentionally untouched**: small fixed checkbox sets (`feat-misc-modifiers`,
  `feat-spellcasting`, `component-checkbox` V/S/M).
- **Not yet done** (different shapes): the spell-builder spell-list grid and the
  item-builder `base-builder-field` selectors (base-armor/weapon, attunement,
  item-modifier-toggles). Decide per-need; the spell list is already grid-shaped.

## Family A integration notes

`default-selection-section-body` builds one normalized option per template option,
calling `option-selector-data` exactly once and reusing that data both for the chrome
(`:selected?`, the `:select-fn`-backed `:on-toggle` used by clicks/chips/Clear) and to
build the card in `cell-fn` — do not call `option-selector-data` twice. Hidden
(prereq-failed) options are filtered out so they leave no empty grid cell. The
item-adder is passed as `:trailer`. Clear/chip-remove reuse each option's real
`:select-fn`; never invent a separate deselect path or single-select/prereq logic
diverges.

## Performance

`dominant-prefix`/`classify` are memoized on the label vector — Reagent re-runs the
component on every keystroke/layout-flip/toggle, and the analysis only depends on the
option names. Derived data is NOT moved into `reg-sub`s: options arrive as component
args (not normalized in app-db), so a layer-3 sub keyed on a 90-item arg vector would
never cache; memoization on the labels is the equivalent.

## Responsive / styling

Responsive by construction — no media queries needed. The grid is
`repeat(auto-fill, minmax(180px, 1fr))` (reflows column count to container width;
one column on a phone), pills/chips/A–Z bar all `flex-wrap`, and the search is 100%
width. Verified headless at 1400/768/390px with no horizontal overflow. The default
Family B cell uses a rounded checkbox (subtle outline off, filled `orange` with a dark
check on) and an `orange` border on the selected cell (the base cell carries a
transparent 1px border so selection doesn't shift the row). Family A keeps its
existing bordered card.

## Verifying live (headless)

No standalone Playwright is installed, but the pieces exist:
- node: the VSCode-server binary at
  `~/.vscode-server/bin/<hash>/node` (no system node on PATH here).
- Playwright + the FULL Chromium (the bundled headless-shell yields blank shots — see
  `orcpub-testing/e2e/AGENT-GUIDE.md`): require `playwright` from
  `orcpub-testing/e2e/node_modules`, launch with
  `executablePath: ~/.cache/ms-playwright/chromium-1200/chrome-linux64/chrome`.
- Builder URLs are `http://localhost:8890/pages/dnd/5e/<name>-builder`.
- Build before verifying: `lein garden once` then `lein fig:build`.
- A menu is present when `.opt-menu` exists; the toggle is `.opt-menu-layout-seg`
  (3 segs); selected chips are `.opt-menu-chip`, Clear is `.opt-menu-clear`.
