# Changelog

All notable changes are documented here, newest release first. Format: [Keep a Changelog](https://keepachangelog.com).
House style and the branch → release fold: [`docs/branch-changelog.template.md`](docs/branch-changelog.template.md).

<!-- Editing this file: one change per bullet under ### Added / ### Fixed / ### Changed; succinct and
     plain; no AI-jargon; end each with (`shorthash`). No prose intros under a heading. A ### Highlights
     block (≤3 sentences, labeled) is allowed only for an impactful release — see the template. -->

## [Summer Patch] — 2026 (character-load resilience, homebrew salvage & library management, PDF printing)

### Highlights

The **My Content** homebrew library is now manageable — see, organize, disable, move, and de-conflict your content, with imports and exports that no longer spawn silent duplicates or false warnings. Characters that used to blank-screen on a bad load now recover in place, and printable spell cards and card backs read cleanly in black and white.

### Fixed

Character loading & display
- **"A single colon is not a valid keyword" crash + self-heal** — a custom element saved with a blank or symbols-only name derived the empty keyword `:`, an unreadable token that crashed the whole character on load; key generation now guards the blank case, and an already-corrupt save is repaired in place on load (`ba80b78d`).
- **An unreadable character recovers in place** — instead of a blank page it shows a recovery panel with a copyable diagnostic, and error messages persist until dismissed (`d50eaf87`).
- **Character sheets no longer go blank** — an unrenderable section shows a recovery message; the rest of the sheet stays usable (`565c33c0`).
- **The Features tab loads for every character**, a nameless trait shows "[Unnamed feature]" instead of crashing the name sort, and Hunter's Evasion is named (`2a6fde93`, `5c3b073f`, `dd65d66a`).
- **Homebrew class source no longer poisons spell-selection keys** — the source label was folded into the class `:name`, so saved spells/cantrips vanished when it changed; keys now come from a stable class identity, and orphaned saves repair on load (`9a709c0d`, `fe549631`, `a3e26155`).
- **Boolean toggles no longer corrupt data** and self-heal old damage (`1e9f27ec`).
- **Non-ASCII name detection works in the browser** (`d9b23021`).
- **Inline "Custom" content isn't flagged as missing** — a character built with the built-in Custom option no longer triggers a false "Missing Content" warning; the `:custom`/`:none` inline sentinels are recognized as present, not homebrew keys to resolve (`124faa9a`).

Homebrew import / export / salvage
- **"Rename all" resolves duplicate keys in one pass** instead of the 20 → 3 → 1 → 0 re-import crawl (`c037de78`).
- **Multi-source paks survive an imperfect sub-source** — detection is structural (shape), not spec-validity, so one flawed sub-source no longer quarantines the whole pak (`c037de78`).
- **Conflict resolution no longer nils out an item** — a redundant double-rename is now a no-op (`9df1b4ae`).
- **Import can't report success it won't keep** — problems surface at import time against the loader's floor, and success fires only after the write persists (`c037de78`, `7782e831`).
- **Dangling spell references render** with a key-derived name + edit link instead of a blank card, reported once per class (`a4dbfe19`, `73e75c9d`).
- **Old-name spells in imported paks resolve** — 17 pre-2024 wizard-possessive keys (Leomund's, Tasha's, Bigby's…) map to their current SRD keys; loaded homebrew is never overridden (`cf1f4f1c`).
- **Keyword-trap imports are caught and routed to repair** instead of silently vanishing (`d9b23021`).
- **Unreadable storage is preserved** for recovery, not deleted (`eedffc08`).
- **Quota-failed saves warn and offer a backup**.
- **Readable import/export errors** — plain-English console messages, dedup shown as a log line (`eba28a9c`, `e512dc45`); **post-save export** and **autosave-on-empty-template** crashes fixed (`e3c9a9ee`).

### Added

Homebrew resilience & repair
- **Per-entry salvage** — one bad entry no longer quarantines its whole source; valid items stay, broken ones are set aside for repair (`957e09ab`, `7782e831`, `c037de78`).
- **Entry-level repair panel** in My Content — editable Name + Option-source per set-aside entry, Fix & Restore, and Discard (`d34007ff`, `c037de78`).
- **Export runs the same checks as import** — duplicates/cleanups caught on the way out; raw/pretty export stays an unchecked escape hatch (`9df1b4ae`, `c037de78`).
- **Resilient homebrew loading** with a My Content repair panel (`eedffc08`).
- **Builder escape hatches** — draft export, refresh-safe WIP restore, "Save anyway" with placeholders, emergency raw export, and Export & Auto-Fix (`eac350d0`, `e3c9a9ee`).
- **"Show homebrew source on class names" toggle** (`8f94a94c`).
- **Fill-in dialog on export** with live field guidance (`1547cd69`, `22172adb`).

PDF & printing
- **Card-back logo** — "Print logo on card backs" under a new Appearance section; the mark redrawn to print legibly (solid black, filled letters), with a faded-color option for color printers (`e8e560a3`, `99e20389`, `d0f2bfd8`).
- **Printer-friendly (black & white) spell cards** — the baked-red casting/range/component/duration/recharge icons render solid black with white-halo labels; a nested "faded grayscale icons" option offers a softer look (`cb51a4fa`, `dcc8d551`).

Support
- **Report a character that won't load** — from the recovery panel, an auth-gated one-click report (or copyable text) emails the support address, falling back to the existing error-notification inbox so no new config is needed; header-injection-safe, raw capped (`c2bc7d03`, `4fb40a20`, `b88d1413`).

### Changed
- **Import and export share one correction gate**, and the exported library is canonicalized so import → export → re-import is idempotent (`9df1b4ae`, `c037de78`).
- **Quarantine granularity is per-entry**, backward-compatible with whole-source entries, with precise per-entry diagnostics (`957e09ab`, `7782e831`, `c037de78`).
- **Source-less imported content lands in the real "Default Option Source"** instead of a phantom placeholder (`54f4e87d`).
- **Save validation covers every required field** — dropdowns and multi-selects too (`e512dc45`).
- **Save and load share one spec registry** so they can't drift (`ca977e0a`).
- **Normal exports strip meaningless blank flags.**
- **Invalid-key errors are element-specific.**
- **PDF form appearances are baked on generation** — filled fields render consistently across all PDF viewers instead of only in Acrobat, and spell-card generation is more efficient (`45d106b4`).

### Homebrew library management (My Content)

**Added**
- **Move / copy content between sources** — one select-mode mechanism for single or bulk; clobber-free key policy (move keeps the key unless taken; copy always mints a fresh one) (`903f44cb`).
- **Four-level disable hierarchy** — global / source / section / item, checked as an OR. The two new levels (global "all homebrew" + per-section) live in a local overlay store, so they're a per-device view preference that never mutates `.orcbrew` data or travels with an export (`95426d8c`).
- **Passive library health-status card** — surfaces unresolved key conflicts, missing-required-fields, and export blockers; one line per problem *type* with a count. Warning-yellow for attention, red for broken; always-on on the My Content hub, dismissable-and-remembered elsewhere (`b58fe80b`, `79982e03`, `d0338049`, `e5372fed`, `e7040f4a`).
- **Opinionated, summary-first import** — safe defaults resolve conflicts up front with a one-click Import; the full per-conflict panel becomes "Review" (`e90466c1`).
- **Richer duplicate-key resolution** — severity split with honest labeling for the collapse-risk types, "keep both, turn one off" for a deterministic winner, rename the *existing* item, and an internal keeper-picker (`87512e47`, `052e6e55`, `0c30a022`, `862d9b26`).
- **Mutual-exclusion legibility** — per-row twin notes, a library banner, disabled-content badges colored by reason, and swap-on-enable keeping ≤1 enabled twin (`8543d8f6`, `d94973a6`).
- **My Content toolbar redesign** — two-zone (content vs library actions), select mode, and a 3-step delete guard (`49f2aafe`, `8fc497d9`).
- **Disabled-item visibility** — a count, a show/hide toggle, and search within a source (`47758423`).
- **Share a character with its homebrew embedded** — view-only, with a keep-in-library option and collision notice; custom magic items included (`4cae54e7`, `7bf4516a`, `35539c4c`).
- **Source-name-choice modal on import** — when a single-source file's name meaningfully differs from the source its content declares, ask whether to rename or keep, instead of silently guessing (`fa5909cf`).
- **Number→word name repair** for keyword-trap recovery ("9 Lives" → "Nine Lives") (`4c128a66`).

**Fixed**
- **Single-source export/import no longer spawns a duplicate source** — the source is recovered from the content's `:option-pack`, not the browser-mangled filename; a last-resort dedup-suffix strip covers files with no declared source (`40413f17`, `e53a8b71`).
- **"Skip this one" in the conflict modal actually skips** — it was a no-op that imported the colliding item anyway (`47b57793`).
- **The `:route` handler no longer crashes on an unmatched (nil) URL** (`dab319a0`).
- **Dark-on-dark text in the conflict-modal body** (`b100b927`).
- **Custom item save persists the shown type** instead of blanking it (`52c0e40a`).
- **Stale `:key` after a rename** — the item's own `:key` is rewritten so a double rename is a no-op (`0c30a022`).
- **Recovery panel "Fix & Restore" auto-names invalid entries** in one click (`5e196348`, `898478b0`).
- **One home for source-less content** — folded the stray "Unsorted Homebrew" default into "Default Option Source"; "Unnamed Content" stays separate on purpose (nameless sources, for findability) (`a5d18e2f`, `b5ba38d0`).
- **Shared-character links render on first load** — decoded homebrew overlays now force a rebuild so the sheet paints immediately instead of only after a manual refresh (`9db84754`).

**Changed / internal**
- **Health detectors are memoized subscriptions** — one library walk per plugins change instead of dozens per render (`47b57793`).
- **Conflict/export modals aligned to the health-card severity vocabulary** (`6cbd890f`).
- **Dead-code sweep** — removed verified-dead helpers; pre-existing dead code restored with dated investigation markers (`47b57793`, `874d57d5`).
- **Data-driven library list** — empty content-type categories hide; the list is derived (`e3023cd3`).
- **Gitignore deploy-injected static assets** (font-awesome) (`d8331619`).
- **Share buttons and the character-list filter sit flush with their toolbars** — plain form-button styling, header/list variants, and an aligned name-filter input (`4ef95b74`, `e5dbf8da`, `a48db2b5`).

### starting-equipment

**Highlights**

Homebrew classes can now define their **starting equipment** from the builder UI — the
**full SRD form**, not just the easy half. Fixed items, plus choice groups where each
option can grant a **bundle** of items and/or a **nested weapon sub-choice**, so
"(a) chain mail, or (b) leather + a longbow + 20 arrows" and "a martial weapon and a
shield" are all buildable without hand-editing `.orcbrew`. It applies on the character
sheet and round-trips through save, export, and import.

You can also **start from an SRD class**: the builder fills in that class's equipment for
you to tweak, taken from the live class definition rather than a hand-copied table. If you
only change a few things, the exported file stores **just those changes** against the base
class instead of a full copy.

**Added**

- **Starting Equipment section in the class builder** — a homebrew class can grant fixed
  items (`:weapons`/`:armor`/`:equipment`) and rich choice groups. Each choice option is a
  label + one-or-more item grants (from the real weapon/armor/equipment vocabulary) + an
  optional "any simple/martial weapon" sub-choice — i.e. the full SRD equipment form
  (bundles and nested picks), via a serializable `:equipment-selections` shape that
  `class-option` compiles to the same structure the SRD classes use. Applies with no new
  engine path, round-trips through save/export/import, and imported legacy simple choices
  convert to the editable form in one click (`a4f13086`, `5a5f65a8`).
- **"Start from an SRD class"** — a dropdown fills the builder with any SRD class's starting
  equipment. It's read from the live class (by applying the class's own modifier functions),
  so it always matches what the class actually grants. All 12 classes (`2470e1d2`, `3fc5585d`).
- **Save only the changes** — a class filled from an SRD class stores a small "based on
  <class> plus these changes" form instead of a full copy. This lives only in the exported
  file; everything in the running app stays the full form the existing functions already use.
  A "Based on <Class>" banner shows the link, with a **Detach** button to save a full copy
  instead (`6f494788`, `8172e915`, `be43690b`, `bcc05aa1`).

**Fixed**

- **Filling from a class keeps the SRD's own names** — a grouped focus pick stayed "Arcane
  Focus" instead of being renamed "Starting Equipment: Arcane Focus" (the rename would have
  changed the key it maps to) (`d6213b6e`).
- **A choice we don't recognise is never silently dropped** — an unrecognised sub-choice is
  listed out option-by-option instead of vanishing; a genuinely empty one raises an error
  with context (`2950117c`).

**Changed**

- **"Start from a class" reads the live class, not a hand-written table** — nothing to drift
  out of sync; verified by a round-trip test against all 12 classes (`3fc5585d`, `f44324b3`).
- **Notification view components collected into `orcpub.dnd.e5.views.notifications`** — the
  message banner, a reusable callout box, and the shared-content banner now live in one
  namespace; the health/legacy banners render through the shared callout instead of
  hand-rolled boxes (`4621b4c2`, `25f5d9a1`).
- **`lein e2e-server`** boots the full app (Pedestal + in-memory Datomic, no transactor) on
  :8890 for browser tests (`47c413b0`).

### feature/one-template-per-style

**Highlights**

Character sheets are generated from one template per style, and a multiclass
caster's spells can be packed one class to a column so a party of four casters
prints on one page instead of four — with a Warlock's Pact Magic kept as its own
pool. Spell rows mark concentration, casting time and costly materials; magic
item cards print alongside spell cards; and every page carries the site name.

**Added**

- `pdf/sheet-masters` names the file each style grows from and where that style's
  artwork carries its attribution, and `pdf/grow-spell-sections!` reshapes an
  opened master to the number of spellcasting sections a character needs
  (`a78aaaf`).

**Fixed**

- A character with more casting classes than its template held got the features
  and traits page wedged between its spell pages: generated pages were appended
  to the end of the document rather than placed after the last spell page. The
  eight-class fixture shipped as spell pages 3–8, features at 9, then spell pages
  10 and 11 (`de9a746`).
- Spells vanished off printed sheets. `pdf_spec` emits `spells-LEVEL-ROW-1`
  counting from 1 with no gaps, and three templates numbered their fields with
  one: styles 1 and 3 ran level 3 as 1–10, 12, 13, 14, so **Glyph of Warding**
  was dropped from every wizard's sheet and the last row printed blank; style 4
  ran level 2 as 1–6 then 9–13, losing **Continual Flame** and **Darkness**
  mid-list. Style 1's PREPARED ticks carried the same numbering, so a prepared
  level 3 spell printed unticked. `dev/fix_spell_row_fields.clj` renumbers them.
- Style 3 printed an empty HIT DICE box on every sheet — the box is drawn, the
  `hd` field was never there. Style 4's second-page name box was likewise always
  empty: it calls the field `character-name-p2` where the export writes
  `character-name-2`. `dev/fix_missing_char_fields.clj` adds the one and renames
  the other.
- `spell-packing/sheet-geometry` claimed capacity the templates did not have —
  13 rows at style 4's level 2 where only 11 could be filled — and undercounted
  its level 1 at 12 where it holds 13. It is the field count now, with a test
  tying it to the templates.
- Styles 3 and 4 threw `StackOverflowError` for any character with two or more
  casting classes, so those sheets could not be exported at all. Both keep their
  spell page LAST, leaving no page to insert a clone before, and the fallback was
  `PDPageTree.add` — which walks the whole object graph checking for a cycle and
  runs out of stack on these masters. Clones now go in with `insertAfter`, which
  does no such walk. Styles 1 and 2 have a features and traits page after their
  spell pages and were never affected, which is why this survived.

**Changed**

- The 28 templates are now 8: for each style, one to grow from and one with no
  spell page for a character who casts nothing. 44.3 MB to 9.7 MB.
- Style 4 grows from a ONE-spell-page master like every other style. Its master
  was the two-spell-page file, on the reading that its licence footer was baked
  into the artwork — and a baked footer can be spread by cloning but never
  removed, so a last-page-only style needed a plain page to clone and a marked
  page to end on. It is not baked: both pages referenced the same background
  XObject and the marked page was the plain page plus an appended BT/ET block, so
  keeping the marked page alone gives clones that all carry the footer. The
  retired file leaves resources/ 4.5 MB lighter, and the surviving page renders
  byte-identically.
- That page's footer block is four operators rather than six: `0 i` sets flatness
  tolerance, which applies to path curves and not to glyph fills, and `/GS2 gs`
  is the page default, differing from GS0 only in stroke adjustment.
- Style 4's structure tree came off with the dropped page. The tree reaches a
  page through each element's `/Pg`, so the page survived removal from the page
  tree while 242 elements still named it; pruning just those reached 27 of them,
  because `/K` is a dictionary, an array, an integer MCID or a reference by turns
  and ParentTree and ClassMap need handling too. Removing the tree took the file
  from 2622 objects to 1301. The cost is style 4's accessibility tagging — styles
  1 and 2 keep theirs, style 3 never had any — and restoring it means writing the
  pruner properly.
- Exports are smaller at every caster count above one, by 49 KB to 671 KB
  depending on style, and a character with no spellcasting gets a file the same
  size as before.
- Generating a sheet repeats less work. Values were looked up in the form twice
  each and the lookup walks the whole field tree; the prose fields were located
  and measured before checking whether they held anything; a cloned spell page
  re-read its source's widget entries once per clone, which returns the same
  objects every time. A six-caster sheet allocates 162 MB rather than 607, a
  single-casting-class one 51 MB rather than 77, and a character who casts
  nothing no longer scans the pages for spell sections at all.

**Added (spell row annotations)**

- A spell row can carry its concentration, casting-time and costly-material marks
  beside the name, behind `print-spell-annotations?`. Of 319 spells concentration
  touches 126, a costly material 52, a bonus action 14 and a reaction 4.
- FIXED columns, not appended to the name. A `C` among letters is the same visual
  class as the letters — single capital, same weight — so finding it is a serial
  search and the eye has to read every row; a column turns that into one vertical
  sweep. More spacing does not fix a serial search, alignment does.
- Drawn, not written into fields: 11 bytes a row against 671 as form fields
  (6.6 KB against 389 KB over 594 rows), on a branch whose point was smaller files.
- The rows are narrowed by the reserved zone BEFORE the values are written, so a
  long name shrinks to clear the columns rather than running under them. Verified:
  the longest real spell names fit the narrowed row on all four styles.
- Ritual is deliberately not marked — its `R` would sit beside the `RE` of
  reaction, and plain V S M is on nearly every spell, so it is the widest to print
  and the least worth reading.

**Added (packing, server half)**

- The export accepts `:spell-relabels`, the small instruction list the browser
  sends alongside the field map when it has packed a character's spells into
  boxes other than their own numeral. The server applies `relabel-spell-level!`
  and `reuse-cantrips-box!` per instruction and needs nothing else — it never has
  to know what a spell level is.
- Bounds-checked, because it comes from the client and reaches field names and a
  drawn label: section must name a page the document actually grew, box must be
  one of the ten, and label a single digit or nil. Malformed instructions are
  refused and counted rather than thrown on, so a client sending something this
  server does not understand cannot cost the character their sheet. The list is
  capped at ten boxes a section.
- `relabel-instructions` counted sections from ZERO off `map-indexed`, while
  every field name carries a 1-based suffix — so the instructions named a section
  no template has.

- `pdf_spec` split a character's spells by a hardcoded copy of style 1's row
  counts, whatever style was being exported: a style 4 sheet was handed 8 cantrips
  for a box with 7 fields and lost one, and 12 first-level spells for a box that
  holds 13. It reads `spell-packing/sheet-geometry` now, so the counts have one
  home and a test ties them to the templates.

- `spell-packing/packed-fields` turns a packing into the field map the export
  writes: each class holds its own contiguous run of boxes in one column, so
  **four short lists fit one page** where today they take four. Rendered proof in
  `target/packed-demo.pdf`.
- This is also what separates a Warlock's Pact Magic. Every level box carries its
  own `spell-slots` field, so a class holding its own column carries its own slot
  counts — Warlock 2, Sorcerer 4/3, Paladin 4/3, Bard 4 on one page. Grouping by
  ability, as today, merges a Warlock and a Sorcerer into one CHA section and
  writes every box the character-wide total.
- A pact caster is given the first column outright: cantrips in box 0, the one
  level it casts at in box 1 — renumbered as the character levels rather than
  taking a new box — spilling into box 2 only because a level 20 Warlock knows 15
  spells against box 1's 12 rows. That is both simpler than fitting it like any
  other class and what keeps its slot pool off the classes beside it.
- Each column is headed with the class holding it, in the bar of the cantrips box
  it starts with: CANTRIPS small in the narrow compartment a level bar gives SLOTS
  TOTAL, and the class name at 11pt bold centred in the wide one it gives SLOTS
  EXPENDED. A class with NO cantrips — a Paladin, a Ranger — starts at a level box
  whose bar carries a live input, so the name takes the left of that compartment
  and the SLOTS EXPENDED box is moved to the right of it rather than the column
  going unnamed. The label is padded from whatever bounds it on that bar rather than
  from the compartment: a level bar's simply opens at its SLOTS TOTAL field, while
  box 0's puts a divider at x 51-59 right where that compartment begins, so one
  number gave box 0 two points of clearance and a level box nine. A cantrips box has no slots, so both are free there — which is why
  this is only ever done for a box holding cantrips, never one whose slot inputs
  the player writes in.
- The compartments are read off the live fields rather than written down, so they
  follow the artwork: 51.9–91.1 and 103–195.8 on style 1. A style with no
  `slots-expended` field (2 and 4) has the wide one taken from the spell row's
  right edge instead. Box 0 has no slots fields at all and borrows level 1's.
- A name too long even at the 6pt floor is shortened with an ellipsis rather than
  overflowed — at 6pt "Eldritch Knight" still measures 43pt against a level box's
  35 and would print through the divider. A cantrips box has no slots, so the compartment a
  level bar gives to SLOTS TOTAL and SLOTS EXPENDED is dead space there. A class
  with no cantrips starts at a level box whose slot inputs the player writes in,
  so it gets none and the section header names it.

- Each class's spellcasting ability, save DC and attack bonus print ABOVE its
  column's bar, bold and near-black — numbers a player reads mid-turn, set like
  the class name rather than like the CANTRIPS caption beside it. The sheet gives a section ONE such triple and a packed page holds
  several classes whose numbers differ, so the triple is left empty there and
  filled only when a page holds a single class. Sharing the bar with the class
  name did not work: the pair came to 96pt in a 92.8pt compartment, so fitting one
  shrank the other and "Sorcerer" printed as "Sorce…".
- Packing runs on all four styles. The printed level numeral is covered with a
  white rectangle cut to that style's measured digit box (`pdf/numeral-boxes`,
  from `dev/scan_numerals.clj`) rather than a hexagon traced off style 1, which
  works everywhere because the paper around every numeral is white (`a8752173`).

**Added (packing, builder half)**

- `pdf_spec/packing-classes` regroups `spells-known` — which is keyed by LEVEL —
  into the per-class lists the packer takes. That regrouping is the point: the
  shipped layout groups by `:ability`, which is why a Warlock and a Sorcerer share
  one CHA section.
- **Pact Magic is separated in the character model.** `?spell-slots` was
  `(merge-with + <shared table> <pact schedule>)`, so a Warlock/Sorcerer's pact
  slots were ADDED to the shared ones and printed as one inflated number — on the
  normal sheet, today, not only when packed. `?shared-spell-slots` and
  `?pact-spell-slots` are kept apart now, with `spell-slots` still their sum for
  everything that reads it. 5e gives a multiclass one shared table from combined
  caster levels, so shared is right for everyone except a pact caster.
- A pact caster's whole list is reported at its highest pact slot level, because
  that is how a Warlock casts — which is what lets it hold one box however high it
  climbs.
- `:spell-layout` picks `:packed` or `:per-class`; the default is computed from
  the build — packed only when there is more than one casting class AND the style's
  numerals can be relabelled. A single caster already reads down its own page.
- The export accepts `:spell-headings` alongside `:spell-relabels`, bounds-checked
  the same way.

**Added (guards)**

- A full character is written to every style and the values `write-fields!` could
  not place must match `pdf/unsupported-fields` exactly. That report had always
  been returned and never checked, which is how the losses above shipped. Exact
  equality, so a stale declaration fails too once its template gains the field.
- Every indexed field family must run 1..n with no gap, and no two fields in a
  master may share a name.
- `pdf/unsupported-fields` records what a style genuinely cannot print. Style 4
  is the Cthulhu Mythos sheet and carries "Conditions and Insanities" where the
  others carry inspiration, so inspiration is all that is left in it.

- Style 4 has no allies or backstory box, and one general Notes box. Both values
  are written into it under headings rather than dropped
  (`pdf/merged-fields`). Notes is 263×252pt against the 354×369 and 176×219 the
  other styles give those two, so a long backstory shrinks to fit and a very long
  one clips at the 4pt floor — the tail of a paragraph rather than both entries.
  An empty section prints no heading, and a character with neither leaves the box
  blank rather than printing bare headings.

**Added (cards)**

- Spell and magic item card backs carry `dungeonmastersvault.com`, centred at the
  foot of every card. The backs were chosen over the fronts because they cost no
  card content: a blank back leaves the bottom tenth clear below the mark, and the
  fronts are filled to the edge by spell text that would have to give up a line.
  The text a back carries over from its front is laid out to a box shortened by
  the strip the stamp sits in, so a card filled to overflow still clears it.

- Character sheets carry the same line along the foot of every page, at a
  position measured per style off RENDERED pages (`dev/scan_site_line.clj`) and
  held by a test. A page that prints its own line is skipped rather than stamped
  over, and the skip is per PAGE, not per style: style 4 prints the line on its
  spell pages only, and its other pages are stamped like any other.
- Each stamped page gets its own appended content stream. Cloned spell pages
  share the master's stream, so writing into it would have printed the line once
  per clone on every one of them.

- An export's two images are fetched concurrently, before either is drawn. Each
  allows 10s to connect, 10s on the socket and a 20s transfer deadline, and the
  fetch happens holding an export slot — so drawn one after the other, two slow
  images occupied a slot for up to 80s. Started together they cost one image's
  worst case rather than two.
- The route no longer calls `safe-image-url?` before fetching. `safe-image-bytes`
  validates on its own, and ITS resolved addresses are the ones the connection is
  pinned to, so the earlier call only resolved the host a second time. The cheap
  scheme regex stays: it refuses `file://` and `ftp://` with no lookup at all.

**Added (capacity)**

- `ORCPUB_HTTP_MAX_THREADS`, `ORCPUB_PDF_CONCURRENCY` and
  `ORCPUB_PDF_QUEUE_TIMEOUT_MS` let the operator size the export stack for the
  host. Sheet generation is bounded separately from the HTTP pool, so a rush of
  exports no longer competes with logins and saves for the same workers, and an
  export that cannot get a slot is answered 503 with a measured `Retry-After`
  rather than held open until the browser gives up.
- `docs/PDF-EXPORT-CAPACITY.md` documents what an export costs, what the numbers
  mean, and how to size the settings, with the measurements behind them.
- A turned-away export gets a busy page that retries itself, counting down a
  measured interval with jitter and carrying the original request forward, then
  hands over to a button after `ORCPUB_PDF_MAX_RETRIES` attempts. The export is a
  form POST into a new tab, so this needed no change to the builder and none to
  how a finished sheet arrives. The page carries the site header, logo and
  stylesheets, as the privacy and terms pages do.
- `lein e2e-server-busy` runs the e2e server with an export queue small enough to
  reach by hand, for seeing the busy page on a dev machine.

**Added (magic item cards)**

- Magic item cards, opt-in from the builder alongside spell cards. Each card
  carries the item's name, kind and rarity, an attunement badge in the header and
  the clause at the foot, a charge track when the description names a number of
  charges, and rarity-graded cornerwork. Descriptions that overrun continue on the
  back. `dev/measure_item_card.clj` prints the clear space between every pair of
  stacked elements on a worst-case card, so the spacing is measured rather than
  eyeballed.

**Changed (cards)**

- Card icons are drawn from SVG paths instead of 32px rasters. At the 0.25in a
  card draws one, a 600 DPI printer was being asked for about 150 device pixels
  from a 32 pixel source. `orcpub.pdf/svg-path-ops` parses the path grammar the
  icons use; each icon is embedded once per document as a form and referenced
  where it is drawn, because emitting the path per card cost 2.8 KB a card and
  more than doubled a 45-card spellbook. The result is +7% on card pages and
  byte-identical on a sheet with no cards. The `-bw` duplicates are gone: colour
  is applied at the draw site, so one path fills red, solid black or 40% black.
  `resources/public/image/ATTRIBUTION.md` credits the icon authors, which nothing
  did before.
- Card fonts and the image embedder are built once per document by the export
  handler rather than once inside each card function. Both are per-document, so a
  sheet printing spell cards AND item cards carried two complete copies of
  Vollkorn and two of the card-back mark — 35% of the file on card pages.

**Fixed (hardening)**

- The work one request can buy is bounded at the request itself.
  `routes/bound-request` drops a `spellcasting-class-N` name past the ceiling and
  truncates every collection before any part of the export sees the body, so a
  feature added later that reads a list is bounded without being wired up.
  Unclamped, `spellcasting-class-9999` ran 310 seconds from a few dozen bytes and
  died out of memory.
- `safe-image-url?` refuses the private addresses `InetAddress` has no predicate
  for: `fc00::/7` (what private IPv6 actually uses — `isSiteLocalAddress` knows
  only the deprecated `fec0::/10`), the NAT64 and 6to4 wrappers that carry an IPv4
  address inside an IPv6 one, `100.64.0.0/10`, and `0.0.0.0/8` and `240.0.0.0/4`.
  Tests pin both directions, including that public addresses inside those same
  wrappers stay fetchable.
- An image transfer is bounded in TIME as well as bytes. `setReadTimeout` bounds
  each read, so a server dribbling a byte before each timeout held a connection —
  and an export slot — indefinitely: measured, 40 bytes over 12.0 seconds with no
  timeout firing.

**Added (tests)**

- `svg_path_test` covers each path command and then parses every SVG in
  `resources/` as a net. That caught the extractor matching only double-quoted
  attributes when the whole `black/` set uses single quotes — 148 icons that would
  have rendered blank.
- `card_export_test` counts what a saved document actually contains, stripping
  PDFBox's per-subset prefix, so two copies of one face cannot pass as two fonts.
- `pdf_image_fetch_test` drives the fetch transport against a real HTTP server:
  the byte cap against a body with no declared length, the refusal to follow a
  redirect, the transfer deadline against a trickling server, and what the pixel
  budget does with a page served as a 200.

**Fixed (hardening, cont.)**

- The image fetch resolves the host once and connects to that answer. It used to
  resolve for the check and again for the connection, so DNS an attacker controls
  could answer public for the first and private for the second — the address
  validated was not the address talked to. The pin sits on the connection manager,
  because `HttpClientBuilder.setDnsResolver` is overridden by
  `setConnectionManager` and clj-http always sets one. The hostname stays in the
  URL, so certificate and hostname verification are unchanged; rewriting the URL
  to an IP would have meant overriding hostname verification, a worse hole than
  the one being closed. Behind an egress proxy the client connects to the proxy,
  so the pin cannot apply and is skipped — pinning unconditionally failed every
  HTTPS fetch with `not the pinned host`, which only a real fetch revealed.

**Added (packing, every style)**

- Column headings survive the annotation columns: each spell row records its
  pre-reservation right edge, which the bar of a style with no `slots-expended`
  field reads instead of the narrowed row — an 83pt compartment had read 27pt and
  printed "Warlock" as "Warl…" (`a8752173`).
- The CANTRIPS word printed into a box-0 bar is covered by a band measured per
  style (`pdf/cantrips-word-patch`, `dev/scan_cantrips_word.clj`); one band
  either left style 3's word showing or painted through style 4's rules
  (`a8752173`).
- The per-class ability, DC and attack sit on a backing strip, so they read over
  the scrollwork styles 3 and 4 print above the bar (`a8752173`).
- `dev/stress_packing.clj` runs seven caster shapes on every style and fails on
  a spell that goes missing without being reported (`3b87a48a`, `50d122fc`).

**Fixed (packing)**

- A packing that could not hold a class dropped it in silence — a Wizard 20
  beside a Cleric and a Druid printed without the Cleric, 33 spells gone.
  `spell-packing/unplaced` reports what a packing could not place, `pdf_spec`
  falls back to a page per class when anything is, and the builder does not
  offer a layout that cannot hold the character (`3b87a48a`).
- A no-cantrips class leading a free column started at box 0, which the export
  redrew as a level box from style 1's measurements: on style 3 the numeral
  missed the ring, and on every style the class name was clipped by the input
  drawn over it. Box 0 holds cantrips only; the server refuses a label on it
  (`50d122fc`).

**Added (builder)**

- A **Spell Sheet Layout** choice in the PDF options — Automatic, one column per
  class, one page per class — shown to a multiclass caster on a style that can
  be packed, with a line saying what the current setting prints. An untouched
  control sends nil so the computed default stays live (`642796c7`).
- The PDF options are grouped — Character Sheet, Cards, Appearance — and every
  option carries a `?` that opens a line saying what it does. Click rather than
  hover, for phones (`9d3a22ef`).
- `test/browser/spell_layout_pdf_e2e.js` builds a Warlock 5 / Sorcerer 5 through
  the real builder and exports every style under both layouts against the running
  server (`642796c7`).

**Fixed (builder)**

- The Appearance group followed spell cards alone, so printing only magic item
  cards lost black & white and the card-back logo, both of which apply to them
  (`9d3a22ef`).

**Changed (page shell)**

- One sticky header instead of a fixed copy above an inline one. Every header
  control existed twice in the DOM — twice in the tab order, and the whole PDF
  options panel with it. `.app` clips overflow with `overflow-x: clip` now, which
  trims the same overflow without becoming the scroll container that stopped the
  header sticking (`6cd529ee`).
- `test/browser/sticky_header_e2e.js` drives the phone case as a real device
  descriptor: the app picks its layout off the user agent, so a narrow desktop
  viewport renders the desktop tree into a phone width (`4ffc02a7`).

**Changed (tooling)**

- `scripts/test/run-cljs-tests.js` runs the compiled ClojureScript test build in
  headless Chromium; `lein fig:test` only compiles it. The packer and annotation
  tests are in the ClojureScript runner, since both run in the browser.
- Lint is clean: 30 warnings to 0 (`28fd620d`).


### perf/homebrew-builder-loop

**Fixed**

- **The character builder no longer freezes when you switch between Race and Class with a
  large homebrew library.** Three internal caches were keyed on the whole class list, so
  every lookup compared all of it and built out every class's 20 levels — around a second of
  frozen tab, on one click, on a machine also running the server. A Class-tab switch went
  from 1125 ms to 100 ms in development and 654 ms to 92 ms in production, and the page holds
  ~48 MB less (`c90016ac`, `4b67b3f7`).
- **A character change now rebuilds the character once, not twice.** The builder's preview
  pane subscribed with a stray argument, which created a second, independent debounced
  builder over the same character; both ran on every edit (`7eb968db`, `dc667154`).
- **Spell details are built when you open a spell, not when a list of spells is drawn.**
  Listing 41 spells built 41 full descriptions nobody had asked to read (`ebe708f9`,
  `2747553e`).

**Changed**

- **Modifier ordering is linear rather than quadratic**, so character rebuilds get cheaper as
  a character grows: 23.0 ms → 3.0 ms on the JVM and 25.2 ms → 4.9 ms in the browser, with
  output order proven identical in both runtimes across 808 generated graphs (`8785b16a`).

**Added**

- **A browser probe suite for the builder's performance** — longest-task-per-interaction
  under CPU throttling, class-body cost, builds-per-click, CPU profiling by inclusive time,
  and the localStorage measurements. `test/browser/README.md` lists what each answers
  (`d08a792b`, `30bb6355`, `0986cf44`).
- **A functional test for the class handlers** — set-class, set-class-level, add-class and
  delete-class driven for real against app-db. Neither test suite clicks anything, so these
  had no coverage (`0634c5ce`, `1bac07c6`).
- **`docs/kb`** — an indexed knowledge base: the freeze investigation and its root cause, a
  scan of every `memoize` site with the risky ones traced, the localStorage measurements and
  a parked chunked-storage plan, and the verification lessons this cost (`2bb966d8`,
  `0634c5ce`).


### feature/browser-side-character-images

**Highlights**

A character's portrait now reaches the sheet from hosts it never used to. The
browser reads the picture and the export carries the bytes; where the browser is
refused — Pinterest and D&D Beyond send no CORS header — the server fetches it
instead, and both of those work with nothing asked of the user. Neither host had
ever blocked us: they were refused by a single 128 KB constant serving as both the
download ceiling and the ceiling on what may go into the PDF, so a 393 KB portrait
a host handed over without complaint was dropped for weight.

The builder asks before it speaks. A browser read that fails puts the question to
the server, and only when that also comes back no does anything appear — one line
under the field, naming the fault and offering at most one thing to do about it.
Most of what goes wrong is caught from the address alone, before any request: a
page's URL pasted instead of the picture's, a login wall, a missing scheme. http is
upgraded to https automatically once the https address is known to load.

Where nobody can fetch it, the picture can be pasted or copied in — the clipboard
carries the decoded image, so no host has a say.

**Added**

- `orcpub.image-capture` reads a character's picture in the browser: a
  CORS-attributed `<img>` drawn to a canvas, scaled to the size the sheet prints
  and encoded until it fits the 128 KB ceiling. Only the canvas route exists —
  the app's CSP is `connect-src 'self'`, so `fetch` to an image host is blocked
  and attempting it would log a violation on every export, while `img-src` allows
  `https:`.
- `pdf/decode-image-bytes` takes those bytes on the server. The same 128 KB and
  2000×2000 ceilings as `safe-image-bytes`, checked against the ENCODED length
  first so an oversized image never becomes a byte array, and the format read from
  the bytes rather than from the mime type the client claimed.
- The export spec carries `:image-data` and `:faction-image-data`; when they are
  present `/character.pdf` does not fetch at all.
- `POST /image-probe`, which the builder asks as soon as a browser read fails:
  can THIS server fetch THAT picture? The bytes are kept for ten minutes, so the
  export that follows costs the host no second request, and a negative answer is
  remembered too. It answers a boolean and never the picture — the endpoint needs
  no login, and returning fetched bytes would make it a general-purpose proxy —
  and every address rule that guards the export guards it.
- A "Use copied image" button beside the field, so the last resort is one click
  rather than an instruction. It reads a picture the VIEWER has copied; it cannot
  do the copying, because a page-initiated copy of a cross-origin image puts its
  markup on the clipboard and not its pixels -- the same rule that taints the
  canvas, and the reason extensions can do this and pages cannot.
- Paste, for a host that lets nobody read its pictures. The clipboard carries the
  DECODED image -- the browser's own "Copy image" put it there -- so none of the
  host's rules reach it. Two clicks, and no download-and-upload round trip. This
  is the answer for Pinterest and anything else that refuses page and server
  alike.
- An upload under the Image URL field for a host that allows no read. It runs the
  same ceilings, and falls back to the image loader when `createImageBitmap`
  refuses a file the loader renders — it is the stricter decoder of the two.
- `test/browser/character_image_capture_e2e.js` drives both routes through the
  real app. The server refuses loopback addresses, so an image reaching a PDF from
  the test origin can only have arrived as bytes the browser read.

**Added**

- `orcpub.image-url/advise`, which reads the address alone and catches most real
  mistakes before a request is made: a PAGE's address pasted instead of the
  picture's (Pinterest pin, Imgur gallery, Reddit post, Flickr, DeviantArt,
  ArtStation), a login wall (Instagram, Facebook), a missing or non-web scheme, a
  space from a half-copied link, and `http://`, which this page's CSP will not
  display whatever the host does. Dropbox `?dl=0` and Google Drive
  `/file/d/<id>/view` are offered as corrections to take or leave.

  Advice, never enforcement, and a correction only where it is mechanical --
  nothing guesses a picture's address from a page's. Debounced, because the field
  commits on every keystroke and advice that objects to `htt` on the way to
  `https://` teaches people to ignore advice.

**Changed**

- http is upgraded to https automatically, once the https address is KNOWN to
  load. The check is a plain `<img>` in the browser -- no server, no permission,
  and no request that was not about to be made anyway, since the thumbnail loads
  that same address a moment later and takes it from cache. The field changes only
  after it succeeds and says why; a host that serves no https is told instead, and
  its address is left exactly as typed. An http picture cannot be displayed by
  this page at all, so this is not a guess about a suspect address, it is a fix
  for a broken one.
- **One line under a field, never four blocks.** A single unreachable picture
  could raise a scheme warning, a suggested correction, a fetch failure and a
  panel of controls at once -- six lines of prose and three controls to say that
  one picture could not be had. Only the most actionable of them now shows: a
  mechanical correction first, then what the address itself gives away, then what
  the server found, then that it simply did not load. The other ways in wait
  behind one disclosure, and most people never open it.
- Controls live outside notices. A notice says what is wrong; a red panel holding
  a button, a sentence and a file picker is a control surface wearing an error's
  colours, and both halves get harder to read. A correction is now a question --
  "Did you mean https://...?" -- under its notice, and supplying a picture by
  hand is its own labelled block.
- Field notices are a component rather than a red line. `.field-notice` carries a
  severity accent, a panel ground and two parts with two jobs: the fault in the
  severity colour, and the instruction -- the only part anyone acts on -- brighter
  and heavier beneath it. Run together in one colour and one weight, the vaguer
  half reads first and the useful half is skipped.
- **`.red` was unreadable on the app's own background.** `#9a031e` sits at about
  9:1 on white and about 2:1 on near-black, less than half the readable minimum,
  and the app is dark by default. The dark theme now takes a lighter red and the
  light theme keeps the deep one; the same colour cannot serve both. This reaches
  every use of `.red`, not just these fields.
- Inline rather than a hover tooltip, deliberately: these notices carry buttons,
  and `.tooltiptext` disappears when the pointer moves toward it, is fixed at
  130px, and is absent on mobile.


- The builder says nothing about pasting or uploading until BOTH routes are known
  to be shut: the browser refused, and the server's own answer came back no. Most
  hosts that refuse the browser serve the server perfectly well, so speaking up
  earlier asked people to supply a picture that was about to arrive. Measured: of
  sixteen common portrait hosts, nine let the browser read (Imgur, Discord,
  Fandom, Wikimedia, ArtStation, DeviantArt, Google, Tumblr, githubusercontent)
  and most of the rest allow the server.
- Exporting is held while a picture is still being read, so the browser's bytes
  win that race instead of falling through to the server. `capture` carries a
  deadline, so a read always ends and the hold is bounded.
- An oversized picture gives up SIZE before quality, down to what the sheet can
  actually show -- the portrait box is 2.35 x 3.15 inches, so 945px on the long
  edge at 300dpi, against a 200x100 thumbnail on screen. Pixels past that cost
  nothing visible; quality costs something immediately. A picture already smaller
  than that is never scaled, only re-compressed, and going below it happens last.
  Measured: a 5.8 MB noise PNG leaves the browser at 92 KB and full quality, where
  spending quality first had produced 37 KB and a worse picture.


- Pictures are read when the thumbnail loads and when the export panel mounts,
  never on the export click: the export is a synchronous form submit into a new
  tab, and an await in between spends the user activation that keeps that tab from
  being blocked. Bytes are held in app-db keyed by URL, outside the character
  entity — that entity is what gets persisted, and localStorage has a ceiling.
- `docs/CHARACTER-IMAGE-FETCH.md` leads with the browser path; the server fetch is
  documented as the fallback it now is.

**Changed**

- `/image-probe` answers a REASON rather than a boolean, and the builder turns it
  into wording split by what is worth fixing: the link (`not-found`, `redirect`,
  `not-an-image`, `unreachable`, `blocked-address`), the picture (`refused`,
  `too-large`, `too-many-pixels`, `timeout`, `host-error`), or simply waiting
  (`rate-limited`). Telling someone to copy a picture when they have mistyped a
  link is not help. The server never sends a sentence, so nothing it says reaches
  a person unedited, and every address refusal collapses to one code so the
  endpoint cannot be read as a map of what this network can reach.

**Fixed**

- **A picture the host served happily was refused for weight.** The ceiling on
  what the server would DOWNLOAD and the ceiling on what may go INTO the PDF were
  the same 128 KB, so a Pinterest portrait (393 KB, served with a 200) and a
  Wikimedia one (224 KB) were dropped although nothing had blocked them. The two
  are now separate — 2 MB down, 128 KB into the document — and a heavy picture is
  scaled to the printed size and re-encoded to fit, exactly as the browser does.
  What bounds the danger was never this number: the pixel budget caps the decode
  and the transfer deadline caps the time, and both are unchanged. A Pinterest
  portrait now reaches the sheet with nothing asked of the user.


- The builder flashed "Image failed to load" at pictures that were fine.
  `image-error` dispatched when it was CALLED, at render time, rather than
  returning a handler -- so every fresh URL was marked failed before the browser
  had tried it, and only the load took the mark back.

- A picture whose host allows no read stopped displaying in the builder. The
  builder marks a URL failed optimistically as soon as the thumbnail renders and
  relies on the load to take that back; the load handler had captured the flag at
  the moment it was built, when it was still clear, so the mark was never
  withdrawn. The clear no longer reads the flag, and is a no-op when there is
  nothing set, so an ordinary load does not count as an edit.

**Removed**

- `create-monsters-pdf`, which was private with zero callers, and the
  `draw-text-from-top` helper, `HELVETICA_OBLIQUE` font and
  `orcpub.dnd.e5.monsters` require that it was the only user of.

### feat/whats-new-panel

**Highlights**

The site now says what changed. New release highlights open once per browser and
then stay one click away in the footer, so a release is something people notice
rather than something they'd have to go read the changelog to find.

**Added**

- **What's New panel** — the current release's highlights open on the first visit
  after it ships, and the footer link and version line reopen them any time.
  Closing it stamps the release, so it stays shut until the next one (`ee3e4d8b`).
- **`orcpub.whats-new`** — the release entries and the id that gates the panel, in
  one cljc file the panel and the tests both read (`ee3e4d8b`).
- **Twelve Summer Patch highlights under three headings** — library, characters,
  printing — covering the builder freeze, the spell rows that never printed, the
  two styles that could not export a multiclass caster, and the packed multiclass
  layout, alongside the homebrew and portrait work (`9e3017d3`).

### feat/option-picker

**Highlights**

Equipment items are picked from a filtering dropdown instead of a 1037-option native select.
Type to narrow it, scroll the whole list, or walk it with the arrow keys.

**Added**

- Equipment inventory sections use a filtering dropdown: type to narrow, click or press Enter
  to add (`95d38f67`).
- Arrow keys walk the list and scroll the highlight into view, so it can be used without the
  mouse (`ba52a219`).
- The matched text is highlighted, which shows why a row matched when the match lands
  mid-word — filtering by `+1` marks the suffix, not the name (`4082bc20`).
- The dropdown shows how many items it holds and what the keys do (`4082bc20`).
- `scripts/test/run-browser-probes.js` runs every asserting browser probe and fails the run
  if any fails. Neither test suite invokes `test/browser/`, so nothing was checking them
  (`89d49918`, `6e60959f`).

**Fixed**

- An item with no name no longer throws while filtering (`78deb2ad`).
- The dropdown lines up with its input and flips above it rather than running off the bottom
  of the screen (`95d38f67`).
- Two browser probes had been left pointed at a control that no longer existed: one was
  failing unnoticed, the other had quietly stopped taking screenshots (`d51aa979`).

**Changed**

- The Equipment dropdown no longer caps what it shows. The previous 12-row cap left 294 of
  306 magic weapons unreachable unless you already knew the name (`ba52a219`).
- The dropdown menu was flat; it now has depth, a themed scrollbar, a highlight bar and a
  short open animation that respects reduced-motion (`78deb2ad`).
- Removed the growable option-menu namespaces lifted from `redesign/growable-option-menus`.
  Nothing referenced them, and they carry theming, layout modes and page structure — a
  site-wide redesign, not a picker (`233d032e`).
- A probe that stops asserting, or sits silent for 180s, now fails instead of passing
  (`7369cc33`, `ee0b3781`).

## [breaking/2026-stack-modernization]

### Infrastructure

- **2026 full-stack modernization** (`22823da`)
  Java 8 → 21, Datomic Free → Pro, Pedestal 0.5 → 0.7.0, React 15 → 18,
  Reagent 0.6 → 2.0, re-frame 0.x → 1.4.4, PDFBox 2 → 3, clj-time → java-time,
  figwheel-main, lambdaisland/garden, Jackson/Guava pinning.

- **Consolidate dev tooling** (`6249565`)
  Unified `user.clj` with lazy figwheel, nREPL helpers, lein aliases
  (`fig:dev`, `fig:watch`, `fig:build`, `fig:test`), operational scripts
  (`start.sh`, `stop.sh`, `menu`), `:dev`/`:uberjar`/`:lint`/`:init-db` profiles.

- **Merge develop** (`1d50782`)
  Integrate character folders, weapon builder (special/loading properties),
  docker-compose updates from `origin/develop` (24 commits).

### Bug Fixes

- **`:class-name` → `:class`** (`263f290`)
  Reagent 2.x overwrites hiccup tag classes with `:class-name`. Converted all
  UI uses to `:class`; 18 remaining `:class-name` are D&D data keys (correct).

- **Subscribe-outside-reactive-context — phase 1** (`c2290ca`)
  42 fixes across events.cljs, options.cljc, classes.cljc, core.cljs.
  Patterns: direct db read, plugin-data map, track! template cache, SSOT pure fns.

- **Subscribe-outside-reactive-context — phase 2** (`09d7e4c`)
  14 fixes across options.cljc, pdf_spec.cljc, equipment_subs.cljs, views.cljs.
  Patterns: plugin-data threading, reg-sub-raw, move to render scope.

- **Prereq subscribes → pure character fns** (`9cbc25a`)
  22 prereq-fn lambdas in options.cljc converted from `@(subscribe)` to pure
  `(fn [character] ...)` functions.

- **Multiclass/wizard prereqs** (`3249f88`)
  7 multiclass and spell-mastery prereqs in classes.cljc converted to pure fns.

- **`def` + `partial` → `defn`** (`f578cdb`)
  `option-language-proficiency-choice` captured subscribe at load time via
  `partial`. Converted to `defn` for proper reactive context.

### Cleanup

- **Remove 11 orphaned subscriptions** (`bb2400d`)
  4 static map wrappers deleted (superseded by homebrew-aware versions).
  7 unused subs reader-discarded (`#_`) with comments: `all-melee-weapons`,
  `item`, `base-spells-map`, `spell-option`, `spell-options`,
  `filtered-monster-names`, `has-prof?`. Pre-existing tech debt, not caused
  by subscribe refactor.

- **Fix 591 missing-else-branch lint warnings** (`29c9f28`, via `fix/lint-missing-else`)
  Mechanical `if→when`, `if-let→when-let`, `if-not→when-not` across 33 files.
  Scripted fix (`scripts/fix-missing-else.py`) with column-precise substitution.
  Also fixed 2 pre-existing bugs: `when` used instead of `if` for two-branch
  conditionals in classes.cljc:1808 and options.cljc:463.

- **Fix forward-reference lint error** (`792fe3c`)
  `show-generic-error` used before its `def` alias in events.cljs. Changed to
  fully-qualified `event-utils/show-generic-error`.

- **Consolidate lint config** (`7476f10`)
  All linter settings moved from project.clj `:lint` profile to
  `.clj-kondo/config.edn` (single source of truth for IDE + CLI). Lint scope
  expanded to cover `native/`, `test/`, `web/`. clj-kondo bumped to 2026.01.19.
  LSP false-positive suppression via `:exclude-when-defined-by` for re-frame.

- **Dead code cleanup — ~92 vars** (`6bbcd9a`, `b68b917`)
  `#_` reader-discard on dead defs across 10 source files: deprecated ua/scag
  refs, superseded template UI (ability roller, amazon frames), 17 never-dispatched
  event handlers, dead style defs, duplicate constants. Includes cascade cleanup
  (helpers that lost all callers). Each `#_` has a comment explaining why.

- **Redundant expression fixes** (in `6bbcd9a`, `b68b917`, `429152e`)
  Remove nested `(str (str ...))`, flatten `(and (and ...))`, remove duplicate
  destructuring param, remove unused refers, narrow test `:refer` lists,
  fix unreachable code in registration.cljc.

### Enhancements

- **Input debounce** (`d108134`)
  Moved debounce from component-level `input-field` to `debounced-build-sub`
  in subs.cljs (leading+trailing edge, 500ms). Eliminates per-keystroke
  entity/build recomputation.

- **Folder hardening** (`f28f58f`)
  `on-folder-failure` event re-fetches server state on HTTP error. Client +
  server blank-name validation. `check-folder-owner` wrapped with
  `interceptor/interceptor`, returns 404 for missing folders. Named tempid
  `"new-folder"` + `d/resolve-tempid`. `case` default clause in folders sub.
  CSS class fix (`builder-dropdown` → `builder-option-dropdown`).

- **UI polish** (`d163ca9`)
  Zero-warning dev/prod builds, dev-mode CSP nonce, favicon, custom
  `externs.js` for React 18 advanced compilation.

### Tests

- **CLJS test infrastructure** (`b96b1b6`)
  figwheel-main test build, `test_runner.cljs`, pure function tests for
  compute, entity, character accessors.

- **JVM tests for new code** (`6124d9f`)
  `compute-all-weapons-map`, feat-prereqs, pdf_spec pure functions, folder
  routes (CRUD + blank rejection + trimming).

- **Folder validation tests** (in `f28f58f`)
  Blank name → 400, whitespace trimming, nil defaults to "New Folder",
  name unchanged after rejected renames.

### Documentation

- **Migration docs** (`026b031`)
  MIGRATION-INDEX.md, JAVA-COMPATIBILITY.md, datomic-pro.md, pedestal-0.7.md,
  frontend-stack.md, library-upgrades.md, dev-tooling.md, ENVIRONMENT.md,
  testing.md.

- **STACK.md** (in `f28f58f`)
  Library/dependency onboarding guide: architecture diagram, all frameworks,
  build system, profiles, dependency pinning rationale.

### Current Status

- **174 JVM tests**, 444 assertions, 0 failures
- **0 CLJS errors**, 0 warnings (dev + advanced)
- **0 subscribe warnings** in browser console
- **0 linter errors**, 0 warnings

---

## [feature/error-handling-import-validation] (merged)

### New Features

#### Import Validation (`import_validation.cljs` -- new file)
- **Unicode normalization**: Converts smart quotes, em-dashes, non-breaking spaces, and 40+ other problematic Unicode characters to ASCII equivalents on import and homebrew save. Prevents copy-paste corruption from Word/Google Docs.
- **Required field detection & auto-fill**: On import, missing required fields (`:name`, `:hit-die`, `:speed`, etc.) are auto-filled with placeholder values like `[Missing Name]`. Content types covered: classes, subclasses, races, subraces, backgrounds, feats, spells, monsters, invocations, languages, encounters.
- **Trait validation**: Nested `:traits` arrays are checked for missing `:name` fields and auto-filled.
- **Option validation**: Empty options (`{}`) created by the UI are detected and auto-filled with unique default names ("Option 1", "Option 2", etc.).
- **Multi-plugin format detection**: Distinguishes single-plugin from multi-source orcbrew files for correct processing.

#### Export Validation
- **Pre-export warning modal**: Before exporting homebrew, all content is validated for missing required fields. If issues are found, a modal lists them with an "Export Anyway" option.
- **Specific save error messages**: `reg-save-homebrew` now extracts field names from spec failures and shows targeted messages instead of generic "You must specify a name" errors.

#### Content Reconciliation (`content_reconciliation.cljs` -- new file)
- **Missing content detection**: When a character references homebrew content that isn't loaded (e.g., deleted plugin), the system detects missing races, classes, and subclasses.
- **Fuzzy key matching**: Uses prefix matching and base-keyword similarity to suggest available content that resembles missing keys (top 5 matches with similarity scores).
- **Source inference**: Guesses which plugin pack a missing key likely came from based on key structure.

#### Missing Content Warning UI (`character_builder.cljs`)
- **Warning banner**: Orange expandable banner appears in character builder when content is missing, showing count and details.
- **Detail panel**: Lists each missing item with its content type, key, inferred source, and suggestions for similar available content.
- **DOM IDs for testability**: `#missing-content-warning`, `#missing-content-details`, `.missing-content-item` with `data-key` and `data-type` attributes.

#### Conflict Resolution Modal (`views/conflict_resolution.cljs`, `events.cljs`)
- **Duplicate key detection**: On import, detects keys that conflict with already-loaded homebrew (both internal duplicates within a file and external conflicts with existing content).
- **Resolution UI**: Modal presents each conflict with rename options. Key renaming updates internal references (subclass -> parent class mappings, etc.).
- **Color-coded radio options**: Rename (cyan), Keep (orange), Skip (purple) with left-border + tinted background. All styles in Garden CSS.

#### Import Log Panel (`views/import_log.cljs`)
- **Grouped collapsible sections**: Changes grouped into Key Renames, Field Fixes, Data Cleanup, and Advanced Details (collapsed by default). Empty sections hidden automatically.
- **Detailed field fix reporting**: Field Fixes section shows per-item breakdown — which item, content type, which fields were filled, how many traits/options were fixed.
- **Collapsible section component**: Reusable `collapsible-section` with configurable icon, colors, and default-expanded state.

#### OrcBrew CLI Debug Tool (`tools/orcbrew.clj` -- new file)
- `lein prettify-orcbrew <file>` -- Pretty-prints orcbrew EDN for readability.
- `lein prettify-orcbrew <file> --analyze` -- Reports potential issues: nil-nil patterns, problematic Unicode, disabled entries, missing trait names, file structure summary.

### Bug Fixes

#### nil nil Corruption (`events.cljs`)
- **Root cause fix**: `set-class-path-prop` was calling `assoc-in` with a nil path, producing `{nil nil}` entries in character data. Now guards against nil path before the second `assoc-in`.

#### Nil Character ID Crash (`views.cljs`)
- Character list page crashed with "Cannot form URI without a value given for :id parameter" when characters had nil `:db/id`. Added `(when id ...)` guard to skip rendering those entries.

#### Subclass Key Preservation (`options.cljc`, `spell_subs.cljs`)
- Subclass processing now uses explicit `:key` field if present (for renamed plugins), falling back to name-generated key. Prevents renamed keys from reverting.
- `plugin-subclasses` subscription preserves map keys and sets `:key` on subclass data correctly.

#### Plugin Data Robustness (`spell_subs.cljs`)
- `plugin-vals` subscription wrapped in try-catch to skip malformed plugin data instead of crashing.
- `level-modifier` handles unknown modifier types gracefully (logs warning, returns nil instead of throwing).
- `make-levels` filters out nil modifiers with `keep`.

#### Unhandled HTTP Status Crash (`subs.cljs`, `equipment_subs.cljs`)
- All 7 API-calling subscriptions used bare `case` on HTTP status with no default clause. Any unexpected status (e.g., 400) threw `No matching clause`. Replaced with `handle-api-response` HOF that logs unhandled statuses to console.

#### Import Log "Renamed key nil -> nil" (`events.cljs`, `import_validation.cljs`)
- Key rename change entries used `:old-key`/`:new-key` fields but display code expected `:from`/`:to`. Unified on `:from`/`:to` across creation, application, and display.

### Error Handling (Backend)

#### Database (`datomic.clj`)
- Startup wrapped in try-catch with structured errors: `:missing-db-uri`, `:db-connection-failed`, `:schema-initialization-failed`.

#### Email (`email.clj`)
- Email config parsing catches `NumberFormatException` for invalid port (`:invalid-port`).
- `send-verification-email` and `send-reset-email` check postal response and raise on failure (`:verification-email-failed`).

#### PDF Generation (`pdf.clj`, `pdf_spec.cljc`)
- Network timeouts (10s connect, 10s read) for image loading. Specific handling for `SocketTimeoutException` and `UnknownHostException`.
- Nil guards throughout `pdf_spec.cljc`: `total-length`, `trait-string`, `resistance-strings`, `profs-paragraph`, `keyword-vec-trait`, `damage-str`, spell name lookup. All use fallback strings like "(unknown)", "(Unknown Spell)", "(Unnamed Trait)".

#### Routes (`routes.clj`, `routes/party.clj`)
- All mutation endpoints wrapped with error handling: verification, password reset, entity CRUD, party operations. Each uses structured error codes (`:verification-failed`, `:entity-creation-failed`, `:party-creation-failed`, etc.).

#### System (`system.clj`)
- PORT environment variable parsing validates numeric input (`:invalid-port`).

#### Error Infrastructure (`errors.cljc` -- expanded)
- New error code constants for auth flows.
- `log-error`, `create-error` utility functions.
- `with-db-error-handling`, `with-email-error-handling`, `with-validation` macros for consistent patterns.

### Supporting Changes

#### Common Utilities (`common.cljc`)
- `kw-base`: Extracts keyword base before first dash (e.g., `:artificer-kibbles` -> `"artificer"`).
- `traverse-nested`: Higher-order function for recursively walking nested option structures.

#### Styles (`styles/core.clj`)
- `.bg-warning`, `.bg-warning-item` CSS classes for warning banner UI.
- `.conflict-*` Garden CSS classes for conflict resolution modal (backdrop, modal, header, footer, body, radio options with color-coded variants: cyan/rename, orange/keep, purple/skip).
- `.export-issue-*` Garden CSS classes for export warning modal.

#### App State (`db.cljs`)
- Added `import-log` and `conflict-resolution` state maps to re-frame db.

#### Subscriptions (`subs.cljs`, `equipment_subs.cljs`)
- Import log, conflict resolution, export warning, missing content report subscriptions.
- `handle-api-response` HOF (`event_utils.cljc`) — centralizes HTTP status dispatch with sensible defaults (401 → login, 500 → generic error) and catch-all logging for unhandled statuses. Replaces bare `case` statements across 7 API-calling subscriptions.

#### Entry Point (`core.cljs`)
- Dev version logging on startup.
- Import log overlay component mounted in main view wrapper.

#### Linter Configuration
- `.clj-kondo/config.edn`: Exclusions for `with-db` macro and user namespace functions.
- `.lsp/config.edn` (new): Explicit source-paths to prevent clojure-lsp from scanning compiled CLJS output in `resources/public/js/compiled/out/`.

### Design Principles

- **Import = permissive** (auto-fix and continue), **Export = strict** (warn user, let them decide)
- **Placeholder text convention**: `[Missing Name]` format (square brackets indicate auto-filled)
- **Modal pattern**: db state -> re-frame subscription -> event handlers -> component in `import-log-overlay`
