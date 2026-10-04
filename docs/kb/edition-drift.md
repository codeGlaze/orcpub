# Edition drift — what changes between 2014 and 2024, and where it lands

Worked from the 5etools data for two-weapon fighting, because it is the case this branch already
had hand-written code for. The point is not the rules; it is which *layer* an edition difference
hits, because that decides whether the data model absorbs it or has to change.

Short excerpts quoted for comparison. The 2014 base rule and fighting style are SRD 5.1; the feats
are not, and are described from the 5etools entries rather than reproduced whole.

## Two-weapon fighting, side by side

| | 2014 (PHB) | 2024 (XPHB) |
|---|---|---|
| the base rule lives in | the **Two-Weapon Fighting action** | the **Light weapon property** |
| eligibility | "a light melee weapon that you're holding in one hand… a different light melee weapon in the other" | attacking with a Light weapon grants the extra Bonus Action attack; the off-hand weapon must lack Two-Handed |
| the fighting style is | a class **optional feature** (`FS:F`, `FS:B`, `FS:R`) | **a feat** (category `FS`) |
| Two-Weapon Fighting style | "you can add your ability modifier to the damage of the second attack" | same benefit, worded against the Light property |
| Dual Wielder prerequisite | none | **level 4+, Str 13 or Dex 13** |
| Dual Wielder ASI | none | **+1 Str or Dex** |
| Dual Wielder benefits | +1 AC while wielding a separate melee weapon in each hand · two-weapon fighting with non-light one-handed melee weapons · draw/stow two one-handed weapons | an extra Bonus Action attack with a Light weapon, off-hand must be Melee and non-Two-Handed · draw/stow two non-Two-Handed weapons |
| Crossbow Expert, dual-wield clause | bonus-action attack with **a hand crossbow** you are holding | **damage only** — add your ability modifier to the Light property's extra attack when it is with a Light crossbow |

## What each difference costs us

**The +1 AC is 2014-only.** It is simply absent from the 2024 feat. So `:two-weapon-ac-1` and the
`{:ac-bonus {:bonus 1 :dual-wielding? true}}` it now compiles to are **edition-specific content**,
not a permanent fixture. Nothing in the model needs to change; the content does.

**"Light" moved from the rule to the weapon.** 2014: the action requires light melee, and the feat
lifts that. 2024: the property carries the rule, and the feat changes what the off-hand may be.
Both are expressible as tag specs on `?dual-wield-weapon-specs` — `{:light? true :melee? true}`
versus `{:melee? true :two-handed? false}`. **The data model absorbs this entirely.**

**Fighting styles changed category.** 2014 they are class optional features; 2024 they are feats
carrying `category "FS"`. The 5etools data models this as a **type within the feat collection**,
which is the same shape as pools-with-types — so a 2024 game's fighting styles are a filtered view
of feats, not a separate pool.

**Crossbow Expert changed layer entirely.** 2014 it is an *eligibility* rule (a hand crossbow
qualifies for the off hand). 2024 it is a *damage* rule (add your modifier when the extra attack is
with a Light crossbow). Same feat name, different vocabulary — the first is
`?dual-wield-weapon-specs`, the second is `:damage-bonus` with a requirement.

## Two data traps found while checking

**Light Crossbow is not Light.** It carries `ammunition? loading? ranged?` and no `::light?`. Hand
Crossbow does carry it. So `{:light? true}` is not "a light crossbow", and
`{:light? true :ranged? true}` is uniquely the hand crossbow across the SRD weapon set. A creator
writing the obvious thing gets the wrong weapon.

**The 2014 Dual Wielder implementation dropped a word.** The feat says "one-handed **melee**
weapons"; the code used `one-handed-weapon?`, which is only `(not two-handed?)`. A hand crossbow
qualified off a melee feat. Fixed when the predicate became a tag spec — `{:two-handed? false}`
sitting beside the rules text makes the missing `:melee?` obvious in a way a function name never
did.

## The general lesson

Ask which layer an edition difference hits:

- **content values** (the +1 AC exists or does not) — the model is fine, author different content
- **which vocabulary applies** (eligibility vs damage) — the model is fine if both vocabularies
  exist, which is the argument for having them
- **where a fact is stored** (the rule vs the weapon property) — absorbed if eligibility is data
  rather than a predicate
- **how content is categorised** (optional feature vs feat-with-a-type) — absorbed if types are a
  filter over one collection rather than separate collections

All four were absorbable here. That is a reasonable sign the shapes are right, and a cheap test to
re-run whenever a new edition or setting lands.

## Provenance

5etools `feats.json` / `optionalfeatures.json`, read locally. **The repo is not checked out in this
container and this data is not committed** — it lived in a scratch directory for one session. Anyone
repeating this needs their own copy.
