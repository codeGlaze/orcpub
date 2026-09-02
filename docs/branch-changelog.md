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
