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

## Maintainability — the GATING requirement (easier to add tooling, not harder)

The user's hard criterion: a big retooling must make it **easier to expose more tooling, not
harder, and must not make the app unwieldy to maintain.** This is a gate, not a nice-to-have.

**Why the pattern is N+M, not N×M.** Today, exposing a grant-type (e.g. "choose a fighting
style") means editing each builder's hardcoded selection vector (`custom-race-option`,
`custom-subrace-option`, …) — O(builders) per capability. With pool/grant: a grant-type is
data; the builder's "add a grant" UI iterates the **registered pools**. Register a pool
**once** → grantable in **every** builder. So exposing a capability is O(1), and adding a
builder is O(1). "Boons shareable to feats / custom classes" falls out for free: boons are
already a pool (`::e5/boons`); a feat/class granting "choose a boon" is just that builder
offering the `:boon` pool — **no boon↔feat wiring.**

**The two disciplines that keep it from rotting into a god-function (non-negotiable):**
1. **`grant` is a thin compiler** — `{:pool :count :filter :gate}` → a `selection-cfg`, nothing
   else. Pool-*kind*-specific logic (flat pool vs class-feature pool derivation) lives in each
   **pool's own definition**, never as branches inside `grant`. A `cond` over pool kinds inside
   `grant` = the D14 god-function trap = failure.
2. **One reused grant-authoring UI component**, not a forked menu per builder.
   - Light refinement: pools carry scoping metadata (*which builders may offer me*) so a feat
     can't grant "choose a subrace." Small annotation; still N+M.

**The proof (falsifiable, not a promise) — the first slice's acceptance test:**
> After the first slice lands, exposing a **second** pool in a builder must be a ~1-line
> registration — shown in a commit. If it isn't trivially cheap, the retooling failed its own
> purpose; STOP and reassess. This is the real "measure the effort of adding a feature."

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
3. ✅ DONE (`acaa131d`) — **first pool+grant slice, on real mechanical content.**
   `draconic-ancestries` (a fixed def) → an **open pool** (`::races5e/draconic-ancestry-pool`
   = built-in colours ++ homebrew ancestries from `::e5/draconic-ancestries`). Dragonborn
   grants from it; a homebrew ancestry inherits the **full mechanics** (damage resistance +
   the breath-weapon the race's Breath Weapon attack reads), not a text stub.
   - New leaf primitive `content_pools.cljc` (`pool` / `homebrew-entries`) — the named form
     of `(mapcat (comp vals key) plugin-vals)`, reading through `::e5/plugin-vals` (the
     resolved-content seam; variant resolution slots in there later, no pool change).
   - Behavior-preserving for built-ins (10 colours, order, keys, modifiers identical).
   - `::e5/draconic-ancestries` is additive-safe — the `::plugin` spec is open, no spec change.
   - Falsifiable tests: `content_pools_test.cljc` (JVM) incl. the **maintainability proof**
     (same primitive serves a second type in one expression); `draconic_ancestry_test.cljs`
     (harness) — built-ins unchanged + homebrew ancestry appears with 2 modifiers and its key.
   - **What this slice proved vs not:** the *pool* primitive + one *live* grant end-to-end
     with mechanics + openness. It did NOT yet build the generic grant-authoring **UI** (the
     "register a pool → it appears in every builder's grant menu" claim is proven at the
     primitive level by the test, not yet in a live builder UI). That UI is the next lever.
   - **Richer ancestries** (`026f8707`): an ancestry pool entry can carry a declarative
     `:props` map (extra mechanics — speed/flying/saves/skills/languages), compiled by the
     **existing** `opt5e/plugin-modifiers` vocabulary. Built-ins unchanged.

### Validation against official expansion (Fizban's Treasury of Dragons, FTD)
Checked FTD because it officially expands draconic ancestry — a real stress-test, not theory.
It expands along **three axes**, and the pool model maps cleanly onto where each lands:
1. **More entries** (gem dragons + new damage types/shapes) → the pool handles for free.
2. **Per-ancestry extra mechanics** (resistances, flight, etc.) → an entry's `:props` map via
   `plugin-modifiers` (done). Some specific effects (e.g. telepathy/"Psionic Mind") need a new
   `:props` key added to `make-feat-modifiers` (`options.cljc:3287`) — small, bounded.
3. **Level-gated ancestry features** (Gem Flight @5, Chromatic Warding @5, Metallic Breath @5)
   AND the **3-as-separate-lineages** structure → these hit the **pins**: `:props` has no
   level-condition yet (level-gated grants need the `?total-levels` conditional pattern the
   breath-weapon dice already use), and the three variant dragonborn are the **variant/`_copy`
   lineage** pin. FTD is thus real-world confirmation that the pins are the right pins.
4. **NEXT levers** (pick per value): (a) the generic **grant-authoring UI** so authors declare
   "grant a choice from pool X" in a builder (this is where the N+M maintainability win becomes
   user-visible); (b) **cross-silo reuse demo** — point the sorcerer draconic bloodline
   (`classes.cljc:2280`, today uses the raw `draconic-ancestries` list) at the *same* pool, so
   one pool feeds two silos and homebrew colours show up in both ("built here, called over there");
   (c) a real `.orcbrew` **fixture** exercising the import path for `::e5/draconic-ancestries`.
5. Keep vocabulary to **pool/grant**; build from existing `selection-cfg`/`prereq-fn`/
   `modifiers`; intent-revealing call sites. No cryptic DSL.

### Builder FORMS are data, not "irreducible per-type work" (`109b5dd0`)
A correction worth recording: it was claimed (in conversation) that each content type needs a
bespoke builder *form* — "irreducible per-type work." The code disproved it. `boon-builder`
and `invocation-builder` were **byte-identical** forms (Name + Option Source + Description)
differing only by their `set-*-prop` event keyword; `boon-input-field`/`invocation-input-field`
were one-line wrappers differing only by that keyword. Collapsed into **`simple-content-builder`
[item-sub set-prop & [extra-fields]]** — the form is now data; the two builders are one-liners.
So a "simple" type's form costs **zero** beyond naming its sub + event; a "richer" type costs a
**field list** (`extra-fields`) + the occasional reusable custom widget. The honest cost table
for ADDING a type (the real answer to "is this easier?"):

| Layer | Mechanism | Cost |
|---|---|---|
| Events | `register-homebrew-content!` | one descriptor |
| Form | `simple-content-builder` (+ `extra-fields`) | sub+event (simple) / a field list (rich) |
| Spec | `bf/fields->spec` over the field schema (✅ draconic; optional-by-default) | one field schema |
| Route / db slot / `content_types` | from one descriptor (partly via `content_types`) | small, mechanical |
| Game-rule wiring (grant/modifiers) | pool + `:props`/`plugin-modifiers` | the genuine per-type part |

The genuinely irreducible core is small: **the field schema (data) + a reusable widget registry
for complex fields + the field→mechanics mapping** (mostly the existing `:props` vocabulary). NOT
a bespoke form per type. (Spec-from-field-schema is the next collapse — a field schema would also
generate the `s/keys` spec, shrinking the table's one remaining hand-written row.)

### Draconic-ancestry builder — DONE end-to-end (`0aca6113`)
A user can now author a draconic ancestry in-app; it stores into `::e5/draconic-ancestries`
(the pool dragonborn grants from), exports, reimports, and a character's choice survives
save/load. **The real "add a type" cost, measured with the new tools:** 9 files touched, but
only **two required thought** — the view's damage-type field (~10 lines) and the 1-line spec.
The rest were one-line registrations down the established pattern: `register-homebrew-content!`
(one descriptor vs ~10 scattered event regs), `simple-content-builder` (form = sub+event+one
extra field), `content_types` entry, route def/seg/allowlist/page-map (one line each), db
slot/default/local-store (mirrors boon). Verified by a real **builder-flow** test (drive
set/set-prop → output validates against the save spec) + the pool + round-trip proofs — not
injection. So adding a type is genuinely cheaper now; the residue is the field schema + the
occasional custom field widget, exactly as D22 predicted.

### Foundation: registry DRIVES the layers (the real "fewer files" fix)
The original complaint was *file count*, not per-file effort. `content_types` was built as a
passive list only the subs loop read; every other layer was hand-wired per type → "9 files of
one-liners." The fix: make each layer **generate** its wiring from the registry. Progress
(each behavior-preserving, harness-gated):

- ✅ **events** (`d2e002b4`) — ONE loop over `:homebrew-builder?` registry entries calls
  `register-homebrew-content!`. Event keywords derived from `:builder-item` by the uniform
  `<ns>/<verb>-<base>` convention (still literal at dispatch sites, so grep works); the
  localStorage interceptor built generically from `:local-storage-key`. **No events.cljs edit**
  for a new homebrew type.
- ✅ **db** (`af68061d`) — the `:homebrew-builder?` types' `default-value` builder-item slots
  generated from the registry's `:builder-item` + `:default`. **No db.cljs edit** for new types.
- ✅ **routes** (`506c32b3` cycle break, `c5e9aea6` bidi, `58c4de47` set+allowlist) — the
  registry→route_map dep cycle is broken (`:route-kw` is now a plain keyword literal, registry
  is a pure-data leaf), and the **bidi tree segments**, the **`dnd-e5-my-content-routes` nav
  set**, and the **`routes.clj` SPA allowlist** all generate from the registry. A new homebrew
  type's URL resolves, joins My Content, and is allow-listed **automatically**. Guarded by
  `content_types_routes_test` (drift: literals == route_map vars; bidi: every URL resolves;
  set + allowlist membership). `route_map` keeps only the one route-keyword `def` per type
  (D6 — referenced by symbol in views/core); `routes.clj` needs **no** per-type edit.
- ⚠️ **core page-map** — NOT a clean win, skip: a builder's view *function* can't be derived
  from data (cljs has no reliable runtime symbol→var resolution), so generating it only *moves*
  a per-type binding (best co-located in a `views/builder-page-views` map next to the forms).
  The view-fn binding is irreducible; put it where the form already is.

**Net after events+db+routes:** adding a homebrew type no longer touches events.cljs, db.cljs,
or routes.clj, and route_map only needs its one route-keyword `def`. Remaining per-type files:
the registry entry (the one you should write), the view form (irreducible custom UI), the spec
(until spec-from-field-schema), the route-keyword def, and the core/views view binding.

### NEXT levers (pick per value)
- (a) the generic **grant-authoring UI** so authors declare "grant a choice from pool X" in a
  builder (where the N+M maintainability win becomes user-visible — the biggest remaining lever);
- (b) **spec-from-field-schema** — generate the `s/keys` spec from the field list, removing the
  one hand-written row left in the cost table;
- (c) **cross-silo reuse demo** — point the sorcerer draconic bloodline (`classes.cljc:2280`) at
  the *same* ancestry pool, so one pool feeds two silos ("built here, called over there");
- (d) **breath-area field** + the level-gated/variant pins for full FTD coverage.

### PINS (designed-in-now, built-later — do not let these get refactored away)
- 🔴 **HIGH PRIORITY — conditional-required field validation (`:required-when`).** `bf/fields->spec`
  generates the save spec optional-by-default and enforces plain `:required?`, but does NOT yet
  enforce fields that are required *only given another field's value* — e.g. `line-width`/`line-length`
  are required when shape = `:line`, `length` is required when shape = `:cone`, and each is
  meaningless otherwise. Today those are plain-optional in the spec (the form's `:when` only
  hides/shows them), so a `:line` ancestry with no width currently *validates*. Build a
  `:required-when (pred)` field key → `bf/fields->spec` adds a `spec/and` predicate enforcing it.
  Flagged loud in `builder_fields.cljc` too. **Do not let this get lost.**
- **Variants** (`_copy` + `_mod`): the `resolved-content` indirection above is the only thing
  required now. Build `resolve-variants` later; pools/grants stay untouched.
- **New skills** (creating a brand-new skill, not granting one): adds to the skill registry
  itself — different shape. Defer.
- **Class-feature pool** (`[:class-feature :X]`): richer than flat pools; later phase.
- **Declarative cross-type prereq vocabulary** (`has-class?`, `level>=`, `has-feature?`,
  `ability>=`): homebrew-authored prereqs must NOT be raw fns (security/stability). The engine
  evaluates prereqs already; the small declarative vocabulary is the new part. Build when the
  first cross-type gate is needed.
- **Mechanical effects for text-only content** (Axis B sibling): boons — and ki/sorcery-points
  — are today just descriptive `:summary` text; the mechanical benefit they describe isn't
  modeled. Authors should be able to attach real modifiers/resources, not just prose. Same
  family as the play-time-resources finding (ki is text, not a tracked pool). User flagged
  boons explicitly as an enhancement. Defer; same "declare-as-data" pattern will apply.
- **Level-gated grants in `:props`** (FTD axis 3): the `:props` mechanics-as-data vocabulary
  has no level condition, so "gain X at level 5" (Gem Flight, Chromatic Warding, the level-5
  Metallic Breath option) isn't expressible by an author yet. Mechanism exists in the engine
  (the `?total-levels` conditional, as breath-weapon damage dice use); the gap is exposing it
  declaratively. Likely needs a new `:props` key added to `make-feat-modifiers`
  (`options.cljc:3287`) for telepathy and similar, too.

## What already stands (don't redo)
- `register-homebrew-content!` (the wiring sub-layer) + boon swapped through it.
- Phase 4b passthrough-subs loop; the `content_types` registry (data + audit test).
- Import-validation fixes + `save-character` crash fix.
- The cljs harness, compat invariants, verification-discipline lessons.

## Deferred — own branch (surface at branch close)
- Character-validation contract (`character-validation.md`).
- ClojureScript tests into CI (the harness here is the prototype).
