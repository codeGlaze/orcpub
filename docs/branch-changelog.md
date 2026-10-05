<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch,
     fill it in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold strips the guidance and the
     "Why" section; a "## Highlights" section (if earned) survives. Undated by design. -->

# Branch changelog — `feature/spellbook-print`

## Why this branch exists

Two long-standing requests about printed spells, taken together because they meet in the
same place. #169 asks for a compact spell sheet: the full sheet prints a page per spell
section and the cards are a stack to cut out, and neither lets a player see their spells
at a glance with the numbers they cast with. #520 asks for spell cards in the order of the
class spell list. The cards were meant to be sorted already; the sort was computed and then
never used, so cards came out in the order the spells were picked.

The spellbook is new pages after the sheet, in three layouts (a two-column book with the
full text, a one-row-a-spell ledger, a prep sheet with boxes to tick), with each class's
save DC and attack on every page it appears on. The class emblems are game-icons.net art
(CC BY 3.0), pickable per class; the publisher's class symbols are not SRD and are not used.

While wiring the export, a multiclass caster on the packed sheet layout turned out to send
no spell list at all, so asking for spell cards printed none. That is fixed here because the
spellbook reads the same list.

## Highlights

Characters can now print a spellbook: every spell they know or have prepared, set out by
class with its save DC and attack on every page, as a full-text book, a one-line ledger or
a prep sheet to tick off. Each class is headed by an emblem the player can choose.

## Added

- Spellbook pages in PDF Options, after the sheet and before the cards: Spellbook (full text, two columns), Ledger (one row a spell) or Prep sheet (a box to tick per spell, slot pips once at the top). (`647fec9e`, `1262c0cc`)
- Page breaks that keep a level heading with its first spells, never split a spell unless it is taller than a column, and let a second class run straight on or start a fresh page. (`647fec9e`)
- Each class's save DC and spell attack on a shield-and-arrow crest at its heading, and in the running head of every page it appears on. (`647fec9e`)
- Level tabs in the running head, or down the side a quarter inch in from the edge, where a home printer still reaches. (`647fec9e`)
- A class emblem per class, chosen from 10 to 15 game-icons.net icons, drawn inside a ring with one tick per class level and a mark for the class. (`d8ece17a`, `1262c0cc`)
- A spell order option, by level then name or by name alone, for the spellbook and the cards. (`c4a627ca`, `1262c0cc`)

## Fixed

- Spell cards print in class-list order, by level then name, instead of the order the spells were picked (#520). (`c4a627ca`)
- A multiclass caster on the packed sheet layout gets their spell cards; the packed layout sent no spell list, so none printed. (`c4a627ca`, `a7d77b54`)
