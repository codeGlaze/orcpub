# TODO — Tracked Issues

## Datomic transactor crashes — investigate Postgres migration

**Status:** Open  
**Severity:** Critical — transactor crashing 3–5× per day, 2–3 min downtime each  
**Reported:** 2026-02-26  
**KB doc:** [docs/kb/datomic-crash-analysis.md](kb/datomic-crash-analysis.md)

### Summary

The Datomic transactor is self-terminating multiple times daily with
`"Critical failure, cannot continue: Heartbeat failed"`. Root cause is H2
write-lock contention during memoryIndex flushes starving the heartbeat thread.
`writeConcurrency=4` amplifies the problem — H2 cannot parallelize writes.

### Immediate mitigation (low risk, config only)

Set `datomic.writeConcurrency=1` in the transactor properties file. See KB doc
for caveats.

### Permanent fix

Migrate from Datomic Free + H2 to Datomic Pro + PostgreSQL. Datomic Pro is
free under Apache 2.0 (see `docs/migration/datomic-pro.md` — peer migration
already done). What remains is the **storage backend migration**:

1. Provision PostgreSQL (Docker service or managed)
2. Run Datomic's SQL init scripts (`bin/sql/postgres-*.sql`)
3. Export data from H2 transactor with `bin/datomic backup-db`
4. Restore into Postgres transactor with `bin/datomic restore-db`
5. Update transactor properties: `storage-class=sql`, JDBC params
6. Update Docker Compose to add Postgres service and remove H2 volume

### Related

- `docker/datomic/` — transactor container and config templates
- `docs/migration/datomic-pro.md` — peer library already migrated to Pro
- `docs/kb/datomic-crash-analysis.md` — full root cause analysis with log evidence

---

## localStorage corrupt data persistence

**Status:** Open
**Severity:** Medium
**Reported:** 2026-02-21

### Problem

When `reg-local-store-cofx` reads localStorage data that fails spec validation,
it logs a warning and ignores the data — but never removes it. The corrupt data
persists across reloads, producing `INVALID ITEM FOUND, IGNORING` on every page
load. If the user never interacts with the affected feature (to trigger an
overwrite), the corrupt data stays indefinitely.

Known corruption vector: `assoc-in` on `nil` builds maps with integer keys
instead of vectors. Example from combat tracker:

```clojure
(assoc-in nil [:monsters 0 :monster] :adult-gold-dragon)
;; => {:monsters {0 {:monster :adult-gold-dragon}}}  — MAP, not vector
```

This was partially fixed by guarding `set-combat-path-prop` with
`(or combat default-combat)`, but other handlers using `assoc-in` through
`path` interceptors may have the same vulnerability.

### Proposed fix

Scope cleanup by data criticality:

| Category | Examples | Action on invalid |
|----------|----------|-------------------|
| Ephemeral | combat, builder state | `.removeItem` — safe to lose |
| Rebuildable | spells, monsters | `.removeItem` — regenerated from source |
| Critical | plugins, characters, user | Quarantine: rename key to `<key>_corrupt_<timestamp>` |

This preserves recovery options for irreplaceable user data (homebrew plugins
can be 2-5MB of daily imports) while cleaning up transient state that would
otherwise stubbornly persist.

### Related

- `src/cljs/orcpub/dnd/e5/db.cljs` — `reg-local-store-cofx` (line ~252)
- `src/cljs/orcpub/dnd/e5/events.cljs` — `set-combat-path-prop` nil guard
- All `*->local-store` serializers use `(str data)` / `reader/read-string`

---

## Restrict PDF image-URL schemes and reject private address ranges

**Status:** Open
**Severity:** Medium — unauthenticated endpoint, pre-existing
**Reported:** 2026-04-22
**File:** `src/clj/orcpub/routes.clj:678-693`
**Surfaced during:** PDF widget-warnings review session (working branch
`claude/fix-pdf-widget-warnings-hUt9i`, code landed on
`bugfix/pdf-widget-warnings`).

### Summary

`character-pdf-2` accepts `image-url` / `faction-image-url` from the EDN
request body and fetches them via `java.net.URL.openConnection` to embed
as portrait / faction images. The current validation regex allows four
URL schemes:

```clojure
(re-matches #"^(https?|ftp|file)://..." image-url)
```

`file://` and `ftp://` are meaningful attack vectors, and `http[s]://`
is not restricted to public address space. The endpoint also has no
`check-auth` interceptor (`routes.clj:1498`), so any caller can drive
the URL fetch.

### Realistic exploit shape

- **LFI** (e.g. `file:///etc/...`): the URL is fed to `ImageIO/read`. If
  the file isn't a valid image format, the read fails and the PDF comes
  back without that image. So LFI is gated on the target file being a
  valid image — but server-readable PNG/JPEG files (other characters'
  uploaded portraits, branding assets, container layer files) are
  reachable.
- **SSRF / metadata theft** (e.g. cloud metadata endpoints): typically
  return JSON, not images, so the embed fails — but timing differences
  (success / 4xx / 5xx / timeout) are observable from the response body
  size and can be used for service discovery.
- **Internal SSRF**: same shape as cloud metadata — observable timing
  reveals reachable internal services even without successful embed.

### Proposed fix

- Restrict schemes to `https?` only.
- Resolve the hostname once and reject if the resolved address falls in
  a private / link-local / loopback / unspecified range
  (IPv4: `10/8`, `172.16/12`, `192.168/16`, `127/8`, `169.254/16`,
  `0/8`; IPv6: `::1`, `fc00::/7`, `fe80::/10`).
- Decide whether `/character.pdf` should require authentication at all —
  currently anyone reaching the host can drive PDF generation.

### Notes for future agents

- Pre-existing — predates the PDF widget-warnings fix. That fix narrowed
  the attack surface slightly (removed UA-driven flatten path) but did
  nothing for image URL handling.
- Not filed on the public-facing `bugfix/pdf-widget-warnings` branch —
  kept here on `agents/develop` to avoid surfacing a security writeup
  on a public PR. When a real fix lands, that change can carry a clean
  changelog entry describing the validation tightening without exploit
  detail.

---

## Wire up `:allies` PDF field + add Allies/Organizations builder UI

**Status:** Open
**Severity:** Low — feature gap, not a regression
**Reported:** 2026-04-22
**Upstream issue:** [Orcpub/orcpub#160](https://github.com/Orcpub/orcpub/issues/160)
**Files:** `src/cljc/orcpub/dnd/e5/character.cljc`,
`src/cljs/orcpub/character_builder.cljs`,
`src/cljc/orcpub/pdf_spec.cljc`

### Summary

The bundled fillable templates expose a text field named `allies` that
no code path populates. The character entity has no `allies` getter, the
builder has no UI for it, and `pdf_spec.cljc` doesn't include it in the
generated field map.

Existing Description tab fields (verified 2026-04-22):
- Faction Name (wired → `faction-name`)
- Faction Image URL (wired → `faction-image-url`)
- Description/Backstory (wired → `backstory`)
- Notes (entity field exists; no template field to populate)

### Verified gap

```
$ probe template fillable-char-sheetstyle-1-3-spells.pdf
Field 'allies' present, currently filled by: nothing
```

`pdf_spec.cljc` populates ~50 named fields; `:allies` is not among them.
Builder grep for "allies" or "organi[sz]ation" returns no matches.

### Proposed scope

Three pieces:
1. Add `notes`-pattern getter `(defn allies [built-char])` in `character.cljc`
2. Add an `Allies/Organizations` textarea in the Description tab of
   `character_builder.cljs` (mirrors the `Description/Backstory`
   textarea pattern at line ~1866)
3. Add `:allies (char5e/allies built-char)` in the appropriate
   field-builder fn in `pdf_spec.cljc`

Tangentially helped by `bugfix/pdf-widget-warnings`: Firefox/Safari
users who used to copy-paste from the form-fillable PDF can now do so
on every browser, expanding who can use the workaround the issue
describes — but doesn't address the deeper "make this editable in the
app" ask.

### Notes

Repurposing `notes` for `allies` would conflate two distinct concepts.
Cleaner to add a new field.

---

## Features and traits PDF cutoff for very long content

**Status:** Open — partially improved by `bugfix/pdf-widget-warnings`,
not fully fixed
**Severity:** Low — affects characters with unusually long features lists
**Reported:** 2026-04-22
**Upstream issue:** [Orcpub/orcpub#192](https://github.com/Orcpub/orcpub/issues/192)
**Files:** `resources/fillable-char-sheetstyle-*.pdf` (templates)

### Summary

Characters with enough features to overflow the `features-and-traits`
field's height get cut off in the exported PDF. No overflow-to-new-page
behavior.

### Partial improvement landed

`bugfix/pdf-widget-warnings` removed the User-Agent sniff and made
interactive PDFs the default. Interactive PDFs use the template's
`/Helv 0 Tf` auto-size default appearance, so PDF readers shrink text
to fit the field. Before, non-Chrome users got a flattened PDF with
size 8 baked in — guaranteed cutoff for long content.

### Why not a complete fix

PDF auto-size has a minimum readable size; below that, content still
overflows. The field's height is fixed by the template. Genuinely long
features-and-traits will still cut off at the field bottom even with
auto-size.

### Proposed fix

Two viable approaches:
- **Template-side**: re-author the bundled templates with taller
  `features-and-traits` fields, or split into `-2`/`-3` overflow
  fields (some templates already have a `features-and-traits-2`).
  Affects all 28 `fillable-char-sheetstyle-*.pdf` resources.
- **Client-side overflow**: detect overflow at PDF generation time
  and inject an additional appendix page with the remainder. Avoids
  re-authoring templates but adds complexity to `routes.clj`.

Don't claim this issue closed in the `bugfix/pdf-widget-warnings` PR.
Note the partial improvement on the issue itself when commenting.

---

## Cross-linked spell-prep checkboxes (template field-name collision)

**Status:** Open
**Severity:** Medium — incorrect form behavior visible to every user
**Reported:** 2026-04-22
**Upstream issue:** [Orcpub/orcpub#202](https://github.com/Orcpub/orcpub/issues/202),
duplicate of [#323](https://github.com/Orcpub/orcpub/issues/323) (closed
without fix)
**Files:** `resources/fillable-char-sheetstyle-*.pdf` (templates)

### Summary

The bundled fillable templates contain widgets across multiple pages
that share field names. PDF readers treat same-named widgets as one
logical field, so checking a checkbox on page 1 also checks the
identically-named checkbox on page 2.

### Verified

`fillable-char-sheetstyle-1-3-spells.pdf` (3 spell pages) contains 34+
duplicate field names across pages — `Check Box 3010`, `Check Box 3011`,
... `Check Box 3043` each appear on multiple pages. `CHARACTER IMAGE`
also duplicates.

```
$ probe-template-duplicates fillable-char-sheetstyle-1-3-spells.pdf
Check Box 25
Check Box 3010
Check Box 3011
[...34 more...]
CHARACTER IMAGE
```

### Proposed fix

Template-side. Each widget needs a unique fully-qualified field name.
Two approaches:

- **Manual re-author** in Acrobat/PDF tooling: open each of the 28
  templates, rename duplicate widgets per page (e.g.
  `Check Box 3010` → `Check Box 3010-page2`).
- **Programmatic regen via PDFBox**: a Clojure script that walks each
  template's `AcroForm.fields`, detects collisions, renames widgets by
  appending a page index, and saves back to resources. Reproducible,
  idempotent, but needs verification that no `pdf_spec.cljc` field key
  references the renamed names by exact match (verified 2026-04-22 for
  the duplicate set: none of `Check Box 30NN` or `CHARACTER IMAGE` are
  used as keyword keys in the spec — they're decorative widgets the
  user fills manually after export).

Out of scope for `bugfix/pdf-widget-warnings`. Worth filing as its own
PR, especially since the programmatic approach is testable and produces
diff-able binary output.
