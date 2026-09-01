# Branch changelog — `fix/pdf-endpoint-hardening`

## Why this branch exists

`/character.pdf` is reachable without signing in, and it took an image URL it
would fetch, a sheet style id it interpolated straight into a resource path, and
a request body of any size. Those are fixed here.

Working in the exporter then surfaced how much of the sheet was wrong once a
character outgrew one page. Spell pages stopped at whatever the template carried,
long text shrank until it was unreadable rather than continuing anywhere, and
fields shared names across pages — which in a PDF form means they share a value,
so one class's prepared spells and expended slots appeared on another's page.
That is the bulk of the diff.

The template preparation moved out of the request path along the way: the
cleanups are pure functions of the template, so they are baked into `resources/`
by `dev/prepare_templates.clj` and committed, rather than recomputed on every
export.

## Highlights

Character sheets now hold a whole character. Spell pages are generated for as
many casting classes as the character has rather than stopping at the six the
artwork ships, a class whose spells outgrow a page continues onto another marked
`(continued)`, and long personality, ideal and backstory text spills onto a
continuation page instead of shrinking past legibility.

## Added

- Spellcasting pages are generated for classes beyond the last one the template
  carries, so an eight-class character gets eight sections instead of six
  (`7b51381`).
- Text too long for its box spills onto a continuation page under a heading,
  instead of shrinking until it cannot be read (`439f445`).
- A class whose spells run past one page continues onto another, marked
  `(continued)` in its heading, with its slot totals left on the first page only
  (`dd1bf01`).
- A spare spell level box can be relabelled to carry a different level, the
  cantrips box included, so continuation pages reuse boxes the character does not
  need (`fb663ae`, `6201cff`, `331a167`, `cab51a0`).
- The export is covered by a browser check that drives the real builder and
  asserts the resulting PDF's structure (`29fd42c`, `f9ca6ae`, `dfb0d07`,
  `10ec0dc`, `bc9ba8a`).

## Fixed

- The exporter no longer fetches whatever image URL it is handed (`d1bb764`).
- The sheet style id is validated against the styles on disk rather than
  interpolated into a resource path (`23ef685`).
- Request bodies are capped, and a rate-limit predicate that always returned true
  now answers correctly (`a12e28e`).
- A custom spell name wider than its box hung the export. Reachable without
  signing in (`439f445`).
- Ticking a spell prepared on one class's spell page no longer ticks it on
  another's, and the two classes no longer share one SLOTS EXPENDED value. 101
  fields on the widest template had widgets on two pages, which in a PDF form is
  one field showing one value twice (`e371c19`).
- Every form field has its own name; none are left sharing one (`0296d84`).
- A three-character ability modifier no longer clips against the edge of its box
  (`581513a`).
- Values with no matching field in the chosen template were dropped silently and
  are now reported (`fe2a3e9`).

## Changed

- Template cleanups are baked into `resources/` by `dev/prepare_templates.clj`
  instead of running on every request. A production export drops from 1407 fields
  and 2679 KB to 333 fields and 1313 KB with every filled value intact
  (`f956c69`, `fe2a3e9`).
- Checkboxes are named for what they do — `prepared-1-1-1` beside `spells-1-1-1`,
  and the death saves — rather than `Check Box 25` (`94fa2fc`).
- The blanks beside each SLOTS TOTAL box are named `slots-expended-<level>-<class>`
  rather than `SlotsRemaining 19`, so the field a player types into says what it
  is (`eb1d542`).
- The overflow cutoff is a font size rather than a character count: text shrinks
  to 8pt and spills below that (`fa83619`).
