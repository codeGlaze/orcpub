# Plan: companions, summons and Wild Shape

*Fall update research. Nothing here is built. Written 2026-09-08 from the codebase, D&D
Beyond's docs, and the Foundry VTT dnd5e source; line references are against `integration` at
432cf375. Revised 2026-09-08 and 2026-09-10 — see [Revisions](#revisions).*

Players of rangers, druids, warlocks and artificers have nowhere to put the creature their
class gives them.

**The plan: build the druid's flow — prepare a short list of forms, pick one, see the beast's
stats merged with the druid's, print the list as cards. Nearly every piece exists; the work
is wiring, plus one type fix that has to land first.**

## The druid flow, and what it maps onto

| Step | Existing machinery |
| --- | --- |
| Prepare N forms, bounded by level | `prepares-spells` / `prepared-spells-by-class` / `?prepare-spell-count` (`template_base.cljc:282`) — prepare N from a filtered list, keyed by class |
| Pick from eligible beasts | `filter-monsters` (`spell_subs.cljs:1304`), 86 beasts in the list |
| A view per form | `details-tabs` (`views.cljs:3782`) is a plain `{name -> {:icon :view}}` map |
| Merged stats | `entity/build` + `modifiers.cljc` |
| Compact printed cards | Card machinery in `pdf.clj` — `card-pt`, `draw-card-frame!`, charge tracks. **Two families already print**: spell cards and magic item cards |

Beast forms would be the third card family, not the first. 2.5 x 3.5in, 9 to a sheet; a druid
prepares four or five, so one sheet covers a character.

## The blocker: CR is two different types

Monsters store CR as a number or ratio, the character stores it as a string:

```clojure
;; monsters.cljc      :challenge (/ 1 4)   :challenge 2   :challenge 0
;; classes.cljc:788   ?wild-shape-cr  ->  "1/4"  "1/2"  "1"
```

They cannot be compared. Every other gap is downstream of this one, so it goes first.

Two smaller gaps, both cheap:

- **`filter-monsters` has no CR dimension** — name/size/type/subtypes only, though
  `::monsters/challenge-ratings` exists. The filter is exclusion-based (`monster-filters`
  holds what to *hide*), so a bound is a few lines.
- **`:speed` is prose** — `"30 ft., fly 60 ft."` — in a consistent format, so
  `?wild-shape-limitation`'s "no flying speed" is a substring check, not a schema change.

### Circle of the Moon

Moon is **`#_` discarded** (`classes.cljc:976`) as non-SRD content — see [the SRD
boundary](#the-srd-boundary-decides-where-this-ships) below — so Moon druids reach the app
only through a plugin. Its discarded code writes a different type to `?wild-shape-cr` than the
live code does:

```clojure
;; base druid, live:   (mod5e/level-val (?class-level :druid) {1 "1/4" 4 "1/2" 8 "1"})
;; Moon, commented:    (max 1 (int (/ (?class-level :druid) 3)))
```

An integer, which is the type the monster data already uses. **The live code is the one out
of step, not the discarded code.** Fix the type first; whoever ships Moon as plugin content
inherits the fix. Keep the prose as a separate derived value — the Wild Shape action summary
still needs a sentence.

## Merging druid onto beast

5e keeps the druid's mental stats and proficiencies and takes the beast's HP, AC and physical
stats. That is a merge over two maps, not a new model. Foundry encodes the same thing as
`keep` / `merge` lists plus formulas (`module/config.mjs:3542`):

```js
wildshape: {
  keep:  ["bio","class","feats","hp","languages","mental","tempHP","type"],
  merge: ["saves","skills"],
  minimumAC:   "(13 + @abilities.wis.mod) * sign(@subclasses.moon.levels)",
  tempFormula: "max(@classes.druid.levels, @subclasses.moon.levels * 3)"
}
```

`sign(@subclasses.moon.levels)` is worth stealing: Moon's AC floor applies only with Moon
levels, as arithmetic rather than a branch.

## Slices: the Wild Shape half

1. **CR as one comparable type.** Characterize `?wild-shape-cr` first, then change it.
2. **CR + speed dimensions on `filter-monsters`.** Reused by everything below.
3. **Prepared forms on the character**, following the prepared-spells shape.
4. **Picker + per-form view** in the sheet.
5. **Beast cards** in the PDF, reusing the card frame.
6. **Moon's CR rule** — safe once 1 lands, but it ships as plugin content, not base (see the
   SRD boundary), so this is a plugin-data question rather than an uncomment.

Steps 1-2 are also the summoning substrate: Foundry's summon profiles are the same query
(CR + types + sizes), so Find Familiar, Find Steed and the Summon spells inherit them.

## Slices: the companion half

Separate sequence, and it starts later: steps 1-2 above build the beast query these depend
on. The order is forced by the SRD boundary — Beast Master arrives as plugin data, so the
declaration hook comes before anything that consumes it.

1. **A plugin subclass can declare that it grants a companion.** Plugin subclasses already
   reach the builder as data — `::classes5e/plugin-subclasses-map` is threaded into every
   `*-option` fn (`spell_subs.cljs:1088`). The hook is a key on that map, read where the
   subclass is expanded. **Nothing else on this list can start before it**, and it is the only
   step with no existing machinery to copy.
2. **A character can reference a creature.** `character.cljc` references none today. One
   reference plus a source (SRD monster, homebrew monster, or a constraint to resolve).
3. **Parameterisation**, in Foundry's two pieces: `bonuses` as formulas over the owner, and
   `match` flags for what the companion inherits. Covers taxonomy rows 2-5 in one mechanism;
   `?proficiency-bonus` already exists to feed it.
4. **A companion view** in `details-tabs`, rendering the existing monster stat block with the
   computed values applied.
5. **Companion cards**, once beast cards (Wild Shape step 5) establish the creature card.

**Deliberately deferred: sidekicks.** A sidekick gains class levels, making it a character-lite
rather than a parameterised monster. It needs the builder, not this. Building rows 1-6 first
and sidekicks never is a reasonable end state.

## Companions are a second system

Still true, and still worth keeping separate from Wild Shape: *having* a creature and
*becoming* one are different data and different UI. Foundry splits them; D&D Beyond's Extras
tab has Pet / Mount / Familiar / Beast Companion and **nothing for Wild Shape**. Foundry and
Roll20 have no first-class Wild Shape answer at all — third-party modules or manual
actor-swapping — and nobody models the short list a druid prepares in advance.

### Foundry's summon model — `module/data/activity/summon-data.mjs`

```js
bonuses:  { ac, hd, hp, attackDamage, saveDamage, healing }     // all FormulaField
match:    { ability, attacks, disposition, proficiency, saves }  // booleans: inherit from owner
profiles: [ { count, cr, level: {min,max}, name, types, uuid→Actor } ]
creatureSizes, creatureTypes
```

Three ideas worth stealing:

- **`bonuses` are formulas evaluated against the owner.** Beast of the Land's HP is
  `5 + 5 x ranger level`; Steel Defender's is `2 + INT mod + 5 x artificer level`. Neither
  needs a forked stat block.
- **`match` is inheritance-as-flags.** 2014 Beast Master's "add your proficiency bonus to its
  attacks and saves" becomes a checkbox.
- **A profile is EITHER a named creature (`uuid`) OR a constraint** (cr + types + sizes), so
  "Summon Beast" and "any beast of CR <= X" are one mechanism.

The scaling source is four lines (`module/data/activity/base-activity.mjs:236`):

```js
get relevantLevel() {
  const keyPath = (this.item.type === "spell") && (this.item.system.level > 0) ? "item.level"
    : this.visibility?.identifier ? `classes.${this.visibility.identifier}.levels` : "details.level";
```

Spell slot level, a named class's levels, or character level, chosen by whatever granted the
thing — Tasha's summons vs Primal Companion vs a generic feature.

### The taxonomy a companion feature has to cover

| Kind | Parameterised by | Example |
| --- | --- | --- |
| Static | nothing | Find Familiar as printed |
| PB grafted on | owner's proficiency bonus | 2014 Beast Master |
| Level-scaled template | owner level + an ability mod | Primal Companion, Steel Defender |
| Choice-parameterised | a player choice | Drakewarden damage type; Beast of Land/Sea/Sky |
| Slot-scaled | **spell slot level**, not character level | Tasha's Summon Beast/Fey/Elemental |
| Eligibility filter | level + subclass bounds | Wild Shape |
| Class-levelled creature | its own progression | Tasha's sidekicks |

The last row is the odd one: a sidekick gains class levels, so it is a character-lite. Do not
let it drag the other six into a heavier model.

A homebrew companion needs no new homebrew type — it is a homebrew monster
(`::monsters/homebrew-monster`, own builder page) that the character references. The missing
link is that nothing in `character.cljc` references a creature at all.

## The SRD boundary decides where this ships

**27 subclasses in `classes.cljc` are `#_` discarded.** Exactly one per class survives, and the
survivors are precisely the SRD 5.1 list: Berserker, Lore, Life, Land, Champion, Open Hand,
Devotion, Hunter, Thief, Draconic, Fiend, Evocation. Already documented in
[srd-vs-plugin-content.md](srd-vs-plugin-content.md) — "non-SRD are `#_` discarded".

The discards are a licensing line, not unfinished work, and the line cuts straight through this
feature:

| Piece | Status | Ships where |
| --- | --- | --- |
| Wild Shape | SRD, base druid feature | Base content |
| Find Familiar, Find Steed | SRD spells | Base content |
| Pact of the Chain | SRD, live | Base content |
| **Circle of the Moon** | non-SRD, `#_` | Plugin |
| **Beast Master** | non-SRD, `#_` | Plugin |
| Drakewarden, Battle Smith, sidekicks | not in the repo at all | Plugin |

**The Wild Shape filter is safe to build in base content. The companion feature is not.** Its
two flagship cases both arrive as plugin data, so it has to be plugin-driven from the first
commit rather than retrofitted — there is no version of this that hardcodes Beast Master.

The discarded Beast Master (`classes.cljc:1942`) is worth reading anyway: it holds a selection
over 41 named beasts, which is a companion picker already written once, in the shape this
codebase reaches for.

## Open questions

- **What does a companion look like as plugin data?** Beast Master arrives via orcbrew, so
  the feature needs a way for plugin content to declare "this subclass grants a companion
  of kind X". No such hook exists. This is the companion half's equivalent of the CR type
  fix — the thing everything else waits on.
- **2024 rules.** Beast Master and Wild Shape both changed. Confirm which edition the data
  shape targets before building.
- **The 5etools schema was not obtained** — GitHub returns 403 through this sandbox's proxy.
  Worth their `summonedBySpell` / `summonedByClass` field names if import compatibility
  matters.
- The Foundry reference is a **fork**: `codeGlaze/dnd5e` of `foundryvtt/dnd5e`.

## Revisions

**2026-09-08.** The first draft concluded "it is not one feature, it is two systems, and the
starting point is neither of them," and called per-form PDF sheets the wrong shape. Both were
overstated, and the second was wrong:

- The two-system split is a *modelling* distinction, not a reason to defer the druid flow.
  Prepared forms, the picker and beast cards all land on machinery that already exists.
- Per-form printed cards are a good fit, not a bad one — the app already prints two card
  families off shared machinery.
- The draft named the commented-out Moon code as the type trap. Backwards: Moon's integer
  matches the monster data, and the live string does not.

**2026-09-10.** Two more corrections, both from checking rather than assuming:

- "Why is Circle of the Moon commented out?" was listed as an open question. It was already
  answered in this same KB directory: non-SRD subclasses are `#_` discarded, 27 of them, and
  Moon is one of them. Licensing, not incompleteness. Grep the KB before asking a question.
- That answer moves the companion half out of base content. Beast Master is discarded on the
  same grounds, so companions are plugin-driven or they do not ship. Wild Shape is unaffected:
  it is a base druid feature and stays in SRD content.
