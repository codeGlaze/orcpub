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

## Changed

- The 28 templates are now 8: for each style, one to grow from and one with no
  spell page for a character who casts nothing. 44.3 MB to 9.7 MB.
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
