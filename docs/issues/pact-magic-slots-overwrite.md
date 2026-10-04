# Pact Magic slots overwrite the Spellcasting slots instead of joining them

**Status (2026-09, `feature/one-template-per-style`):** the calculation is
fixed. `template_base.cljc` keeps `?shared-spell-slots` and `?pact-spell-slots`
apart and `?spell-slots` is their sum, so no slot is lost. The packed spell sheet
prints the two pools separately — a Warlock's column carries its own slot
counts. The web sheet and the page-per-class export still show one summed number
per level; showing two there is the display decision this note describes.

`template_base.cljc` builds `?spell-slots` as

    (merge <the Spellcasting table>
           (when ?pact-magic? (warlock-spell-slot-schedule (?class-level :warlock))))

`merge` replaces. Pact Magic and Spellcasting are separate pools in 5e -- PHB
p.164 says a character with both "can use the spell slots you gain from the Pact
Magic feature to cast spells you know or have prepared from classes with the
Spellcasting feature, and vice versa", which only means anything if both exist.
Replacing one with the other loses slots the character has.

Measured, with `opt5e/total-slots` and `t-base/warlock-spell-slot-schedule`:

    Wizard 5 / Warlock 3   spellcasting {1 4, 2 3, 3 2}   pact {2 2}
                           shown {1 4, 2 2, 3 2}     level 2: 2 shown, 3 + 2 pact

    Wizard 9 / Warlock 5   spellcasting {1 4, 2 3, 3 3, 4 3, 5 1}   pact {3 2}
                           shown {1 4, 2 3, 3 2, 4 3, 5 1}   level 3: 2 shown, 3 + 2 pact

    Cleric 3 / Warlock 2   spellcasting {1 4, 2 2}   pact {1 2}
                           shown {1 2, 2 2}         level 1: 2 shown, 4 + 2 pact

The last is the worst shape: a first-level slot count of 2 for a character with
six. It is worse the lower the pact level, because the Warlock's schedule holds
few slots at a low level while a full caster's table holds many.

This is not a PDF problem. `?spell-slots` is a built-character property, so the
web sheet and the export both read the same wrong numbers.

Not fixed here because the fix is a display decision rather than a calculation:
the two pools have to be shown as two things. The sheet has one SLOTS TOTAL box
per level and no second place to put a pact count, so this wants deciding
alongside the spell page layout work in `pdf-overflow-and-page-generation-plan.md`.

What is NOT wrong: a Warlock has no `:level-factor`, so its levels correctly do
not feed `?total-spellcaster-levels`. The multiclass Spellcasting table itself is
right.
