# Content Extensibility — CURRENT DIRECTION (read this first)

**Status (v2, re-centered).** This supersedes both the original "sweeping registry +
catalog/grant DSL" framing AND the over-deflated "just collapse boilerplate, don't build
catalogs" framing that briefly replaced it. The truth is in the middle and is the spine of
this branch. Treat `content-extensibility.md` / `-plan.md` as history.

## Why the re-centering (don't misread the deflation)

A readability review correctly killed one *unreadable wrapper* (`by-parent`, which hid
`group-by`). That lesson is real but **local**: it's about how plumbing reads, not about
how capable the content model is. It was then over-applied — the *capability* of letting
content tap content across silos got deflated along with the bad wrapper. That was the
mistake. The capability is the whole point of the branch.

**The two reasons this rewrite exists (equal weight):**
1. **Stability.**
2. **Flexibility.**

The key insight that makes both achievable at once: **they are the same abstraction.**
Today every cross-link between content types is *bespoke positional wiring* (boons threaded
into the warlock by argument; the custom-race menu a hardcoded vector; fighting styles baked
per class). Bespoke wiring is exactly why adding anything touches ~8 files and is fragile —
that's the instability. Replace all of it with **one declarative primitive** and N×M bespoke
wirings collapse to N+M declarations down **one tested path**: that single move is the
stability win *and* the flexibility win. They do not trade off here.

## The one principle (a constraint, not a ceiling)

> **An abstraction earns its keep only when it is *thicker* than what it hides AND its
> interface reveals intent.** Build from the engine's existing thick parts; keep vocabulary
> minimal; keep call sites intent-revealing. `by-parent` failed this (thinner than
> `group-by`). A pool/grant that adds openness + cross-silo reuse + filtering over a
> hardcoded vector *passes* it (thicker, and the call site says what it does).

This forbids a cryptic DSL. It does **not** forbid the capability — it tells us how to
implement it readably.

## The engine ALREADY supports mix-and-match. The gap is the AUTHORING layer.

Verified in `template.cljc`:
- `selection-cfg` carries `prereq-fn`, `tags`, `ref`, `different?`, `min`/`max` — a choice
  can already be **filtered** (tags), **gated** (prereq-fn), and **cross-referenced** (ref).
- `option-prereq [explanation func hide-if-fail?]` — **prerequisites already exist** as
  evaluated fns with user-facing explanations.
- `option-cfg` carries `prereqs`, nested `selections`, `modifiers`, `associated-options`.
- `options.cljc:225 ability-increase-selection-2` — deferred "choose N ability increases
  from a set" (the Tasha's floating-ASI mechanism) already exists and is general.

So the runtime composition power is present. What's missing is that **content cannot
*declare* these cross-links as open data** — they're hand-wired in source, and homebrew
authors can only fill fixed slots. This branch closes the authoring gap; it does not rebuild
the engine.

## The spine: two words — POOL and GRANT

- **Pool** — a named, open, type-addressed collection of grantable things: `:spell`,
  `:feat`, `:fighting-style`, `:invocation`, `:boon`, `:draconic-ancestry`, `:ability`,
  `:speed`, and class-parented ones like `[:class-feature :warlock]`. "Built here."
  - Built-ins register their pools; **a homebrew pack adds entries to a pool, or registers a
    new pool.** That openness is the capability that's missing today (the custom-race menu is
    a closed source-level vector).
  - Pools **derive over the existing plugin storage** (memoized `reg-sub`s, D11) — never
    reformat it (D9). Display vs identity kept separate; stable keys passed through (D10).
- **Grant** — what any content item declares to tap a pool. ONE primitive, three faces
  (e.g. a background granting feats):
  - `grant {:pool :feat :key :lucky}` — fixed ("this feat"). Equivalent to a modifier (D4).
  - `grant {:pool :feat :filter … :count 1}` — choose from a filtered list.
  - `grant {:pool :feat :count 1}` — choose from the whole (open) pool.
  - A choice grant compiles to a `selection-cfg` (options = filtered pool entries carrying
    their own prereqs; min/max = count; tags; prereq-fn = the gate). "Called on over there"
    = any (sub)class / (sub)race / feat / background references a pool by key.

**Filtering is optional and graceful (your rule):** a filter is a predicate over *present*
metadata. Absent metadata → predicate is false → entry simply isn't offered by that filtered
grant; it is **never an error**, and the entry still lives in the unfiltered pool. Tagging
feats `:martial` / `:no-prereq` etc. is a useful add, not a required schema.

**Blank-slate parametric grants** are just built-in pools + parametric modifiers, same
primitive: "+N ASI to X" (`ability-increase-selection-2`, already there), "+N to
swim/climb/move/etc." (parametric over the existing speed/move modifiers).

## Variants — designed in NOW, built LATER (a real pin with a real constraint)

A variant (`_copy` + `_mod` delta — the 5etools shape) is a content item that says "I am
like base B, with these modifications." We do **not** build resolution now, but we MUST not
paint ourselves into a corner. The forward-compat guarantee is one decision:

```
raw :plugins → [resolve-variants] → resolved-content → pools (subs) → grants
```

`resolve-variants` is **identity today** (no `_copy` keys exist). The binding rule:

> **Every pool derives from one `resolved-content` indirection — never from raw `:plugins`
> directly.**

Hold that, and adding variants later is inserting one transform at one seam; **pools and
grants never change** (no refactor of the new work). Variants reference base by **stable
key**, not name (D10). This is the whole cost of "build the idea in now."

## Sequencing — flat pools before rich pools

- **Flat pools first** (a list of self-contained items): `:spell`, `:feat`,
  `:fighting-style`, `:invocation`, `:boon`, `:draconic-ancestry`. Straightforward.
- **Class-feature-as-grantable** (`[:class-feature :warlock]`) is *richer* — features are
  level/context-bound, not flat. Later phase. Honest sequencing, not a dodge.

## Next steps (goal: STABILIZE while adding features)

1. ✅ DONE (`9777ce88`) — reverted `by-parent`/`plugin-options`, deleted `option_catalog`.
2. ✅ DONE (`3980ea1b`) — `register-homebrew-content!` HOF (the **wiring** sub-layer:
   save/delete/edit/new + set/set-prop/reset from one descriptor); boon swapped through it
   (7 scattered sites → 1), falsifiable handler tests added. Harness-verified.
3. **NEXT — prove the POOL/GRANT spine on one slice, end-to-end:**
   - Introduce the `resolved-content` indirection (identity passthrough today) + the
     `pool` read (a memoized sub deriving over resolved content) + the `grant` primitive
     (fixed | choice → `selection-cfg`).
   - Prove it by routing **one existing closed cross-link** through it *behavior-identically*
     first (candidate: the custom-race menu, or an existing flat list), gated by the golden +
     `.orcbrew` fixture tests — nothing about a built character may change.
   - Then add **one new open capability** on the same primitive (candidate: `:draconic-ancestry`
     as a pool dragonborn grants from, that a pack can extend with new colors). This is the
     real test of openness.
4. Keep vocabulary to **pool/grant**; build from existing `selection-cfg`/`prereq-fn`/
   `modifiers`; intent-revealing call sites. No cryptic DSL.

### PINS (designed-in-now, built-later — do not let these get refactored away)
- **Variants** (`_copy` + `_mod`): the `resolved-content` indirection above is the only thing
  required now. Build `resolve-variants` later; pools/grants stay untouched.
- **New skills** (creating a brand-new skill, not granting one): adds to the skill registry
  itself — different shape. Defer.
- **Class-feature pool** (`[:class-feature :X]`): richer than flat pools; later phase.
- **Declarative cross-type prereq vocabulary** (`has-class?`, `level>=`, `has-feature?`,
  `ability>=`): homebrew-authored prereqs must NOT be raw fns (security/stability). The engine
  evaluates prereqs already; the small declarative vocabulary is the new part. Build when the
  first cross-type gate is needed.

## What already stands (don't redo)
- `register-homebrew-content!` (the wiring sub-layer) + boon swapped through it.
- Phase 4b passthrough-subs loop; the `content_types` registry (data + audit test).
- Import-validation fixes + `save-character` crash fix.
- The cljs harness, compat invariants, verification-discipline lessons.

## Deferred — own branch (surface at branch close)
- Character-validation contract (`character-validation.md`).
- ClojureScript tests into CI (the harness here is the prototype).
