# Class config: `:key` is identity, `:name` is display

**Date:** 2026-05-04
**Source:** Direct code analysis of `spell_subs.cljs:467-485`, `options.cljc:469`, `template_base.cljc:275`, `options.cljc:635, 1060, 2904`, `template.cljc:74-88`, `db.cljs:267-305`, `events.cljs:208-228`, `content_reconciliation.cljs`, plus session reasoning trail dated 2026-05-04.

## TL;DR design rule

`:key` is identity. `:name` is display. Display strings never bleed into keys.

- *Inside the editor:* renaming a class moves the key with the name (e.g. `gambit` → `charlatan`). Editor flow owns key changes and the rebinding of dependent selections.
- *Outside the editor:* `:key` is read, never re-derived. `name-to-kw(:name)` outside the editor/import path is the bug pattern — apparent in the cantrip regression case study below.
- Built-in classes (`:cleric`, `:wizard`, ...) are source-code constants — never rename, never collide.

## Case study: cantrip/spell selection regression (2026)

### Symptom
Homebrew Cleric/Druid replacements lose cantrip/spell selections after a UX change to suffix the source name onto class display labels. Saved entity still has `:cleric-cantrips-known`; UI shows nothing selected. Setting the orcbrew source name to blank temporarily "fixes" it.

### Root cause
`spell_subs.cljs:475-478` (`::classes5e/plugin-classes` sub) mutated class `:name` to `"Cleric (Source)"` for display. Class `:key` was untouched. The mutation contract on `:479-480` claimed *"`:name` is display-only"* — but downstream code violated that contract by re-deriving identity via `common/name-to-kw` from the mutated `:name`.

### The four downstream leak sites

| # | Site | What leaked |
|---|------|-------------|
| 1 | `options.cljc:469` (`spell-selection`) | mutated `name` → `name-to-kw` → `::t/key` (the cantrip bug) |
| 2 | `template_base.cljc:275` (`?prepare-spell-count`) | mutated `class-name` → `name-to-kw` → lookup miss against canonical `?spell-slot-factors` |
| 3 | `options.cljc:2951` (`mods/map-mod ?prepares-spells`) | feeds #2; same root |
| 4 | `options.cljc:635` (`class-key-name` fallback) | latent; current callers pass `class-key`, but the fallback is wrong |

### Fix
Reverted the `:name` mutation. Plumbed `:plugin-source` through `option-cfg` as a distinct `::plugin-source` slot. Display suffix computed at the dropdown render site, gated by a new `::show-class-source-suffix` user pref. All four leaks heal from the single revert.

A load-time reconciler (`reconcile-spell-selection-keys` in `content_reconciliation.cljs`) heals regression-window orphans on character load when a single suffix-match candidate exists.

## When `name-to-kw(:name)` is benign vs. a leak

Common false-alarm pattern during reviews — `name-to-kw(:name)` shapes can look identical but differ in safety.

**Benign (constructor / minting time):**
- `template.cljc:option-cfg` / `:selection-cfg` — destructured `name` kwarg, derived once at template-build time
- `options.cljc:feat-option` (`:1060`) — operates on feat configs whose `:name` is not mutated upstream
- `common.cljc:add-keys` — applied to static seed data (equipment, weapons, conditions, monsters, spells)
- Import path slugification (`import_validation.cljs`)
- Homebrew duplicate-detection (`views.cljs:6404+`) — operates on user-typed names in the editor

The shared property: `:name` is stable at the time of derivation. The key gets minted once from a canonical name.

**Leak shape:**
- `:name` is *mutated upstream* of the identity derivation (e.g., a sub appends a source label to `:name` for display, then a downstream consumer derives a key from it)
- The derived key drifts away from the canonical key the rest of the pipeline expects
- Saved data referencing the canonical key is orphaned

**Audit heuristic:** for any `name-to-kw(:name)` site, trace `:name`'s upstream. If any code rewrites `:name` for display purposes, you have a leak. If `:name` is the canonical name from source code or stable config, you don't.

## Plugin-load race: verified non-issue

**✅ VERIFIED 2026-05-04 — direct trace of `db.cljs` and `events.cljs`.**

A reviewer worried that `:set-character` could fire before plugins hydrate, leaving any reconciliation logic with empty `:plugins` in `db`. Confirmed not the case:

- `::e5/plugins` is registered via `reg-local-store-cofx` at `db.cljs:302` — synchronous localStorage read.
- `:initialize-db` at `events.cljs:208` injects `:local-store-character` and `::e5/plugins` as cofx in the same handler.
- The handler writes both atomically into `db` via a single `cond->` (`events.cljs:222-228`).
- Subsequent `:set-character` dispatches see `db` with plugins already populated.

The "user opened a character on a machine without the originating plugin imported" case is real but distinct — that's correctly the missing-content / parked-orphan case, not a race. Reconciler treats it conservatively (no silent rewrite).

## Reconciler approach: transient parked state

The reconciler returns `{:rewrote [...] :parked [...]}`:
- `:rewrote` — orphan keys with a single unambiguous canonical candidate; rewritten in place at character load.
- `:parked` — orphans with multiple candidates (multiclass overlap) or zero candidates (originating class not loaded). Data preserved on the entity; held in transient db state for UI banner.

**Design call:** parked entries are NOT persisted to the entity as a separate field. They're re-derived every character load from the entity's orphan keys × currently-loaded plugins. This avoids a schema slot that could drift from the underlying data.

Resolution UI lives in `views.cljs` (banner + resolution view); resolution event mutates the entity and re-runs the reconciler.

## Source comments worth leaving

For sites where future reviewers are most likely to speculate about behavior:

1. **`spell_subs.cljs` `::classes5e/plugin-classes`** — note that `:name` was historically mutated for display and the mutation was reverted; refer to this kb doc.
2. **`content_reconciliation.cljs:reconcile-class-entry-options`** — note that the "multiple candidates within one class entry" branch is unreachable today (single suffix per class) but the cross-class accumulator in `reconcile-spell-selection-keys` populates `:parked` for multiclass overlap.
3. **`events.cljs` reconciler invocation site** — one-liner that the reconciler trusts `db` plugins to be hydrated because `::e5/plugins` is a sync cofx at `:initialize-db`.

Comments should be one-liners pointing here, not duplications of this doc.
