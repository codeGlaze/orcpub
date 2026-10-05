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

**What changed.** Nothing in the rules — this is ours. With both editions enabled, the 87
spells that exist in both with differences appear twice under identical names.

**Idea.** Append the version at render time rather than storing it in the name. The pattern
already exists: `pdf_spec.cljc` appends a parenthesised qualifier to a spell name when
printing, and `options.cljc` computes a display name for selection UI. Two chokepoints, not
twenty. Stored names stay clean, so a character saved in both-mode carries no tag that becomes
wrong when the user picks one edition. The collision set is computable from the delta, so the
qualifier never needs hand-maintaining.

**Constraint that rules out a badge.** `pdf.clj` renders the spell name as a bare string and
measures it for layout. A chip in the spell list leaves the printed sheet with two identical
cards.
