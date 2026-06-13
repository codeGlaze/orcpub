# Content Extensibility

**Purpose:** Explain why adding a content type or builder to the 5e app touches so
many files, and propose a direction to reduce that cost without losing the
standardization the codebase already has.

**Status:**
- "The problem" and "Current cross-links" are **verified from code** (file:line).
- "Proposed direction" is a **design proposal — not implemented.** Keep that line
  intact when editing; only verified source behavior belongs in the rest of the KB.

**Branch note:** Line references were read on a branch where the frontend is still
monolithic (`views.cljs`, `events.cljs`, one `spell_subs.cljs`). On `agents/develop`
the views layer is split (see [views-builders-split.md](views-builders-split.md)),
so view references resolve by symbol, not line. Grep the named symbol to confirm.

---

## The problem

Adding one content type touches ~8 files. The Pact Boon builder (commit `6029fd0`)
touched 10. The diff splits into two unrelated costs:

1. **Registration** — route keyword, bidi entry, db default, localStorage key,
   `->local-store` fn, init-db slot, set/reset/save events, a passthrough sub, and a
   page-map entry. All keyed by the same entity, scattered across files. This is the
   route-registration pain already noted in
   [spa-routing-architecture.md](spa-routing-architecture.md), widened to db/events/subs.
2. **Injection** — wiring the new options into a *parent* entity (boons into the
   warlock). Today this is done with positional function arguments, which is the
   fragile half: a new child type means editing the parent's signature and the
   subscription's binding vector in the exactly-right position.

The registration cost is mechanical. The injection cost is where bugs hide.

## Current cross-links (verified from code)

How each child option set reaches its parent today. Note that one pattern
(bucket-by-key, used by subraces) is already clean; the others are not.

| Link | How it's wired today | Reference |
|------|----------------------|-----------|
| subraces → races | **Bucket-by-key** ✅ `(group-by :race …)` merged into each race in `::races5e/races`. Adding a subrace needs **no** race edit. | `spell_subs.cljs` ~887, ~893–925 |
| subclasses → classes | **Bucket-by-key** ✅ `(group-by :class …)`. Same clean pattern. | `spell_subs.cljs` ~893 |
| boons → warlock | **Positional** ⚠️ `boons` threaded through `warlock-option` and `base-class-options`, plus an input added to the 8-input `::classes5e/classes` sub. | `classes.cljc` ~2629, ~2987; `spell_subs.cljs` ~945 |
| invocations → warlock | **Positional** ⚠️ Same shape as boons. | `classes.cljc` ~26; `spell_subs.cljs` ~945 |
| ancestries / lineage → dragonborn | **Static** ⛔ `draconic-ancestries` is a fixed `def`; `dragonborn-option-cfg` is a `def`, not a function. No plugin path exists. | `options.cljc` ~3428; `spell_subs.cljs` ~759 |
| spells → classes | **Context thread** `spell-lists`/`spells-map` passed positionally to every class builder. | `spell_subs.cljs` ~932 |

This same problem appears in the issue tracker — see
[issues/homebrew-builders.md](../issues/homebrew-builders.md): #58 (invocations
hardcoded to Warlock, "requires generalizing"), #57/#209 (invocation prerequisites),
#172/#170 (selections in feat/subclass builders), #210/#107 (spells-known and
subclass spell-list expansion), #280 (metamagic builder), #173 (custom spell school),
#128 (choice-of-ASI in race builder). They are instances of the same two costs.

## Proposed direction (design — not implemented)

Two independent layers. Either is useful alone; together they cut the per-type cost
to "one descriptor + the builder form + the spec."

### Layer 1 — content-type registry

One list of descriptors as the single source of truth. The scattered registrations
become loops over that list, calling the factory functions that **already exist**
(`reg-save-homebrew`, `reg-new-homebrew`, `reg-edit-homebrew`, `reg-local-store-cofx`,
`builder-page`). This is moving call-sites into data, not new machinery.

```clojure
(def content-types
  [{:id :boon :name "Pact Boon" :builder-item ::boon-builder-item
    :spec ::homebrew-boon :plugin-key ::e5/boons :default {}
    :route-kw dnd-e5-boon-builder-page-route :view :boon-builder-page
    :catalog-type :pact-boon}
   ;; ...one entry per type
   ])

(doseq [ct content-types]    ; events.cljs, db.cljs, subs, core/pages, route_map
  (register-content! ct))    ; calls the existing factories
```

Constraints found: keep the `(def …-route :kw)` lines (referenced by symbol at
compile time); the registry namespace must stay a dependency leaf to avoid the
circular-dep the code already works around (`events.cljs` ~204), so views are
referenced by keyword and resolved in `core.cljs`.

### Layer 2 — type-addressed option catalogs + grants

Generalize the subrace "bucket-by-key" pattern from `:race` to option **type**, so
producers and consumers never name each other.

```clojure
;; producer: an option declares WHAT it is, not where it attaches
{:id :my-boon :type :pact-boon ...}

;; one read API for "all options of a type" (built-ins + plugins + homebrew)
(catalog :pact-boon)

;; consumer: grant a choice from a catalog, optionally filtered
(grant-choice :pact-boon :n 1)
(grant-choice :spell :n 1 :filter cantrip?)   ; "choose a cantrip"
```

Why not fixed parent slots: in 5e a pact boon can be granted by the warlock *and* by
a feat, and homebrew adds more later. Addressing by parent location forces multiple
attachment declarations; addressing by type does not. Keep the existing modifier
system (`mod5e/*`) for granting a *specific known* thing (e.g. "grants Fire Bolt");
catalogs/grants are only for "choose from a whole set."

The result: a homebrew cantrip flows into every "choose a cantrip" grant for free,
and a feat that grants boons reuses the same `grant-choice :pact-boon` — neither
needs the producing module to change.

## Suggested next step

A behavior-preserving spike: add the generic catalog injector and migrate
**subraces** onto it first (they already work this way), review the diff, then
migrate boons/invocations, then add new capability (lineages) the easy way.

## Related

- [spa-routing-architecture.md](spa-routing-architecture.md) — the route-registration
  side of Layer 1 (route_map / index-page-paths / core.cljs pages).
- [entity-options-architecture.md](entity-options-architecture.md) — the
  entity/option/selection model that `grant-choice` would build on.
- [srd-vs-plugin-content.md](srd-vs-plugin-content.md) — what is hardcoded SRD vs
  plugin-supplied, which determines what each catalog contains.
- [content-extensibility-decisions.md](content-extensibility-decisions.md) — the
  decisions behind this direction and the options rejected.
- [content-extensibility-compatibility.md](content-extensibility-compatibility.md) —
  backward-compat audit: persisted formats, invariants, and how the design must stay
  additive for existing orcbrew libraries and saved characters.
