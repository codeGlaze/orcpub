# Per-class feature catalogue (roadmap C1)

Sizes the feature registry and surfaces the odd cases, by reading all **12** base-class option fns in
`src/cljc/orcpub/dnd/e5/classes.cljc`. Everything here is **VERIFIED** (read from code, file:line
cited) unless marked. Companion to `class-features-and-mechanization.md` (the structure + the
`compile-feature` proof) and `decision-vocabulary.md` (the wiring).

Scope: the **auto-granted** features each class gets (the regression net's `:feature-names`). It
excludes (a) **scaling/padding** — `:ability-increase-levels`, `num-attacks` increments, `level-val`
dice growth, speed-bonus increments; (b) **choice-gated** features — Fighting Style, Expertise,
Metamagic, Favored Enemy, Hunter's Prey — which are `selections` (pools) already; (c) **spellcasting**,
a separate declarative `:spellcasting` subsystem (full/half caster via `:level-factor` 1/2).

## The 12 classes (option fn line; distinct auto-features; notable shape)

| Class | fn | Distinct auto-features (base class) | Notes |
|---|---|---|---|
| Barbarian | `:46` | Rage, Extra Attack, Fast Movement, Brutal Critical, Indomitable Might + 5 text traits (Reckless Attack, Danger Sense, Feral Instinct, Relentless Rage, Persistent Rage) | Rage = bonus-action w/ scaling uses **and** scaling damage in summary; Unarmored Defense (AC channel); capstone ability boost @20 |
| Bard | `:249` | Bardic Inspiration, Jack of All Trades, Song of Rest, Countercharm, Font of Inspiration, Superior Inspiration | spellcaster; Bardic count = **ability-derived**; Jack of All Trades = **multi-part** (skill-fn + initiative mod + trait) |
| Cleric | `:397` | Channel Divinity (+ Turn Undead), Destroy Undead, Divine Intervention | spellcaster; subclass @ **level 1**; Destroy Undead scaling is **strings** ("1/2"→4); summaries read `?spell-save-dc` |
| Druid | `:752` | Wild Shape, Druidic + 3 text traits (Timeless Body, Beast Spells, Archdruid) | **lean**; full caster; subclass @ 2; Wild Shape reads `?wild-shape-cr`/`?wild-shape-limitation` attrs (**string- and nil-valued** scaling), duration = **formula** (level/2 hrs) |
| Fighter | `:1052` | Second Wind, Action Surge, Indomitable, Extra Attack | the proof's worked example; cleanest case |
| Monk | `:1228` | Martial Arts, Ki, Flurry of Blows, Patient Defense, Step of the Wind, Deflect Missiles, Slow Fall, Stunning Strike, Stillness of Mind, Empty Body + ~6 text traits | **biggest**; **ki pool** (=level) spent as text everywhere; `?martial-arts-die` is a shared attribute; Martial Arts = **multi-part** (attack + bonus-action) |
| Paladin | `:1425` | Divine Sense, Lay on Hands, Channel Divinity, Divine Smite, Improved Divine Smite, Aura of Protection, Aura of Courage, Cleansing Touch, Divine Health, Extra Attack | half-caster; **Lay on Hands pool** (=5×level); Auras read `?paladin-aura` attr; Aura of Protection = **multi-part** (save mods + trait); frequencies **ability-derived** |
| Ranger | `:1771` | Favored Enemy, Natural Explorer, Primeval Awareness, Extra Attack, Foe Slayer + 4 text traits | half-caster; Favored Enemy/Natural Explorer summaries interpolate **user selections** (`?ranger-favored-enemies/terrain`) |
| Rogue | `:1978` | Sneak Attack, Cunning Action, Uncanny Dodge, Thieves' Cant | the proof's second worked example; Sneak Attack scaling = **formula** `round-up(level/2)` |
| Sorcerer | `:2206` | Sorcery Points, Flexible Casting | **lean**; full caster; **sorcery-point pool** (=level); subclass @ level 1 |
| Warlock | `:2994` | (none auto-granted before Mystic Arcanum @11); Eldrich Master @20 | **most choice-driven**; pact magic; **longer option-fn arity** (invocations, boons); base levels are *all selections* (invocations, pact boon, mystic arcanum) — almost nothing to extract |
| Wizard | `:2379` | Arcane Recovery, Spell Mastery, Signature Spells | **lean**; full caster; Arcane Recovery scaling = **formula** `round-up(level/2)`; Spell Mastery/Signature interpolate **selections** |

**Sizing:** most classes sit at the predicted **~3–6 distinct auto-features**, but the spread is wider
than expected: **monk (~10) and paladin (~10) are outliers**; **druid/sorcerer/wizard (~2–3) are lean**;
and **warlock is essentially zero** auto-features (everything is a selection — invocations, pact boon,
arcanum). Rough total: ~50–60 base-class auto-features across the 12 (subclasses add comparable
shapes). The "~3–6 × 12" estimate in class-features-and-mechanization.md was right on average but
understates monk/paladin and overstates warlock. The migration is still bounded — dozens of features,
not whole level tables — but monk's ki economy is its own sub-project (see below), and warlock barely
participates (its identity lives in selections + pact-magic, not extractable auto-features).

## Cross-cutting findings — the "odd cases" the registry/compiler must handle

These extend the `compile-feature` proof, which today covers only level-schedule/literal `:uses` and
effect-param interpolation. Each is VERIFIED from the classes above.

1. **Frequency/use-count has ≥4 sources, not just level schedules.** The proof handles a literal and a
   `{level→n}` schedule. Real features also use:
   - **ability-modifier-derived** — Bardic Inspiration `(max 1 (?ability-bonuses ::cha))` (`:300`),
     Divine Sense `(inc cha-bonus)` (`:1488`), Cleansing Touch `cha-bonus` (`:1481`);
   - **formula of level** — Lay on Hands `(* 5 (?class-level :paladin))` (`:1494`), Arcane Recovery /
     Sneak Attack `round-up(level/2)` (`:2415`, `:2011`);
   - **level itself** — Sorcery Points / Ki `(?class-level …)` (`:2247`, `:1289`).
   So `:uses` needs to be a small expression form (literal · level-schedule · `:level` · ability-mod ·
   arithmetic), not just a schedule map.

2. **Resource POOLS are distinct from per-rest counters, and are class-wide.** Ki (=level, `:1289`),
   Sorcery Points (=level, `:2248`), and Lay on Hands (=5×level HP, `:1495`) are *pools* whose size is
   a formula and which many features **spend** ("spend 1 ki", "spend 5 sorcery pts.", "expend 5
   points") — the costs live as prose in unrelated features' summaries. This is bigger than a single
   feature's `:frequency`: it's a class-level resource + per-feature costs. The existing counter
   (`features-used` + `clear-period`) is per-feature/per-period; modelling a shared spendable pool with
   declared costs is a separate mechanism. **Monk's ki is the hardest single case** — nearly every monk
   feature references it.

3. **Summaries interpolate far more than level + the feature's own params.** The fill template in the
   proof resolves effect params; real summaries pull from the **built character**:
   - derived stats — `?spell-save-dc` (cleric/monk/paladin/sorcerer), `?ability-bonuses`, `?speed`
     (Fast Movement `:103`), `?prof-bonus`, `?paladin-aura` (`:1473`);
   - **user selections** — `?ranger-favored-enemies`/`?ranger-favored-terrain` (`:1811`/`:1815`),
     `?sorcerer-draconic-ancestry` (`:2288`), `?spell-mastery`/`?signature-spells` (`:2421`/`:2429`);
   - arithmetic — `(* 5 level)`, `round-up(level/2)`, `(* 3 level)`.
   **Implication:** a production fill step needs access to the build context (the entity-spec
   attributes), not just the feature's params. This is the real residue of the "summary" problem — not
   string interpolation mechanics, but *what values the template can reference*.

4. **Scaling values can be strings, not numbers.** Destroy Undead's CR table is `{5 "1/2" 8 1 …}`
   (`:474`); Brutal Critical scales "one"/"two"/"three" (`:108`). `level-lookup` already returns any
   value, so this is covered — but the fill rule's "numbers sign, strings pass" must treat these as
   plain strings (they do).

5. **A named feature is often multi-part** (several modifiers of different kinds under one name):
   Aura of Protection = per-ability save-bonus modifiers **+** a trait (`:1463-1473`); Martial Arts =
   an `attack` **+** a `bonus-action` (`:1274-1283`); Jack of All Trades = a skill-bonus fn **+** an
   initiative mod **+** a trait (`:304-311`); Channel Divinity = a trait **+** the Turn Undead action
   (`:451-465`). So a registry entry may compile to a **list** of modifiers, not one cfg.

6. **Features interdepend through spec attributes.** `?martial-arts-die` is *set* by a modifier and
   *read* by the Martial Arts attack (`:1268`/`:1276`); `?paladin-aura` is set once and read by three
   auras (`:1483`); `?wild-shape-cr`/`?wild-shape-limitation` are set by level-val modifiers (with
   string and **nil** values) and read by the Wild Shape summary (`:787-808`);
   `?unarmored-defense`/`?unarmored-ac-bonus` couple Unarmored Defense to AC (`:70-78`,
   `:1262-1267`). Extraction must preserve these attribute writes/reads — a feature isn't always
   self-contained.

7. **Partial extraction precedent already exists.** `opt5e/monk-base-cfg`, `paladin-base-cfg`,
   `ranger-base-cfg` are merged into those class cfgs (`:1235`, `:1432`, `:1778`); `extra-attack-trait`
   and `uncanny-dodge-modifier` are shared helper fns. These are shared *code*, not keyed/data entries
   — the direction the registry generalizes.

8. **Subclass timing varies** — subclass @ level 1 (cleric, sorcerer), 2 (wizard), 3 (most). Subclasses
   carry the same feature shapes (and grant spells). Not a blocker, but the registry's level model
   can't assume subclass starts at 3.

## What this means for the build order (refines the roadmap)
- The `compile-feature` slice is correct but **partial**: it proves fighter/rogue (level-schedule +
  param interpolation). Before extracting the resource classes it needs (1) a richer `:uses`
  expression form and (3) a build-context-aware fill. Both are B1 work.
- **Ki/sorcery/Lay-on-Hands pools (finding 2) are their own mechanism** — schedule the pool layer
  (B3 resource counters) deliberately; don't fold it into per-feature frequency.
- Multi-part features (5) and attribute interdependence (6) mean a registry entry's `:compile` returns
  **a seq of modifiers**, and extraction must keep the `?attr` reads/writes intact — exactly what the
  byte-identical snapshot net guards.
- Start extraction on the **clean classes** (fighter, rogue, then bard/cleric/wizard) where features
  are self-contained; **defer monk/paladin** until the pool + build-context-fill mechanisms exist, and
  **defer druid** until the wild-shape attribute pair is modelled. **Warlock needs almost no extraction**
  — its auto-feature set is empty until Mystic Arcanum @11; its content is selections + pact magic.

## NOT-EXPLORED (flagged)
- Subclass feature inventory (only base classes catalogued; subclasses skimmed, not tallied).
- The exact `:spellcasting` block semantics (covered separately in spell-granting-across-silos.md).
- How `?martial-arts-die`-style shared attributes would be declared in a data registry (design-open).
