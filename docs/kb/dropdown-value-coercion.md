# Dropdown value coercion — the `<select>` string footgun and the `:typed?` fix

## The discrepancy (what bit us)

A homebrew race authored through the race-builder's floating-ASI widget persisted **broken data**:

```clojure
;; what the form saved                        ;; what the data model needs
{:ability "cha", :amount 1}                   {:ability :orcpub.dnd.e5.character/cha, :amount 2}
{:select {:from "martial", :amount "1"}}      {:select {:from :martial, :amount 1}}
```

Bare strings where keywords/ints belong. Downstream this is silent corruption that only blows up
later: `compile-ability-increases` hands `"cha"` to `mod5e/race-ability` (expects a keyword), and
`(ability-groups "martial")` returns `nil` (the map is keyed by `:martial`) → an **empty** choice
list. No exception at author time; the race just quietly does nothing.

## Root cause (general, not specific to ASI)

An HTML `<select>`'s value is **always a string**. The `dropdown` primitive
(`views.cljs`) rendered each option's `value` from the item's typed `:value`, but on `change` it
read `.target.value` back as a **string** and handed *that* to the caller's `:on-change`:

```clojure
:on-change #(on-change (event-value %))   ; event-value = (.. e -target -value) -> a String
```

reagent renders a keyword option value via `name`, so `::character/cha` round-trips out as the
bare string `"cha"` (namespace dropped). **Every** caller therefore had to manually re-hydrate the
type, and forgetting was undetectable by source review or the JVM/harness tests (those dispatch
events with already-correct values). Only a browser driving the real `<select>` exercises this
layer — which is exactly how the bug surfaced (`test/e2e/race-builder-asi.js`).

## This was a latent, repo-wide hazard (evidence)

Census of `dropdown`/`labeled-dropdown` `:on-change` handlers in `views.cljs` (~70 sites):

- The overwhelming majority manually coerce: `(keyword %)`, `(js/parseInt %)`, `(js/parseFloat %)`.
  Each is a place the coercion can be omitted (as it was in ASI).
- **`views.cljs:5781`** (class spellcasting ability) is the *same* qualified-keyword case as the ASI
  bug, surviving only because someone wrote `(keyword "orcpub.dnd.e5.character" %)` — with the
  namespace hardcoded. Fragile, and the single most likely next instance of this bug.
- **`views.cljs:6580`** (the `:enum` builder field) had *already* solved it the robust way, inline,
  with a comment: *"index-based option values so ANY value type (incl. qualified keywords)
  round-trips through the string-only `<select>`."* The right technique existed in the codebase —
  it had just never been lifted into the `dropdown` primitive.

## The fix — `:typed?` (the template that makes the mistake impossible)

`dropdown` now takes `:typed? true`. It generalizes the `:enum` field's index-round-trip into the
primitive: option `value`s become **indices**, and `:on-change` is handed the selected item's
original `:value` — any type, including `nil` and qualified keywords — round-tripped automatically.
Callers do **no** coercion:

```clojure
;; before (each caller re-hydrates, easy to forget):
[labeled-dropdown "Ability" {:items ability-items :value (:ability a)
                             :on-change #(set-ai! (assoc-in ais [i :ability] (ability-by-str %)))}]
;; after (:typed? — on-change receives the real keyword):
[labeled-dropdown "Ability" {:items ability-items :value (:ability a) :typed? true
                             :on-change #(set-ai! (assoc-in ais [i :ability] %))}]
```

The default (no `:typed?`) is unchanged string passthrough — so this is **backward compatible**;
the ~70 existing call sites are untouched. The floating-ASI widget is migrated to `:typed?` and its
manual lookup maps are deleted; `test/e2e/race-builder-asi.js` still passes (the template produces
the correct types end to end, with zero coercion in the widget).

## Guard / convergence rule

- **New dropdowns whose `:value`s are not already strings → use `:typed? true`.** Then there is
  nothing to coerce and nothing to forget. Strings-only dropdowns (e.g. `:size` stored as a name)
  may stay on the default path.
- **Convergence target (not yet done):** migrate the existing coercing call sites to `:typed?`,
  starting with the qualified-keyword ones (`views.cljs:5781`) and folding the bespoke `:enum`
  index-round-trip (`views.cljs:6580`) onto the primitive. Per the prototype-then-converge rule
  (D23), that cleanup follows this decision rather than preceding it; it is a marked follow-up, not
  a blocker.

See also: `docs/kb/cljs-headless-harness.md` (the E2E that caught this and the other interaction
gotchas) and the decision log entry **D32**.
