# CLAUDE.md

## Project Overview

OrcPub is a D&D 5e character builder (Clojure/ClojureScript). PDF character sheets are generated server-side using PDFBox 3.x against bundled fillable template PDFs (28 templates: 4 styles x 7 spell-count variants in `resources/fillable-char-sheetstyle-{1-4}-{0-6}-spells.pdf`).

## Build & Test

```bash
lein test                                         # all tests
lein test orcpub.pdf-test                         # PDF unit/integration tests
lein test orcpub.routes-pdf-test                  # handler-level PDF tests
lein test orcpub.pdf-test orcpub.routes-pdf-test  # both PDF suites
```

Note: `lein` must be installed and clojars must be reachable (the CI sandbox proxy blocks it — tests must run in a real dev environment).

## Key Files

| File | Role |
|---|---|
| `src/clj/orcpub/pdf.clj` | PDF generation: `write-fields!` (populate + optionally flatten AcroForm), `fix-widget-page-refs!` (set missing `/P` entries on widget annotations), image drawing, spell card rendering |
| `src/clj/orcpub/routes.clj` | HTTP handler `character-pdf-2` (~line 626): parses EDN payload, selects template, calls `write-fields!`, draws images, returns PDF response |
| `src/cljc/orcpub/pdf_spec.cljc` | `make-spec` generates the field name/value map passed to `write-fields!` |
| `src/cljs/orcpub/dnd/e5/views.cljs` | Client-side UI including print options (~line 3693) |
| `test/clj/orcpub/pdf_test.clj` | Unit/integration tests for `fix-widget-page-refs!`, `write-fields!`, template loading |
| `test/clj/orcpub/routes_pdf_test.clj` | Handler-level tests for `character-pdf-2` |

## Current Branch: `claude/fix-pdf-widget-warnings-hUt9i`

### What it does

1. **Fixes PDFBox "missing /P entry" warnings.** Template PDFs have widget annotations without `/P` (page back-reference) dictionary entries. PDFBox 3.x warns about each one during `AcroForm.flatten()`, producing hundreds of log lines per PDF. `fix-widget-page-refs!` walks pages -> annotations and sets `.setPage(page)` on orphaned `PDAnnotationWidget` instances before flatten runs.

2. **Removes Chrome UA sniffing; defaults to fillable PDFs.** The old code forced `flatten=true` for non-Chrome user agents — a 2017 workaround for Firefox's pdf.js not supporting AcroForms (fixed in Firefox 84, Dec 2020). Now everyone gets interactive/fillable PDFs by default. Clients can opt into flattening via `:flatten? true` in the EDN request payload.

3. **Strict boolean check on `:flatten?`.** Uses `(true? flatten?)` instead of Clojure's loose truthiness, so a malformed client payload (`"yes"`, `1`, `{}`) falls through to the safer interactive default.

### Commit history

```
b1b8cef Strict :flatten? check; strengthen PDF tests with real coverage
139c570 Revert font-sizes decoupling; preserve template `0 Tf` auto-sizing
c9e0b11 Review follow-ups: font-sizes regression, docs, tests  [PARTIAL REVERT in 139c570]
f1ea7fc Remove Chrome UA sniffing; default PDFs to fillable
c8a5306 Fix missing /P (page reference) warnings on PDF widget annotations
```

**Important:** `c9e0b11` incorrectly decoupled font-size overrides from the flatten flag. The bundled templates use `/Helv 0 Tf` (PDF auto-sizing) on all text fields — the font-size override to 8pt exists only for the flatten path, because flattening has to bake in a concrete point size. Applying it unconditionally clobbered auto-sizing for interactive forms. `139c570` reverts that specific change and adds a regression test that asserts the template's `0 Tf` default appearance is preserved in interactive mode.

### What's left before merge

- **Run the test suite** in a dev environment with clojars access: `lein test orcpub.pdf-test orcpub.routes-pdf-test`. Tests couldn't be executed in the CI sandbox due to proxy/auth issues.
- **Visual smoke test** on sheet styles 1-4 in Firefox, Safari, Chrome, and Adobe Reader: confirm the portrait image is visible (not hidden under widgets now that the form isn't flattened), and long-text fields auto-size correctly.
- **CHANGELOG.md entry** (the changelog is actively maintained and has a `PDF Generation` subsection): document the /P fix, the fillable-by-default change, and the new `:flatten?` opt-in key.
- **Optional: squash or clean up history** if the revert-of-bad-commit (`c9e0b11` -> `139c570`) bothers you. The end state is correct regardless.

## Known Issues (Pre-existing, Not Part of This Branch)

### SSRF/LFI on image-url / faction-image-url

`routes.clj:667,675` validates image URLs with a regex that permits `file://` and `ftp://` schemes. The URL is then fetched server-side via `java.net.URL.openConnection()`. This allows:

- **`file://`** — local file inclusion (read any server-readable file, embed in PDF)
- **`ftp://`** — SSRF to internal FTP servers
- **`http://169.254.169.254/...`** — classic cloud metadata credential theft

Fix: restrict to `https?` only, resolve hostname once, block private IP ranges (10/8, 172.16/12, 192.168/16, 169.254/16, 127/8). Separate PR.

### DRY: sheet0..sheet6 template selection

`routes.clj:636-650` has 7 identical string-format bindings and a 7-branch cond that differ only by a number. Could be 3-5 lines with a loop. Pre-existing, separate cleanup.

## PDF Architecture Notes

- **Template auto-sizing:** All text fields in the bundled templates use `/Helv 0 Tf 0 g` (PDF auto-size). Readers auto-fit text to the field rectangle. The font-sizes map in `routes.clj:566-582` is only applied in the flatten path — flattening can't preserve auto-size and needs a concrete point size (8pt for long-text fields like personality-traits, backstory, etc.).

- **`write-fields!` parameters:**
  - `fields` — map of `{field-name-keyword value}`. Checkboxes accept truthy/falsey; text fields accept any value (stringified). Unknown names are silently skipped.
  - `flatten?` — when `true`, bakes widget appearances into the page content stream and removes the interactive form. When falsey, leaves the form interactive/fillable.
  - `font-sizes` — map of `{field-name-keyword pt-size}`. Only consulted when flattening. Ignored otherwise to preserve template auto-sizing.

- **`fix-widget-page-refs!`** only runs in the flatten path (`when flatten?`). For interactive forms, the warnings don't fire because `.flatten` is never called.

- **Image z-order:** Images are drawn via APPEND content streams after `write-fields!`. In interactive mode, widget annotations render above page content in PDF readers. The portrait/faction image areas are typically widget-free, but any overlap would put widgets visually on top of images.
