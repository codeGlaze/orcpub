# Branch changelog — `feature/one-template-per-style`

## Why this branch exists

`resources/` shipped seven PDF templates per sheet style, one for each spell-page
count, cut from a master by deleting pages. Each carried its own copy of that
style's artwork: 32.7 MB of images across the 28 files against 13.2 MB of
distinct pixels, with style 3 storing the same image twenty times.

This ships one master per style and grows it to the character instead.

## Highlights

Character sheets are generated from one template per style rather than chosen
from seven pre-cut files. The templates in `resources/` fall from 44.3 MB to 8.6
MB, and exports shrink with them — a six-caster style 1 sheet from 565 KB to 328,
the eight-class fixture from 638 KB to 424.

## Added

- `pdf/sheet-masters` names the file each style grows from and where that style's
  artwork carries its attribution, and `pdf/grow-spell-sections!` reshapes an
  opened master to the number of spellcasting sections a character needs
  (`a78aaaf`).

## Fixed

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

## Changed

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

## Added (spell row annotations)

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

## Added (packing, server half)

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
- Packing is refused on styles 2, 3 and 4. `relabel-spell-level!` covers the
  printed numeral with a patch cut to `hexagon-path`, traced off style 1 — and the
  styles do not merely offset that shape, they draw a different one. Measured: the
  numeral sits at dx −14.4 from its slots box on style 1, −12.4 on 2, −28.0 on 3
  and −23.0 on 4, and style 3 rings its numerals where style 4 uses a small
  hexagon. Rendering a packed page on each showed both numbers, the old beside the
  new: "3 0", "4 1", "7 2". `:packing?` in `sheet-masters` marks the one style
  whose numerals have been measured.

## Added (guards)

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

## Added (cards)

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

## Added (capacity)

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

## Added (cards)

- Magic item cards, opt-in from the builder alongside spell cards. Each card
  carries the item's name, kind and rarity, an attunement badge in the header and
  the clause at the foot, a charge track when the description names a number of
  charges, and rarity-graded cornerwork. Descriptions that overrun continue on the
  back. `dev/measure_item_card.clj` prints the clear space between every pair of
  stacked elements on a worst-case card, so the spacing is measured rather than
  eyeballed.

## Changed (cards)

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

## Fixed (hardening)

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

## Added (tests)

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

## Fixed (hardening, cont.)

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
