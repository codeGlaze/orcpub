# Cross-Link Map: current injection sites → catalog/grant shape

**Date:** 2026-06-13
**Source quality:** High — mapped from direct code inspection. Symbols and line
numbers verified this session; line numbers drift, grep the symbol to confirm.
**Part of:** the Content Extensibility initiative — design, handoff, and decisions
live in [`docs/extensibility/`](../extensibility/README.md). This is the verified-findings
half; per KB rules it cites code directly and keeps forward-looking design out.

This document inventories the existing places where one content aspect injects into
another, characterizes *how* each is wired today, and shows what it becomes under the
proposed type-addressed catalog/grant model ([TARGET_ARCHITECTURE.md](TARGET_ARCHITECTURE.md)).

The pattern to notice: exactly **one** of these (subraces) is already done the "right"
way (bucket-by-parent-key, merged in a sub, parent definitions untouched). The others
are either hand-threaded positional arguments or fully static lists.

## Legend

- **Bucket-by-key** = grouped by a parent key and merged in a subscription; adding a
  child requires **no** edit to the parent. (The target shape.)
- **Positional thread** = the parent function and its driving subscription gained a
  positional argument for this child set; fragile, order-sensitive.
- **Static list** = hardcoded options; not a plugin extension point at all.

---

## 1. subraces → races  ✅ already the target shape

| | |
|---|---|
| Today | **Bucket-by-key.** `::races5e/plugin-subraces-map` = `(group-by :race plugin-subraces)` (`spell_subs.cljs:887`); `::races5e/races` merges `(subraces-map (:key race))` into each race (`spell_subs.cljs:893–925`). |
| Producer declares | `:race <parent-key>` on the subrace. |
| Parent edits to add a subrace | **None.** |
| Catalog/grant form | `:type :subrace` in its catalog; race declares `grant-choice :subrace :filter (for-this-race)`. Essentially already this; migration is mostly renaming the bucket key from `:race` to a generic `:type` + filter. |
| Migration risk | **Lowest** — behavior-preserving. This is the recommended first spike. |

## 2. subclasses → classes  ✅ bucket-by-key (parallel to subraces)

| | |
|---|---|
| Today | **Bucket-by-key.** `::classes5e/plugin-subclasses-map` = `(group-by :class plugin-subclasses)` (`spell_subs.cljs:893`); subclasses carry `:key` and emit `opt5e/plugin-modifiers` (`spell_subs.cljs:440`). |
| Producer declares | `:class <parent-key>` on the subclass. |
| Parent edits to add a subclass | **None.** |
| Catalog/grant form | `:type :subclass`; class declares `grant-choice :subclass :filter (for-this-class)`. |
| Migration risk | Low — same shape as subraces. |

## 3. boons → warlock  ⚠️ positional thread (the cautionary tale)

| | |
|---|---|
| Today | **Positional thread.** `warlock-option` takes `boons` as its 8th positional arg (`classes.cljc:2987`) and passes it to `pact-boon-options` (`classes.cljc:2629`, call at `:3039`). `boons` is also threaded through `base-class-options` and inserted into the 8-input `::classes5e/classes` subscription (`spell_subs.cljs:945`, input `::classes5e/boons` at `:953`) in the exact vector position. |
| Producer declares | a homebrew boon with `::homebrew-boon` spec (`classes.cljc:28`); save via `reg-save-homebrew` into `::e5/boons`. |
| Parent edits to add the boon feature | **Many** — signature of `warlock-option`, signature of `base-class-options`, and the subscription's input list + destructuring vector. Wrong position = silent mis-binding. |
| Catalog/grant form | `:type :pact-boon`; warlock declares `grant-choice :pact-boon :n 1` at level 3. **No** positional args, **no** subscription edits. A feat granting a pact boon uses the *same* `grant-choice :pact-boon` — impossible today without re-threading. |
| Migration risk | Medium — touches the class-options subscription; do after subraces proves the pattern. |

## 4. invocations → warlock  ⚠️ positional thread

| | |
|---|---|
| Today | **Positional thread**, identical in shape to boons. `invocations` is a positional arg threaded through `base-class-options` → `warlock-option` and an input of `::classes5e/classes`. Derived subs `::classes5e/plugin-invocations` / `::classes5e/invocations` exist (`spell_subs.cljs` ~:430–950). Spec `::homebrew-invocation` (`classes.cljc:26`). |
| Catalog/grant form | `:type :invocation`; warlock declares `grant-choice :invocation` at the appropriate levels. Same collapse as boons. |
| Migration risk | Medium — migrate alongside boons (same subscription). |

## 5. draconic ancestries → dragonborn  ⛔ static list (not even an extension point)

| | |
|---|---|
| Today | **Static list.** `draconic-ancestries` is a plain `def` of a fixed vector (`options.cljc:3428`); `dragonborn-option-cfg` is a plain `def` (not a function) whose "Draconic Ancestry" selection maps over that static list (`spell_subs.cljs:759–789`). There is **no** plugin path — homebrew cannot add an ancestry today. |
| Producer declares | nothing — there is no homebrew ancestry/lineage type yet. |
| Catalog/grant form | introduce `:type :draconic-ancestry` (or `:lineage`); `dragonborn-option-cfg` declares `grant-choice :draconic-ancestry`. Requires converting `dragonborn-option-cfg` from a `def` to a function (or having it read the catalog sub), and deciding where the domain model/spec lives (no `lineages` namespace exists). |
| Migration risk | Higher — this is *new capability*, not a refactor; also surfaces the "no home for the spec" problem. This is the "dragonborn lineage builder" the conversation used as the hard example. |

## 6. spells → classes (spell lists)  ⚙️ context thread (different flavor)

| | |
|---|---|
| Today | `spell-lists` and `spells-map` are threaded as positional args into **every** class option builder (`barbarian-option`, `bard-option`, … in `base-class-options`, `spell_subs.cljs:932`). |
| Catalog/grant form | These are better modeled as ambient build **context** (the `ctx` map in TARGET_ARCHITECTURE Layer 2) rather than a per-aspect grant, since nearly every class consumes them. Folding `spell-lists`/`spells-map`/`language-map`/`weapons-map` into a single `ctx` map removes most of the positional-arg width that made adding boons/invocations painful. |
| Migration risk | Medium-high — wide but mechanical; the `ctx` refactor is what makes the subscription stop growing an argument per feature. |

---

## Summary table

| Cross-link | Today | Target | First-spike order |
|------------|-------|--------|-------------------|
| subraces → races | bucket-by-key ✅ | catalog/grant (rename key→type) | **1 (proves pattern)** |
| subclasses → classes | bucket-by-key ✅ | catalog/grant | 2 |
| boons → warlock | positional ⚠️ | `grant-choice :pact-boon` | 3 |
| invocations → warlock | positional ⚠️ | `grant-choice :invocation` | 3 (same sub) |
| spells → classes | context thread ⚙️ | ambient `ctx` map | 4 (enables the rest) |
| ancestries/lineage → dragonborn | static ⛔ | `grant-choice :draconic-ancestry` | 5 (new capability) |

## The headline finding

The app **already contains** the target pattern (subraces, subclasses). The pain
points (boons, invocations) and the impossible-today case (homebrew lineages) are
exactly the cross-links that *didn't* use it. Layer 2 is therefore not inventing a
new idea — it is generalizing an existing, working one and retiring the positional
and static variants.
