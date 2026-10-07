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

Contained fixes: each removes a defect with no design choice involved.

| defect | entries | effect |
|---|---|---|
| Skills stored as top-level keys (`:stealth 6`) instead of inside `:skills` | Adult Green Dragon, Ancient Brass Dragon, Ancient Gold Dragon, Adult Gold Dragon, Succubus/Incubus, Spy | the stat block reads only `:skills`, so these bonuses are never shown (*verified in code*, `views.cljs` `monster-component`) |
| Traits under `:trait` instead of `:traits` | Flying Snake | its Flyby trait is never shown (*verified in code*, same component) |
| Typo `:sell-contiainer` | Ball bearings | the field is ignored |
| No `:description` | Alchemy Jug, Cap of Water Breathing, Driftglobe, Sending Stones, Rod of the Pact Keeper +1/+2/+3 | seven SRD magic items with no text |
| No cost | 60 of 162 gear entries, including every pack and instrument | the SRD prices all of them (Burglar's Pack 16 gp) |
| No cost or weight on any weapon; no cost on any armor | all 40 weapons, all 14 armor | the SRD tables give both: "Crossbow, light 25 gp 1d8 piercing 5 lb." (SRD 5.1) |
| 30 weapons link out to Wikipedia | weapons with `:link` | an outbound link where an SRD page or nothing would serve |

## What is prose that should be data

Extends the catalogue in implementation-notes 9a with measured counts from the 2014 data.

| content | today | proposal |
|---|---|---|
| spells: concentration | **shown**: spell rows mark it (`spell_annotations.cljc`, from #50), read from the start of the duration string (126 spells). No field of its own | small: a field would let search and filters use it without re-reading the text. Not a missing feature |
| spells: costly materials | **shown**: the gp figure is read out of the component text (52 spells). Whether the material is consumed is not read | add consumed-or-not; otherwise as above |
| spells: damage, save, attack, area | only in the description; just 6 spells carry `attack-roll?` | open5e's fields (damage roll and types, save ability, shape), for both editions |
| spells: upcasting | the last sentence of the description | a scaling rule: per slot above N, add dice or targets |
| monsters: actions | prose: "Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 5 (1d6 + 2) slashing damage." | attack bonus, reach or range, damage dice and type as data. The SRD writes every attack in this one fixed pattern, so a strict parser can take it and report what it cannot |
| monsters: senses, speed | strings ("darkvision 60 ft., passive Perception 9") | named values, so encounters can search and filter on them |
| magic items: charges | in the description (55 items mention charges) | a resource with a recharge rule |

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
  branch off `integration` and are PR'd back to it, not onto the 5.5 trunk. There is no matching
  2024 fix branch: 2024 content is new and lives on `srd52/develop`. Not started; the branch needs
  the owner's approval by name.
- **Data enrichment and the features** serve both editions, so by the machinery split they go on
  the refactor tree, not on `srd52/develop`.
- **2024-only additions** (weapon mastery and so on) stay on `srd52/develop`.

## Open

- Which defect fixes to take, and on which branch.
- The order of the features. The list above is a proposal.
- Monster action parsing needs a measured success rate over all 317 monsters before it is promised.
