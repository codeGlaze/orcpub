# Monolith Decomposition Plan

Post-builders-split analysis: what to break down next, in what order, and how.

## 1. Did the Builders Split Make Issues Easier to Find?

Yes. Scanning 10 focused files (40-750 lines each) vs one 3,193-line monolith surfaced:

- **Pre-existing efficiency issue in `modifier-values`** (classes.cljs:56) — rebuilds a full sorted-map with a subscribe on every render. Was buried at line 1458 of the monolith. Now it's line 56 of a 740-line file, immediately visible.
- **Pre-existing TODO in `plugin-datalist`** (builders.cljs:70) — local atom state management could use re-frame. Was hidden at line 1340 of the monolith.
- **Good patterns confirmed**: monster.cljs correctly uses `js/isNaN` guards, item.cljs validates integer-only input with regex, selection.cljs catches duplicate option names. These patterns are now visible and can be replicated elsewhere.

The split also exposed that `classes.cljs` at 740 lines is the largest child — if any child were to grow further, it would be the first candidate for re-evaluation.

## 2. What Files Can or Should Be Broken Down?

### Tier 1: Data/logic separation (high impact, low risk) — DONE

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

### Tier 2: Domain decomposition (medium impact, medium risk)

These are **behavioral monoliths** where multiple concerns are interleaved. Splitting requires understanding call graphs.

| File | Lines | Split strategy |
|------|-------|----------------|
| `events.cljs` | 4,726 | Domain-based: `events/character.cljs`, `events/builder.cljs`, `events/auth.cljs`, `events/entity.cljs`, `events/ui.cljs` |
| `options.cljc` | 3,483 | `options.cljc` (constants, enums, abilities) + `options_builders.cljc` (option template builders used by character creation) |
| `views.cljs` | 2,733 | Continue active refactor — detail pages, shared display utils |
| `character_builder.cljs` | 2,165 | `character_builder.cljs` (core form + step logic) + child files for major sections |
| `subs.cljs` | 1,518 | Domain-based mirroring events split |

### Tier 3: Not worth splitting

| File | Lines | Why not |
|------|-------|---------|
| `character/random.cljc` | 2,462 | Single cohesive feature (random char gen) |
| `styles/core.clj` | 1,587 | Data-driven design system, cohesive by design |
| `template.cljc` | 1,567 | Core infrastructure, tightly coupled |
| `import_validation.cljs` | 1,554 | Single domain, already focused |
| `routes.clj` | 1,443 | Standard pattern — all routes in one place |
| `spell_subs.cljs` | 1,375 | Already domain-scoped |
| All `templates/*.cljc` | varied | Content data bundles, one per source book |

## 3. Order of Precedence

### Phase A: Data extraction (Tier 1) — DONE
**monsters → spells → magic_items → classes**

Completed 2026-02-25 on `refactor/data-extraction`. All 4 files extracted, 206 tests passing, 0 CLJS warnings.

### Phase B: Events decomposition
**events.cljs → subs.cljs** (mirror the same domain boundaries)

Rationale: events.cljs at 4.7K is the second-largest behavioral file. It's where most bugs are introduced (event handlers). Splitting by domain makes every handler findable. subs.cljs should mirror the same domain split for consistency.

### Phase C: Remaining views + options
**views.cljs (continue) → character_builder.cljs → options.cljc**

Rationale: views.cljs refactor is already in progress. character_builder.cljs overlaps with the views work and should coordinate. options.cljc is lower priority since it's cljc (shared) and harder to split cleanly.

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
