# Target Architecture: Registry + Catalogs/Grants

**Status:** Proposed design. Not yet implemented. See [DECISIONS.md](DECISIONS.md).

This document describes the proposed end-state for adding content to the 5e app. It
is split into two independent, composable layers. Pseudocode is deliberately
simplified Clojure-ish — it shows *intent*, not final signatures.

---

## The problem, precisely

Adding a content type (builder + homebrew option) today costs ~8 file touches. That
cost decomposes into two unrelated problems:

1. **Registration** — the route, db default, localStorage plumbing, events, subs,
   and page-map entry, all keyed by the same entity, scattered across files.
2. **Injection** — wiring the new option set *into a parent entity* (boons into the
   warlock, lineages into dragonborn), currently done with fragile positional
   arguments.

Layer 1 solves (1). Layer 2 solves (2). They are useful independently and better
together.

---

## Layer 1 — Data-driven content-type registry

### Intent

Replace scattered, parallel registration call-sites with a single descriptor list
consumed by loops. The loops call the **factory functions that already exist**
(`reg-save-homebrew`, `reg-new-homebrew`, `reg-edit-homebrew`, `reg-delete-homebrew`,
`reg-local-store-cofx`); this is not new machinery, it's moving call-sites into data.

### Shape

```clojure
;; ONE source of truth — appended to when a content type is added.
(def content-types
  [{:id :boon
    :name "Pact Boon"
    :builder-item ::boon-builder-item
    :spec ::homebrew-boon
    :plugin-key ::e5/boons
    :default {}
    :route-kw dnd-e5-boon-builder-page-route
    :route-seg "boon-builder"
    :view :boon-builder-page
    :catalog-type :pact-boon}        ; ties Layer 1 to Layer 2 (see below)
   ;; ... spell, monster, race, subrace, feat, lineage, ...
   ])

;; events.cljs — loop instead of N copy-pasted blocks per type
(doseq [ct content-types]
  (reg-save-homebrew   ct)   ;; existing factory
  (reg-new-homebrew    ct)   ;; existing factory
  (reg-edit-homebrew   ct)   ;; existing factory
  (reg-set-event       ct)   ;; tiny new helper
  (reg-reset-event     ct)   ;; tiny new helper
  (reg-local-store-cofx-for ct))

;; subs — the ~13 near-identical passthrough subs collapse to one loop
(doseq [{:keys [builder-item]} content-types]
  (reg-sub builder-item (fn [db _] (get db builder-item))))

;; core.cljs — pages map built from the registry (view resolved by keyword)
(def pages (into base-pages
                 (for [{:keys [route-kw view]} content-types]
                   [route-kw (resolve-view view)])))

;; route_map.cljc — bidi entries + route-set membership derived from the registry
(def builder-routes
  (into {} (for [{:keys [route-seg route-kw]} content-types]
             [route-seg route-kw])))
```

### Constraints discovered

- **Keep the `(def …-route :kw)` lines in `route_map.cljc`.** Other code references
  those route keywords by symbol at compile time; generating vars is more trouble
  than the one line it saves. Generate everything *downstream* of them (bidi tree,
  route sets, pages map, events, subs, db).
- **The registry namespace must be a dependency leaf.** It can require only spec
  namespaces and `route-map`, never `events`/`subs`/`views`, or it reintroduces the
  circular dependency the code already works around (see the `event-utils`
  delegation note at `events.cljs:204`). That is why `:view` is a keyword resolved
  in `core.cljs` (which already depends on `views`), not a function stored in the
  registry.

### Result

Adding a content type → **append one descriptor**, write the builder form, write the
spec. The registration half (5–6 files) collapses to one list entry. It becomes
impossible to half-wire a type.

---

## Layer 2 — Type-addressed option catalogs + grants

### The two kinds of "aspect A taps aspect B"

| Kind | Example | Mechanism |
|------|---------|-----------|
| **A — grant a fixed, known thing** | "this feat grants Fire Bolt" | **Existing modifier system** (`mod5e/spells-known`, `mod5e/damage-resistance`, …). Late-binding, decoupled. **Keep as-is.** |
| **B — grant a choice from B's whole set** | "choose any cantrip"; "a feat of your choice" | **New:** catalog + grant. This is the expansion/homebrew-friendly case. |

### Why NOT parent-keyed slots

The first framing addressed a child option by its parent location, e.g. a boon at
`[:class :warlock :pact-boon]`. This breaks under 5e reality: a pact boon may be
grantable by the warlock *and* by a feat *and* by future homebrew. Fixed attachment
points multiply combinatorially. Rejected — see [DECISIONS.md](DECISIONS.md) D3.

### The model: producers write a catalog, consumers read it

```clojure
;; 1. Every option declares WHAT IT IS, not where it attaches.
{:id :my-homebrew-boon  :type :pact-boon  ...}
{:id :fire-bolt         :type :spell  :level 0  ...}

;; 2. One uniform read API: "everything of type X" (built-ins + plugins + homebrew).
(defn catalog [type]
  (options-of-type type))

;; 3. A consumer GRANTS a choice from a catalog, optionally filtered.
;;    Sugar over the existing selection machinery.
(defn grant-choice [type & {:keys [n filter]}]
  (selection-cfg {:options (cond->> (catalog type) filter (clojure.core/filter filter))
                  :min n :max n}))
```

### The same shape for every cross-aspect grant

```clojure
(grant-choice :feat  :n 1)                              ; background grants a feat
(grant-choice :spell :n 1 :filter #(= 0 (:level %)))    ; feat grants a cantrip
(grant-choice :pact-boon :n 1)                          ; feat grants a pact boon
(mod5e/ability ::char5e/str 1)                          ; fixed ASI -> stays Kind A
(grant-choice :magic-item :n 1)                         ; house rule: background grants an item
```

A new producer (homebrew cantrip) needs **zero** consumer edits. A new consumer
(feat that grants boons) needs **zero** producer edits. The magic-item module never
learns about backgrounds.

### This generalizes the one extension point already done right

Subraces already work this way — bucketed by parent key, merged in a subscription,
with **no edits to race definitions**:

```clojure
;; TODAY (subraces) — the pattern we want everywhere
(reg-sub ::plugin-subraces-map :<- [::plugin-subraces]
  (fn [subraces] (group-by :race subraces)))

(reg-sub ::races :<- [::plugin-subraces-map]
  (fn [[by-race]]
    (for [race all-races]
      (update race :subraces concat (by-race (:key race))))))
```

Layer 2 is "do this, but bucket by `:type` instead of `:race`, and let consumers
pull via `grant-choice` rather than each parent open-coding the merge."

### The one genuinely new concept: filters/prerequisites

`grant-choice` needs a predicate to express "cantrips only," "feats you qualify for,"
"fire-themed lineages." This is where real design care goes, and it is also what
makes the catalog faithful to 5e's actual rules. A filter of `identity` means "any."

### Compatibility

- Complements, does not replace, the modifier engine. Kind A stays `mod5e/*`; Kind B
  becomes `grant-choice`. Both feed the same entity build.
- Catalogs are just queryable data sets; grants resolve during the existing staged
  entity build, which already handles nested selections.

---

## How the two layers compose

| Cost when adding "dragonborn lineage" | Today | With both layers |
|---|---|---|
| Register the type (route/db/events/subs/core) | edit 5–6 files | append 1 descriptor |
| Inject into its parent | edit parent fn signature + `base-*-options` + subscription vector (positional, fragile) | already handled via `:catalog-type` + grant; parent declares a `grant-choice` |
| Genuinely new work | spec + builder form + breath-weapon modifiers | spec + builder form + breath-weapon modifiers (**unchanged — irreducible**) |

The `:catalog-type` field on each registry descriptor (Layer 1) is what lets a
producer auto-register into the right catalog (Layer 2), so the two layers meet at
exactly one field.
