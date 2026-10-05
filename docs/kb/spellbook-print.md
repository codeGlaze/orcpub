# Spellbook print

The spellbook pages the character PDF can end with, and the spell-card order beside them. Code:
`src/clj/orcpub/spellbook.clj` (layout, pagination, drawing), `src/cljc/orcpub/dnd/e5/emblems.cljc`
(class emblems), `routes.clj` `spellbook-options` / `add-spellbook!` (request and wiring),
`pdf_spec.cljc` `spellbook-spec` (what the client sends). Shipped in PR #50 (2026-10-05), from
upstream issues Orcpub/orcpub#169 (a compact spell sheet) and #520 (cards by level, then name).

## What it prints

Off until "Print Spellbook" is ticked in PDF Options. Pages come after the sheet and before the
cards. Three layouts: the book (two columns, full text), the ledger (one row a spell, a two-line
summary) and the prep sheet (a box to tick per levelled spell, slot pips once at the top). Spells
follow the sheet's Known / Prepared setting. Each class gets a chapter head with its emblem, level,
casting ability, and a crest: save DC on a shield, spell attack on an arrowhead. The running head
repeats each class's DC and attack on every page that class appears on, plus level tabs (in the
head, or inset down the side) and guide words. Defaults: book, run on, tabs in the head.

## Why the layout works the way it does

- **Keep rules.** A heading must never end a column alone. In two columns one spell under it is
  enough, because the next sits beside it rather than overleaf; in one column it keeps two. A
  chapter head keeps the same count, so the room it reserves is the room the level heading below
  it then needs. When the two counts differed, a chapter head ran on and its level heading did
  not, stranding the head.
- **Rebalance the whole block.** Before a full-width unit (the next class's head), both columns of
  the open block are re-split as evenly as order allows. Re-splitting only the first column left a
  tall second column, so the next class could never run on.
- **Never taller than the page.** The re-split may not make a column taller than the space left;
  with no split that fits, the columns stay as they were filled. A spell taller than a column is
  cut into pieces short enough for a continued page's 30pt head and a 22pt level heading above.
- **Class break.** `:runon` puts a later class straight after the last wherever its head and first
  spells fit; `:page` starts it on a fresh page. A "run on only in the top half of the page" rule
  was tried in the mockups and made the two choices print the same thing almost always.
- **Pact casters.** A Warlock's slots exist only at its pact level, so every one of its level
  headings shows the pact slots ("2 pact slots, cast at 3rd"), not "no slots yet".
- **Summaries.** SRD descriptions often open with how a spell looks ("A bright streak flashes..."),
  so the ledger and prep sheet use the first sentence with dice, a save, damage or a condition,
  else the first sentence. List bullets become middle dots: a bullet is outside the WinAnsi set and
  printed as "?".

## Lining figures

Vollkorn's default digits are old-style: "15" reads as "I5", "1st" as "Ist" in a heading. Its
lining figures are OpenType alternates named `one.lf` and so on. A PDF content stream shows glyphs
through the font's cmap and PDFBox applies no OpenType features, so `showText` can never reach
them. Headings, numbers, the crest and the tabs are therefore drawn from the glyph outlines
(fontbox `getPath`, quadratics raised to cubics). That text is not selectable; running text keeps
the old-style figures and stays selectable.

## Emblems

The publisher's class symbols are not in the SRD, so they are not used; only SRD content ships.
Hand-drawn line motifs were tried first and were not good enough. The icons are game-icons.net art
(CC BY 3.0), the set the app already used: 85 vendored under `resources/public/image/emblems`, a
pool of 10 to 15 per spellcasting class, the user's defaults first (harp, holy symbol, leaf swirl,
cross shield, arrow flights, fluffy flame, warlock eye, spell book). The druid pool is the widest
on purpose: leaves, beasts, tracks, fungus, fire, water and moon all read as druid. CC BY asks for
credit where the icons appear, so each author is in `resources/public/image/ATTRIBUTION.md`, the
site footer names them, and the spellbook's last page carries a credit line. Each class's ring has
its own mark (wave, beads, vine, banded rim, compass points, flame tongues, thorns, rune dashes)
and one tick per class level. The server draws only names in `emblems/all-icons`, because the name
becomes part of a resource path.

## Fixed on the way

- **#520, card order.** `add-spell-cards!` computed a sorted list and then paginated the unsorted
  one, so cards came out in pick order. Cards now sort by class, then level and name, or name alone
  with the new spell-order option.
- **Packed sheets lost their cards.** `spellcasting-fields`' packed branch returned only the packed
  fields, without the spell list, so a multiclass caster on the packed layout who asked for cards
  got none. The browser check "packing saves a page" had passed only because of this: the packed
  export was shorter because it had no cards. It now compares the sheets with cards off.
- **"1 reAct.".** `pdf/abbreviate-casting-time` replaced "action" before "reaction", so every
  reaction printed as "reAct." on the cards. The spellbook shares the helper.

## Review history

Greptile's first review of #50 found four real problems, all fixed in `21c78c1b`: a rebalanced
column could run into the footer, pact headings said "no slots yet", several classes' labels ran
into the header tabs, and the prep sheet's slot pool did not wrap.
