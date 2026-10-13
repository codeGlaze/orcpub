# Making the app's SRD better than the 2014 one

**The goal (owner, 2026-10-07): improve on the 2014 SRD as the app holds it, not reproduce it.**
The 2024 work is the occasion, not the limit. Where the app's 2014 content is missing something,
holds it wrongly, or keeps as prose what could be data, that is analysed and a fix or upgrade is
proposed, for both editions. Nothing here is built yet; it is the proposal list.

Related: [srd-2024-roadmap.md](srd-2024-roadmap.md) (the plan this feeds),
[srd-2024-implementation-notes.md](srd-2024-implementation-notes.md) section 9 (turning prose into
data during import, which this doc extends to the app as it stands).

## How this was measured

By loading each data namespace on `srd52/develop` and counting which fields its entries carry,
then reading the components that display them. Not by grep. Findings marked *verified in code*
were read in the renderer; none has been seen in a browser yet.

## What the app shows today

Browse pages exist for **spells, monsters and magic items** only (a list and a page each, in
`route_map.cljc`). Everything else the SRD contains is either inside the character builder or
absent:

| SRD content | in the app | browsable |
|---|---|---|
| spells, monsters, magic items | yes | yes |
| weapons, armor, gear | yes, as data | no page of their own |
| classes, races, backgrounds, feats | yes, as builder templates (code, not entries) | no |
| conditions | **no data at all** | no |
| rules glossary, combat, adventuring rules | **no data at all** | no |

## Defects in the shipped 2014 data

**Fixed on `fix/srd-2014-data-defects`** (off `integration`, pushed 2026-10-07, PR not yet opened).
Data corrections only, each value read from SRD 5.1, with a test (`srd_data_shape_test`) seen to
fail on the old data:

| defect | entries | fix |
|---|---|---|
| Skills stored as top-level keys (`:stealth 6`) instead of inside `:skills`, so the stat block never showed them | Adult Green Dragon, Ancient Brass Dragon, Ancient Gold Dragon, Adult Gold Dragon, Succubus/Incubus, Spy | moved into `:skills`; values match each SRD stat block |
| Traits under `:trait` instead of `:traits` | Flying Snake | renamed; Flyby now shows |
| No cost (and no weight) | 42 tools, instruments, gaming sets and packs | SRD prices and weights from p. 70, checked on the rendered page |
| `:frequncy` on an action, so its daily limit was dropped | Alchemy Jug | spelled `:frequency` |
| Typo `:sell-contiainer` | Ball bearings | renamed (nothing reads the field, so no visible change) |

**Not defects, despite how they first looked:**

- **Seven magic items with no description** (Alchemy Jug, Cap of Water Breathing, Driftglobe,
  Sending Stones, Rod of the Pact Keeper +1/+2/+3). They are Dungeon Master's Guide items, not
  SRD: absent from SRD 5.1 and from open5e's SRD data. The app carries a one-line summary for them
  on purpose. An earlier version of this doc listed them as SRD items missing text.
- **18 gear entries still without a cost.** The SRD names them only inside pack contents (alms
  box, censer, vestments) or does not have them (Monster Hunter's Pack, Dragonchess Set).

**Done in PR #58 as well** (owner: same branch, no micro-branches): monster reactions now show,
and the builder can add one; the 15 SRD 5.1 conditions have pages, Orcacle results and links from
stat-block condition immunities. Browser-tested on the production bundle (10 checks, 9 of them
seen failing against `integration`).

**Still open:**

| gap | entries | what it needs |
|---|---|---|
| ~~Monster reactions are never shown~~ **fixed in PR #58** | 12 monsters carry `:reactions` (the Marilith's Parry among them) | a Reactions section in `monster-component`, which never reads the field. Re-checked 2026-10-07 on `integration` (`views.cljs` 1609): it draws traits, Actions and Legendary Actions only. The Reactions section that does exist is the **character** sheet's (`views.cljs` 3993, and the PDF). The monster builder cannot author one either: its trait types are Other, Action and Legendary Action |
| No price or weight on any weapon; no price on any armor | all 40 weapons, all 14 armor | the data (SRD 5.1 tables: "Crossbow, light 25 gp 1d8 piercing 5 lb.") and somewhere that shows it |
| 30 weapons link out to Wikipedia | weapons with `:link` | a decision: an SRD page, or nothing |

## What is prose that should be data

Extends the catalogue in implementation-notes 9a with measured counts from the 2014 data.

| content | today | proposal |
|---|---|---|
| spells: concentration | **shown**: spell rows mark it (`spell_annotations.cljc`, from #50), read from the start of the duration string (126 spells). No field of its own | small: a field would let search and filters use it without re-reading the text. Not a missing feature |
| spells: costly materials | **shown**: the gp figure is read out of the component text (52 spells). Whether the material is consumed is not read | add consumed-or-not; otherwise as above |
| spells: damage, save, attack, area | only in the description; just 6 spells carry `attack-roll?` | open5e's fields (damage roll and types, save ability, shape), for both editions |
| spells: several damage rolls | none; open5e has one `damage_roll` per spell | **a list of damage parts** (dice, type, a label such as "on hit" or "explosion"), each scaling on its own. Exact for the 11-15 spells per edition with several rolls: Meteor Swarm deals 20d6 fire *and* 20d6 bludgeoning, Ice Knife scales only its cold explosion, Wall of Ice scales the wall and the frigid air differently. open5e cannot hold this (one dice field; owner decided 2026-10-12 not to ask for a schema change for 2-3 spells per edition), so the generator builds the parts from the SRD text. The homebrew spell builder offers the same parts (owner, 2026-10-12) |
| spells: upcasting | the last sentence of the description | open5e's casting options per slot (dice, targets, duration, area, concentration), plus typed kinds open5e only has as `desc` text: a flat amount (Aid, False Life, Heal, Magic Weapon), a spell-level threshold (Counterspell, Dispel Magic, Globe of Invulnerability), a summon count or challenge rating (the Conjure spells), and hit-point-pool dice (Sleep, Color Spray). Only 12-17 spells per edition need the extra kinds, too few for an open5e schema change (2026-10-12), but the homebrew spell builder could offer the same fields, so custom spells scale too. The generator maps open5e's `desc` to them with a small table |
| monsters: actions | prose: "Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 5 (1d6 + 2) slashing damage." | attack bonus, reach or range, damage dice and type as data. The SRD writes every attack in this one fixed pattern, so a strict parser can take it and report what it cannot |
| monsters: senses, speed | strings ("darkvision 60 ft., passive Perception 9") | named values, so encounters can search and filter on them |
| magic items: charges | in the description (55 items mention charges) | a resource with a recharge rule |

## What verifying open5e's spells taught us (2026-10-13)

Checking every spell value in both editions against the SRD (open5e-upstream-notes.md, "SRD data
checks") found data open5e cannot hold and defects that would have reached the app. What OrcPub's
own spell data should do with it:

**Hold what open5e cannot**
- **Upcasts as data at every slot and character level:** dice, targets, area, duration,
  concentration. A spell card can say "Fireball at 5th: 10d6" without arithmetic.
- **Typed upcast kinds** for what open5e only has as text: a flat amount (Aid), a spell-level
  threshold (Counterspell, Dispel Magic, Globe of Invulnerability), a summon count or challenge
  rating (the Conjure spells). See "What is prose that should be data".
- **Damage parts** in place of one damage roll: Meteor Swarm's 20d6 fire and 20d6 bludgeoning; Ice
  Knife's cold explosion that scales while its 1d10 piercing does not.
- **Complete damage types**, so "every fire spell" is a query. 32 srd-2014 spells had none or too
  few; Magic Missile had no force.
- **Base values present** (dice, area, targets) for spell cards, area templates and the combat
  tracker. 21 spells across both editions had an empty base their upcasts scaled from.
- **What a spell grants**, in its own field, never in damage types: resistance, immunity or
  vulnerability to damage types (Stoneskin, Protection from Poison, Fire Shield), condition
  immunity or a condition it ends (Heroes' Feast). open5e had been putting these in
  `damage_types`; the demand is real. The character sheet could show an active buff's
  resistances. One-off interactions (Disintegrate destroying Wall of Force) stay in the text.

**Keep it right**
- **One pipeline.** The app's data is generated from the corrected source, so a fix lands in
  open5e and OrcPub together (srd-2024-roadmap.md, "One pipeline").
- **The same checks on our own files.** The rules in `check-srd-data.py` read open5e's fixtures;
  the same rules can run over our generated EDN in `lein test`, so our data cannot drift either.
- **Every value traceable.** Each value no rule could compute carries the SRD sentence it was
  checked against (`srd-data-reviewed.json`). The app could show it as a source note.

**Text**
- SRD wording verbatim; our summaries in their own field.
- Clean text (no PDF hyphens, broken words, lost line breaks) is what rules links, condition
  links and previews stand on.

**Next:** monsters likely carry the same defect kinds (attacks as prose, missing damage types,
the legendary-action costs open5e has just fixed). The same method applies.

## Built (PR #58, 2026-10-08)

- **A Rules tab and reference**: all 43 SRD 5.1 rules sections in six groups, plus the 15 conditions.
- **Links in SRD text** to the spells, conditions and rules it names, marked when the data file is
  generated (one pass, a closed list of names), with **previews**: hover on a desktop, first tap
  on a phone with an Open link. Screenshots and the browser test are with the PR.
- **The Orcacle finds rules and conditions** by name.

## Built (PR #61, 2026-10-08)

- **Weapon and armor prices and weights**, in the data and in each row's expanded details on the
  character display (magic items show weight, not the base price).
- **Rules clarifications in our own words:** `resources/public/notes/rules-clarifications.edn`,
  two notes (spell attacks can crit; actions and bonus actions don't trade), each linked to the
  Sage Advice Compendium and shown after its rule, marked as ours. Add more there.
- **Condition links in spell, magic item and monster text**, with previews:
  `srd-conditions/link-conditions`, the generator's patterns in cljc.

## Wanted, not built

- **Spell and monster links in spell and monster text** (Find Steed, Conjure Animals): conditions
  are linked now; spell and monster names need the same closed-list approach, and monster
  previews (a short stat block) earn their place there.

## Features the data would unlock

In rough order of value against effort. Each serves both editions.

1. **Conditions and rules pages.** The largest missing piece: the 2014 SRD's conditions and rules
   are not in the app at all, and the 2024 rules glossary depends on them. Small data, already
   verified for conditions.
2. **Clickable rolls on monster stat blocks**, from parsed actions. The combat tracker and
   encounter builder already exist, so the rolls have a place to go.
3. **Links from text to rules.** A spell that says "Blinded" links to the condition. Needs (1).
4. **Spell filters on mechanics**: concentration, damage type, save, ritual, costly material.
   Concentration and cost are already read for display (`spell_annotations.cljc`), so those two
   filters can reuse it.
5. **Equipment pages** for weapons, armor and gear, with the costs and weights the data is missing.
6. **One search across every content type**, instead of a page per type.

## Where the work would land

- **The data defects** are 2014 bugs in shipped content and edition-neutral, so they go on one fix
  branch off `integration` and are PR'd back to it, not onto the 5.5 trunk:
  `fix/srd-2014-data-defects`. There is no matching 2024 fix branch: 2024 content is new and lives
  on `srd52/develop`, and errors in open5e's data are fixed in the open5e clone.
- **Data enrichment and the features** serve both editions, so by the machinery split they go on
  the refactor tree, not on `srd52/develop`.
- **2024-only additions** (weapon mastery and so on) stay on `srd52/develop`.

## Open

- Weapon and armor prices, and where they would show.
- A navigation entry for the conditions pages (none yet: reached through the Orcacle and stat-block links).
- The order of the features. The list above is a proposal.
- Monster action parsing needs a measured success rate over all 317 monsters before it is promised.
