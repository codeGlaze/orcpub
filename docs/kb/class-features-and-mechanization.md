# Class features, the rolling layer, and the mechanization ceiling

How built-in class features are actually structured, what the app can/can't do mechanically
(including the dice roller), and the design direction for centralizing features. This is the
"tough nut" — read the flags carefully.

Flags used: **VERIFIED** = read from code, file:line cited. **USER-REPORTED** = stated by the
maintainer, not independently verified here. **SPECULATION / NOT-EXPLORED** = reasoning or an
un-investigated area; do not take as fact.

## How a class + its features are structured — VERIFIED (fighter, rogue read)
- A class is a function → `(opt5e/class-option … cfg)` with a **hand-written cfg map**. There are
  **10** such class option fns (`classes.cljc`, `defn …-option [spells spells-map …]`).
- Features live inline in `:modifiers` (class-wide) or `:levels {N {:modifiers/:selections}}`
  (level-gated). Level-gating is that plain `:levels` map keyed by level number, plus per-trait
  `:level`.
- **Features have no key.** Verified across fighter/rogue: `trait-cfg`/`action`/`bonus-action`/
  `dependent-trait` take `:name`/`:level`/`:frequency`/`:summary`, never `:key`. Some features are
  bare modifiers with no name at all — Extra Attack is `(mod5e/num-attacks 2)` at fighter level 5.
- **Class-level coupling is literal**: `(?class-level :fighter)` / `(?class-level :rogue)` are written
  directly into summaries, frequencies, and scaling tables (Second Wind, Action Surge, Sneak Attack,
  Indomitable).
- **Partial extraction already exists**: a few features are shared helper fns — `extra-attack-trait`
  (`classes.cljc:40`, used by barbarian/ranger), `uncanny-dodge-modifier` (`options.cljc:3049`, used
  by rogue + a subclass). These are shared *code* (compile-time fns), not keyed/data-addressable
  units, and parameterized only trivially (a page number), not by class-level.

## Two kinds of feature "mechanics" — VERIFIED
1. **Sheet-affecting modifiers** (compute a derived value): `num-attacks` (Extra Attack), saving
   throws, ability boosts, AC/speed fns, spellcasting, uncanny-dodge.
2. **Dependent-traits** (text + a level-computed summary + optional `:frequency`): Sneak Attack's
   "Nd6", Second Wind's heal, Action Surge, Rage's damage/resistance, Indomitable. The number is
   computed into the string; the effect itself is described, and (without the rolling/counter wiring)
   player-applied.

## The rolling layer — VERIFIED (corrects an earlier wrong claim)
An earlier note said "the app does not resolve rolls/combat." **Wrong** — that came from grepping only
the `orcpub.dnd.e5` namespaces and missing the dice layer. Verified:
- `orcpub.dice` (`src/cljc/orcpub/dice.cljc`): `die-roll`, `roll-n`, `dice-roll {:num :sides :drop-num
  :modifier}`, `dice-roll-text-2` (parses "1d20+5" and rolls).
- `roll-button`s across the sheet — attack rolls, skill checks, saves, ability checks — with
  **advantage/disadvantage** (`views.cljs` `button-roll-fn`, `roll-button`).
- Attacks compute through `?attack-modifier-fns` / `?damage-bonus-fns` (`modifiers.cljc:545-556`) —
  the same fns Dueling uses for a real conditional +2 damage — and these feed both the *displayed*
  attack and the roll. **This is the attachment point** for mechanizing a feature's dice/mods.

USER-REPORTED (not verified here): **many damage/etc. rolls are stuck as text and not exposed to the
roller**, and users have explicitly asked for this to be fixed. So the roller exists but is applied
inconsistently — wiring the stuck-as-text rolls into it is a known, requested improvement.
USER-REPORTED: the **PDF sheets don't roll** anything but auto-calculate some fields (not verified).

## Use/resource counters — VERIFIED
A general counter exists: `actions-indicators` (`views.cljs:2344`) renders a feature's uses as
checkboxes (small count) or a 0..N selector (large count), backed by `::entity/values
::char5e/features-used` (keyed by rest-period + name) and reset by `clear-period` on long/short rest
(`events.cljs:2556-2579`). Ki/sorcery points are *authored as text* today, not because the engine
can't count — wiring them as a frequency/amount feature would give them a counter.

## The mechanization ceiling (where "make it real, not text" stops)
- **In reach:** anything resolving to a derived sheet value (AC, speed, saves, resistances, ability
  mods, flat/conditional attack & damage bonuses), known spells, use/point counters, and — via the
  roller + bonus-fns — a feature's dice/mods flowing into an actual roll (Sneak Attack's Nd6, Rage's
  +2). The situational *condition* (advantage, enemy within 5 ft) stays player-chosen (you pick the
  roll/adv mode).
- **Out of reach (would be a different product):** combat *state* and *turn* resolution — targets,
  hit/miss outcome, applying damage to a creature, reactions firing, action economy ("extra action"),
  enforced "once per turn." That's a simulator, not a sheet+roller.

## Design direction for centralizing features — DESIGN (not built)
The maintainer's intuition: move core features out of class declarations, centralize, re-insert. The
question was "small per-feature pools, or one larger keyed/filterable registry?"

Recommendation: **one keyed, filterable feature registry**, not many small pools.
- A pool (existing `content_pools/pool`) is "choose one from a set of interchangeable options" — it
  fits *choices* (alternate features, a feature that grants a pick), not a single named feature.
  "Each feature its own pool" is a category mismatch.
- Features are named units you reference, place at a level, replace, and query (all rogue features;
  all features of kind X; features valid at this slot). That's a **registry: key → parameterized
  definition + filterable metadata** (class(es)/generic, level/scaling, kind, source).
- **Pools become filtered views over the registry** — the same built-in ++ homebrew pool sub, but
  sourced by a filter on the registry. So it's not registry *vs* pools; it's one registry, with pools
  derived from it. This matches how the app already builds pools.
- Registry entries must be **parameterized by class-key** so a feature scales by whatever class grants
  it (today `?class-level :fighter` is hardcoded).

**Distinct features vs scaling (VERIFIED on fighter/rogue) — this shrinks the scope.** A class's
`:levels` table is mostly *scaling/padding*, not distinct features: ASI levels
(`:ability-increase-levels`), Extra Attack increments (`num-attacks` 2/3/4), and feature dice growing
by level (`level-val`, e.g. Sneak Attack). Each class has only ~3–6 genuinely-distinct features
(fighter: Second Wind, Action Surge, Indomitable, Fighting Style). So:
- The registry holds the **handful of distinct features per class** (class-tagged), not whole level
  tables. "A class's standout features" = a **filter** on the registry (`:class :fighter`), not a
  per-class silo — per-class pools would over-fragment ~3–6 entries.
- Scaling/padding stays where it is — `:ability-increase-levels` + `level-val` + bare modifiers are
  fine declarative primitives and don't enter the feature registry.
- So the migration is ≈ extracting ~3–6 features × ~12 classes (+ subclasses), not rewriting every
  level table. Smaller and lower-risk than "re-architect every class."

Sequence (to contain regression risk): build a characterization net (snapshot every class's built
features) → define the feature record + registry → extract incrementally, proving byte-identical
output per step → then make entries data-addressable and expose to the custom builder + alternates.
Backward-compat note: features are auto-granted (not stored choices), so extraction doesn't touch
saved characters as long as output is identical; only *choosable* alternates put feature keys into
saved data.

## NOT-EXPLORED / to verify before sizing
- The full per-class feature catalogue (only fighter + rogue read here) — needed to size the migration
  and find odd cases (ranger/paladin mix spellcasting + features).
- Exactly how a *new conditional dice rider* (e.g. "+Nd6 when you have advantage") would attach to a
  roll button in the UI.
- Whether the combat tracker tracks uses/resources (separate from the sheet).
- The PDF auto-calc fields (USER-REPORTED; not checked).
