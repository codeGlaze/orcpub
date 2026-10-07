# SRD 2024 — what we still need to build, and ideas for how

**Running notes. Append as things are learned; this is not a finished plan.** The point is
to record what the 2024 rules need that the app cannot currently express, and any idea about
how it might be done, so nothing is rediscovered.

Counts are from the open5e repository's per-publisher data
(`data/v2/wizards-of-the-coast/srd-2014` and `srd-2024`), cross-checked against the SRD PDFs.
Spells match the PDFs exactly in both editions (319 and 339), which is why that source is
trusted here. See `srd-pdf-as-source-of-record.md` for how the sources relate.

## The structural shifts, measured

| | 2014 | 2024 |
|---|---|---|
| Species | 13 | 9 |
| SpeciesTrait | 93 | 51 |
| Background | 1 | 4 |
| BackgroundBenefit | 5 | 20 |
| Feat | 1 | 17 |
| FeatBenefit | 2 | 35 |
| Spell | 319 | 339 |

Species gained Goliath and Orc and lost Half-Elf and Half-Orc. The other four that vanish —
High Elf, Hill Dwarf, Lightfoot, Rock Gnome — were never species: they are 2014 records with
`subspecies_of` set, i.e. subraces. In 2024 **no** species has `subspecies_of` at all.

## 1. Subraces became lineages

**What changed.** The subrace relationship is gone. Three species instead carry a lineage
trait: Dragonborn's Draconic Ancestry, Elven Lineage, Gnomish Lineage. A lineage is chosen at
level 1 and grants more at **levels 3 and 5** ("Elven Lineages" table: `Lineage | Level 1 |
Level 3 | Level 5`), and Gnomish Lineage additionally lets the player choose which ability
score casts what it grants.

**What we cannot express.** Level-gated grants. `:props` has no level condition, so "gain X at
level 5" is not authorable. This is already pinned in
`content-extensibility-direction.md`; lineages make it load-bearing rather than optional, and
the FTD analysis hit the same wall from the dragonborn side.

**Idea.** The engine already does level conditions — breath-weapon dice scale off
`?total-levels`. The gap is exposing that declaratively. A `:props` entry that carries a level
alongside its effect, compiled into the conditional the engine already evaluates, would cover
lineages and FTD's gem-dragon features with one mechanism. Worth checking whether the existing
draconic-ancestry pool can carry it before designing anything new.

## 2. Backgrounds grant ability scores and an origin feat

**What changed.** A 2024 background is five typed benefits. From `BackgroundBenefit`:

| type | example |
|---|---|
| `ability_score` | "Intelligence, Wisdom, Charisma" |
| `skill_proficiency` | "Insight and Religion" |
| `tool_proficiency` | "Calligrapher's Supplies" |
| `equipment` | "*Choose A or B:* (A) Calligrapher's Supplies, Book (prayers)…" |
| `feat` | "Magic Initiate (Cleric)" |

**What we cannot express.** Two of the five. The app's background shape is `:profs`,
`:equipment` and `:selections`, with no slot for an ASI or a granted feat. Worse, backgrounds
are not data: the one built-in lives as `acolyte-bg`, a `def` in `spell_subs.cljs` containing
live calls, and reaches the builder through a subscription. There is no data namespace to add
to.

**Idea.** The ASI is the same spread the refactor already converged on for feats
(`[amount pool]`), so it may need no new vocabulary — only a place to put it. The granted feat
is a grant from the feat pool with a fixed key, which is the `grant {:pool :feat :key …}` form
the direction doc describes. The real work is moving backgrounds out of `spell_subs.cljs` into
data, and that belongs to the content-extensibility track rather than here.

**Caveat.** The benefit descriptions are prose. `"Intelligence, Wisdom, Charisma"` is a string,
not three ability keywords, so something still has to parse them. Structured one level, not
mechanised.

## 3. Fighting styles became feats

**What changed.** 2014's SRD has one feat (Grappler). 2024 has 17, and four of them are
fighting styles: Archery, Defense, Great Weapon Fighting, Two-Weapon Fighting. Also present
are six Epic Boons and Ability Score Improvement as a feat.

**Why it matters.** `edition-drift.md` claimed this from 5etools data that no longer exists;
it is now confirmed from the SRD. It is also the cleanest case of the "how content is
categorised" axis: a 2024 game's fighting styles are a filtered view of feats, not a separate
collection.

**Idea.** If feats are a pool and fighting styles are a filtered view of it, this needs no new
mechanism — only a tag on the feat entries and a filtered grant. The refactor's
`fighting-style-vocabulary-gap.md` is the place to check before assuming that.

## 4. Monster stat blocks changed format

**What changed.** 2014 writes `Armor Class 15` and `Challenge 1/4`; 2024 writes
`AC 15 Initiative +2 (12)` and `CR 1/4`. Creature types moved too — the 2014 Goblin is a
Humanoid (goblinoid); 2024 has Goblin Minion, Goblin Warrior and Goblin Boss, all **Fey**.

**Consequence.** The two editions cannot share a monster parser, and a monster dataset that
matches 2014 counts (322-323) is 2014 data whatever it is labelled. One published dataset
(`adkinn/srd-5.2.1`) is exactly this: open5e's `wotc-srd` document, which is 2014, republished
with `source: srd-5.2.1` on every record.

## 5. Telling two editions of the same item apart (UX)

**What changed.** Nothing in the rules — this is ours. With both editions enabled, every spell that
exists in both editions with any wording difference appears twice under identical names — 312 of
the 317 shared spells, not the 87 a field-only comparison suggested.

**Idea.** Append the version at render time rather than storing it in the name. The pattern
already exists: `pdf_spec.cljc` appends a parenthesised qualifier to a spell name when
printing, and `options.cljc` computes a display name for selection UI. Two chokepoints, not
twenty. Stored names stay clean, so a character saved in both-mode carries no tag that becomes
wrong when the user picks one edition. The collision set is computable from the delta, so the
qualifier never needs hand-maintaining.

**Constraint that rules out a badge.** `pdf.clj` renders the spell name as a bare string and
measures it for layout. A chip in the spell list leaves the printed sheet with two identical
cards.

## 6. The rules tier — smaller than feared, but only readable by hand

**What is there.** `Rule.json` drops from **227 records (2014) to 56 (2024)**; `RuleSet` from
41 to 11 (Combat, D20 Tests, Damage and Healing, Exploration, Proficiency, Multiclassing and
so on). Conditions are **15 in both editions**, same names. So the whole rules tier is roughly
**71 prose records**, not an open-ended sprawl — a readable amount.

**Where the dial candidates actually live.** Not where you would look. 2024's `Rule.json` has
no record mentioning grapple, shove or exhaustion, where 2014 has dedicated rules for each
("Grappling", "Grapple Rules for Monsters", "Shoving a Creature"). They moved into
**conditions**: in 2024 grappling is an Unarmed Strike option that imposes the Grappled
condition, rather than a contest rule of its own.

**Exhaustion is the cleanest rules-dial case found so far.** 2014 is a six-row table, each
level a different effect (1 disadvantage on ability checks, 2 speed halved, 3 disadvantage on
attacks and saves…). 2024 is one formula applied uniformly:

> *D20 Tests Affected.* When you make a D20 Test, the roll is reduced by 2 times your
> Exhaustion level. *Speed Reduced.* Your Speed is reduced by 5 times your Exhaustion level.

A table lookup becomes arithmetic over a counter. No content changes; the engine computes
differently. Nothing in the content dial can express this.

**Grappled gained real mechanics**: 2024 adds Disadvantage on attack rolls against anyone but
the grappler, and makes the grappled creature Movable (the grappler can drag or carry them).
Neither exists in 2014.

### A measurement caution, learned the hard way

Diffing condition bodies reports all 15 as changed and 12 as "rewritten" (similarity < 0.5).
**That number overstates mechanical change.** 2024 restyled every condition into one template
— "While you have the X condition, you experience the following effects", with named bullet
sub-effects — and shifted from third person to second. Deafened scores 0.69 and is the *same
rule* reworded; Grappled scores 0.02 and is genuinely different. The score cannot tell them
apart, exactly as raw string comparison could not tell `1 action` from `Action` for spells.

**Consequence for planning.** Which conditions and rules changed *mechanically* cannot be
settled by diffing. It needs 71 prose records read by a person. That is a bounded, estimable
task — and it is the only reliable way to enumerate the rules dial.

### Idea

Read the 71 records once and produce a table: rule, mechanically changed yes/no, and if yes
whether it is expressible as data, as a constraint, or only as a computation. The last
distinction is the one that matters, and `rules-override-layer.md` already states why: a
permission expressed as a selection constraint can be granted or waived; one expressed as a
computation cannot be overridden at all. Exhaustion is a computation. Grappled's Movable
clause is probably a constraint.

**Data note.** open5e's 2014 text carries soft-hyphen artifacts from the 5.1 PDF
(`long-­‐term`), which their 2024 text does not. Normalize before comparing or they register
as differences.

## 7. Rule interlinking and indexing (owner ask, 2026-10-05)

**What is wanted.** The app is an SRD reference as much as a builder, and the rules side
should interlink and index properly — a reader on a spell or a weapon property should be able
to follow the rule it depends on.

**What already exists upstream.** open5e's `CrossReference.json` is a typed link graph:
`source_content_type` + `source_object_key` -> `reference_content_type` +
`reference_object_key`, with an `anchor` for the display text. Seven source types
(creatureaction, speciestrait, spell, magicitem, weaponproperty…) and six reference types
(spell, creature, feat, conditiondescription, rule…). It is how the **Light** weapon property
points at **Making an Attack** — which is the 2024 "the rule moved onto the property" case,
already modelled as a link rather than as duplicated text.

**The catch: 45 records.** That is a seed, not coverage. Every condition referenced from a
spell, every spell referenced from a monster action, every rule referenced from a class
feature — mostly absent. So the *shape* is reusable; the data is not.

**What already exists in-app.** `homebrew-reference-web.md` maps thirteen kinds of link
between homebrew items, by key and by name, with `reference_web_test` pinning them under a
rename. That is the same problem solved for user content, and it is the obvious place to look
before designing anything: a rules index is a reference web over built-in content rather than
homebrew.

**Idea.** Derive the links rather than hand-maintaining them. Condition names, spell names and
rule headings are a closed, known vocabulary in each edition, so a pass over rule and content
bodies can mint cross-references wherever one appears in another's text — the same way the
reference web finds links between homebrew items. open5e's 45 records then become a
correctness sample to check the derivation against, not the source. Worth deciding early
whether a link is stored (an edge in the data) or computed at render, since that is the same
stored-versus-rendered question the edition name qualifier raised, and the answer should
probably match.

**Open question.** Links are edition-scoped: 2024's Light property points at a rule that reads
differently in 2014, and conditions were rewritten in every case. A cross-reference therefore
belongs to an edition, not to the item — which the `document` field on every open5e record
already assumes.

## 8. The app is already a 2014 SRD. The gap is surfacing, not content.

**Stated by the owner 2026-10-05, and worth writing down because this work kept treating `e5`
as a base to diff against rather than as what it is.** The app already ships most of the 2014
SRD: 319 spells, 317 monsters, 805 magic items (base items expanded per variant), the 12
classes, 162 gear entries, 40 weapons and 14 armor (counted by loading each namespace, 2026-10-07;
an earlier version of this line had grep counts that were several times too high).
That is an SRD reference. Unlike comparable projects it does not surface it well, and most
users do not know it is there. What it shows, what is wrong in it, and what to improve:
[srd-improvements.md](srd-improvements.md).

**Consequences for this track.** The 2024 work is not "add an SRD"; it is "add the second
edition of an SRD we already have". Two things follow:

- Anything built for presenting 2024 content — indexing, interlinking, search, a browsable
  rules tier — should serve 2014 equally. There is no reason to build it edition-specific.
- The rules we hold must be **our own copies in our own syntax, verbatim in content**, so the
  project owns what it serves. Not vendored third-party files. `e55/spells.cljc` is already
  this shape; the question of vendoring open5e's JSON was the wrong question.

## 9. Mechanical enrichment — the axis this track kept dropping

**The ask.** Every entry should carry as much mechanical capability as can be derived: a spell
with a clickable damage roll, Sneak Attack with a button that rolls damage scaled to the
character, and so on. Not required now, but the plan has to carry it, and **both editions need
it** — a 2024-only capability would be worse than none.

**What this means in practice.** When content is imported or re-imported, note what still
needs processing to become mechanical rather than textual. A description that says "8d6 fire
damage" is prose; `{:damage-roll "8d6" :damage-types [:fire]}` is a button.

**What is already available and currently being thrown away.** open5e's `Spell.json` carries
structured mechanical fields, populated as follows over 339 spells:

| field | populated |
|---|---|
| `target_count`, `target_type` | 339 |
| `concentration` | 133 |
| `saving_throw_ability` | 128 |
| `higher_level` | 122 |
| `damage_roll` | 119 |
| `damage_types` | 107 |
| `shape_type`, `shape_size` | 52 |
| `attack_roll` | 42 |
| `ritual` | 29 |

Fireball comes through as `damage_roll 8d6`, `damage_types [fire]`,
`saving_throw_ability dexterity`, `shape sphere/20`, and a `higher_level` string describing the
upcast. That is a clickable damage roll, a save prompt and an area template, without parsing a
word of prose.

**Known debt, recorded rather than fixed.** `e55/spells.cljc` as committed captures only name,
level, school, casting time, range, duration, components and description. **Every mechanical
field above was discarded.** It needs re-emitting to carry them. The same question applies to
every content type not yet done: ask what mechanical fields the source offers before writing
the emitter, not after.

**Idea.** Treat mechanical fields as a separate, additive pass rather than a blocker. The
textual entry is useful immediately; the mechanical fields enrich it later without changing
its identity or key. That keeps "mechanical oomph" off the critical path while making sure the
data is not thrown away on the way in — which is what has been happening.

**Open question.** 2014's `Spell.json` should be checked for the same fields. If open5e
populated them for 2014 too, the existing `e5` content could be enriched from the same source,
which is the clearest case of 2024 work paying for a 2014 improvement.

### 9a. What else is prose that should be data

Damage rolls were one example, not the scope. The general case is **a rule expressed as prose
that a machine could act on**, and it is everywhere — including inside sources that look
structured. Fireball arrives with `damage_roll "8d6"` as data and its upcast as the string
`"The damage increases by 1d6 for each spell slot level above 3."`; the damage is clickable
and the scaling is not.

A catalogue of candidates, to grow as content types are surveyed:

| content | currently prose | could be |
|---|---|---|
| spells | upcast scaling (`higher_level`) | a rule: per slot level above N, add dice |
| spells | conditions imposed, saves to end, triggers | condition keys + when they apply |
| backgrounds | `"Intelligence, Wisdom, Charisma"` | three ability keys |
| backgrounds | `"Insight and Religion"` | two skill keys |
| backgrounds | `"*Choose A or B:* (A) …, 8 GP; or (B) 50 GP"` | a choice between two equipment bundles |
| backgrounds | `"Magic Initiate (Cleric)"` | a feat key plus its sub-choice |
| species traits | speeds, darkvision range, resistances | the `:props` vocabulary that already exists |
| species traits | lineage options and their level 1/3/5 grants | level-gated grants (see 1) |
| feats | `"level 4+, Str 13 or Dex 13"` | a prerequisite expression |
| magic items | charges and recharge ("regains 1d6+4 at dawn") | a resource with a recharge rule |
| magic items | `+1/+2/+3` bonuses buried in description | a modifier |
| magic items | `"requires attunement by a cleric"` | an attunement constraint with a predicate |
| monsters | attack bonuses, damage, save DCs, recharge | the roll layer that already exists |
| class features | scaling tables (Sneak Attack dice by level) | a level-indexed value |
| class features | resource counts (rage uses per rest) | a tracked resource |

**Why this is tractable.** Almost all of it parses against a **closed vocabulary**: ability
names, skill names, damage types, conditions, dice expressions and creature types are finite
known sets in each edition. That is a very different problem from free text, and it means a
parser can be strict — anything it cannot resolve to a known term is reported rather than
guessed, which keeps the failure mode loud.

**Why it is worth the trouble.** The clickable damage roll is the visible payoff, but the same
data drives search ("spells that deal fire damage"), filtering ("items that need attunement by
a cleric"), validation ("this feat's prerequisite is not met"), and the rules dial (a rule
expressed as data can be overridden; one expressed in a sentence cannot). The prose stays —
it is what a reader wants — but it stops being the only representation.

**Sequencing idea, unchanged.** Additive passes. Land the textual entry, enrich later. A key
never changes because a mechanical field arrives, so enrichment cannot invalidate a saved
character.
