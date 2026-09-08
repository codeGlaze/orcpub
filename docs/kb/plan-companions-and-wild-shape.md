# Plan: companions, summons and Wild Shape

*Fall update research. Nothing here is built. Written 2026-09-08, from reading the codebase,
D&D Beyond's docs, and the Foundry VTT dnd5e system's source.*

Players of rangers, druids, warlocks and artificers have nowhere to put the creature their
class gives them. The question was whether to build custom PDF sheets per kind of pet.

**The answer that survived research: it is not one feature, it is two systems, and the
starting point is neither of them.**

## What other tools do

**D&D Beyond** has an *Extras* tab: Pet, Mount, Familiar, Beast Companion, plus
followers/sidekicks. Manage Extras -> Add an Extra -> pick a category; each creature opens in
a sidebar with HP tracking. A "pet" does not fight, a "follower" does. Their own forums carry
years of "how do I add my primal companion" threads, and **Extras has nothing for Wild Shape**.

**Foundry and Roll20 have no first-class answer.** Wild Shape is third-party modules
(`Wildshape Companion`, `AutomaticWildShape`) or manual actor-swapping. Nobody models the
short list a druid prepares in advance.

## What Foundry's dnd5e system does, which is the useful part

Their system source is the best available reference. Two SEPARATE systems, not one:

### 1. Summoning — `module/data/activity/summon-data.mjs`

```js
bonuses:  { ac, hd, hp, attackDamage, saveDamage, healing }    // all FormulaField
match:    { ability, attacks, disposition, proficiency, saves } // booleans: inherit from owner
profiles: [ { count, cr, level: {min,max}, name, types, uuid→Actor } ]
creatureSizes, creatureTypes                                    // constraint instead of a fixed creature
tempHP:   FormulaField
```

Three ideas worth stealing outright:

- **`bonuses` are formulas evaluated against the owner.** Beast of the Land's HP is
  `5 + 5 x ranger level`; Steel Defender's is `2 + INT mod + 5 x artificer level`. Expressed
  as a formula, neither needs a forked stat block.
- **`match` is inheritance-as-flags.** 2014 Beast Master's "add your proficiency bonus to its
  attacks and saves" is a checkbox, not a rewritten creature. This is the cleanest part of
  their model and the one this repo would most benefit from.
- **A profile is EITHER a specific creature (`uuid`) OR a constraint** (`cr` +
  `creatureTypes` + `creatureSizes`). "Summon Beast" and "any beast of CR <= X" are the same
  mechanism.

And the scaling-source problem is solved in four lines
(`module/data/activity/base-activity.mjs:236`):

```js
get relevantLevel() {
  const keyPath = (this.item.type === "spell") && (this.item.system.level > 0) ? "item.level"
    : this.visibility?.identifier ? `classes.${this.visibility.identifier}.levels` : "details.level";
```

Spell slot level, or a named class's levels, or total character level — chosen by whatever
granted the thing. That is Tasha's summons vs Primal Companion vs a generic feature.

### 2. Transformation — `module/config.mjs:3542`

Wild Shape is NOT a companion in their model. It is a transformation preset:

```js
wildshape: {
  keep:  ["bio","class","feats","hp","languages","mental","tempHP","type"],
  merge: ["saves","skills"],
  minimumAC:   "(13 + @abilities.wis.mod) * sign(@subclasses.moon.levels)",
  tempFormula: "max(@classes.druid.levels, @subclasses.moon.levels * 3)",
  spellLists:  ["subclass:moon"]
}
```

Note `sign(@subclasses.moon.levels)`: Circle of the Moon's AC floor applies only if you have
Moon levels, expressed as arithmetic rather than a branch. Their whole
subclass-changes-the-rule problem is formulas over `@`-paths.

**So "one companion renderer for beasts, sidekicks and wild-shape forms" is wrong.** Becoming
a creature and having a creature are different data and different UI.

## The taxonomy this has to cover

| Kind | Parameterised by | Example |
| --- | --- | --- |
| Static | nothing | Find Familiar as printed |
| PB grafted on | owner's proficiency bonus | 2014 Beast Master |
| Level-scaled template | owner level + an ability mod | Primal Companion, Steel Defender |
| Choice-parameterised | a player choice | Drakewarden damage type; Beast of Land/Sea/Sky; Pact of the Chain's four familiars |
| Slot-scaled | **spell slot level**, not character level | Tasha's Summon Beast/Fey/Elemental |
| Eligibility filter | level + subclass bounds | Wild Shape |
| Class-levelled creature | its own progression | Tasha's sidekicks (Expert/Spellcaster/Warrior) |

The last row is the odd one: a sidekick gains class levels, so it is a character-lite, not a
parameterised monster. Do not let it drag the other six into a heavier model.

## What this repo already has

| Piece | State |
| --- | --- |
| Monster stat block | **Exists.** AC, HP, speed, six abilities, senses, languages, skills, damage res/imm/vuln, traits, actions, legendary, CR |
| Homebrew monsters | **Exists.** `::monsters/homebrew-monster`, own builder page |
| Monster filtering | **Exists.** `filter-monsters`, `spell_subs.cljs:1304` — name, size, type, subtypes |
| Modifier/derivation engine | **Exists.** `entity/build`, `modifiers.cljc`; `character.cljc` already derives `proficiency-bonus` and doubles it for expertise |
| Card PDF machinery | **Exists.** 2.5 x 3.5in, 9 to a sheet, vector |
| Character -> creature link | **Missing.** Nothing in `character.cljc` references a creature |
| Wild Shape as data | **Missing.** Prose in a trait |

A homebrew companion needs no new homebrew type: it is a homebrew monster the character
references. Sheet views are cheap too — `details-tabs` (`views.cljs:3782`) is a plain map of
`{name -> {:icon :view}}`.

## Start with the Wild Shape filter

Filtering the beast list from the sheet, bounded by the character's own limits, is the
smallest useful slice AND the piece everything else reuses — Foundry's summon profiles are the
same query (`cr` + types + sizes). Build it for Wild Shape and Find Familiar, Find Steed and
the Summon spells inherit it.

The character **already computes both bounds** (`classes.cljc:788`):

```clojure
?wild-shape-cr         ; "1/4" at druid 2, "1/2" at 4, "1" at 8
?wild-shape-limitation ; "no flying or swimming speed" / "no flying speed" / nil
```

Three gaps, all small:

1. **The bounds are prose.** Built to be interpolated into the Wild Shape action's summary
   sentence. A filter needs CR as a comparable number and the limitation as a predicate over
   the beast's `speed` keys.
2. **`filter-monsters` has no CR dimension** — name/size/type/subtypes only, though
   `::monsters/challenge-ratings` exists as a sub. The most important axis is unwired.
3. **No speed predicate.**

### The trap: `?wild-shape-cr` already has two types

Circle of the Moon is **commented out** (`#_`, `classes.cljc:976`) — so Moon druids are
missing from the app entirely — and the dead code writes a different type to the same key:

```clojure
;; base druid, live:
(mod/modifier ?wild-shape-cr (mod5e/level-val (?class-level :druid) {1 "1/4" 4 "1/2" 8 "1"}))
;; Circle of the Moon, commented out:
(mod/modifier ?wild-shape-cr (max 1 (int (/ (?class-level :druid) 3))))
```

Strings from one writer, an integer from the other. Uncommenting Moon without reconciling
that would feed a filter values it cannot compare. **Make the bound data first, with one
type, then restore Moon on top of it.** Keep the prose as a separate derived value for the
action summary rather than deleting it.

## Open questions

- **The 5etools schema was not obtained.** GitHub's API and raw URLs both return 403 through
  this sandbox's proxy. Worth getting their `summonedBySpell` / `summonedByClass` field names
  before fixing a data shape, if import compatibility matters.
- **Why is Circle of the Moon commented out?** No explanatory comment at the `#_`. It may be
  incomplete rather than deliberately disabled — check before assuming either.
- **2024 rules.** Beast Master and Wild Shape both changed. Confirm which edition the data
  shape targets before building.
- The Foundry reference is a **fork** — `codeGlaze/dnd5e` of `foundryvtt/dnd5e`.
