# Spell-slot progression, multiclassing, and the `:level-factor` overload

What this answers: how spell *slots* (not known spells — see spell-granting-across-silos.md) are
computed from a class's `:spellcasting`, why Artificer can't be expressed, how warlock pact magic
differs from a normal caster when multiclassing, and the agreed builder design (a bucket of
named/explicit slot tables + a separately-declared multiclass rule).

Markers: **VERIFIED** = read from code, file:line cited. **DESIGN** = agreed shape, not built.

## How slots are computed today — VERIFIED
1. A class's `:spellcasting :level-factor` (1/2/3) emits a `spell-slot-factor` modifier
   (`options.cljc:2648`, `modifiers.cljc:274`) into a map `?spell-slot-factors` of class-key → factor.
2. `?spell-slots` (`template_base.cljc:285-299`) is built from that map:
   - **one** normal caster → `total-slots(class-level, factor)`;
   - **more than one** → pool them: `?total-spellcaster-levels = Σ int(class-level / factor)`
     (`:263-269`) and use the **full-caster table** on that pooled total;
   - then, separately, `(when ?pact-magic? (warlock-spell-slot-schedule (?class-level :warlock)))` is
     `merge-with +`'d on top.
3. `total-slots` (`options.cljc:595`) folds `spell-slot-schedule` (`:502`), which is a **`case` on the
   integer factor**: 1 = full, 2 = half (first slots at level **2**), 3 = third. It also carries
   **undocumented factors 4, 5, 6** — non-standard tables encoded into the divisor.

## The overload — why Artificer can't be expressed — VERIFIED
`:level-factor` is one integer doing **three** jobs:
- the **solo slot table** (the `case`, `options.cljc:502`);
- the **multiclass contribution** `int(class-level / factor)` (`template_base.cljc:263`);
- the **prepared-spell count** `ability-mod + int(level / factor)` (`:274`).

Artificer is a half-caster that casts from level 1 (rounds up). Its solo table happens to equal the
**factor-4** entry (a half-caster table shifted to start at level 1 — worked cumulatively, it matches
the official Artificer slots at the sampled levels 1/3/5/7/9/11/13/15/17/19). But it needs the
*division* of factor 2: as a multiclass half-caster it contributes `floor(level/2)`, and it prepares
`Int + floor(level/2)`. Set factor 4 and the solo table is right while a multiclassed/preparing
Artificer comes out as a **quarter**-caster. One integer can't say "this table, but count as a half
for multiclassing and preparation." That's the mismatch — and it's why factors 4/5/6 exist at all:
they're attempts to smuggle non-standard tables through a selector that also controls division.

## Warlock vs sorcerer when multiclassing — VERIFIED
- **Sorcerer** (any normal caster): declares `:level-factor` → it's in `?spell-slot-factors`. Multiclassed
  with another normal caster, levels **pool** (`Σ int(level/factor)`) and slots come from the full-caster
  table on the pooled level — the standard PHB Multiclass Spellcaster rule. Sorcerer 5 / Wizard 5 casts
  as a 10th-level caster.
- **Warlock**: declares **no** `:level-factor` (its `:spellcasting` has `:pact-magic? true` +
  `:slot-schedule`, `classes.cljc:3003-3017`), so it is **not** in `?spell-slot-factors`. Its pact slots
  come from `warlock-spell-slot-schedule` keyed on **warlock level alone** and are merged on top
  (`template_base.cljc:298`). Warlock **never pools** — Sorcerer 5 / Warlock 5 gets 5th-level-caster
  sorcerer slots **plus** independent pact slots. That matches 5e (pact magic sits outside the multiclass
  table). The implementation is a **hardcoded branch** for warlock, not a general mechanism.

Caveat (a simplification to NOT copy forward): pact slots are `merge-with +`'d into the regular slots —
counts are summed per spell level rather than tracked as a genuinely separate pool with short-rest
recharge. Mechanically warlock's separateness is real (no pooling); the *display* conflates the pools.

## Agreed design — DESIGN (this thread, not built)
Replace the overloaded integer with three decoupled declarations:
1. **Slot table** — a bucket of named presets (`:full :half :half-from-1 :third :pact`) **or** an explicit
   per-level table. The engine already consumes explicit tables natively (`spell-slot-schedule` returns a
   per-level map, `total-slots` folds it), so the `case` becomes a registry lookup: keyword → preset, map →
   pass-through. This is the substantive feature — it's needed to represent Artificer and the custom
   progression tables homebrewers make, independent of multiclassing.
2. **Multiclass behavior** — a separate declared field (`:full | :half | :third | :none | :separate`).
   Trivial in itself; it only became a separate field because an arbitrary table has no integer to derive a
   rule from. `:separate` is the warlock/pact path generalized.
3. **Prepared/known count** — its own formula (also currently riding the factor).

So: sorcerer = `{:table :full :multiclass :full}`, artificer = `{:table :half-from-1 :multiclass :half}`,
warlock = `{:table :pact :multiclass :separate :recharge :short-rest}`. The magic factors 4/5/6 disappear
because the table is named/explicit, not inferred from a divisor.

**Builder surface:** an absolute per-level **grid** (level × spell-level counts — the table as printed,
not the engine's sparse-delta form; the app converts), with a preset chosen as a **seed** to prefill the
grid then tweak. Same grid pattern covers `:cantrips-known` and `:spells-known` (already per-level maps).
Plus one dropdown for multiclass behavior, and — for `:separate` — a recharge period. A `:separate`
schedule should own its slot pool + reset rule rather than `+`-merging, which is the same machinery as the
trackable-resource layer (roadmap B3 / the usage-limits gap in spell-granting-across-silos.md).

## Relation to other docs
Spell *granting* (known spells, choices) is spell-granting-across-silos.md; this doc is the *slot
progression* it explicitly defers (that doc, "spells-known grants an innate known spell, NOT a casting
progression"). The trackable-resource layer a `:separate` pool needs is the same one that doc flags for
usage limits.
