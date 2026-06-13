# Handoff: Content Extensibility / "8-file problem"

**Date:** 2026-06-13
**Status:** Design discussion complete; no code changed. Awaiting go-ahead on the first spike.
**Branch this was captured on:** `claude/zen-wright-04xhdz`

## Why this conversation happened

Adding a relatively minor interaction to the app requires touching 5–8 files. The
prompting example was "add a new plugin/builder option," for which a minimum of 8
files were traced:

```
routes.clj          route_map.cljc      db.cljs        events.cljs
spell_subs.cljs     views.cljs          core.cljs      classes.cljc (spec/def home)
```

The question: is there a less error-prone, more maintainable way to handle the
wiring / routing / subscribing, without destroying the standardization wins the
codebase already has? The ask was for **a plan (or plans), not immediate action**.

## What we learned by looking at the actual code

### The "8 files" is real — verified against the Pact Boon builder

Commit `6029fd0` ("Pact Boon Builder for Warlocks") touched **10 files, +144/-8**.
Splitting that diff by *kind* of change was the key insight:

- **Mechanical half (pure registration, keyed by "boon"):** `route_map.cljc` (+3),
  `routes.clj` (+1), `db.cljs` (+8), `core.cljs` (+1), most of `events.cljs` (+48),
  and the `::boon-builder-item` passthrough sub in `spell_subs.cljs`.
- **Real half (weaving boons into the warlock):** `classes.cljc` (the `pact-boon-options`
  rewrite + adding a positional `boons` arg to `warlock-option`), `spell_subs.cljs`
  (new derived subs + threading `boons` through `base-class-options` and into the
  8-input `::classes5e/classes` subscription in the exactly-right vector position),
  and `views.cljs` (the `boon-builder` form, `my-boons` card, menu entry — genuine UI).

### The codebase has already done a lot of the right abstraction

Factory functions already collapse families of registrations: `reg-save-homebrew`
(events.cljs:533), `reg-new-homebrew` (events.cljs:4238), `reg-edit-homebrew`
(events.cljs:2080), `reg-delete-homebrew` (events.cljs:719), `reg-local-store-cofx`
(db.cljs:252), and the `builder-page` view helper (views.cljs:8026). Each content
type is *already* reduced to a small descriptor — it's just expressed as repeated
call sites in 8 files instead of one record in one place.

### The expensive part is injection, not registration

The boon's danger wasn't registering the type — it was that the warlock's option
pipeline hardcodes its child-option sources as **positional function arguments**
(`warlock-option` now takes 8 positional args; `::classes5e/classes` takes 8
subscription inputs). Add a child type → edit the parent's signature → edit the
subscription's binding vector, in the right slot, or it silently binds wrong.

### Dragonborn lineage would be the same shape, slightly worse

A "custom dragonborn lineage" is a child of the Draconic Ancestry selection inside
`dragonborn-option-cfg`. Two extra frictions: (1) `dragonborn-option-cfg` is a plain
`def`, not a function (spell_subs.cljs:759), so it would have to be converted to a
function to accept plugin lineages; (2) there's no natural home for the domain model
(dragonborn lives in `spell_subs.cljs`, ancestries are a static `def` in
`options.cljc:3428`). And ancestries aren't even a plugin extension point today.

## The reframe (where we landed)

There are **two** problems, not one:

1. **Registration** of a standalone content type → solved by **Layer 1**, a
   data-driven content-type registry built on the *existing* factories.
2. **Injection** of plugin-contributed options into a parent entity → solved by
   **Layer 2**, generalizing the one extension point already done right (subraces).

### The cross-aspect caveat that refined Layer 2

5e/5.5e is built around expansion: backgrounds can grant feats/spells/items, feats
can grant spells/ASIs/proficiencies/class-features (even pact boons). So one aspect
must be able to tap another aspect's options — and homebrew added later should flow
in automatically.

This exposed a flaw in the first framing of Layer 2. **Rigid parent-keyed slots**
(e.g. `[:class :warlock :pact-boon]`) would make cross-tapping *harder*, because a
boon grantable by both the warlock and a feat would need multiple attachment
declarations — combinatorial. The fix:

- Distinguish **Kind A** ("grant a fixed, known thing," e.g. Fire Bolt) — already
  handled well by the modifier system (`mod5e/*`), keep it untouched — from
  **Kind B** ("grant a choice from another aspect's whole option-set").
- Model Layer 2 as **type-addressed option catalogs + grants**, not parent-keyed
  slots: every option lands in a catalog by its *type*; every consumer declares a
  *grant* that pulls from a catalog (with an optional filter). Producers and
  consumers never name each other. Cross-linking drops from O(producers × consumers)
  bespoke positional wiring to O(producers + consumers), and homebrew flows in for
  free.

See [TARGET_ARCHITECTURE.md](TARGET_ARCHITECTURE.md) for the full model + pseudocode,
and [the cross-link map](../kb/content-extensibility-cross-links.md) for how today's cross-links map onto it.

## Current plan

- **Layer 1 — content-type registry:** one descriptor list as the single source of
  truth; route_map / core / db / events / subs registrations become loops over it
  that call the existing factories. Adding a type → append one descriptor + write
  the builder form + write the spec.
- **Layer 2 — catalogs + grants:** generalize the subrace "bucket-by-parent-key"
  pattern into "bucket-by-type" catalogs, plus a `grant-choice` helper for consumers.
  Preserve `mod5e/*` for fixed grants.

The two layers compose: with both, adding a boon or a dragonborn lineage drops from
~8 files to (1) a registry descriptor, (2) a builder form, (3) a spec, and the
genuinely irreducible domain work — with no positional/order-sensitive threading.

## Next step

A **behavior-preserving spike**: introduce the generic catalog injector alongside
the existing `plugin-subraces-map`, migrate **subraces** to it first (they already
work this way, so it proves the shape without behavior risk), then evaluate the diff
before migrating boons/invocations and before adding lineages the easy way.

Do **not** start with a broad refactor. Start with the subrace spike and review it.

## Key file references (as of 2026-06-13)

- Routes: `src/cljc/orcpub/route_map.cljc` (route kws :42–52; bidi tree :122–203;
  my-content set :71–81), `src/clj/orcpub/routes.clj` (builder allowlist ~:1318).
- DB/init: `src/cljs/orcpub/dnd/e5/db.cljs` (`default-value` :121–160; localStorage
  keys :32–49; `reg-local-store-cofx` :252).
- Events: `src/cljs/orcpub/dnd/e5/events.cljs` (factories at :533, :719, :2080, :4238;
  interceptors :103–198; set/reset events :4063+, :4147+).
- Subs: `src/cljs/orcpub/dnd/e5/spell_subs.cljs` (`plugin-subraces-map` :887;
  `::races5e/races` :893; `plugin-subclasses-map` :893; `::classes5e/classes` :945;
  `dragonborn-option-cfg` :759; builder-item subs :1284+).
- Views: `src/cljs/orcpub/dnd/e5/views.cljs` (`builder-page` :8026; builder wrappers
  :8026–8063).
- Specs/domain: `src/cljc/orcpub/dnd/e5/classes.cljc` (homebrew specs :21–28;
  `pact-boon-options` :2629; `warlock-option` :2987), `src/cljc/orcpub/dnd/e5/options.cljc`
  (`draconic-ancestries` :3428).
- Pages map: `web/cljs/orcpub/core.cljs` (:33–76).

> Line numbers drift; treat as approximate anchors, grep the named symbol to confirm.
