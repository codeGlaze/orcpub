# Content Extensibility — CURRENT DIRECTION (read this first)

**Status:** This supersedes the original framing in `content-extensibility.md` /
`-plan.md`, which pitched a sweeping registry + "catalog/grant DSL." After a long
readability review, that ambition was **deliberately deflated**. Treat the earlier docs
as *analysis/history*; treat THIS as the plan.

## The one principle everything now hangs on

> **An abstraction earns its keep only when it is *thicker* than what it hides AND its
> interface reveals intent.** Collapse mechanical duplication; keep readable, meaningful
> code explicit. Never make one pattern swallow a genuinely different kind of thing.

Evidence this is the right line (all from this codebase):
- `by-parent` **fails** it — it hid `group-by` (thinner than the name). **→ REVERT it** to
  plain `group-by`. (`plugin-options` is borderline: it names a repeated *compound*
  `(mapcat (comp vals ::e5/X) plugins)` — keep only if it reads clearly to you.)
- `reg-save-homebrew` / `reg-new-homebrew` / `reg-option-traits` **pass** it — thick, clear,
  already used and trusted. The good direction *composes* these, it doesn't replace them.

## The real problem (unchanged, and worth solving)

Adding a new **content type** (a builder: boon, lineage, …) touches ~8 files. About **half
is genuine per-type work** (the builder *form*, the *spec*, how it wires into game rules) —
irreducible. The other half is **mechanical boilerplate** (identical passthrough subs,
repetitive `set`/`reset`/register events, db plumbing, route entry). Only the mechanical
half should be collapsed. Realistic outcome: ~8 edits → ~5, where the 5 are the actual feature.

A simple **button** is ~2 files (view + event) — that's normal re-frame, not a target.

## The agreed shape: descriptor (data) + named HOF that composes existing factories

```clojure
(def boon
  {:id :boon  :name "Pact Boon"
   :builder-item ::classes/boon-builder-item
   :spec ::classes/homebrew-boon
   :plugin-key ::e5/boons
   :default {}
   :route route-map/dnd-e5-boon-builder-page-route
   :builder-features #{}})            ; e.g. #{:traits :modifiers :selections} for richer types

;; events.cljs — one intent-revealing call composing the factories that already exist:
(register-homebrew-content! boon)     ; reg-save/new/edit-homebrew + set/reset; + option-families per :builder-features
```
Readable because the call site states intent and the inputs are visible. NOT a macro that
hides what's generated; NOT a loop over readable data (`default-value` stays explicit).

## Audit — does this cover every builder? (verified from `events.cljs`)

**12 types share the `reg-save-homebrew` shape:** spell, monster, encounter, background,
language, invocation, boon, feat, race, subrace, subclass, class. They split into:

- **Bucket 1 — fits verbatim (6 "basic"):** spell, encounter, language, invocation, boon, feat.
- **Bucket 2 — fits + additive `:builder-features` (6 "richer"):** class & subclass
  (`:traits :modifiers :selections`), race, subrace, monster, background (`:traits`). These
  *add* `reg-option-traits/modifiers/selections` (existing HOFs), keyed off a readable flag set.
- **Bucket 3 — do NOT force into the common registrar:**
  - **magic-item** — different *kind*: server-persisted (`::mi/save-item` POSTs to
    `dnd-e5-items-route`, stored in `::mi/custom-items`, internal↔external conversion). Give it
    its **own** `register-server-content!`. Branching the homebrew registrar for it = the
    god-function trap.
  - **selection** — standard flow + a duplicate-option-name validation; a `:save-fn` hook.
  - **combat tracker** — not a builder (transient state). Exclude.

**Maintainability verdict:** common registrar for the 12 + a flag set for the additive
extras + a *separate* registrar for server-backed content = **easier**. One universal
registrar with branches for every deviation = **harder** (unreadable conditionals). The
current state is fully bespoke; the common registrar is strictly less duplication for the 12.

## Next steps (the goal is STABILIZING while adding features, not shaky foundations)

1. **Revert `by-parent`** → `group-by` (undo the one readability regression). Decide on
   `plugin-options` (keep if it reads clearly, else revert too).
2. **Build `register-homebrew-content!`** (composing the existing factories) and **swap one
   existing builder (boon) through it + commit** — gated by the headless cljs harness
   (`cljs-headless-harness.md`).
3. **Create a NEW builder end-to-end** to measure the real "add a feature" effort going
   forward (the actual test of whether this helps).
4. Keep `default-value` explicit; do not build the catalog/grant DSL; do not loop readable data.

## What already stands (don't redo)
- Phase 4b (13 identical passthrough subs → one loop) — a clean fit, keep.
- The `content_types` registry **as data + its audit test** — cheap, guards the orcbrew contract.
- Import-validation fixes + `save-character` crash fix (committed, harness-verified).
- All the analysis docs, compat invariants, the cljs harness, verification-discipline lessons.

## Deferred — own branch (surface at branch close)
- Character-validation contract (`character-validation.md`).
- ClojureScript tests into CI (the harness here is the prototype).
