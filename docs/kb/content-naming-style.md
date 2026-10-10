# Content naming style

**How OrcPub names things in its content data: Title Case.** Owner decision, 2026-10-09:
"Potion of Healing", not "Potion of healing". It applies to magic items and to everything like
them a player reads as a name: weapons, armor, gear, tools, packs, spells, monsters, conditions,
feats.

## The rule

- **Capitalise every word** except short connecting words in the middle of a name: *of, the, and,
  or, a, an, to, in, on, for, with, from, at, by, into, against, without, via, vs*. A name's first
  word is always capitalised ("The Dark Lord's Ring").
- **Keep the SRD's word order and words**; only the capitals change. "Crossbow, light" becomes
  "Crossbow, Light", not "Light Crossbow" (that is the 2024 SRD's own name for it, a different
  string, and belongs to 2024 data).
- **Keys don't change.** The app keys content by `common/name-to-kw`, which lower-cases, so a
  re-capitalised name keeps its key: saved characters and homebrew are unaffected.

## Where it stands (measured 2026-10-09)

| content | names | not Title Case |
|---|---|---|
| magic items | 805 | 0 (two keep "against" lowercase, which the rule allows) |
| spells | 319 | 0 (three keep "into", "without", "via") |
| monsters | 317 | 0 |
| gear | 162 | **47**, e.g. "Blowgun needle", "Sprig of mistletoe" |
| weapons | 40 | **5**, e.g. "Light hammer", "Crossbow, light" |
| armor | 14 | **4**, e.g. "Chain mail", "Half plate" |

The 56 are a rename for a code branch, not done yet. Our generated SRD files carry open5e's names
as they are; open5e's own convention is also Title Case (their maintainer retitled the srd-2014
poisons, [open5e-upstream-notes.md](open5e-upstream-notes.md)).

## Why

Names are proper nouns in play: "the Potion of Healing" is a specific thing, and players search,
scan and refer to items by name. One convention across every list keeps the app consistent and
matches how the community writes them.
