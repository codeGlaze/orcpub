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

## This bug class has bitten this branch TWICE (provenance, git-verified)

This is **not** a newly-discovered hazard in old code — it is a recurring mistake *introduced on this
branch*, because the first fix was never written down anywhere durable. Established with `git blame`
and the merge-base (`980cc790`):

- **First occurrence — dragonborn breath weapon (this branch).** The `:enum` builder field's
  index-round-trip (`views.cljs:~6595`, commit `f32790b1`, 2026-06-15, **not** in the merge-base) was
  written *by a prior session on this branch* to fix exactly this — its own comment says it stores
  *"the keyword, not the dropdown's raw string — the bug that shipped a broken breath weapon."* So the
  technique and the lesson already existed **in a code comment**, and nowhere else.
- **Second occurrence — floating ASI (this branch).** Because that fix lived only in a comment on one
  field, the ASI widget repeated the identical mistake, and when the old fix was found it was at first
  mis-described as someone else's pre-existing solution. It was this session's own prior work. **This
  KB note + D32 exist so the lesson is recorded once, not re-derived a third time.**

The wider population of `dropdown` `:on-change` handlers (~70 in `views.cljs`) that coerce by hand
(`(keyword %)` / `(js/parseInt %)` / `(js/parseFloat %)`) are **not** presumed-broken — most are
upstream code that has shipped for years. They show the *pattern* is per-caller and easy to forget,
not that they are bugs. In particular:

- **`views.cljs:5801`** (class spellcasting ability, `(keyword "orcpub.dnd.e5.character" %)`) — **verified
  NOT a bug.** `git blame`: Larry, 2017, and present in the merge-base, so ~8 years live in production.
  `(keyword "orcpub.dnd.e5.character" "cha")` = `::character/cha` — correct. It is the same *shape* as the
  ASI case but it coerces correctly; the only critique is stylistic (the namespace is hardcoded). It is an
  **optional** `:typed?` cleanup, not a fix.

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

## Numbers already have a typed input — `number-field`

For free numeric entry there is no string-coercion problem to begin with: `number-field`
(`views.cljs:3954`) is an `<input type=number>` that parses internally and calls `:on-change` with a
real int (or `nil` for empty/non-numeric) — the caller never sees a string. It's already used in the
monster/magic-item builders. So: free numeric entry → `number-field` (typed for free); a constrained
numeric *choice* (a fixed small set, e.g. ASI `+1/+2/+3`) → a `:typed?` dropdown (an `<input number>`
can't constrain to a set). There is no native "numeric `<select>`" — a `<select>` is string-valued by
the DOM spec, which is the whole reason `:typed?` has to do the index-round-trip.

## Guard / convergence rule

- **New dropdowns whose `:value`s are not already strings → use `:typed? true`** (or `number-field`
  for free numeric entry). Then there is nothing to coerce and nothing to forget. Strings-only
  dropdowns (e.g. `:size` stored as a name) may stay on the default path.
- **The existing ~70 coercing call sites are NOT a bug list** — most are upstream and correct
  (e.g. `views.cljs:5801`, verified above). Migrating them to `:typed?` is optional readability
  cleanup that removes per-caller coercion (and would let the bespoke `:enum` round-trip,
  `views.cljs:~6595`, fold onto the primitive). Per the prototype-then-converge rule (D23) it follows
  this decision rather than preceding it; it is a marked follow-up, not a blocker.

See also: `docs/kb/cljs-headless-harness.md` (the E2E that caught this and the other interaction
gotchas) and the decision log entry **D32**.
