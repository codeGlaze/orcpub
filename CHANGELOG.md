# Changelog

All notable changes are documented here, newest release first. Format: [Keep a Changelog](https://keepachangelog.com).
House style and the branch → release fold: [`docs/branch-changelog.template.md`](docs/branch-changelog.template.md).

<!-- Editing this file: one change per bullet under ### Added / ### Fixed / ### Changed; succinct and
     plain; no AI-jargon; end each with (`shorthash`). No prose intros under a heading. A ### Highlights
     block (≤3 sentences, labeled) is allowed only for an impactful release — see the template. -->

## [Summer Patch] — 2026 (character-load resilience, homebrew salvage & library management, PDF printing)

### Highlights

You can now move and copy homebrew between sources, turn content off without deleting it, and see in one place what needs fixing, with imports and exports that no longer spawn silent duplicates or false warnings. Characters that used to blank-screen on a bad load now recover in place, and printable spell cards and card backs read cleanly in black and white. Signing up, signing in and recovering an account have been rebuilt, with breach-screened passwords, a live strength meter, and reset links that can only set a new password.

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
- **Export runs the same checks as import** — duplicates/cleanups caught on the way out, on every export: one source or the whole library, plain or pretty-printed. The unchecked hatches stay: the footer safety valve, emergency raw export, per-builder draft export, quarantined-source export, and "export as-is" (`9df1b4ae`, `c037de78`).
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
- **Move / copy content between sources** — one select-mode mechanism for single or bulk,
  with a clobber-free key policy: a move keeps the key unless it is taken, a copy always
  mints a fresh one (`903f44cb`).
- **Four-level disable hierarchy** — global / source / section / item, checked as an OR. The two new levels (global "all homebrew" + per-section) live in a local overlay store, so they're a per-device view preference that never mutates `.orcbrew` data or travels with an export (`95426d8c`).
- **Passive library health-status card** — one line per problem type with a count, covering
  unresolved key conflicts, missing required fields, and export blockers. Warning-yellow for
  attention, red for broken. Always on in the My Content hub, dismissable and remembered
  elsewhere (`b58fe80b`, `79982e03`, `d0338049`, `e5372fed`, `e7040f4a`).
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

Homebrew classes can define their starting equipment from the builder UI, in the full SRD
form: fixed items, choice groups, bundles, and nested weapon sub-choices. It applies on the
character sheet and round-trips through save, export, and import. You can also start from an
SRD class and change only what you want, and the export stores just those changes against
the base class.

**Added**

- **Starting Equipment section in the class builder** — a homebrew class can grant fixed
  items and choice groups, including bundles and nested weapon picks, so the full SRD
  equipment form is buildable without hand-editing `.orcbrew`. It round-trips through save,
  export and import, and legacy simple choices convert to the editable form in one click
  (`a4f13086`, `5a5f65a8`).
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
- Spells vanished off printed sheets because three templates numbered their spell-row fields
  with gaps: **Glyph of Warding** was dropped from every wizard's style 1 and 3 sheet, and
  style 4 lost **Continual Flame** and **Darkness** mid-list. Style 1's PREPARED ticks
  carried the same numbering, so a prepared spell printed unticked.
  `dev/fix_spell_row_fields.clj` renumbers them.
- Style 3 printed an empty HIT DICE box on every sheet — the box is drawn, the
  `hd` field was never there. Style 4's second-page name box was likewise always
  empty: it calls the field `character-name-p2` where the export writes
  `character-name-2`. `dev/fix_missing_char_fields.clj` adds the one and renames
  the other.
- `spell-packing/sheet-geometry` claimed capacity the templates did not have —
  13 rows at style 4's level 2 where only 11 could be filled — and undercounted
  its level 1 at 12 where it holds 13. It is the field count now, with a test
  tying it to the templates.
- Styles 3 and 4 threw `StackOverflowError` for any character with two or more casting
  classes, so those sheets could not be exported at all. Clones are now inserted with
  `insertAfter`, avoiding the whole-object-graph cycle check that exhausted the stack.
  Styles 1 and 2 were never affected, which is why this survived.

**Changed**

- The 28 templates are now 8: for each style, one to grow from and one with no
  spell page for a character who casts nothing. 44.3 MB to 9.7 MB.
- Style 4 grows from a one-spell-page master like every other style: its licence footer
  turned out not to be baked into the artwork, so the marked page alone yields clones that
  all carry the footer. The retired file leaves `resources/` 4.5 MB lighter, and the
  surviving page renders byte-identically.
- That page's footer block is four operators rather than six: `0 i` sets flatness
  tolerance, which applies to path curves and not to glyph fills, and `/GS2 gs`
  is the page default, differing from GS0 only in stroke adjustment.
- Style 4's structure tree is removed along with the page it referenced, taking the file
  from 2622 objects to 1301. The cost is style 4's accessibility tagging — styles 1 and 2
  keep theirs, style 3 never had any — and restoring it means writing the pruner properly.
- Exports are smaller at every caster count above one, by 49 KB to 671 KB
  depending on style, and a character with no spellcasting gets a file the same
  size as before.
- Generating a sheet repeats less work: form values are looked up once instead of twice,
  prose fields are measured only when they hold something, and a cloned spell page no longer
  re-reads its source per clone. A six-caster sheet allocates 162 MB rather than 607, and a
  character who casts nothing no longer scans the pages for spell sections at all.

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
- Each spell column is headed with the class that holds it, so a party of casters can be
  told apart at a glance. A class with no cantrips is headed in its first level box
  instead of going unnamed, and headings are only placed on bars with no slot inputs the
  player writes in.
- The compartments are read off the live fields rather than written down, so they
  follow the artwork: 51.9–91.1 and 103–195.8 on style 1. A style with no
  `slots-expended` field (2 and 4) has the wide one taken from the spell row's
  right edge instead. Box 0 has no slots fields at all and borrows level 1's.
- A name too long even at the 6pt floor is shortened with an ellipsis rather than printing
  through the divider — at 6pt "Eldritch Knight" still measures 43pt against a level box's
  35.

- Each class's spellcasting ability, save DC and attack bonus print above its column's bar,
  bold and near-black, so they read at a glance mid-turn. A sheet section carries one such
  triple, so on a packed page holding several classes it is left empty, and filled only
  where a page holds a single class.
- Packing runs on all four styles. The printed level numeral is covered with a
  white rectangle cut to that style's measured digit box (`pdf/numeral-boxes`,
  from `dev/scan_numerals.clj`) rather than a hexagon traced off style 1, which
  works everywhere because the paper around every numeral is white (`a8752173`).

**Added (packing, builder half)**

- `pdf_spec/packing-classes` regroups `spells-known` — which is keyed by LEVEL —
  into the per-class lists the packer takes. That regrouping is the point: the
  shipped layout groups by `:ability`, which is why a Warlock and a Sorcerer share
  one CHA section.
- **Pact Magic is separated in the character model.** A Warlock/Sorcerer's pact slots were
  added to the shared ones and printed as a single inflated number — on the normal sheet,
  not only when packed. Shared and pact slots are kept apart now, with their sum still
  available to everything that reads it.
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

- Style 4 has no allies or backstory box, so both values are written into its general Notes
  box under headings rather than dropped. That box is smaller than the two it replaces, so a
  long backstory shrinks to fit and a very long one clips. An empty section prints no
  heading.

**Added (cards)**

- Spell and magic item card backs carry `dungeonmastersvault.com`, centred at the foot of
  every card. Backs were chosen over fronts because a blank back has room where a front is
  filled to the edge by spell text. Text carried over from a front is laid out clear of the
  stamp.

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

- Card icons are drawn from SVG paths instead of 32px rasters, so a 600 DPI printer is no
  longer asked for about 150 device pixels from a 32 pixel source. Each icon is embedded
  once per document and referenced where drawn: +7% on card pages, byte-identical on a sheet
  with no cards. The `-bw` duplicates are gone, since colour is applied at the draw site,
  and `resources/public/image/ATTRIBUTION.md` credits the icon authors, which nothing did
  before.
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

- The image fetch resolves a host once and connects to that same answer, closing a window
  where DNS an attacker controls could answer public for the safety check and private for
  the connection. Hostname and certificate verification are unchanged, and the pin is
  skipped behind an egress proxy, where the client connects to the proxy instead.

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
  every lookup rebuilt every class's 20 levels. A Class-tab switch went from 1125 ms to 100
  ms in development and 654 ms to 92 ms in production, and the page holds about 48 MB less
  (`c90016ac`, `4b67b3f7`).
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

A character's portrait now reaches the sheet from hosts that used to refuse it, including
Pinterest and D&D Beyond: the browser reads the picture, and where it is refused the server
fetches it instead, with nothing asked of the user. When an address cannot work, one line
under the field says why and offers at most one thing to do about it. A picture can also be
pasted or copied straight in.

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

- `orcpub.image-url/advise` reads the address alone and catches most real mistakes before
  any request is made: a page's address pasted instead of the picture's, a login wall, a
  missing or non-web scheme, a stray space, and `http://`, which this page's CSP will not
  display whatever the host does. Dropbox and Google Drive links are offered a correction to
  take or leave. Advice, never enforcement.

**Changed**

- http is upgraded to https automatically, once the https address is known to load. The
  check costs no request that was not about to be made anyway; the field changes only after
  it succeeds and says why, and a host that serves no https is told instead, with its
  address left exactly as typed.
- **One line under a field, never four blocks.** A single unreachable picture could raise a
  scheme warning, a suggested correction, a fetch failure and a panel of controls at once.
  Only the most actionable now shows, and the other ways in wait behind one disclosure.
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


- The builder says nothing about pasting or uploading until both routes are known to be
  shut. Most hosts that refuse the browser serve the server perfectly well, so speaking up
  earlier asked people to supply a picture that was about to arrive: of sixteen common
  portrait hosts, nine let the browser read, and most of the rest allow the server.
- Exporting is held while a picture is still being read, so the browser's bytes
  win that race instead of falling through to the server. `capture` carries a
  deadline, so a read always ends and the hold is bounded.
- An oversized picture gives up size before quality, down to what the sheet can actually
  show — the portrait box is 945px on its long edge at 300dpi. A picture already smaller
  than that is never scaled, only re-compressed. A 5.8 MB noise PNG leaves the browser at 92
  KB and full quality, where spending quality first produced 37 KB and a worse picture.


- Pictures are read when the thumbnail loads and when the export panel mounts,
  never on the export click: the export is a synchronous form submit into a new
  tab, and an await in between spends the user activation that keeps that tab from
  being blocked. Bytes are held in app-db keyed by URL, outside the character
  entity — that entity is what gets persisted, and localStorage has a ceiling.
- `docs/CHARACTER-IMAGE-FETCH.md` leads with the browser path; the server fetch is
  documented as the fallback it now is.

**Changed**

- `/image-probe` answers a reason rather than a boolean, so the builder can say what is
  worth fixing: the link, the picture, or simply waiting. Telling someone to copy a picture
  when they have mistyped a link is not help. The server never sends a sentence, and every
  address refusal collapses to one code, so the endpoint cannot be read as a map of what
  this network can reach.

**Fixed**

- **A picture the host served happily was refused for weight.** One 128 KB limit capped
  both what the server would download and what could go into the PDF, so a 393 KB
  Pinterest portrait was dropped although nothing had blocked it. The two limits are now
  separate — 2 MB down, 128 KB into the document — and a heavy picture is scaled and
  re-encoded to fit.


- The builder flashed "Image failed to load" at pictures that were fine.
  `image-error` dispatched when it was CALLED, at render time, rather than
  returning a handler -- so every fresh URL was marked failed before the browser
  had tried it, and only the load took the mark back.

- A picture whose host allows no read stopped displaying in the builder: the optimistic
  "failed" mark set when the thumbnail renders was never withdrawn, because the load handler
  had captured the flag while it was still clear. The clear no longer reads the flag, so an
  ordinary load does not count as an edit.

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

### fix/header-flyout-under-sticky-toolbar

**Fixed**

- **Header dropdowns are clickable again** — Characters, Spells, Monsters, Items,
  Encounters and My Content all rendered under the button row, which swallowed
  the click on most of their items (`9eb3ac81`).

### fix/tall-flyout-and-held-release

**Fixed**

- **Tall header menus stay on screen** — an opening flyout is capped to the room
  below it and scrolls, so every item in My Content is reachable on a short
  window instead of running off the bottom (`2573f976`).
- **The What's New panel opens when the cookie notice is dismissed** — it no
  longer waits for a reload the visitor never makes (`2573f976`).

### fix/release-panel-hold-ceiling

**Fixed**

- **An ignored cookie notice no longer hides the release panel** — the hold has a
  ten-second ceiling, after which the panel shows anyway with the notice left
  where it was, behind it.

### test/overlay-reachability

**Added**

- `test/browser/overlay_reachability_e2e.js` — hit-tests every control an overlay
  shows (header chrome, Orcacle, the PDF options panel, the release panel) and
  fails if one is covered or off the window inside a floating layer. `SELFTEST=1`
  proves it can fail; a state that audits nothing fails as a stale selector, so it
  cannot quietly stop asserting. Overlays needing a login, an import or saved
  content are listed as skips rather than left unmentioned.

### hotfix/my-content-dropdown-and-probe-cost

**Fixed**

- **The "+ add content…" dropdown in My Content is readable and matches the app** —
  its option list follows the theme instead of opening as a white box with
  invisible entries, and the control takes the same border and text as every other
  dropdown. `color-scheme` is set on `select` for both themes, so any unclassed
  dropdown is covered too (`61ddd603`).

**Changed**

- **The release-panel probe runs in 30 seconds instead of 124** — same 18 checks.
  Cases that did not need their own app boot are folded together, and the two
  independent stories (the ordinary one, and the cookie notice) run in parallel
  contexts, so the wall clock is the longer lane rather than the sum (`61ddd603`).

### hotfix/boot-config-report

**Added**

- The server says it started and what it is configured with, on every start. Every setting
  the app reads is listed with its value and where that value came from — `SET` from the
  environment or `DEFAULT` — grouped by runtime, database, security, email, content and
  capacity, plus a count of the 21 `APP_*` branding variables (`a80b5aec`, `c2754d28`).
- A value that was supplied and rejected is called out by name beneath the table, since its
  row shows the default that is running rather than the value you asked for (`e65dfda6`).
- A setting whose absence breaks the site says **why and how to fix it**, in the boot banner,
  the startup warning and the `500` a caller receives — the same text in all three, so
  nobody has to work the remedy out from the symptom (`2c425afa`).

**Fixed**

- The database password is no longer printed at startup. It was in four places, not one: the
  startup line and three `ex-info` maps, which reach logs and error reporters just as
  readily. Credentials are blanked in both shapes they arrive in — a `password=`-style query
  parameter and `user:pass@host` userinfo — while the host, port, database and user still
  show (`24aa9971`).
- `SIGNATURE` missing now fails clearly everywhere. `create-token`, `unsubscribe-token` and
  `unsubscribe` read the environment straight into `jwt/sign`, failing deep inside the
  library with nothing an operator could act on (`2c425afa`).
- `EMAIL_SERVER_URL` missing is reported as breaking, because it is: the verification email
  is sent inside the signup transaction, so unconfigured mail does not skip an email — every
  registration fails with "please try again", and password reset fails too (`e66eeef8`).
- `DATOMIC_URL` unset defaults to a local development transactor, and the failure read
  "cannot connect to localhost:4334", naming the symptom rather than the cause. It now says
  the development default is in use (`e66eeef8`).
- `ENVIRONMENT.md` was missing `ORCPUB_PDF_MAX_CASTER_SECTIONS`, `ORCPUB_PDF_MAX_CARDS`,
  `EMAIL_SERVER_URL`, `EMAIL_SERVER_PORT` and `EMAIL_ACCESS_KEY`, despite being the variable
  reference (`e66eeef8`, `c2754d28`).

**Changed**

- Secrets report presence only — `SIGNATURE`, `DATOMIC_PASSWORD`, `EMAIL_ACCESS_KEY` and
  `EMAIL_SECRET_KEY` show `set` or `NOT SET`, never a value, dropped before printing rather
  than at print time (`c2754d28`).
- `ORCPUB_PDF_MAX_CARDS` defaults to `198` rather than `200`. Nine cards to a sheet, so 200
  was 22 sheets plus two cards stranded on a twenty-third (`ac886c74`).
- `ORCPUB_HTTP_MAX_THREADS` reports the pool size read back off the running server when it is
  unset, rather than a formula copied from Pedestal that would drift (`cf7d8cdd`).

### test/overlay-probe-user-menu-and-modals

**Added**

- The overlay probe drives the **import conflict modal** — the fixture pack is
  imported twice, the second time with its source renamed so all 180 keys collide,
  which is the conflict a user hits when two packs overlap (`26f8c602`).
- A **signed-in lane** that logs in through the real form when `ORCPUB_TEST_USER`
  and `ORCPUB_TEST_PASSWORD` are set, and audits the user menu (`26f8c602`).
- The overlay probe walks **My Content's delete-all guard** — the quiet `Delete…`
  button, the `.mc-liftpop` it unfurls, and the `.mc-confirmbar` underneath. It
  cancels at the last step and then asserts the stored library is the same size it
  was, so a run can never be one stray click from wiping a library. None of the
  three steps is a modal, so a probe that waits on a modal selector skips.

**Fixed**

- **The import helper targets the labels that exist**, most specific first, so a
  rename fails loudly instead of quietly matching something else (`950ab526`).
- **The cookie-banner fallback is scoped to the banner**, so it can no longer
  close the release panel and record that as consent (`950ab526`, `031daf73`).

### fix/message-banner-spacing

**Fixed**

- **Message banners keep their line breaks**, so a multi-part message reads as
  separate lines instead of sentences run together with no punctuation (`2e13d2b2`).
- **The banner has room to breathe** — padding above it as well as below, a gap
  between the text and the close icon, and the icon aligned to the first line
  rather than halfway down a three-line message (`2e13d2b2`).

### consolidate/notifications-and-exports

**Highlights**

Homebrew can now be rescued from a broken app. The boot shell carries a download
control that the app removes once it has actually rendered, so a missing bundle,
a failed init or a component that throws all leave it standing — and the error
screen puts it back. It reads storage at the moment you click it, not at page
load, so work done during the session is in the file.

**Added**

- **Homebrew rescue in the boot shell** — downloads what is stored when the app
  itself cannot. Present by default and removed on a clean render, so nothing has
  to detect the failure (`46b6f807`, `7bd0d7bc`).
- **Developer mode** — a named switch in the footer reveals "Dump library" and
  "Debug info", off by default and remembered per device. Replaces two unlabelled
  icons that sat on every page (`c1dad978`).
- **Export coverage for the save banner's link** — a source holding one item from
  before the session and one saved seconds ago must export with both, and the file
  must re-import into a clean store (`c1dad978`, `cb40740e`).

**Fixed**

- **Banners render markup again** — every message built from hiccup had been
  rendering as its own source text since `e13d2b2f`, which removed every control
  inside one, including the save banner's export link (`68c9873e`).
- **Per-source export runs the same checks as Export All** — the same source no
  longer produces two different files depending on which button you press
  (`f7b259fe`).
- **The size a rescue reports** — anything under a kilobyte read as "0 KB", which
  looks like there is nothing to save (`7ccc9d3a`).

**Changed**

- **The footer's raw library dump is named and explained** — it stays reachable in
  production, because it is the way out when validation is what is broken
  (`17658bb5`, `c1dad978`).

### fix/item-builder-save-label

**Fixed**

- **The item builder's save button says "Save Item"** rather than claiming browser
  storage it does not use.

### refactor/banner-parts-and-design

**Changed**

- **Import messages are structured** — `format-import-result` returns
  `{:title :details}`, one idea per line, and the banner renders each line itself.
  No newline sentinels, no `white-space: pre-line`, no regex collapsing blank
  lines. A plain string is still accepted and split at the edge, so the remaining
  legacy callers keep working while they move over.
- **The banner has a hierarchy** — a severity icon carrying the colour, a headline,
  supporting detail at a lighter weight, a tinted box instead of a filled slab,
  and a dismiss with a real touch target.
- **A successful import reads as success** — green, not warning-orange.
- **The banner says what happened and stops there.** The advice line — "To be
  safe, export all content now" — is gone rather than promoted to a button: on My
  Content it would have sat directly above the page's own Export All. A message
  pointing at a control already on screen is noise.

- **The save-validation error leads with the problem.** It opened with a line
  naming the builder you were already standing in ("Spell:"), then the problem,
  then the escape hatch — three stacked blocks for one short message. The problem
  is the headline now, with the rest beneath it.
- **Exporting from the storage warning no longer closes the card.** The banner
  dismisses on any click that reaches it, and the export link did not stop its
  own, so the surface vanished mid-action — which is how a working control comes
  to feel broken.
- **The browser-storage warning leads with the point.** It was one
  200-character sentence opening with "IMPORTANT!:" and ending with the export
  link — the part that matters, last. Now: "Spell saved — in this browser only",
  and under it "Clearing browser data loses it. Export this source to keep a copy."
- **The banner is sized to what it says** rather than to one fixed width. A flat
  cap fixed the five-word slab and then squeezed a two-sentence warning into a
  narrow column; `fit-content` with a ceiling does both jobs.
- **A save confirmation says what it saved** — "Saved “Flame Tongue”" rather than
  "Your item has been saved.", for characters too when they have a name.

**Fixed**

- **A message that is markup renders as markup.** The builders' "please fill in
  X" carries a bolded field name and a clickable "Save anyway with placeholders",
  and the new banner ran `str` over it, printing the hiccup at the reader. Vectors
  now pass through untouched.

- **Callout action buttons carry a React key again** — the key was attached to the
  `let` form rather than the element it returns, so every callout with actions
  logged a missing-key warning.

### fix/my-content-source-toolbar

**Changed**

- **An expanded source's search sits inline with its buttons** — search, show
  disabled, then Export and Delete on one row, with side padding so nothing is
  flush against the panel. On a phone the search takes the row and the toggle and
  buttons wrap beneath it.

### fix/filtered-list-staleness

**Fixed**

- **A filtered list keeps up with its contents again.** Creating, editing or
  deleting a custom item left the visible list unchanged while any filter was
  active, because the list read a snapshot written at the last keystroke and
  nothing ever cleared it. Filters now derive from the live data (`27a92457`).
- **A 401 on custom items leaves a breadcrumb.** The request still fails
  quietly by design; it no longer fails invisibly (`b7bd29df`).

**Changed**

- **`get-auth-token` lives in `event_utils.cljc`** instead of being redefined
  where it was needed (`dbb71dce`).
- **Five API-backed subscriptions share one `reg-api-sub`** rather than
  repeating the same fetch-and-store shape (`f1952497`).
- **The orphaned `::mi5e/remote-item` chain is switched off** with `#_` rather
  than left looking live. Nothing subscribed to it; the item page that ships
  uses `::mi/custom-item` (`7af6253b`).

**Tests**

- Thirteen tests cover filtered-list reactivity, two of which fail against the
  old shape, plus the share overlay and eight regression guards (`436284ad`).

### port/save-gate

**Highlights**

Homebrew edits can no longer quietly lose data. Changes made in the app go through one check that
keeps links intact or says what a deliberate delete breaks, merges edits made in two tabs, and asks
before touching another pack, and characters keep their homebrew through renames on every page.

**Added**

- **One gate for the app's library writes** — saves, imports, moves, restores and key changes pass
  one check. A save, move or key change that would strand a link is refused; a deliberate delete or
  a re-import goes ahead and says which links it left pointing at nothing. The server's first-load
  fetch writes only into an empty library (`31329bcd`, `5209eb97`, `4f7ebf3b`).
- **Two tabs no longer overwrite each other** — a write from a tab holding an old copy is merged onto
  the newer library, and a real conflict is reported instead of lost (`bf1ca6a5`, `f3578592`,
  `4f7ebf3b`).
- **Links follow a key change** — changing an item's key moves the links in its own pack and offers
  to move the ones in other packs; those move only when the author clicks (`1042c746`, `b5f2a1b6`,
  `7c199b20`).
- **A copy of the library before its one-time tidy**, with Restore on My Content (`c0e38458`).
- **Repairs for links a past rename left behind** — My Content marks items that link to nothing and
  suggests the renamed target; nothing changes until the author picks (`8e76bbf3`, `c0e38458`).
- **A character is asked which item it meant** when an import renames an item it used, once, in a
  banner that waits for an answer (`c50e6536`).
- **Asking before deleting what other items use**, and saying what a re-import updated (`12eb5a23`).
- **A check on comments and docstrings** — a test flags history, long docstrings and long comment
  blocks; each hit is fixed or approved with a reason, and field notes are kept by hash
  (`90334b7a`, `d34bdb2f`, `76395a16`, `48f945b3`, `332ae1dd`).

**Fixed**

- **Saves land on the item the builder opened** — a stale or ambiguous address is refused rather
  than guessed, and an older untagged entry is replaced only with consent (`b5b36d29`, `b9a98f4f`,
  `4968a3be`).
- **Characters keep homebrew renamed since they were saved** — picks heal on every page, not only in
  the builder, and a key two kinds of content share (the Dragonborn "Blue" ancestry and a background
  picked as `:blue`) no longer blocks the heal (`43f93777`, `38dbaa91`).
- **The missing-content warning** also reports missing spells and languages, and a background whose
  key another type holds (`4e88b04c`, `43f93777`).
- **"Link to nothing" is judged by content type** — a built-in language `Orc` no longer answers a
  race's link to `:orc` (`7c199b20`, `a376470b`).
- **A move takes an item's subclasses and subraces along** (`3fb06300`, `ab8b0393`).
- **Restore shows what is still set aside, and keeps what the author typed** when its write fails
  (`796e15dd`, `6cfccce3`).
- **Every on/off switch on My Content works from the keyboard** (`6cfccce3`).
- **Share links bundle a picked key only as the content types its choice can hold** (`4f7ebf3b`,
  `a90fe7c6`).
- **The server's first-load `.orcbrew` fetch** writes only into a library that is still empty
  (`4f7ebf3b`).

**Changed**

- **Homebrew backgrounds are offered under their stored key**; characters that picked one under its
  name's key heal to it (`5209eb97`, `43f93777`).
- **Notices and import modals share one design** — each decision in one framed callout, names
  instead of keys, no decorative icons (`b1feb5b3`, `7ae78fc6`).

### fix/comment-debt

**Fixed**

- **Party add/remove answer with the updated party** — both returned the party as it was before the change, because they pulled from the request's snapshot. The app ignored the body, so nothing visible broke; a new test covers both (`d09964ce`).
- **Stale comments and docstrings corrected against the code** — among them the CSP notes that described a Report-Only dev policy that no longer exists, and PDF, spell-packing and share-link docstrings that named the wrong return shape (`ac0dc950`).

**Changed**

- **Comments and docstrings trimmed to spec** across 47 source files, clearing the comment check's recorded debt; history and measurements moved to the knowledge base (`ac0dc950`).
- **The linter's config is tracked** — `.gitignore` now ignores the linter's cache, not `.clj-kondo/config.edn` (`ac0dc950`).

### feature/style-guide

Parent: `integration` at `614c17ff`, merged up to `1aa80ec3`.

**Highlights**

The page now lays itself out by window width: a desktop browser narrowed to phone size gets the
phone layout, and switches back when widened. On phones the header, buttons and builder share
one 10px gutter, and the header's tab menus open on screen again.

**Added**

- A "Dark Button Text" option beside "Light Theme" puts dark text on the yellow buttons for anyone who finds white hard to read. Off by default; remembered in the browser and on the account. (`97639a41`)
- A style guide for the site: palette and roles, type, spacing, radius, focus, buttons, and rules for new work (`docs/design/style-guide.md`). (`4ef09256`)

**Fixed**

- The Light Theme and Dark Button Text settings work from the keyboard: Tab reaches them, Space or Enter switches them, they show a focus ring, and screen readers hear them as on/off switches. (`8568786f`)
- A desktop browser narrowed to phone width gets the phone layout, and the desktop one back when widened; it used to keep the desktop page squeezed into the narrow window. A phone keeps the phone layout at any width. (`77fcb2e6`)
- The builder stays on Description when the window crosses phone width; it used to jump to the character sheet. (`77fcb2e6`)
- On phones the header tab menus open on screen; the header clipped them out of view. (`66ccbcb6`)
- On phones the first tab's menu no longer hangs off the left edge: the three left-hand tabs open their menus to the right. (`66ccbcb6`)
- On phones the header tabs share the row evenly, instead of spreading out with wide gaps. (`b58d2af3`)
- On phones the top bar fits the screen; the login button ran off the right edge. (`3b652700`, `b58d2af3`)
- On phones the search button is a square with its magnifier centred, and LOGIN is centred in its button. (`942c177d`)
- On phones the logo, tabs, title, buttons and builder all start 10px from the edge; they started at 20, 12, 10 and 15px. (`bea36ada`)
- On phones all six ability buttons fit on the row; the sixth was cut off at the right edge. (`6ce56c00`)
- The Light Theme and Dark Button Text toggles no longer touch the right edge of the screen. (`6ce56c00`)
- "MY CONTENT" fits on one line in the header. (`844c15ca`)
- Text marked bold renders bold: Open Sans is loaded at 400, 600 and 700, not 400 alone. (`4159164c`)
- Buttons use the site font instead of the browser's default Arial. (`4159164c`)

**Changed**

- Text on the yellow buttons has a faint edge in the opposite tone, so the letters stand out from the amber. (`bf7861a7`)
- The header tabs get the same faint dark edge. (`844c15ca`)
- With Dark Button Text on, the ability buttons get a stronger glow and a thin yellow outline, because their short labels read weaker than the other buttons. (`844c15ca`, `d579d94b`)
- The character sheet's roll buttons have 16px labels instead of 14px, at the same height. (`1e677a3f`)
- The yellow buttons are 700, restoring the stroke weight they had in Arial Bold. (`a891db35`, `844c15ca`)
- Bold text at 18px and up renders at 600, not 700: page titles, headings, spell and monster names. (`a891db35`)
- The builder's info boxes are regular weight, not bold; their CLICK HERE links stay bold. (`a891db35`)
- Page titles are 28px on phones, so "Character Builder" fits a 320px screen. (`b58d2af3`)

### fix/integration-browser-tests

**Fixed**

- **Building the character template no longer builds 86 spell help panels** — the wizard's Spell Mastery and Signature Spells options built every spell's help up front, which the template's offered-keys walk now triggers on every build; they wait until a panel is opened (`6f5375f1`).

**Changed**

- **Two browser tests follow the app again** — the export test selects the footer's developer switch, not the first on/off switch on the page, and the rescue test compares with the library as stored when the view fails, not the seed (`cf6ceec5`).

### fix/test-runner-fails-loudly

**Fixed**

- **A browser run that tests nothing now fails, and says why** — no browser found, a stale or wrong-kind bundle, an app that never starts (scripts blocked by the security policy, a missing bundle, a startup error), a suite that reports no checks, or a failed check behind a clean exit. Each was a silent false result before (`586140e1`).
- **The Changelog guard runs on pull requests**, where an un-folded branch changelog can still be folded; it only ran after the merge before (`51946ed0`).

**Changed**

- **Browser suites run on the production build unless they declare otherwise** — 27 read the app's internals and say `Needs: dev bundle`, 18 measure rather than check and say `Kind: probe`, and the other 12 run on what the site serves (`8acf1093`).
- **One command runs every browser suite** and prints a table that keeps production and development-only results apart (`edd5c14f`).

### fix/e2e-step-2

**Fixed**

- **Browser suites no longer click into the What's New panel** — every suite starts with it and the cookie banner already seen unless it tests them, and the busy-export test waits for the Export button the panel used to cover (`a94af091`).
- **A browser suite that hangs is stopped** after 20 minutes and reported as failed, instead of holding the run open (`f374a758`).
- **The busy-export browser test is reliable** — the test-only busy profile now waits 1ms for an export slot instead of 250ms. The slot queue is fair, so with 250ms an export arriving under load often reached the front and got its PDF, and the test failed about 4 runs in 10 with nothing wrong in the app. It now also stops with one clear reason when the server is not busy (`147eac8a`).
- **The starting-equipment browser test follows source-tagged keys** — a saved class lives under a key tagged with its source (`:browser-test-class-brttse`), and the test read the untagged one, so it reported lost weapons that had saved correctly (`35ff99d5`).

**Changed**

- **The full browser run boots three servers instead of 39** — one per bundle batch, plus one for the busy-export test; all 39 suites take 20 minutes (`d84337f7`, `e310fd32`).
- **Five more suites run on the production build**; importing homebrew through the visible import flow works there. Suites that take a homebrew pack use the test fixture pack when given none (`4c3fbba6`).

### fix/e2e-faster

**Changed**

- **The full browser test run takes 8.5 minutes instead of 21** — tests on a shared server run three at a time, the measuring probes run only with `--probes`, and the bundle already built goes first so a run rebuilds once. Same 21 verdicts (`36ce7ca6`).

### fix/e2e-overlap

**Changed**

- **The full browser test run takes 5.5 minutes instead of 8.5** — the bundle builds while the test server starts, so every test can start in parallel; the summary shows peak memory, the limit on running more at once (`7da2e4e8`).

### hotfix/locale-safety

**Fixed**

- **The server gives the same content the same keys on any machine** — on a Turkish or Azerbaijani system, lowercasing turned "I" into a dotless "ı", so names, keys, sorting and HTTP dates depended on the operating system's language (`b86d3d12`, `5d2bbd14`).
- **A blank setting counts as unset** — an empty `SIGNATURE=` signed login tokens with a publicly known empty key; every setting is now read through one place that treats blank as missing, and lint rejects the old pattern (`db2f7ee7`).
- **Docker secrets work for the login key** — the server read `SIGNATURE` from the environment only, so a deployment that mounted the secret as documented failed every login (`969cf644`).
- **`PORT` works in development**, where the server always took 8890 and the start scripts disagreed with it (`b6df8098`).
- **A failed verification email no longer strands an account** — a new account is rolled back, a failed resend keeps the link already sent, and a sign-up failure now shows a message (`e4a69649`, `b29d89ef`, `94a71ea0`, `55c949da`).
- **Resending a verification link to an address with no account does nothing** — it used to create a record and email a link to any address typed (`55c949da`).
- **Without email configured, registration fails closed** — accounts are auto-verified only when `ALLOW_UNVERIFIED_REGISTRATION` is set, and the server says at startup what registration will do (`fcaf894e`, `896d186f`).
- **The Windows start scripts find ports and processes correctly**, and say so when they cannot (`c27cbd32`, `6636369a`).
- **Source abbreviations, spell-card annotations and image hosts are locale-safe too**, as is the comment-rule test; code written after this fix began, pinned in the merge (`af0dd8a1`).

**Changed**

- **The security-policy docs match the server** — there is no report-only mode; development sends no policy, everything else enforces it (`4e972cf7`, `c9fd5a5b`).

### integration-local (accounts and the September hotfixes)

**Added**

- **New homebrew keys carry their source's tag** — two authors' "Stone Elf" become
  `:stone-elf-trcs` and `:stone-elf-kbtx` instead of colliding, and a source can set its own
  tag from My Content. The builder also gains a key row to change a key on purpose
  (`ed4f5a4c`, merged in `01180480`). The duplicate-save gating that came with it was reverted
  (`65502f3d`) because it could let a save overwrite another library's item.
- **A check for homebrew no repair knows about** — a browser probe breaks one race on
  conversion and one only when drawn, and checks the app, the notice, the set-aside,
  Restore and import (`986c25a4`).

- **A check that every fix is saved** — a browser probe applies each repair through
  the app, reloads and exports, so a fix that only lives on screen fails a test
  instead of reverting on refresh (`c275c101`).

- **A character brings its custom items from the server** — whoever can open a character now sees the
  custom items it has equipped, as they are now. Share links no longer carry items, so they are
  shorter; homebrew still travels in the link, and older links still open (`397aea6a`).

- **Short share links** — a character's owner gets a link of about 85 characters that stays the
  same and always shows the character's current homebrew, which the server keeps and checks; New link
  revokes every earlier link, nothing is stored until the owner presses Share link, the limits are
  config settings, and anyone else's link still carries the homebrew itself (`5ef7939b`, `56723035`,
  `79b79f9d`, `0eda1015`).

- **A party keeps a shared character's homebrew** — a character added to a party from its share link
  shows its homebrew on the party page, until its owner makes a new link (`f328ca60`).
- **Unused share links expire** — a share link nobody opens for 180 days of the server running is
  deleted with its share data; the character stays, the owner can share again, and
  `ORCPUB_SHARE_PRUNE_DAYS` sets the window, 30 days at least. A request without the link's token does
  not count as use, and time the server was off does not count against a link (`c5532564`, `4b17550d`,
  `8b7e011a`).
- **Stop sharing** — a character's owner can stop sharing it at any time: every link made before stops
  showing its custom content, and nothing is stored again until Share link is pressed (`124c1c62`).
- **The owner is told when a share link expired** — the character page and list show the date beside
  Share link on every visit, until the owner shares again or dismisses the note; the note keeps only the
  character and the date (`124c1c62`).
- **The server records when it was running** — an hourly heartbeat kept in the database notes when the
  server was off, and scheduled jobs run after it; share link pruning is the first (`124c1c62`).

- **The password rules judge length and shape, not character classes** — a minimum of 12
  characters, with repeats, runs and too few distinct characters refused, and no rule about
  symbols or capitals. NIST has told everyone to stop imposing composition rules, which push
  people to Password1! and no further. The validator is shared between the browser and the
  server so neither can disagree with the other (`e8496511`, `696a962e`, `29783af5`).

- **A strength meter that asks the validator instead of guessing beside it** — four rungs at
  12, 16, 22 and 28 characters, and it refuses to show a verdict the server would contradict.
  The username and email go into the judgement, so a password made of them does not score
  (`29783af5`).

- **Breach screening against the Pwned Passwords corpus** — only the first five characters of
  the password's SHA-1 leave the server, so the password itself is never sent. A password is
  refused only when it appears 1000 or more times in the corpus, and a service that cannot
  answer never blocks anyone (`09ac513c`, `729458ec`, `1cf25582`).

- **A notice when one account is signed into from several places** — with the time, the
  browser and where from, held to one message per account per cooldown so a person with a
  phone and a laptop is not mailed all day (`073d542c`, `18d1deb2`, `416a3dce`).

- **A browser suite for signing up, verifying, signing in and recovering an account** —
  `scripts/e2e/auth-flows.js` runs twenty-two checks on flows that had no coverage.
  `scripts/e2e/lib/mail-sink.js` is a small SMTP server that catches the emails, and `run.sh`
  starts it, so the suite follows the same links a person would click. It confirms that wrong
  guesses give identical answers and that a reset link works only once (`991e5ff9`,
  `b50602f1`).

**Fixed**

- **A password reset link no longer signs you in** — opening it used to start an hour-long session without changing the password, and the owner was never told; now it only lets you set a new password, once, and is checked when you submit (`f8f270dd`).
- **Signing out everywhere cannot be undone by the hourly refresh** — a refresh that read the
  database just before a reset or sign-out-everywhere committed replaced the register with that
  older read, and the withdrawn sessions worked again for up to an hour (`530837f4`).
- **A password is judged as it is saved** — signup and reset checked it as typed but stored it
  trimmed, so `" pomegranate "` passed the 12-character minimum and was saved at 11 (`91806337`).
- **Moving a class or race keeps its subclasses and subraces** — a move into a source that already
  had that key renamed the item but left its dependents on the old key, where they joined the
  other source's item. They now follow it, in a bulk move too, and the old key is recorded
  (`aab74709`, `8f481078`).
- **Blank icons show again** — the Edit button on character and item pages, New link and three
  import-log lines used Font Awesome 4 names the app does not serve, so on a phone they were empty
  buttons (`f6354d71`).
- **A mistyped `ORCPUB_SHARE_PRUNE_DAYS` is called out at boot** — it showed as set while the default
  was in use (`d108cd46`).
- **Broken keys and card lists in homebrew are repaired** — an entry whose key was not
  a keyword, or whose traits or options held something other than cards, crashed the
  import or later the export; it now imports with what can be kept (`d1c234eb`).
- **Startup only reads homebrew** — the character options autosave needs are built on
  the first save instead of on every page load, and a library that cannot be loaded is
  set aside intact with a download, so the app still starts (`986c25a4`).
- **Homebrew that still breaks is set aside, not left to break the page** — an entry
  that fails to build is set aside with a notice that names it and links to My Content,
  and a page that fails on homebrew checks it and loads again on its own (`986c25a4`).

- **Bad stored homebrew no longer stops the app from starting** — a race whose key
  was text left every page on the loading spinner. A failed startup step no longer
  stops the app mounting, and sorting keys no longer breaks on mixed types
  (`ae80c93f`).

- **A character is repaired on refresh too, and stays repaired** — the repair is
  saved back to the builder's draft, so it happens once instead of on every load,
  and its notice shows after the page navigates instead of being cleared by it
  (`d17ea6b3`, `f0e267dc`).
- **Entries set aside on load leave the stored library** — they were set aside in
  memory only and came back on the next refresh (`47f6e886`).
- **The save banner's export link exports the source as it is now** — it exported
  the copy it remembered from when the banner appeared, dropping later edits
  (`0db360ed`).
- **Damaged homebrew is repaired on import and on load** — a section stored as
  text or as a list comes back with everything in it, and a file whose whole
  content was stored as text imports instead of crashing. What nothing can read is
  set aside with Export raw and Discard (`117af073`, `71e37588`).
- **A partial import shows as a warning** — it arrived in the green success card.
  Each line is now marked imported, repaired or skipped, and counts read "1 item"
  (`246d3ef2`).
- **A nameless item reads "Unnamed Spell" in the missing-fields dialog** — with its
  key beneath, instead of a bare ":no-name" (`5ae5bbbf`).
- **The import-log button no longer covers header menus** — it stays off the page
  while the log is empty and draws under an open menu (`74d8b241`).
- **The save sparkle stays on the save button** — it drifted into the gap beside
  it (`7ad28714`).
- **A library stored as text loads again** — it loaded nothing (`4ac618e2`).
- **A stored value that isn't a library is handled once** — it was copied aside
  again on every load (`4ac618e2`).
- **Save anyway in the selection builder keeps the selection** — a name like
  "9 Lives" was saved under a key the next load set aside (`155abb4c`).
- **Renames chosen for existing items are kept when nothing incoming imports** —
  they were dropped along with the incoming entries (`544cd342`).
- **An entry with no source takes the name of the source it sits in** — unless its
  source field was there but blank, it was skipped on import or set aside on load.
  Only an entry with no name to take goes to Default Option Source (`34472aeb`).
- **An import with an entry that isn't a map no longer crashes** — the entry is
  skipped and listed in the import log (`c79a28e7`).

- **The email-support button on a character that will not load sends the report again** — clicking
  it threw after the login change, so no report went out (`a53520ed`).
- **A login the server has stopped accepting now logs you out** — the app kept showing you signed in
  while every request failed, until you logged in again or reloaded (`ffa050ea`).
- **Share links no longer carry item ids or your username** — each custom item went into the link as
  stored, with its database ids and its owner; links made before this drop both when opened
  (`852bc8f7`).
- **Only its owner can read a custom item by its id** — anyone with the id could fetch the item and its
  owner's username; everyone else now gets "not found" (`904f7be2`).
- **A character page never shows an email address** — characters saved during nine days in May 2017
  named their owner by the email used to log in (`904f7be2`).
- **Logins, API calls and PDF downloads go to the server that served the page** — every
  http://localhost page sent them to port 8890, which broke the Docker setup opened at
  http://localhost and a server on any other local port (`f9f50e55`).

- **Copy link shares the character it sits beside** — on the character page and in the character list
  it carried the homebrew of whatever character was open in the builder (`3ece5ac9`).
- **Pasted images and video are left out of shared homebrew** — a data: URI in any text field is
  emptied before sharing and again when a link is opened (`806d1a23`).

- **Only characters can be added to a party** — adding checked nothing, so any id could be added
  (`f328ca60`).

- **The mobile header no longer crowds itself** — on a phone the logo is capped, a
  full-width child no longer measures wider than the bar it sits in, the import log
  panel cannot exceed the screen, and a child that still outruns the bar is clipped
  rather than scrolling the whole page sideways (`c3988d92`).

- **Login no longer says whether a username exists** — every failure answers the same way, and
  the per-address throttle is consulted BEFORE the credentials rather than computed and thrown
  away, so a spray is turned back without first being told whether it guessed a real account
  (`360709a8`, `80121fce`).

- **Password reset no longer says whether an address is registered** — it answers 200 either
  way, including once throttled, because an endpoint that changes its answer under a limit has
  simply moved the oracle (`1d8d7be8`).

- **Reset keys are stored as a digest and expire** — the table holds a SHA-256 of the key
  rather than the key itself, and it is good for two hours. An unknown key used to fall through
  to signing a token for username nil (`072786ca`).

- **Registration is capped per host** — ten an hour, counted only once a form was otherwise
  going to succeed, so a signup that was failing anyway is not held against the address. What
  the limits turn away is counted and summarised hourly, so the numbers can be tuned against
  something (`8788b764`, `3134d972`, `49b37009`).

- **A margin utility no longer makes 194 places bold** — `.m-b-10` sat in a Garden selector
  group that read as a descendant chain and was not one, so a margin class carried
  `font-weight: bold` across the app. Measured rather than guessed: 194 uses in source, 11
  rendering on the logged-out pages, 7 computing a different weight, 5 actually looking
  different (`4ed59b1f`).

- **The legal links printed twice on the register page** — the consent line links both
  documents and the footer printed the same pair under it. The footer keeps the copyright there
  and drops its copies (`d5feb650`).

- **The gryphon panel tiled** — no `background-repeat` and no `background-size`, so once the
  form column outgrew the image a second half-cropped gryphon drew below the first. It is a
  Garden class now instead of an inline style map (`d5feb650`).

- **The email help link was one word in the middle of a sentence** — "whitelist" as the whole
  clickable target, which is a small target and names no destination when a screen reader reads
  it alone. Both instances link a phrase now, and the login copy no longer reads "Didn't receive
  validation the email?" or advise resetting a password to fix a missing validation email
  (`d5feb650`).

- **Sign out everywhere** — an account can shut every session from My Account, including the
  current one. It does not ask for the password, so someone who fears their account is open on
  a device they cannot reach can close it from the session they hold. Signing out never
  requires changing the password (`3c4b147d`).

- **Resetting a password takes back the sessions that existed before it** — every session from
  before a reset used to keep working. Tokens now record when they were minted, and one minted
  before the password changed is refused. Signing in again works normally on any number of
  devices. The list of withdrawals is rebuilt from the database at boot, so a restart does not
  bring old sessions back (`c9de41dd`).

- **A changed password now tells the account it happened** — nothing was sent. Six
  outbound mails existed and none of them fired on a credential change, so a takeover was
  silent until the owner tried to log in, and then all they learned was that they could
  not. The notice says when and from what browser, and links the reset PAGE rather than
  carrying a working credential (`5142b5b5`).

- **An email change tells the address that currently owns the account** — the verification
  goes to the NEW address, which is the one place the owner cannot read if it was not
  them, so the party losing the account was the only one never told (`5142b5b5`).

- **Moving an account to another address takes the password, not just a session** — it
  asked for nothing but a session, so a borrowed laptop or a reset link followed and left
  open was enough to walk off with an account. Checked before the address is even looked
  at, so an unauthenticated caller cannot use it to learn which addresses are taken
  (`5142b5b5`).

- **scripts/e2e/run.sh rebuilds a stale bundle instead of testing it** — the server compiles
  from source at every boot, but the bundle and stylesheet on disk were never rebuilt, so a
  suite could pass or fail against old code. `run.sh` now names what is newer and rebuilds the
  kind of bundle already there. `E2E_SKIP_BUILD=1` keeps the existing files and says so loudly
  (`5142b5b5`).

- **Both places a password is made share the area, not just its parts** — the registration and
  reset pages each assembled their own password pair, meter and checks. The reset form had no
  meter and judged a password against no username, so a password could look fine and then be
  refused by the server for matching the username. `password-fields` and
  `registration/password-pair-faults` now serve both pages, and the reset page reads the
  username from a cookie it sets (`5334fc70`, `f8f270dd`).

- **What the reset page's "not your name" check does and does not know** — it judges a
  password against the username only, read from a cookie the reset page sets. The server also
  checks the email's local part. Matching that on the page would mean putting the address in a
  readable cookie, so the server's refusal explains itself on submit. The chip fires only on the
  whole username, never a prefix (`d0719721`, `f8f270dd`).

- **All nine auth pages share a body, not just a shell** — four pages hand-assembled the same
  container, one nested it wrongly and got a double gutter, and the login fields were 350px
  wide where every other page's fill the column. `auth-form-page` takes a heading, a lede,
  fields and a tail, so the nine pages now come in two shapes and fixes land once instead of
  per page (`55cb4d4e`).

- **The pages that only announce an outcome get a layout of their own** — registration
  complete, password changed, unsubscribed and check-your-email had the heading centred and
  everything under it flush left with no gutter. They now centre in the space the card has,
  and the one thing to do looks like a button rather than a stray link (`42756378`).

- **The login page's other ways in are one group with one rhythm** — the three separate
  blocks, with `<br>` gaps pushing each answer away from its question, are now one group where
  a question and its answer share a line. The help line sits below a rule because it is not
  another way in, and the LOGIN button is full width and aligned to the fields (`42756378`).

- **The last three auth pages join the shared heading** — `verify-failed`,
  `send-password-reset-page` and `password-reset-page` still drew their own heading with no
  amber rule and no form gutter, and they are the pages someone locked out actually sees. The
  two reset pages are now titled "Reset your password" (the one that mails a link) and "Choose
  a new password" (the one that takes it).

- **Password reset told nobody why it would not go** — an ordinary password left SUBMIT
  dimmed and the page silent. The rules gated the form while their reasons were suppressed:
  `:messages password-messages` had been commented out since the dual-build era, which was
  survivable at a minimum of eight and is not at twelve. The messages show, and the button
  is never dimmed (`0dd4545c`).

- **The server's reason never left the server** — the reset endpoint answered
  `{:status 400 :message "..."}`, but `:message` is not a Pedestal response key, so the
  reply was a 400 with an empty body and the page showed a generic apology. Field-keyed
  messages in `:body` now, the shape registration already used (`991e5ff9`).

- **Two verdicts that disagreed** — a password the breach corpus refused was drawn as a red
  field error directly above a meter reporting UNCOMMON, in green, about the same string.
  The corpus verdict is the meter's: its own key, the fail colours, a Too common badge and
  the reasoning on a line under the bar (`b50602f1`).

- **The auth card's text was set solid** — the CSS reset sets `body{line-height:1}`, and a
  unitless line-height is inherited as a NUMBER, so every element that did not set its own
  rendered at its own font size and descenders ran into the next line. Eleven elements
  measured under a 1.25 ratio, nine at exactly 1.00 (`b50602f1`).

- **The gryphon panel tiled, and the legal links printed twice** — no `background-repeat`
  and no `background-size`, so once the form column outgrew the image a second cropped
  gryphon drew below it; and the consent line and the footer each linked the same two
  documents (`d5feb650`).

- **The email help link was one word mid-sentence** — "whitelist" as the whole clickable
  target, in two places, one of which also read "Didn't receive validation the email?" and
  advised resetting a password to fix a missing validation email (`d5feb650`).

**Changed**

- **A character's sharing is one line under its title** — a status (Not shared, Shared, or Link expired
  and the date) with its actions as text buttons: Share link until a link exists, then Copy link, New
  link and Stop sharing. It replaces the share buttons in the page header, which split into extra rows
  on a phone (`d2e01052`, `38365ce3`).
- **The character list row shares with one Copy link button** — it copies the link, or makes the share
  and copies it when the character has none; the status, New link and Stop sharing stay on the
  character page (`e388e24b`).
- **Share wording** — the startup log's share settings speak of share data, the Share link button no
  longer describes server storage, and What's New describes short links, New link and Stop sharing
  (`124c1c62`, `50ccee78`).
- **A party forgets a character's link when its share ends** — New link, Stop sharing, expiry and
  deleting the character delete the token the party saved; the character stays in the party
  (`d1080896`).
- **The share settings are documented** in `docs/ENVIRONMENT.md` (`6dbf9161`).
- **Browser probes share one way to find Chromium** — they find Playwright's
  current install without setting four variables by hand, and the runner reports
  a missing browser once as a skip (`47d3ed8f`).
- **The PDF check is now a named suite, `export-character-pdf.js`** — it hides the What's New
  panel that had been taking its clicks (`60adac5f`).
- **run.sh lists the e2e suites and runs them on any machine** — it honours E2E_PORT and finds
  Chromium the same way the probes do (`60adac5f`).
- **run.sh says when it turns CSP off for a development bundle** (`60adac5f`).
- **A browser check that a character shows its owner's items to others** — it opens a seeded character
  logged out, as another account and as the owner, and checks the item reaches the sheet each time;
  run.sh now waits for the test accounts to be seeded before a suite starts (`c3f656cb`).
- **Named Garden classes replace 28 inline style maps on the registration, login and password
  pages** — 49 class definitions were taken from the stalled `refactor/garden-inline-styles`
  branch without merging it. Two classes that were generated from value lists emitted no CSS
  before and are generated here. The password meter keeps an inline width because that width
  is the measurement. Nothing renders differently (`887293f5`, `a104089a`).
- **The nine auth pages share one heading** — `registration-page` was always the shared shell,
  but six pages carried their own copy of the same orange drop-shadowed heading. The shell owns
  it now, as ink with an amber rule, so it changes in one place (`dab92f84`).

- **The auth fields are notched outlines with a real label** — the label rides the input's
  border rather than sitting in a placeholder that vanishes the moment somebody types, so the
  field never stops saying what it is (`5a8d7500`).

- **Errors read in GOV.UK order** — the label lifts out of the notch, the message sits under it
  and the input follows, with a rail down the group, a 1px border and a soft tint instead of a
  heavy outline plus a boxed message. A summary above the form counts fields rather than
  messages, and each line focuses its field (`8ee1dd8a`, `e59bfa6a`).

- **A password can be revealed, and the confirm box retires when it is** — two boxes while it
  is masked, one while it is not, and `display: none` rather than dimmed so a submit cannot
  fail pointing at a field nobody can see (`93faf389`).

- **The email is confirmed, not the password, and a mistyped domain is offered a fix** — a
  mistyped password is recoverable; a mistyped address makes a dead account holding the username
  its owner wanted and mails a stranger on the way. Within two edits of a known domain, a
  correction is offered under the field (`0a64172c`).
- **The last three auth pages join the shared heading** — `verify-failed`,
  `send-password-reset-page` and `password-reset-page` still drew their own bold heading
  with no rule and no form gutter, so three of the nine shipped unredesigned, and they are
  the three somebody locked out of their account sees. The two reset pages would both have
  read "reset password"; the one that mails a link is "Reset your password" and the one
  that takes the new password is "Choose a new password" (`82ea472d`).

- **Every password rule is visible as its own chip, and the meter says what to do next** —
  five chips under the bar, one per rule, each showing whether this password satisfies it
  before anything is pressed. Below the bar, a line points forward at every rung except the
  top, so a password that is already good enough is not told off.

- **The reset form gets the meter and the reveal** — it refused by exactly the same rules
  as registration while showing no meter, offering no reveal and demanding a confirmation
  it never retired. The meter was a block inside `register-form` reading the registration
  form directly; it is a component now, used by both (`05bac5ed`).

- **A password is refused for the corpus only when it is egregiously common** — 1000
  appearances, not one. The count was already returned and both call sites flattened it to
  a boolean. Reset also judges the password against the username, which registration did
  and it did not (`1cf25582`, `991e5ff9`).

- **Five dead rules removed** — the four `password-strength-*` from the meter this replaced
  and `success-header`, all with zero uses outside garden (`b50602f1`).

### refactor/picks-namespace

**Added**

- **Remove and put back one stored pick by its address** — `picks/remove-at` and `picks/put-at`, the base for repairing a character and for setting picks aside (`439593dd`, `b33c765a`).

**Changed**

- **Lint is clean** — the 50 warnings and 3 infos on `integration` are fixed, not suppressed: shadowing locals renamed for what they hold, unused requires removed, nested lets merged; no behaviour change (`ad3c612b`).
- **The pick walker moved into shared code** — `walk-entries`, `walk-picks`, `picks-of` and `relink-picks` now live in `orcpub.dnd.e5.picks` (as `walk`, `walk-typed`, `keys-of`, `relink`), unchanged, so the server can use them and their tests run in CI (`439593dd`).

### ci/clojure-cve-scan

**Added**

- **Security alerts cover the server's Clojure libraries** — a CI job reports them to GitHub's dependency graph each week and on every push and pull request, so Dependabot warns about a vulnerable one; it could not read `project.clj` before (`233b7b80`).

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
