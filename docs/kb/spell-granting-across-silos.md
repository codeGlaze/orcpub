# Spell-granting across silos — the core primitives and why the silos differ

The question this answers: why can a race grant a fixed spell but a feat can't, and why can
a class grant a spell *choice* but a race can't — when it all bottoms out in the same engine
primitive? And what's the sustainable fix (route one data key to the existing primitive)
versus the bloat trap (a new function per silo).

Markers: **VERIFIED** = read up and down the chain, file:line cited. **NOT-TESTED** = the
code chain is verified but I have not built a character and observed the result.

## The two core primitives (what every bespoke spell function wraps)
- **Fixed spell:** `mod5e/spells-known` (`modifiers.cljc:260`) — `[level spell-key ability class
  & [min-level qualifier class-key]]`. Adds one spell to `?spells-known`, castable via the given
  ability. This is "you know spell X." **VERIFIED.**
- **Spell choice:** `opt5e/spell-selection` (`options.cljc:479`) and the simpler `spell-options`
  (`:459`) — build a `selection-cfg` of spells from a list/level/count. This is "choose N from
  list L." **VERIFIED.**

Everything below is a wrapper that ends at one of these two. No silo has its own spell engine;
they differ only in how data reaches the primitive.

## Fixed spell — the chain per silo
DOWN (data → sheet) and UP (who calls the primitive):

| Silo | Data key | Wrapper | Primitive | Status |
|---|---|---|---|---|
| Race / Subrace | `:spells [{:level L :value {:key K :ability A}}]` | `spell-modifiers` (`spell_subs.cljs:124`, used `:146/:159`) | `spells-known` | VERIFIED chain; **NOT-TESTED** on a built character |
| Class / Subclass | `:level-modifiers [{:type :spell :value {:level :key :ability}}]` | `level-modifier` (`spell_subs.cljs:182`) | `spells-known` | VERIFIED chain |
| Feat | — none — | — none — | unreachable | VERIFIED: `feat-option-from-cfg` destructure has no `:spells`/`:level-modifiers` |

So a fixed spell is reachable two ways with two different data shapes (`:spells` vs
`:level-modifiers :spell`), and a feat reaches it zero ways. The primitive is identical; only the
wrapper + data key differ.

## Spell choice — the chain per silo
| Silo | Data key | Wrapper | Primitive | Status |
|---|---|---|---|---|
| Class | `:spellcasting` (full/half/third) | `spells-known-selections` (`options.cljc:647`) | `spell-selection` | VERIFIED chain |
| Feat | `:props` → `:magic-novice`/`:ritual-casting`/`:attack-spell` | the 3 templates (`make-feat-selections`) | `spell-options`/`spell-selection` | VERIFIED |
| Subclass | class-gated UI only | `subclass-spell-selection` / `warlock-subclass-spell-selection` | `spell-selection` | VERIFIED; gated to `#{fighter rogue cleric paladin warlock}` |
| Race / Subrace | — none — | — none — | unreachable | VERIFIED: `race-option` has no spell-choice path; `:spells` is fixed-only |

## Why some work and some don't
Not an engine limit. The primitives (`spells-known`, `spell-selection`) are uniform and capable.
The divergence is entirely in the per-silo wrappers and which data key each silo's assembly fn
reads:
- Fixed spells: races read `:spells`; classes/subclasses read `:level-modifiers :spell`; feats read
  neither. Two shapes for one primitive, plus one silo with no shape.
- Choices: classes have full spellcasting, feats have 3 fixed templates, subclasses are class-gated,
  races have nothing.

This is the "same primitive, divergent wrappers, missing wirings" pattern — the source of the
"works here but not there" confusion.

## The sustainable fix (and the trap to avoid)
You should NOT need a new primitive or a new function-per-silo. The fix is to route ONE data key to
the EXISTING primitive across every silo's assembly fn — the same way `make-feat-modifiers` (the
`:props` fixed-mechanic vocabulary) is already reached cross-silo:
- Fixed spells: pick one data key (`:spells` is the obvious one) and have `feat-option-from-cfg`,
  `make-levels`, etc. all compile it through `spells-known` — exactly what `spell-modifiers` already
  does for races. Feats then grant fixed spells with no new function.
- Choices: route one data key (e.g. `:spell-choice {:from <list> :choose N}`) through every silo's
  assembly fn to `spell-selection`. Races/subraces then grant choices with no new function.

The trap: adding a per-silo wrapper (a `feat-spell-modifiers`, a `race-spell-choice`, …) for each
combination. That multiplies the bespoke functions instead of reaching the one primitive. The audit
rule (D17): a new function earns its place only if it makes the call sites thinner AND it routes to
an existing primitive rather than re-implementing one.

Note for the separate `grant-selection` work: for *spells* the existing choice primitive is
`spell-selection` (it understands spell lists/levels/abilities), not the generic `grant-selection`.
So spells should route to `spell-selection`, not be reinvented through the generic grant. This is a
concrete case where the existing specialized primitive is the right target.

## A sixth spell "source": magic items — text-only (VERIFIED)
Magic items that let you cast a spell ("cast levitate at will", "cast dimension door, once per
dawn") do NOT add the spell to `spells-known` and do NOT track uses. They compile to a
`mod5e/action` entry with a text `:summary` (e.g. Boots of Levitation, `magic_items.cljc:752`; 14
such `mod5e/action` items). VERIFIED: `grep spells-known|spell-options` over `magic_items.cljc`
returns nothing. So the app is **not mechanically aware** of magic-item spells — they appear as
descriptive action/trait text and the **player has to remember** them and their usage limits.
(Caster items like a +1 wand DO apply a mechanical `spell-attack-modifier-bonus`; it's the
"cast spell X" items that are text-only.) Implication: a real `<grant spell>` routing to
`spells-known` would surface magic-item spells properly instead of as loose text.

## Usage limits / "once per long rest" — fragmented, not creator-declarable (VERIFIED)
There is no general, creator-declarable "N uses per rest of X" mechanism. What exists:
- `mod5e/used-resource` (`modifiers.cljc:16`) only records a label `{:resource-key … :option-name …}`
  into `?used-resources` — an *association*, not a use counter. It's used by some built-in features
  (`options.cljc:2042/2293/…`); a homebrew creator can't declare one as data.
- Racial "once per long rest" spells and magic-item "X/day" are **descriptive text only** (the
  qualifier on `spells-known` / the item description), not enforced.
So limited-use handling is split across `used-resource` labels, trait text, and action summaries
with no unified control — matching the "merges too many things with little control" read. Real
support is a **new tracked-resource layer** (a max + current + reset-on-rest a creator can declare),
which is the Axis-B / trackable-resource gap noted in decision-vocabulary.md. NOT-TRACED: where a
built-in feature's *max* uses are computed (likely the sheet UI vs the modifier layer); flagged.

## Limitations / open
- The race `:spells` → sheet path is verified by code but **NOT** verified by a built character. The
  honest next check is a characterization test: a homebrew race with `:spells` → build → assert the
  spell is on `char5e/spells-known`. (Same pattern as the dragonborn-ancestry and divine-soul e2e
  tests.)
- `spells-known` grants an *innate known spell* (castable via the chosen ability), NOT spell slots /
  a casting progression. Fixed-spell grants are innate, not "you become a caster." (See
  runtime-toggles / decision-vocabulary docs.)
- Whether `spells-known` behaves identically when reached from a feat (once wired) is **NOT-TESTED**
  — it should, since the primitive is the same, but that's the thing to confirm when wiring it.
