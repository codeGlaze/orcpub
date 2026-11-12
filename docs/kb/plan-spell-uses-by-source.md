# How a granted spell is cast: uses, slots and charges by source

How the 2014 and 2024 rules let a character cast a spell they got from a race, feat, item or class
feature, what the app stores today, and the plan to show and track it right on the Spells tab and
the PDF. Written 2026-11-11 on `fix/pdf-export` after the owner found race and feat spell pages
printing the class's slots. Builds on [spell-granting-across-silos.md](spell-granting-across-silos.md)
(one primitive, many wrappers) and [plan-charges-and-casting.md](plan-charges-and-casting.md) (a cast
is one act with a different payment). Nothing here is built.

## The rules: a catalogue of casting modes, by edition and era

**Correction (owner, 2026-11-11):** an earlier version of this page said the editions "differ only
in which mode a race or feat uses by default" (2014 free-only, 2024 free plus slots). Wrong. 2014 is
not one default: it changed over the edition's life, and it has a mode 2024 dropped. So no rule may
be keyed on the edition; every grant states its own mode.

**Sources and marks.** *SRD* = checked against `resources/srd/SRD-5.1.readable.txt` or
`SRD-5.2.1.readable.txt` on `srd52/develop` (line numbers there). *Book* = from a non-SRD book,
from memory, not checked against text: confirm before it becomes data. *Ruling* = an official
ruling, not printed text. 2014 eras: PHB (2014), Xanathar's (XGtE, 2017), Tasha's (TCoE, 2020),
Monsters of the Multiverse (MotM, 2022), all 2014 rules.

| Mode | 2014 | 2024 | Overlap |
|---|---|---|---|
| **A. Slots, as a class spell.** Class lists; domain, oath, circle, patron spells; always prepared | SRD | SRD | identical |
| **B. Cantrip, at will.** Race and feat cantrips (Tiefling Thaumaturgy, High Elf, Magic Initiate) | SRD | SRD | identical |
| **C. Free casts per rest; slots cannot be used.** | SRD: Tiefling Infernal Legacy (51:694, "as a 2nd-level spell"); Mystic Arcanum (51:3713). Book: Drow Magic (PHB); Magic Initiate as printed (PHB); XGtE racial feats (Drow High Magic, Wood Elf Magic; Fey Teleportation resets on a short rest) | SRD: Mystic Arcanum (521:6677); Divine Intervention (521:3704, any Cleric spell of level 5 or lower); Signature Spells (521:7309, short or long rest, at level 3) | both. 2014 PHB/XGtE use it for races and feats; 2024 keeps it for class capstones only |
| **D. Free casts per rest, and slots too.** | Book: TCoE Fey Touched, Shadow Touched; MotM races (for example Fairy, Githyanki). Ruling: Magic Initiate | SRD: Elven Lineage (521:7810), Fiendish Legacy (521:7990), Forest Gnome (521:7848), Magic Initiate (521:8062), Paladin's Smite (521:5133), Faithful Steed (521:5181), Favored Enemy (521:5445, twice), Dragon Companion (521:6493), Gift of the Depths (521:6776) | **late 2014 (TCoE, MotM) and 2024 are the same mode.** 2024 makes it the norm and extends it to class features |
| **E. At will, levelled, no slot.** | SRD: invocations (Armor of Shadows 51:3744, Fiendish Vigor "as a 1st-level spell" 51:3803, Mask of Many Faces 51:3825, and others); Spell Mastery (51:4133, lowest level; a slot to upcast) | SRD: the same invocations (521:6713 on); Spell Mastery (521:7296) | identical |
| **F. A slot is spent, but only N per rest.** | SRD: Mire the Mind (51:3836, slow "once using a warlock spell slot"), Sign of Ill Omen (51:3868, bane) | not found in SRD 5.2.1 | **2014 only** |
| **G. Free, with a per-target limit.** | SRD: Chains of Carceri (51:3783, hold monster at will, once per creature per long rest) | SRD: Contact Patron (521:6669, free, auto-succeeds its save) | rare edge cases on both sides; text, not data |
| **H. Charges or uses on an item.** | SRD | SRD | identical; built (`plan-charges-and-casting.md`) |
| **I. Another resource** (ki or sorcery points) | Book: Way of the Four Elements, Shadow Arts (PHB) | none found as spell casting in SRD 5.2.1 | homebrew only for now |
| **J. Ritual only.** | Book: Ritual Caster (PHB); *check* Book of Ancient Secrets in SRD 5.1 | *check* Pact of the Tome | both |

**Attributes that ride on a mode,** each seen in the text above:

- **Count:** a number (1; Favored Enemy: 2), or proficiency bonus (Forest Gnome, 521:7854).
- **Reset:** long rest (most), short or long rest (Signature Spells; Fey Teleportation), dawn (items).
- **Fixed cast level:** Tiefling's Hellish Rebuke at 2nd, Fiendish Vigor at 1st, Signature Spells
  at 3rd, Spell Mastery at lowest. Everyone else casts at the spell's level, or upcasts with a slot
  where slots are allowed.
- **Minimum character level** before it applies (Tiefling: 3rd and 5th; lineages: 3 and 5).
- **"Always prepared" (2024) versus "know" (2014):** 2024 grants say the spell is always prepared,
  which is what lets slots reach it.

**What the overlap means for the data:** modes A, B, C, D, E, H and J exist in both editions; F is
2014-only; D spans late 2014 and 2024. A grant's mode therefore cannot be inferred from its edition,
its source kind (race, feat, class) or its era. **Each grant states its mode.** The modes reduce to
two independent answers plus the attributes:

- **Free casts:** none, N per reset, or at will (with an optional fixed level).
- **Slot casting:** any slots, none, or a slot spent only N per reset (mode F).

## What the app has (checked 2026-11-11, `fix/pdf-export`)

- **One primitive:** `mod5e/spells-known` (`modifiers.cljc:260`) stores `{:key :ability :qualifier
  :class}`. `spells-known-cfg` (`:237`) can store any map (it already carries `:class-key`,
  `:always-prepared?`). **No uses, reset or slot field.** "once per long rest" lives only in the
  free-text `:qualifier`.
- **The source is a display string.** `:class` is "Tiefling" for a race spell, "Wizard" for
  Magic Initiate's (so Magic Initiate cannot be told from a real Wizard spell by `:class`), "Cleric"
  for domain spells with `:qualifier "Domain"`.
- **Homebrew:** the `known-spells` row (`homebrew_shapes.cljc:303`), races and subraces only:
  `{:level :value {:key :ability :level}}`. Feats have no spell row (the silos gap).
- **Uses:** limited-use traits carry a `units5e` frequency (`units.cljc`), and the player ticks
  them in `::char5e/features-used`, which rests clear (`rests.cljc`). Item charges are separate
  pools (built, `plan-charges-and-casting.md`). No spell grant links to either.
- **App display:** `spells-table` (`views.cljs` ~2208) heads each level "N remaining" from the
  shared slots, never per source; `spell-row` drops the `:qualifier`; `cast-spell-component`
  spends a slot for any spell, a race spell included, and is disabled with no slots. So a
  race-only caster reads "0 remaining" and cannot mark the free use.
- **PDF:** pages are grouped by ability, headed by every `:class` on them (`pdf_spec.cljc` ~390),
  and every first page gets the shared slots (`spell-page-fields` ~597; `packing-classes` ~528).
  The qualifier is printed after the name.

## What the refactor line already has (checked 2026-11-11)

Searched `refactor/main`, `feature/grant-rows`, `srd52/develop` and `feature/extras-companions`.
**Nothing for uses, slots or casting by source to lift.**

- `grants.cljc` (`feature/grant-rows`): the generic grant shape, `{:pool p :key k}` fixed or
  `{:pool p :count n :filter ...}` choice, untrusted-input spec. `grant_pools.cljc` registers
  languages, fighting styles, skills, expertise, tools; **no spell pool**.
- Spells as a pool is the **last** step of that branch's plan (`handoff-grant-rows.md` section 8,
  `pool-grant-map.md` "Spells"), not started.
- `srd52/develop` holds the SRD 5.1 and 5.2.1 text used above, nothing that models the uses.

**What this means for the plan:** the `:casting` map below is the per-grant payload a future
`{:pool :spells :key k}` grant would carry, so build it as a plain map on the grant, independent
of how the grant is declared. Today it rides on `spells-known-cfg`; when spells become a pool it
moves onto the grant unchanged.

## The plan

**One field, read by everyone.** Add an optional `:casting` map to a spell grant (through
`spells-known-cfg`, so the primitive is unchanged):

```clojure
{:free  {:count 1                          ; or :proficiency-bonus, or :at-will
         :reset ::units5e/long-rest        ; the units5e periods rests already clear
         :level 2}                         ; fixed cast level; absent = the spell's own
 :slots :any}                              ; :any | :none | {:count 1 :reset ::units5e/long-rest}
```

Mode by mode: A is no `:casting` at all (today's behaviour); C `{:free {...} :slots :none}`;
D `{:free {...} :slots :any}`; E `{:free {:count :at-will} :slots :any}` (upcasting spends a slot,
as Spell Mastery says) or `:slots :none`; F `{:slots {:count 1 :reset ...}}`; B needs nothing
(cantrips); G and I stay text; H is the item's own `::mi/casts`.

- **The rule every reader uses** goes in one cljc function (beside `shown-picture`'s kind of rule),
  e.g. `char5e/spell-payment cfg` returning which of free, at-will and slot a grant allows. The PDF and the app ask it;
  neither guesses from `:class`. This also fixes Magic Initiate, which `:class` alone cannot.
- **Edition defaults live in the data, not the code.** Each built-in race, feat and invocation
  states its own `:casting`; 2014 and 2024 content simply carry different values. A homebrew grant
  with no `:casting` keeps today's behaviour, so nothing old changes meaning.
- **Uses are tracked where limited-use traits already are:** `::char5e/features-used` keyed by the
  grant, cleared by the Rest box. A count above 1 (proficiency bonus) needs counts, which
  `features-used` lacks: reuse the item-charges pool shape for those, not a third mechanism.
- **Casting reuses the item cast panel** (one act, a different payment): a free use, a slot where
  `:slots?`, a charge for an item. Not a new panel.

What the player sees:

- **Spells tab:** "N remaining" only beside levels that hold slot-cast spells, and only when the
  character has slots. A free-use spell shows its own "1/long rest" with a tick; one that also
  takes slots shows both.
- **PDF:** slot boxes only on pages whose spells are cast from slots; a race or feat spell's uses
  print after its name ("Hellish Rebuke (2nd level, 1/long rest)"); packed groups get no slots when
  none of their spells use them.

## Design, proposed 2026-11-11 (text mockups; not agreed, not built)

**Today's Spells tab** (`views.cljs` `spells-table`): per spell level, a heading "1st Level" with
"N remaining" (shared slots) on the right, then a table: Prepared? / Name, Source, Ability, DC, Mod.,
Attack, expand. One row per source and spell (`[class key]`), so a Tiefling Wizard who also learns
Darkness as a Wizard already has two Darkness rows, each with its own Source, Ability and DC.
Expanding a row shows `cast-spell-component`: a level picker and "cast spell", which spends a slot.

**A. Free casts on the Spells tab (steps 4-5).**
- The level heading's "N remaining" counts slots only; hidden when the character has none.
- A row with free casts shows them under the name, in the row's own small text: "Free: ● once/long
  rest" (one tick per use; ticked = used; the Rest box clears them with the period). At will reads
  "Free: at will", no ticks. A slot limit (Mire the Mind) reads "Slot: ● once/long rest".
- Expanding the row: the cast panel lists every way this row can be cast, each its own button:
  "Cast free (1 left)", then the slot levels when slots are allowed ("2nd · 3 left", "3rd · 2 left").
  The same panel the item casts use (`cast-panel`), with a free use as another payment.

**B. One spell from a class and from another source** (owner's question). Keep **two rows**, as
today, because they differ in more than uses: the casting ability, so the DC and attack (a Tiefling
Wizard's Darkness: CHA DC 13 from the Tiefling, INT DC 15 as a Wizard). Make the pair readable:
- the rows sit together (sorted by name, then source), the second row's name dimmed with "also";
- each row's Source says where it comes from, its free line says what it gives;
- casting from the Tiefling row offers its free cast (CHA DC); from the Wizard row, slots (INT DC).
Alternative rejected for now: one row with both sources, which hides that the DC differs.
Where a grant allows slots too (2024 lineages), its own row offers slots at its own ability.

**C. Magic Initiate, the author's choice (homebrew feat builder, step 6).** The feat builder's
"Spellcasting" checkbox ("Choose a class, gain (2) cantrips and (1) 1st-level spell ...") gains,
when ticked, one setting for the 1st-level spell:
- "How the 1st-level spell is cast": **Free once per long rest** (2014 printed, default) /
  **Free once per long rest, or with spell slots** (2014 ruling, 2024).
- Behind the two choices is the general "How it's cast" block step 6 gives every homebrew spell
  grant: free casts (none / N per period / at will; optional fixed level) and slots (any / none /
  N per period). Magic Initiate shows only the two presets; the full block is for race, subrace
  and feat spell rows.

**PDF for the same cases:** already built (steps 1-3): the class page lists the spell with slots,
the race or feat page lists it with its note ("once/long rest, or a slot").

## The PDF (discussed 2026-11-11, not agreed)

The PDF already lays spells out by source: one page (per-class layout) or group (packed) per
source, each headed with its own ability, DC and attack, so it matches the app's separate default
and stays independent of the app's combine toggle. Open pieces:

- **Marking a limited use** (owner's options: reuse the slot boxes, a blank space, or boxes after
  the text). Recommended: boxes drawn after the spell's text, one per use, as shapes (no font
  glyph), a write-in "_ / N" past a cap. Reusing the slot boxes works only on a page of limited
  spells with one per level; kept as a fallback.
- **Wording** follows whatever the app settles on ("1/long rest", maybe the feature name), measured
  against the narrowest spell line first.
- **Decided direction (owner, 2026-11-11): boxes.** Owner asked whether the slot box can serve
  1/day: yes on a page of one source's limited spells, one per level (a plain Tiefling); no where
  a race shares a page with class spells of its ability (a Tiefling Sorcerer's CHA page holds the
  Sorcerer's slots). Open: slot boxes there plus boxes after the text elsewhere, or one rule.
- **Traits & Feats box (owner, 2026-11-11, mocked with `scripts/design/pdf-mockups.clj`,
  https://claude.ai/artifact/JcHSPnwdJb5R5AbQZnGiWc):** one box for every species, background and
  feat spell, fitted into a free box of an existing spell page, numeral blanked, the bar used as
  packing uses it (caption TRAITS where CANTRIPS goes, source where a class name goes, numbers
  above). Spell names in the row fields; LVL, SOURCE, DC and USES drawn at the names' printed size
  (about 6 pt Nimbus Sans), each column as wide as its widest cell. **Variant 2 (source and DC per
  row) "looks incredible"**; Variant 1 (one source: source and DC in the bar) not commented on.
  **Decided (owner, 2026-11-11):** Variant 1 for one source, Variant 2 for several, with a PDF
  option to turn Variant 1 off (always Variant 2). Features page: plain spell names under Bonus
  Actions and Reactions, no extra data (a use box there would double the Traits box's).
  No casting class: the Features page lines, and a spell page with the Traits box, the rest of the
  page used to write each spell out (its own box, bar naming it) or filled with spell cards.
  On a packed page: the box's slot inputs must be removed (packing already shrinks them for
  headings); with no free box it is fitted like a class (by rows) and starts a page only when
  every box is taken.
- **Bonus-action and reaction spells on the Features page, every character (owner idea,
  2026-11-11):** a comma-separated "Spells:" line under Bonus Actions and Reactions, from each
  spell's casting time, a limited spell keeping its box. For a non-caster the owner leans to
  both: this list and the Traits box.
- **Spell cards, checked 2026-11-11:** cards already print school, level, source, DC and spell
  mod (option), casting time, range, components and material, duration, "(ritual)", and the full
  text (higher levels and damage included); the DC already follows the granting source's ability.
  **Missing, and all that is wanted (owner):** a tag naming the granting feature, and the spell's
  own use boxes like a magic item card's charges.
- **Spell cards** (owner: "a good place to hone in on a handful of useful features"). Today a card
  prints school, level and source, DC and spell mod (an option), casting details, text, overflow
  on the back (`pdf/cards.clj` `print-spells`). Candidates: how it is cast with use boxes (reuse the
  item cards' `item-charges` rule), the DC of the granting source (cards look DC up by source
  name: check a Tiefling card), concentration and ritual tags, what it rolls (`spell-rolls.edn`),
  a one-line upcast note.
- Not yet looked at: how the spellbook pages treat a race "source" (they look up a class emblem).

## Next mockup round (planned 2026-11-11, after a round with two failures)

**Done 2026-11-11, version 5 of the review page:** bold fixed by rendering a saved copy; the
non-caster page needed `spellcasting-class-N` (a mock omission, not an app bug: the builder sends
one per page of spells); write-ups drawn, not written into rows. Open: write-ups or spell cards on
a page with no Spellcasting feature; the prepared circle on written-out lines.

Review page: https://claude.ai/artifact/JcHSPnwdJb5R5AbQZnGiWc (version 4 is the last published).
The harness's scenes for this round are written but uncommitted on agents/develop
(`scripts/design/pdf-mockups.clj`): per-class V1 and V2, a packed Wizard 3 / Cleric 3 page, a
non-caster write-up page, the Features page lines. What failed and the plan for each:

1. **Drawn bold renders lighter than the app's heading** (seen on the packed page, beside
   Cleric/Wizard). Test first, in one run: draw a word with the harness and call
   `pdf/draw-column-heading!` on another free box of the same page; then render after saving to
   bytes and reloading. If the reload fixes it, render! always reloads. If not, compare the two
   content streams' font resources.
2. **Non-caster scene crashed:** pass `:spellcasting-class-1 ""`. The Features scene ran after it,
   so it never rendered; nothing else is known wrong with it.
3. **Publish only after looking** at every shot, with an app-drawn heading on the same page as
   the reference for weight and size. Commit the harness only when every scene runs.
4. Two render runs; a shot still wrong is left off and reported.

## No Spellcasting feature: a card page (owner, 2026-11-11)

Written-out spells on the spell sheet were rejected ("a mess"). Instead: no spell sheet page, the
Features page lines, and a spell card page whose first card is a **list card**, which "needs its
own design". The spell cards switch shows on and greyed in PDF Options in this case, with a line
saying why (the card page is the character's only spell output).

The real card, seen 2026-11-11 (`pdf/cards.clj` `print-spells`): bold Vollkorn title, logo top
right, an italic line ("Tiefling Level 1 Evocation DC 13 Spell Mod +5"), a row of red icons with
labels (casting time, range, components, duration), material in italics, then the text. Fronts fill
left to right (right to left is the backs). The italic line gives the spell's level, so Hellish
Rebuke reads "Level 1" though the trait casts it at 2nd: the card's feature tag must say so.

**Owner, 2026-11-11, on the first proposal:** every line shows its level (Cantrip, 1st, 2nd,
"cast at 2nd" where a trait raises it), in every group: at will is not only cantrips (invocations
such as Armor of Shadows, Mage Armor 1st, up to Chains of Carceri, Hold Monster 5th; Deep Gnome's
Nondetection 3rd; Boots of Levitation, Hat of Disguise; Spell Mastery). Show design proposals as
renders, not text sketches. Also check whether item cards got a redesign spell cards lack.

**Grouping (owner, 2026-11-11):** only "At will" is its own group; every limited spell sits in
one list, its boxes saying the count (1, 2, 3, or proficiency bonus at export). The reset is
stated once when all rows share it, else a short SR/LR after each row's boxes.

**Render round 2026-11-11 (not published):** item cards WERE redesigned (framed border with cut
corners, rarity diamonds, italic type line with an attunement badge, double rule, a CHARGES row of
circles, "continued on the back", bottom ornament); spell cards still have the old plain layout.
So: the list card should use the item cards' frame and show uses as the charges' circles, and
bringing spell cards up to the item-card design is a candidate step of its own. Variant B (grouped
At will / Short rest / Long rest) rendered clean; variant A's per-row SR/LR printed over the time
column (needs its own measured column). **Probable app bug seen on a real card:** a long source
name ("Fey Teleportation Level 2 Conjuration DC 13 Spell Mod +5") wraps the italic line into the
icon row ("Mod +5" over "1 B.A."). Mock trouble to sort before trusting the cards in a mock: with
two placeholder cards the Tiefling's own cards did not print and the export added a "did not fit"
page.

**Checked 2026-11-11 (owner asked whether spell cards were designed):** they were not. The
designed work is the spellbook print (#50, `spellbook-print.md`; artifacts "Spellbook Print
Layouts" XwoNRwCsY6TpraqRo76UoH and "Class Emblem Sheet" Xy6dq9k4LiaENfcT94CXGv): book, ledger
and prep-sheet layouts, a chapter per class with its game-icons emblem, a level-tick ring, a crest
(save DC on a shield, attack on an arrowhead). Spell cards had only fixes (card order #520, B&W
icon legibility, artifact A2SgiHMDzNJbR1ANM6eiAA). **Owner: spell cards get designs of their own.**
Candidates to mock: spell cards in the spellbook's language (emblem, DC/attack crest), the long
source name wrap fixed with it; and a Traits & Feats chapter in the spellbook (ledger or prep-sheet
rows with ticks) beside the list card, to compare.

**Spell card design, first mock (2026-11-11, not published):** owner: "complementary design, not
an exact mirror of items". Drawn by `draw-spell-card!` in the harness, beside today's cards:
rounded frame (items are chamfered), the spellbook's level ring as a medallion breaking the top
edge, centred title, school sigil and line, source in small grey capitals, the spellbook's crest
(DC shield, attack arrowhead) instead of the DC/mod text, a labelled 2x2 grid of casting facts, a
uses track (circles, rest type as its label), text. Fix before showing: the uses track's rule runs
through its circles; the material component is missing. The wrap bug was also fixed in today's
cards (`2223006ca`, test `035de7450`).

**Space is the constraint (owner, 2026-11-12):** "these are nice but waste a lot of space"; most
spells should fit the front. Measured over the 319 SRD spells with the app's wrapping
(`draw/split-lines`, text 2.26 in wide): today, 78 pt header at 8 pt text, **59% fit**; the first
mock's ~150 pt header, 24-37%. Header 60/50/40 pt: 65/68/70% at 8 pt, 71/74/77% at 7.5 pt,
80/81/84% at 7 pt. Direction: a compact header of about 40-50 pt (title row with a small level
ring and the crest or the use circles, a school/source row, one facts row), text 7-7.5 pt.

**Compact spell card, mocked 2026-11-12** (https://claude.ai/artifact/MuNQfjD2sXjMWsUHaUNNNM;
harness `draw-compact-card!`): 50 pt header, 7.5 pt text (74% fit): rounded
frame; title row with the spellbook's level ring, title fitted, crest; school row with sigil and
use circles; source in small capitals; one facts row with `sp/abbreviate-*`; material in italics.
**40 pt was tried and dropped:** sources had to be cut and the use label collided with the facts.

**Compact card round 3 (2026-11-12, not published)** after the owner's zoomed crop ("not aligned,
overlapping, you're clearly not measuring; the circles aren't round and the top one is cut off"):
the spellbook's level ring is segments with an open top for cantrips, wrong at 14 pt. Now a true
circle, centred with the sigil, and the harness computes element boxes and reports overlaps and
frame clearance (`LAYOUT` lines). **Stress card** (owner: every field at its maximum; SRD extremes
picked by the harness): fails in five places: title vs crest (Protection from Evil and Good),
source cut when six use circles share its row, facts row off the card ("Until dispelled or
triggered" has no abbreviation), material line off the card (Clone's is a paragraph), "continued
on the back" over the last line. Each needs a rule: title wraps to two lines or the crest drops a
row; uses get their own band when the source row is full; facts wrap to a second line; material
wraps to two lines then points to the back; the text box reserves the footer.
**Owner: icons are designed once and measured, not drawn in code at render time:** a card icon
set as SVG (level badges 0-9, use circle, compact crest with measured number slots, the school
sigils at card size), each with a measured box, placed by those boxes through `draw-svg-icon!`.

**Card header ideas (owner, 2026-11-12, not yet mocked):** a balanced trio, attack on the left,
save DC on the right, a third piece in the middle (proposed: the spell level, which frees the
title row for the full width and fixes the stress title collision). Labels "cheaped" onto the
horizontal rules as legends: each label centred under its value, on a white knockout (measured
width plus about 1.5 pt each side), so value and label share a centre line and the facts row gets
its full width back. The same for SAVE DC / ATTACK under the trio. Both go into the designed icon
set and layout round.

**Card design v2 (2026-11-12, not published):** icons as data (`card-icons`: attack arrowhead
28x15, level medallion 19x19, save shield 20x23, use circle 7x7, each with a measured number
slot), layout computed as boxes and checked before drawing (`card-layout`, `layout-problems`),
labels as legends on the rules. Stress card holds in both configurations. Measured fit at 7.5 pt:
**trio band 57% (dropped, below today's 59%)**, **trio beside the title 67%**. Faults to fix: the
shield sits 2 pt from the frame top; legend at a rule's end leaves a stub of line; icon sheet boxes
overlap.

**Card design v3 (2026-11-12, not published):** owner: level circle back top-left beside the
title (as the compact card had it); attack on a banner with V-notched ends across the top of the
save DC shield, as one crest (`card-icons :crest`, 32x28). Layout right (title wraps beside the
crest on the stress card, rules end at their legends), fit **63%**. Crest faults: the shield's top
edge is stroked through the banner and the attack number (draw the banner white-filled over the
shield); "SAVE DC" is too wide for the shield (use "DC" or nothing); the level circle 0.2 pt inside
the 3 pt clearance.

**Three header designs (2026-11-12, published v2 of https://claude.ai/artifact/MuNQfjD2sXjMWsUHaUNNNM):**
owner: the shapes were too clever and took space; likes name and details top-left; level between
attack and DC was odd; "c" means concentration, not cantrip, and concentration needs a prominent
reserved place. Mocked with `card-layout-3`: **index** (level numeral in a corner, black for
concentration; 67% fit), **tabs** (Name | C | ATK | DC | LVL, dark C tab; 63%), **named** (name and
school line top-left, ATK/DC figures right, black bookmark ribbon for concentration, outlined for
ritual; 67%). Traps found: concentration is a string duration ("Concentration, up to ...") or a
units map with :concentration, not "any map"; Vollkorn's figures are old-style (0 reads as o, 1 as
I): level numbers use the sans. Open gap: concentration plus ritual (Detect Magic) shows only C in
index and tabs.

**Chosen direction (owner, 2026-11-12): name top-left** ("reads most easily even when busy").
Refinements agreed in discussion: concentration and ritual ribbons matter more at the table than
the level; the subheading reads `LVL 9 · Transmutation` (label and figure styled like ATK / DC;
cantrips `CANTRIP`); the use circles move to the right of the subheading row, so the source line
gets the full width. Fallback order on that row, checked by the layout check: abbreviate the school
(Abjur., Conj., Div., Ench., Evoc., Illus., Necro., Trans.), then shorten the uses label to the
rest alone ("LONG REST"), then drop the uses to their own row. Next: build into `cards.clj` on
fix/pdf-export, real exports shown before pushing.

**LVL 9 against 9th Level (2026-11-12, not published):** owner: schools always abbreviated; compare
"LVL 9" with "9th Level". Rendered (`level-styles-scene`); uses share the subheading row and end at
the ribbons. Held back: Vollkorn's old-style figures make "LVL 1" read "LVL I" and "ATK +11" read
"+II" (on every design so far). All card numbers need lining figures: the spellbook already draws
them from the same font (`centred!` / `text!` with `:lining`, `spellbook.clj`).

**Settled spell card (owner, 2026-11-12):** the stress render with `9th Level` is the base, plus:
name top-left (wraps beside ATK / DC); subheading `9th Level · Trans.` (ordinal level in bold, school
always abbreviated, `Cantrip` for level 0); C and R ribbons top-right; **the use circles on the
subheading line**, at its right, ending at the ribbons only when the line is level with them (a
two-line title puts it below them); uses label without a number (`LONG REST ○○○○○○`, one circle for
a single use, it is the tick box); source in small capitals on its own line; facts as
legend columns; material two lines then pointing to the back; body 7.5 pt; footer only on
overflow. Every number in lining figures (spellbook `:lining`).

**Settled card rendered (2026-11-12, not published):** uses on the subheading line without a
number, lining figures throughout (serif text measured with `lining-width`, or neighbours overlap).
Trap: the spellbook's lining `text!` leaves the fill and stroke colours at its text colour, so the
next outline (the R ribbon) and the body text inherit it: reset both to black after it.

**Built 2026-11-12 on fix/pdf-export:** `ff44e16ca` moves the spellbook's lining text and sigils to
`orcpub.pdf.draw` (owner chose the shared drawing namespace over making spellbook functions public);
`3826d84a3` the card in `orcpub.pdf.cards` (owner: "we already have namespaces for this": the
card family's namespace, not a new one); `96387e03f` tests; `02a21e7a8` hides the B&W options.
Details: pdf-generation-architecture.md, "The front".
At will (owner, 2026-11-12): a grant with `{:free {:at-will true}}` (the invocations) prints
"AT WILL" where the uses go, no circles: `routes/add-spell-cards!` maps it to `:uses {:n 0 :label
"AT WILL"}` (`f1fd183db`). Sigils edited down to print: `adb2f8502`.

**Proposed list card (not agreed):** the cards' frame and type; title the source, italic line
"Infernal Legacy · Charisma · DC 13 · Spell Mod +5"; spells grouped the way the rules write innate
spellcasting ("At will", "Once per long rest each"), a box before each limited spell, each line
with the cards' casting-time and range labels; one block per source with its own ability and DC
(so no Variant 1/2 here); overflow onto a second list card. Mock it with the harness
(`card-page-scene`), drawing over the first front (page index 2 on a sheet with no spell page;
find it by rendering, do not guess).

## Build plan (not started; each step its own commit, tests seen failing first)

PDF, in order:
1. **Traits box in packing:** species, background and feat spells become one group packing fits
   like a class (by rows; never the cantrips box unless the page is a non-caster's), numeral
   blanked, its slot inputs removed, bar drawn as packing draws headings (caption TRAITS, source
   name, numbers above). Per-class layout uses a free box of the class page the same way.
2. **Columns:** names in the row fields; LVL, SOURCE, DC, USES drawn (pdf-form-techniques.md:
   drawn, not fields), widths from the widest cell at the rows' printed size. Variant 1 for one
   source, Variant 2 for several; a PDF Options switch for always Variant 2.
3. **Use boxes** after the text, drawn, one per use.
4. **Non-caster:** the Traits box, then each spell written out in a box of its own (bar naming the
   spell, casting facts line, text, "full text on its spell card" when cut), or spell cards: owner
   to pick after the mock.
5. **Features page:** "Spells:" lines of plain names under Bonus Actions and Reactions, from each
   spell's casting time, for every character.
6. **Spell cards:** a tag naming the granting feature and the spell's own use boxes (the item
   cards' charge drawing).
7. **An end-to-end export check** (Tiefling Wizard, packed and per-class) in the PDF export suite.

App and data, after the PDF (steps 4-6 of the build order below): Spells tab separate by source
with a combine toggle and "N remaining" countdowns; Magic Initiate presets; the player's ability
pick for 2024-style grants; Rod of the Pact Keeper scoped to warlock spells (a bug today).

Packed class headings do not draw on a flattened export (checked 2026-11-12): `draw-column-heading!`
finds its bar through the `spells-<box>-1-<n>` form field (`pdf.clj` ~2112), and runs after
`write-fields!` has flattened the form away (`routes.clj` ~998-1010). Players never hit it: the app
never sends `:flatten? true`; only direct callers of `character-pdf-2` (the mockup harness) can.
Moving the drawing before the flatten is not enough: flattening appends the fields' appearances
after the page's content, so a field background would cover the heading.

## Build order (each step small and shippable)

1. **The field and the rule:** `:casting` on `spells-known-cfg`, `spell-payment`, tests over the
   rules table above. No behaviour change.
2. **Built-in data:** 2014 SRD content states its `:casting` by mode: Tiefling (C, fixed 2nd),
   Mystic Arcanum (C), the at-will invocations (E), Mire the Mind and Sign of Ill Omen (F), Spell
   Mastery (E), Magic Initiate (C or D, owner's call), Drow (C). 2024 data on `srd52/develop` does the same when its races land.
3. **PDF:** slots only where `spell-payment` says slot; uses after the name. Fixes the reported bug.
4. **Spells tab display:** per-source uses, "remaining" only where slots apply.
5. **Tracking and casting:** the free-use tick, then the cast panel's payment choice.
6. **Homebrew:** an optional mode (free casts: count, reset, level; slot casting: any, none, N per reset) on the `known-spells` row
   (`homebrew_shapes.cljc`, backward compatible: absent means today's behaviour), and a spell row for
   feats (the silos gap), through the same field.

**Done 2026-11-11 on `fix/pdf-export`:** steps 1-3, `54e609b18` (code) and `43e00ec1e` (tests,
every claim seen failing). The shape built: `:casting {:free {:units u :amount n :level l} | {:at-will
true}, :slots :any | :none | {:units u :amount n}}`, units from `units5e` (any count, any period);
`char5e/slot-castable?` and `char5e/casting-note` are the one rule; `mod5e/merge-spell-cfg` keeps
slots open when a free grant meets a plain class grant of the same spell. Data: Tiefling, the
at-will invocations, Mire the Mind (was wrongly "at will"), Thief of Five Fates, Mystic Arcanum.
The packed PDF keeps a Warlock's free casts at their own level, not its pact level.

**Magic Initiate today (checked 2026-11-11):** the PHB feat is `#_`-discarded (not SRD). The live
paths are homebrew: the feat builder's "Spellcasting" checkbox (`:magic-novice`,
`make-feat-selections` -> `magic-initiate-selection`, `options.cljc`) and the same option list near
`options.cljc:2002`. Both grant the cantrips and the 1st-level spell as plain spells under the class
name ("Wizard"), no qualifier, no uses: the spell merges with a real Wizard's, casts from slots
without limit, and a non-caster cannot cast it at all. Its `:casting` waits for the owner's choice
(printed text, ruling, or both as an author's option).

Steps 1-3 are the fix for the reported PDF bug; 4-6 are the app side. Steps 1-4 touch no saved
data; 5 adds per-grant uses to the character (schema); 6 adds homebrew fields (the shape table).

## Open, for the owner

- Which branch carries steps 1-4 (`fix/pdf-export` is the PDF bug's branch; the app side is wider).
- Whether the 2014 Magic Initiate follows its printed text (free use only) or the ruling (slots
  too).
