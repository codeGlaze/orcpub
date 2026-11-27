# Monolith Decomposition Plan

> **Status and plan, 2026-09-15.** Tier 1 below is marked DONE, and it is — on
> `refactor/data-extraction`, which is unmerged and staying that way. No `*_data.cljc` file exists
> on `agents/develop`.
>
> **That branch is not being merged.** It was weeks of work and became unmaintainable to keep in
> step with bug fixes landing on the live branch at the same time. The plan is to redo the
> extractions incrementally alongside the Fall Update, **using that branch's docs as the
> guideline** — the research and sequencing below is the part worth keeping, so the idea does not
> have to be paid for twice. Treat Tier 1 as a proven recipe, not as work already in the tree.

Post-builders-split analysis: what to break down next, in what order, and how.

## 1. Did the Builders Split Make Issues Easier to Find?

Yes. Scanning 10 focused files (40-750 lines each) vs one 3,193-line monolith surfaced:

- **Pre-existing efficiency issue in `modifier-values`** (classes.cljs:56) — rebuilds a full sorted-map with a subscribe on every render. Was buried at line 1458 of the monolith. Now it's line 56 of a 740-line file, immediately visible.
- **Pre-existing TODO in `plugin-datalist`** (builders.cljs:70) — local atom state management could use re-frame. Was hidden at line 1340 of the monolith.
- **Good patterns confirmed**: monster.cljs correctly uses `js/isNaN` guards, item.cljs validates integer-only input with regex, selection.cljs catches duplicate option names. These patterns are now visible and can be replicated elsewhere.

The split also exposed that `classes.cljs` at 740 lines is the largest child — if any child were to grow further, it would be the first candidate for re-evaluation.

## 2. What Files Can or Should Be Broken Down?

### Tier 1: Data/logic separation (high impact, low risk) — proven, not merged

Completed on `refactor/data-extraction` branch (5 commits, 2026-02-25).

| File | Before | After | Data file | Technique |
|------|--------|-------|-----------|-----------|
| `monsters.cljc` | 9,273 | 54 | `monsters_data.cljc` (9,226) | Pure data move, no deps |
| `spells.cljc` | 4,229 | 59 | `spells_data.cljc` (4,187) | Constants move with data, re-export `schools`/`spells`/`spell-map` |
| `magic_items.cljc` | 3,214 | 512 | `magic_items_data.cljc` (2,721) | `:as-alias` for parent ns keywords, ~1,150 `::` → `::mi/` replacements |
| `classes.cljc` | 3,147 | 45 | `classes_data.cljc` (3,137) | 12 class option fns + helpers, 15 re-exports |

**Key learnings:**
- `:as-alias` (Clojure 1.11+) is the right tool when data entries use `::` qualified keywords from the parent namespace. Avoids circular deps without changing keyword identity.
- `class-level` had to move to the data file (not stay in logic) because class option functions call it internally. Re-exported from the logic file for external consumers.
- Pre-existing issue surfaced: `item-saving-throw-bonuses` dropdown in item builder is a dead control (logged in docs/TODO.md).

### What builder generation would actually take off `views.cljs`

Measured 2026-09-15 on `agents/develop`, because "the Fall Update should cut views.cljs down" is
worth a number rather than a hope.

Of its 10,821 lines, **~3,623 (33%) is the homebrew builder UI** — a near-contiguous run from
about L5000 to L8400, plus the page wrappers near the end.

Two things that change what to expect:

- **The page wrappers are already 3 lines each.** Fourteen `*-builder-page` fns, ~45 lines total.
  Generating page code wins nothing there; the bulk is the per-type field-editing UI.
- **The schema-driven path already exists and already works.** `simple-content-builder` +
  `render-builder-field`, fed by the type's declarative field schema, serves 6 of the 16 builders
  today: language, spell, boon, invocation, draconic-ancestry, fighting-style. All small types.

The 10 still hand-written are ~1,150 lines: class (268), monster (233), race (152), subrace (129),
subclass (105), selection (80), feat (66), item (53), background (35), encounter (25). Converting
all of them is roughly a **10% cut** to `views.cljs` — real, but not the thing that fixes 10,821
lines. The shared widgets underneath (`render-builder-field`, `rows-node`, `vector-rows-node`,
`group-toggles`) are the framework and stay.

**Generation and extraction are different wins.** Generation removes ~1,150 lines; moving the
builder UI out of `views.cljs` relocates all 3,623. Do not expect the first to substitute for the
second.

### Tier 2: Domain decomposition (medium impact, medium risk)

These are **behavioral monoliths** where multiple concerns are interleaved. Splitting requires understanding call graphs.

*Line counts re-measured 2026-09-15 on `agents/develop`; the originals (from the 2026-02 analysis)
had drifted badly. The ranking changed with them — `views.cljs` is now the largest file in the
repo, not the third, so Phase C below is arguably ahead of Phase B.*

| File | Lines | Split strategy |
|------|-------|----------------|
| `events.cljs` | 6,687 | Domain-based: `events/character.cljs`, `events/builder.cljs`, `events/auth.cljs`, `events/entity.cljs`, `events/ui.cljs` |
| `options.cljc` | 4,091 | `options.cljc` (constants, enums, abilities) + `options_builders.cljc` (option template builders used by character creation) |
| `views.cljs` | **10,820** | Continue active refactor — detail pages, shared display utils, and the 16 builder pages |
| `character_builder.cljs` | 2,724 | `character_builder.cljs` (core form + step logic) + child files for major sections |
| `subs.cljs` | 1,697 | Domain-based mirroring events split |

### Tier 3: Not worth splitting

| File | Lines | Why not |
|------|-------|---------|
| `character/random.cljc` | 2,462 | Single cohesive feature (random char gen) |
| `styles/core.clj` | 3,006 | Data-driven design system, cohesive by design |
| `template.cljc` | 1,588 | Core infrastructure, tightly coupled |
| `orcbrew_validation.cljs` | 2,293 | Single domain, already focused (renamed from `import_validation.cljs`) |
| `routes.clj` | 2,015 | Standard pattern — all routes in one place |
| `spell_subs.cljs` | 1,594 | Already domain-scoped |
| All `templates/*.cljc` | varied | Content data bundles, one per source book |

## 3. Order of Precedence

### Phase A: Data extraction (Tier 1) — proven on a branch, to be redone incrementally
**monsters → spells → magic_items → classes**

Completed 2026-02-25 on `refactor/data-extraction`: all 4 files extracted, 206 tests passing, 0
CLJS warnings. Not merged and not going to be — redo these incrementally during the Fall Update,
following the recipe and order above.

### Phase B: Events decomposition
**events.cljs → subs.cljs** (mirror the same domain boundaries)

Rationale: events.cljs at 4.7K is the second-largest behavioral file. It's where most bugs are introduced (event handlers). Splitting by domain makes every handler findable. subs.cljs should mirror the same domain split for consistency.

### Phase C: Remaining views + options
**views.cljs (continue) → character_builder.cljs → options.cljc**

Rationale: views.cljs refactor is already in progress. character_builder.cljs overlaps with the views work and should coordinate. options.cljc is lower priority since it's cljc (shared) and harder to split cleanly.

**Done 2026-11-10, reference pages** (`test/rescued-claude-tests`, `4699ad547`): the Monsters,
Spells and Items lists, the monster, spell and item pages and the Rules and Conditions pages
moved to `views/reference.cljs` (494 lines; `views.cljs` 11,717 -> 11,269). They are leaves:
only `core.cljs`'s route map mounts them, and the new namespace requires `views`, never the
reverse. **Stayed, and why:** the Orcacle (`views.cljs` renders its overlay on every page, so
moving it would make the two namespaces require each other) and the shared display layer.
**Next step, the display layer:** `spell-component`, `monster-component`, `item-component`,
their summaries and `condition-component` (about 430 lines, `views.cljs` ~874-1300). The
character sheet, builder and Orcacle draw with them, and they call `svg-icon`, `srd-inlines`,
`srd-route-handler` and `condition-page-handler`, so they move into a lower namespace that
`views` requires, with `svg-icon` going to a shared widget namespace first. A big move by the
AGENTS.md rule: plan it and ask first.

## 4. Branching Strategy

### Recommendation: One branch per tier, not per file

| Branch | Scope | Off of | Status |
|--------|-------|--------|--------|
| `refactor/data-extraction` | All Tier 1 (monsters, spells, magic_items, classes data separation) | `breaking/` | **DONE** |
| `refactor/events-decomposition` | events.cljs + subs.cljs domain split | `breaking/` | Next |
| `refactor/views-extraction` | views.cljs + character_builder.cljs | `breaking/` | In progress |

**Why per-tier, not per-file:**

- **Tier 1 splits are all mechanical and independent** — they can be done in a single session and committed atomically. Four small commits on one branch, one PR. Creating four branches for four data extractions adds overhead without reducing risk.
- **Tier 2 events + subs should share a branch** — the domain boundaries must match. Splitting events into `events/character.cljs` while subs stays monolithic is inconsistent. Do both together.
- **The current `refactor/views-extraction` branch is already the right scope** for views work.

### Exception: If any split gets complicated

If a Tier 1 data extraction turns out to involve non-trivial logic changes (e.g., circular deps between data and specs), break it out to its own branch. But this is unlikely — data defs are almost always leaf dependencies.

## 5. Pre-existing Issues Surfaced by Scanning

These are bugs/inefficiencies that exist in the codebase today, not introduced by the split:

| Location | Issue | Severity | Notes |
|----------|-------|----------|-------|
| `classes.cljs:56` (was `builders.cljs:1458`) | `modifier-values` rebuilds full sorted-map with subscribe on every render | MEDIUM | Efficiency — should memoize or lift subscribe |
| `builders.cljs:70` | `plugin-datalist` uses local atom, TODO says consider re-frame | LOW | Works fine, just not idiomatic |
| `views.cljs` (various) | Detail pages still in monolith | LOW | Active refactor, tracked separately |
| `views/builders/item.cljs:302` | `item-saving-throw-bonuses` dropdown hardcoded to 1 option, no event wiring | LOW | Logged in docs/TODO.md — wire up "Becomes At Least" option |

## Shared controls and duplicates (audit, 2026-11-27)

Read-only audit of `views.cljs` and duplicates across `src/`, at `test/browser-toolbox` `e2da3723f`. The owner asked (2026-11-27) to merge duplicate controls and reuse as much as possible as part of the split. Nothing here is built yet; the tab and switch helpers are moving to `orcpub.components` on `test/browser-toolbox`. Spot-checked: the two `labeled-checkbox` copies and the dead `default-input-style` / `validation-messages`.


"C" = checked (clj-grep --live or read). "I" = inferred. Line numbers are views.cljs unless a file is named. "In progress" (not re-proposed): `switch-attrs` 574, `tab-key-target` 585, `step-tab!` 597, `tab-attrs` 613, `tab-panel-attrs` 631 (about 70 lines) going to components.cljc.

### 1. Generic helpers and where they go

| function | line | lines | live call sites (files) | kind | proposed home | notes |
|---|---|---|---|---|---|---|
| make-stop-prop-event-handler | 567 | 7 | 8 (views 7, character_builder 1) C | generic | components.cljc beside make-event-handler | same memoized shape; dispatches, then stops propagation |
| details-button | 1986 | 7 | 2 (views) C | generic | folds into components `expand-button` | "hide"/"details" plus caret; same job as expand-button |
| boolean-icon | 3012 | 3 | 5 (views) C | generic | components.cljc | check/cross glyph; reads no state (C) |
| paragraphs | 830 | 18 | 3 (views) C | reads :mobile? and srd-linked-text | stay | the other `paragraphs` hits are different functions (C) |
| columns styles (3) | 813-827 | 14 | 1+2+2 (views) C | generic style maps | builder_fields or a style map in components.cljc | only used by `paragraphs` and monster pages (I) |
| svg-icon | 114 | 6 | 31 views + 1 views_shared C | reads `[:theme]` | stay (wraps comps/themed-icon) | `svg-icon-2` in views_shared 26 ignores theme: unify (see 5.4) |
| vec-remove, without-nth | 1867, 1788 | 8 | 2 + 1 (views) C | pure vector helpers | `orcpub.common` (cljc) | no UI at all (C) |
| title-case, capitalize-words | 1913, 8893 | 9 | 4 + 1 (views) C | pure string | `orcpub.common` | `common/safe-capitalize-kw` exists; check overlap (I) |
| title-with-help | 6522 | 14 | 1 (views) C | generic help toggle | components.cljc, rebuilt on `show-info-button` | atom plus ? and caret; same job as with-help in builder_fields (I) |
| add-option-button, add-trait-button | 7802, 5962 | 17 | 2 + 2 C | dispatches a fixed event | stay | event is domain (C) |
| builder-field-cue | 5199 | 13 | read in views (C) | reads `:builder-field-errors` | builder_fields.cljs | state read is builder-only; sits with its only users |
| builder-input-field | 5212 | 34 | 22 via wrappers C | reads `:builder-field-errors` | builder_fields.cljs | see section 2 |
| selection-help | 6518 | 4 | 2 C | domain text | stay | |

Dead code (C, no live use outside its own def): `default-input-style` 99 (5 lines), `validation-messages` 104 (10 lines). `input-style` 90 (9 lines) is used once, only by that dead style. About 24 lines can be deleted outright.

### 2. Wrapper families (one HOF plus a data table)

- 13 `*-input-field` 5246-5284 (39 lines, 22 call sites C: spell 4, monster 4, class 3, the others 1). All call `builder-input-field` with a fixed event id; only monster and encounter pass `type`. Replace by one `(builder-input-field title prop item event ...)` and `def`s of the event keyword, or `(def input-field-events {:spell ::spells/set-spell-prop ...})` plus `(input-field kind title prop item ...)`: saves about 30 lines, 22 call sites change. Merge 5.1.
- 19 checkbox-grid functions (`option-skill-proficiency`, `-or-expertise`, `option-tool-proficiency(-or-expertise)`, `option-damage-*`, `option-condition-immunity`, `option-hps`, `feat-speed-bonuses`, `feat-initiative-bonuses`, `feat-languages`, `background-*-proficiencies`, `*-choice-checkboxes`): 488 lines for the whole 5293-5960 run (C by read). Each is title plus a `[:div.flex.flex-wrap (map (fn [x] [:span.m-r-20.m-b-10 [comps/labeled-checkbox label checked? false #(dispatch ...)]]))]`. One `checkbox-grid {:title :items :label-fn :checked-fn :event}` with a table row per function. Saves about 250 lines (I, from the shared shape; 52 uses of `m-r-20.m-b-10`, 60 uses of `comps/labeled-checkbox` in views C).
- 4 `item-damage-*/item-condition-immunities` 5129-5155 (28 lines) around `item-modifier-toggles`: a 4-row table (title, items, toggle event, has-sub); saves about 20 lines (C).
- 14 `*-builder-page` 10242-10280 (3 lines each): already minimal (plan says the same); leave.
- 4 `confirm`-style helpers: `confirm-fn`/`confirm-handler` (views 654) and `confirm-handler` (character_builder 2416) share a name but differ in behaviour (C); rename, do not merge.

### 3. Look generic, are not (stay)

- `builder-input-field`, `builder-field-cue`: read `:builder-field-errors` (C).
- `roll-button` 2130 (25 lines): reads `:ua-device-type`, uses dice text; its modifier-key logic is generic but it lives with `button-roll-fn`. Only this one roll button exists; `cast-button` 3770 dispatches nothing but opens a cast panel from item data (C).
- `component-checkbox` 5285: dispatches `::spells/toggle-component` (C). `weapon-switch` 3102, `weapon-menu` 3318: read weapon state (I).
- `svg-icon`: reads `[:theme]` (C). `header-tab`, `header-tab2`: route and subscribe (I).

### 4. Totals
- To components.cljc: about 45 lines of helpers (stop-prop handler, boolean-icon, details-button, title-with-help) plus the checkbox-grid, caret and key helpers from section 5 (about 60 new lines there).
- To existing views/*.cljs: `builder-field-cue`, `builder-input-field` and the 13 wrappers (about 86 lines) to builder_fields.cljs; the checkbox-grid users stay in views until the builder move.
- To `orcpub.common`: 17 lines of pure helpers.
- New namespace: none needed.
- Net lines removable by merging: about 380 (250 grid, 30 wrappers, 24 dead, 20 item toggles, 60 from sections 5.2-5.7), against about 90 added.

### 5. Duplicates across src/, ordered by value (lines saved x call sites)

1. Two `labeled-checkbox`: components.cljc 38 (keyboard-accessible, role checkbox, on-click on the row) and builder_fields.cljs 116 (mouse only, `[label selected?]`, the caller wraps a div with on-click). C. Callers of the builder_fields one: 15 in views, 2 in pdf_options, 1 inside builder_fields (C). Survivor: components version; the old call sites pass an on-click and `false` (15 edits in views, 2 in pdf_options). Gain: keyboard access and one control.
2. Checkbox grid, see section 2: 19 definitions, about 250 lines.
3. Caret toggles `[:i.fa {:class (if open? "fa-caret-up" "fa-caret-down")}]`: about 30 copies (views 20, reference 5, inventory, rest_controls, character_builder, plus angle and chevron variants in notifications, import_log, character_builder 217, views 6736 and 7440). C. Survivor: `comps/expand-button` for the underlined text form, plus a new `caret [open?]` in components for the bare icon; variants become an option (`:up-down`, `:chevron`). About 30 call sites, each 1-2 lines.
4. Icon: `svg-icon-2` (views_shared 26, ignores theme, no dark set) against `comps/themed-icon` (C). views_shared is cljc, so `svg-icon-2` can call `themed-icon` with a fixed theme, or take `theme` as it already pretends to (its def plus 1 use, both in views_shared, C). views.cljs `svg-icon` keeps wrapping it.
5. Space/Enter key handler copied inline (besides `press-to-click` and the in-progress `switch-attrs`/`tab-attrs`): views 436 (What's New pill), views 9987 (newsletter switch), inventory 792 (manage switch), views_shared 82 (second What's New pill) (C). Merge: export `press-to-click` as `comps/key-click` and use `switch-attrs` for 9987 and 792; the two What's New pills (views 432-441, views_shared 78-86) are one pill in views_shared (2 call sites). Saves about 25 lines.
6. Enter-to-blur on number inputs: views 3647, rest_controls 61 (C), same one-liner; and Enter-to-act at views 348, 1075, 10424. One `comps/on-enter [f]` returns the handler: 5 call sites, small gain, removes 5 copies of `(= "Enter" (.-key e))`.
7. Selects: `builder_fields/dropdown` (typed values, de-dup) against raw `[:select.builder-option.builder-option-dropdown ...]` at views 4637, 9066, 10325, character_builder 338 and 364, and `comps/selection` 78 and `selection-adder` 58 (C; I for whether each can use `dropdown`). Differences: placeholder row, auto width, form-button class. Survivor: `dropdown` (moves to components.cljc, since `comps/selection` and `selection-adder` already live there) with `:placeholder`, `:class-name`, `:reset?` options. 8 call sites in app code (C count), `comps/selection` has 0 live uses outside its def (I, grep pattern broad).
8. `event-value` (builder_fields 8) against `(.. e -target -value)` written out: 22 in views, 8 in character_builder, 10 in auth, 4 in inventory, 4 in reference, 2 in components (C). Move `event-value` to components.cljc and replace; mechanical, 50 sites, 0 behaviour change.
9. Modals: no shared modal exists. `deletion-modal-with` 10190, conflict_resolution `*-modal` (303, 450, 539) and views `confirm` overlay (`loading-style` 637, 648-660) each draw their own overlay and button row (I; not read in full). A `comps/modal [{:title :body :buttons}]` is a candidate; check before proposing.
