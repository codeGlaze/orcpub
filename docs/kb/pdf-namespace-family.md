# A namespace family for `pdf.clj`

*Investigation and proposal. Nothing was split; no `src/` file was touched.*

Measured against **`origin/integration` at `b8035afe`**, where `src/clj/orcpub/pdf.clj` is
3,212 lines and `src/clj/orcpub/spellbook.clj` is 956. `agents/develop` carries a different
`pdf.clj` (3,638 lines) and has no `spellbook.clj` at all, so every line number below is an
`integration` line number and will not match this branch's checkout.

Searched with `scripts/clj-grep.py`. **`pdf.clj` and `spellbook.clj` contain no `#_` forms at
all**, so live/discarded does not divide this file; it does divide `classes.cljc`, which the
companion plan reads.

## Verdict on the starting hypothesis

The hypothesis was two shared surfaces: (a) the card frame, (b) measurement + drawing + 5e
abbreviations. **Neither half holds as stated.**

| Claim | Finding |
| --- | --- |
| (a) the card frame is a shared surface | **Refuted.** `draw-card-frame!` (`pdf.clj:3050`) has exactly ONE live consumer, `print-items` (`pdf.clj:3148`). Spell cards do not use it — `print-spells` draws `draw-grid` (`pdf.clj:2603`) and nothing else. The whole 284-line ornament cluster is private and single-consumer. |
| (b) one text+draw+abbrev surface | **Refuted as one surface.** It is three with different consumer sets (measurement, SVG/icon, 5e abbreviations) plus a fourth, the inch-space drawing primitives, which `spellbook.clj` pointedly does *not* use. |
| two surfaces in total | **Refuted.** Six, and the two largest are not in the hypothesis at all: the AcroForm/character-sheet machinery (1,343 lines) and the image-egress stack (526 lines). |

The companion plan repeats the same overstatement: `plan-companions-and-wild-shape.md:96`
says "**Two families already print**: spell cards and magic item cards" on the strength of
"`card-pt`, `draw-card-frame!`, charge tracks". One family prints with that machinery. That
line should be corrected before it is used to size the beast-card work.

## 1. Inventory

186 top-level vars: 97 public, 89 private. No `declare`, no `defmulti`/`defmethod`/
`defrecord`/`defprotocol` — the file is in strict definition order with no forward references
(verified: `clj-grep.py --live 'declare' src/clj/orcpub/pdf.clj` hits only two docstrings,
`pdf.clj:1188` and `pdf.clj:1661`).

| Group | Lines | n | Pub | Key vars |
| --- | --- | --- | --- | --- |
| `ns` form + 25 `:import`s | 1–54 | 54 | — | — |
| Standard-14 fonts, embedded font loading | 55–81 | 27 | 4 | `HELVETICA`, `HELVETICA_BOLD`, `HELVETICA_BOLD_OBLIQUE`, `load-fonts` |
| SVG path parsing, Form XObjects | 82–253 | 172 | 5/11 | `svg-path-ops`, `last-svg-path`, `load-svg-icon`, `svg-form`, `draw-svg-path!` |
| Per-document image loader | 254–266 | 13 | 1 | `make-image-loader` |
| Text normalise / measure / wrap / fit | 267–283, 297–304, 1697–1737, 1752–1798 | 113 | 5/6 | `normalize-text`, `string-width`, `split-lines`, `fit-text`, `min-font-size` |
| **AcroForm + character-sheet machinery** | 284–296, 305–1087, 1738–1751, 1799–1938, 2131–2523 | **1343** | 33/70 | `sheet-masters`, `write-fields!`, `grow-spell-sections!`, `spill-overflow!`, `numeral-boxes`, `apply-relabel-instructions!`, `draw-column-heading!`, `stamp-site-line!`, `widget-box` |
| Inch-space geometry + drawing primitives | 1088–1137, 1694–1696, 1939–2033 | 148 | 17 | `content-stream`, `get-page`, `draw-text`, `draw-text-to-box`, `draw-lines-to-box`, `draw-grid`, `draw-line-in`, `draw-imagex`, `set-text-color`, `scale` |
| Icon colour + icon placement | 1138–1167 | 30 | 2 | `icon-red`, `draw-svg-icon!` |
| **Image egress** (SSRF guard, fetch, decode, fit, draw) | 1168–1693 | **526** | 14/36 | `safe-image-url?`, `safe-image-bytes`, `fetch-image`, `fetch-image-outcome`, `decode-image-bytes`, `draw-image-bytes!`, `image-egress-status`, `report-image-egress!` |
| 5e print abbreviations | 2034–2039, 2055–2088 | 40 | 6 | `abbreviate-duration`, `abbreviate-casting-time`, `abbreviate-range`, `abbreviate-times`, `max-len`, `spell-school-level` |
| Site line | 2089–2130 | 42 | 1/5 | `site-stamp`; private `site-stamp-size`, `site-stamp-strip`, `draw-site-stamp!`, `page-prints-site-line?` |
| Card-sheet page printing (spell cards + backs) | 2040–2054, 2524–2738 | 230 | 3 | `print-spells`, `print-backs`, `draw-spell-field` |
| **Card frame + ornament** | 2739–2782, 2855–3094 | **284** | 2/19 | `card-layout`, `card-flourishes`; private `card-pt`, `polyline!`, `diamond!`, `circle!`, `chamfered-frame!`, `draw-flourish!`, `draw-rarity-rail!`, `draw-foot-ornament!`, `draw-centred-text!`, `draw-centred-lines!`, `draw-attunement-badge!`, `draw-charge-track!`, `draw-card-frame!` |
| Magic-item semantics | 2783–2854 | 72 | 3/5 | `item-charges`, `attunement-phrase`, `magic-item-subtitle`; private `rarity-rank`, `alignment-attunement` |
| `print-items` | 3095–3213 | 119 | 1 | `print-items` |

`n` is lines; `Pub` is public/total vars where any are private. Groups sum to 3,213 (the file's
3,212 lines plus the trailing newline), so the table is exhaustive.

Two `:require`s in the `ns` form are **dead**: `orcpub.dnd.e5.display :as dis5e` (`pdf.clj:13`)
and `orcpub.dnd.e5.options :as options` (`pdf.clj:14`) have zero uses in the file.
`orcpub.dnd.e5.magic-items :as mi` is used on four lines only (`pdf.clj:2841`, `3123`–`3125`).

## 2. Caller map

`orcpub.pdf` is required by **3 `src/` namespaces, 8 test namespaces and 14 `dev/` scripts**.

### Production

| Caller | Uses |
| --- | --- |
| `src/clj/orcpub/routes.clj:41` | `sheet-masters`, `merge-style-fields`, `grow-spell-sections!`, `add-missing-spell-pages!`, `apply-relabel-instructions!`, `reserve-annotation-columns!`, `write-fields!`, `spill-overflow!`, `annotate-spell-rows!`, `draw-column-heading!`, `stamp-site-line!`, `load-fonts`, `make-image-loader`, `print-spells`, `print-backs`, `print-items`, `get-page`, `draw-image-bytes!`, `fetch-image`, `fetch-image-outcome`, `decode-image-bytes` — **21 vars across every group except the card frame**. `pdf/safe-image-url?` (`routes.clj:1536`) and `pdf/safe-image-bytes` (`routes.clj:1181`) appear in `routes.clj` **in comments only**; `safe-image-url?`'s only real external consumer is `pdf_image_test.clj` (39 refs). |
| `src/clj/orcpub/spellbook.clj:8` | 9 vars, listed below |
| `src/clj/orcpub/system.clj:15` | `report-image-egress!` — one call, `system.clj:113` |

`src/cljc/orcpub/pdf_spec.cljc` **does not require `orcpub.pdf`** and is not a caller
(`pdf_spec.cljc:1`–`14`). It is the upstream builder-side field-map producer, 888 lines, and
runs in ClojureScript. `src/cljc/orcpub/dnd/e5/spell_packing.cljc` likewise names
`pdf/relabel-numeral!` (`spell_packing.cljc:6`) and `pdf/numeral-boxes`
(`spell_packing.cljc:267`) **in docstrings only**, and deliberately re-declares the geometry
because "the builder decides the layout and cannot see the server's templates"
(`spell_packing.cljc:268`). That is the existing precedent for duplicating geometry across the
clj/cljc line rather than extracting it.

### `spellbook.clj`'s dependency — the starting fact, corrected

9 vars, as stated, with two naming corrections:

| Var | pdf.clj | spellbook.clj |
| --- | --- | --- |
| `normalize-text` | 267 | 50 (wrapped as `clean`) |
| `string-width` | 297 | 54 (wrapped as `width`, result ×72) |
| `split-lines` | 1697 | 60 (wrapped as `wrap`, width ÷72) |
| `svg-path-ops` | 130 | 208 (inside `svg-d!`) |
| **`draw-svg-icon!`** — not `draw-svg-icon` | 1144 | 309 |
| `abbreviate-casting-time` | 2074 | 407 |
| `abbreviate-duration` | 2065 | 411 |
| `abbreviate-range` | 2084 | 415 |
| **`site-stamp`** — a `def`, a string constant, not a fn | 2089 | 904, 905 |

`load-fonts` and `make-image-loader` are *not* in `spellbook.clj`'s dependency: `fonts` and
`img` arrive as parameters (`spellbook.clj:944`), because each `load-fonts` embeds its own
Vollkorn subset (`routes.clj:1002` docstring).

### Private → public widenings

**Two in the file's whole history on `integration`, and only one of them was for a consumer.**

| Var | Commit | Why |
| --- | --- | --- |
| `normalize-text` | `647fec9e` (2026-10-05) | the spellbook needed it. The only `pdf.clj` change in that commit, as stated. |
| `renumber-page-section!` (`pdf.clj:580`) | `28fd620d` "Clear the lint backlog: 30 warnings to 0" | lint, not a consumer. Its own docstring says "Public because the only caller is dev/style4_one_spell_page.clj" (`dev/style4_one_spell_page.clj:116`). It has **no `src/` or `test/` caller at all.** |

So the widening pressure from a second print surface has so far been exactly one var. That is
the strongest single argument that the shared surface is small and the duplication is
elsewhere — see §3.

### Tests reaching into privates

11 private vars, across 4 test namespaces. Every one of these breaks on a move.

| Private var | Reached from |
| --- | --- |
| `within-pixel-budget?` | `pdf_image_test.clj:77`, `:112`, `:120`; `pdf_image_fetch_test.clj:42` |
| `open-image-stream`, `pinned-connection-manager`, `proxied?`, `read-bounded-bytes`, `max-image-bytes`, `pin-mismatch-reported`, `report-pin-mismatch!` | `pdf_image_fetch_test.clj:22`–`24`, `41`, `50`, `244`–`245` |
| `fit-for-sheet` | `pdf_supplied_image_test.clj:72` |
| `spell-sections` | `pdf_test.clj:691` |
| `page-prints-site-line?` | `pdf_test.clj:800`; `dev/scan_site_line.clj` |
| `heading-size`, `fit-heading`, `heading-target-size`, `bar-compartments`, `shrink-slots-expended!` | `pdf_test.clj:1039`, `1044`, `1141`–`1150`, `1224`, `1236` |
| `cantrips-hexagon-box`, `cantrips-word-patch` | `dev/scan_cantrips_word.clj` |
| `fields-on-page` | `dev/style4_one_spell_page.clj` |

`pdf_test.clj:123` still reaches `normalize-text` through `#'orcpub.pdf/normalize-text`, which
is vestigial now that it is public.

### `dev/` is the heaviest consumer by file count

14 scripts. `dev/prepare_templates.clj` alone calls 9 template-mutation fns; `dev/measure_item_card.clj`
and `dev/compare_item_cards.clj` read `card-layout` and `card-flourishes`;
`dev/svg_icon_proof.clj` calls `draw-svg-path!`, `load-svg-icon`, `draw-text`, `draw-imagex`.
These scripts are the *only* consumers of `draw-svg-path!`, `share-checkbox-appearances!`,
`add-image-predictors!`, `share-duplicate-images!`, `name-slots-expended!`,
`name-prepared-checkboxes!`, `name-death-save-checkboxes!` and `renumber-page-section!`. Any
extraction must count `dev/` in its blast radius, and no planning doc so far does.

## 3. The shared surfaces, from the caller map

Six, ranked by how many independent consumers actually touch them.

| # | Surface | Lines | Independent consumers today | Shared? |
| --- | --- | --- | --- | --- |
| S1 | **Text measurement** — `normalize-text`, `string-width`, `split-lines`, `fit-text`, `min-font-size` | 113 | spellbook, AcroForm machinery, card printing, card frame, `pdf_test`, 3 `dev/` scripts | **Yes, strongly.** The only group every consumer touches. |
| S2 | **SVG + icons** — path parsing, Form XObjects, the loader, `draw-svg-icon!`, `icon-red` | 215 | spellbook, spell cards, `routes.clj`, `svg_path_test`, `dev/svg_icon_proof` | **Yes.** |
| S3 | **5e print abbreviations** — `abbreviate-*`, `max-len`, `spell-school-level` | 40 | spellbook, spell cards, `spellbook_test` | **Yes**, and pure: no PDFBox, no `:import`, cljc-ready. |
| S4 | **Inch-space drawing primitives** — `draw-text`, `draw-text-to-box`, `draw-grid`, `content-stream`, `draw-imagex`, … | 148 | AcroForm machinery, card printing, card frame, egress, `routes.clj`, `dev/svg_icon_proof` | **Shared inside `pdf.clj` only.** `spellbook.clj` re-implemented all of it — see below. |
| S5 | **Card frame + ornament** | 284 | `print-items` only (+ 2 `dev/` scripts reading two constants) | **Not yet.** A candidate, forced by beast cards. |
| S6 | **Image egress** | 526 | `routes.clj`, `system.clj`, 3 test namespaces | **Yes, and wholly independent** — it only reaches S4 for `draw-imagex`/`content-stream`. |

### The real duplication is S4, and it is a coordinate-space mismatch

`spellbook.clj` borrowed S1–S3 and **re-implemented S4 from scratch** rather than reuse it.
Not because the fns are private — they are all public — but because `pdf.clj` works in
**inches with the page height hardcoded** and the spellbook works in **points down from the
page top**:

| Concept | `pdf.clj` (inches, `11` inline) | `spellbook.clj` (points) |
| --- | --- | --- |
| y-flip | `in-to-coord-y` (`1100`), `card-pt` (`2744`), and `(- 11 …)` inline | `py` (`30`) |
| page size | none declared; `8.5`/`11.0`/`(- 11` on 48 lines | `page-w 612.0`, `page-h 792.0` (`16`–`17`) |
| circle | private `circle!` + `bezier-k` (`2739`, `2770`) | `bezier-k` + `circle-path!` + `circle!` (`166`–`185`) |
| polyline | private `polyline!` (`2751`) | `polyline!`, `line!` (`186`–`202`) |
| text | `draw-text` (`1964`) | `text!` (`136`) |
| centred text | private `draw-centred-text!` (`2978`) | `centred!` (`159`) |
| colour | `set-text-color` (`1959`) | `gray!` (`130`) |
| SVG path emit | `draw-svg-path!` (`209`) | `svg-d!` (`203`) |

The starting fact that `page-w`/`page-h` are declared in `spellbook.clj` and not in `pdf.clj`
**holds, and is the crux**. `pdf.clj` has no page-size constants at all; `grep page-w pdf.clj`
hits only `on-page-widgets`.

This is why the hypothesis's surface (b) splits. S1–S3 are unit-agnostic (strings, widths as
ratios, path ops in the SVG's own coordinates) so the spellbook could take them. S4 is not,
so it could not.

**`draw-svg-icon!` is the one leak across that line.** It takes inches and computes
`(- 11 y size)` (`pdf.clj:1167`), and `spellbook.clj:310`–`311` divides its point coordinates
by 72 to call it. It works only because a spellbook page is 792pt = exactly 11in. Any surface
on a different page height gets a silently misplaced icon.

### `draw-grid` is not as parameterised as the plan assumes

`plan-companions-and-wild-shape.md:169`–`170` says "`draw-grid` derives the layout from
`box-width`/`box-height` against 8.5 x 11, so a larger companion card is a parameter change,
not new machinery." `draw-grid` itself does (`pdf.clj:2003`). Its callers do not:
`print-spells` (`pdf.clj:2603`) and `print-backs` (`pdf.clj:2536`) both call
`(draw-grid cs 2.5 3.5)` with literals while taking `box-width`/`box-height` parameters, so a
non-2.5×3.5 spell card draws cut lines in the wrong place. Only `print-items`
(`pdf.clj:3114`) passes them through. A larger card is a parameter change **for item cards
only**.

## 4. Proposed namespace family

Ordered by dependency: each file may require only files above it. The DAG was computed over
every cross-group reference and is acyclic once three comment-only references are discounted
(`pdf.clj:1735` `line-height-factor` → `draw-lines-to-box`, `pdf.clj:1797` banner →
`numeral-boxes`, `pdf.clj:3097` `print-items` docstring → `print-spells`).

| Proposed ns | File | Est. lines | From | Forced by | Consumers that must change |
| --- | --- | --- | --- | --- | --- |
| `orcpub.dnd.e5.spell-print` | `src/cljc/orcpub/dnd/e5/spell_print.cljc` | **45** | abbrev group (2034–2039, 2055–2088) | nothing — do it first, it is free | `spellbook.clj` (3 refs), `spellbook_test.clj` (3), `print-spells` |
| `orcpub.pdf.font` | `src/clj/orcpub/pdf/font.clj` | **30** | fonts group (55–81) | every new surface | `routes.clj` (1), `pdf_test.clj` (6), 2 `dev/` |
| `orcpub.pdf.text` | `src/clj/orcpub/pdf/text.clj` | **120** | measure group | every new surface | `spellbook.clj` (3), `pdf_test.clj` (18), 3 `dev/` |
| `orcpub.pdf.svg` | `src/clj/orcpub/pdf/svg.clj` | **190** | svg + loader groups (82–266) | beast/companion cards (emblems), NPC statblocks | `spellbook.clj` (1), `routes.clj` (1), `svg_path_test.clj` (21), 2 `dev/` |
| `orcpub.pdf.canvas` | `src/clj/orcpub/pdf/canvas.clj` | **200** | draw-in + icon groups, **re-expressed in points with explicit page size** | beast cards, encounter statblock cards, animated-object cards | `routes.clj` (7 refs, all `get-page`), `dev/svg_icon_proof.clj`; `spellbook.clj` could then drop ~90 lines of its own primitives |
| `orcpub.pdf.image` | `src/clj/orcpub/pdf/image.clj` | **540** | egress group (1168–1693) | nothing — extract for isolation, not reuse | `routes.clj` (10 refs), `system.clj` (1), `pdf_image_test.clj`, `pdf_image_fetch_test.clj`, `pdf_supplied_image_test.clj` (11 privates) |
| `orcpub.pdf.card` | `src/clj/orcpub/pdf/card.clj` | **300** | card-frame group, `rarity-rank` generalised to a rank int | **beast cards** — the first second consumer the frame has ever had | `dev/measure_item_card.clj`, `dev/compare_item_cards.clj`; new `card_frame_test.clj` |
| `orcpub.pdf.card.sheet` | `src/clj/orcpub/pdf/card/sheet.clj` | **250** | cards-sheet group + the 9-up partitioning now duplicated in `routes.clj:1002`/`1065` | companion cards, animated-object cards | `routes.clj` (3 refs), `pdf_test.clj` (6), `card_export_test.clj` |
| `orcpub.pdf.card.item` | `src/clj/orcpub/pdf/card/item.clj` | **200** | item-5e + `print-items` | nothing | `routes.clj` (1), `magic_item_cards_test.clj` (28), 2 `dev/` |
| `orcpub.pdf` (what remains) | `src/clj/orcpub/pdf.clj` | **~1,450** | AcroForm + site-line groups | nothing — it is the character sheet and has no second consumer | nothing, if the extracted names are not re-exported |

Family total ≈ 3,325 against 3,212 today: an extraction of this shape **adds** roughly 100
lines of `ns` forms and re-stated `:import`s. It buys nothing in line count and everything in
blast radius.

### What to do, in what order

1. **`spell-print.cljc` (45 lines).** Pure functions, no `:import`, no PDFBox. Three callers.
   Sits beside the existing `orcpub.dnd.e5.spell-annotations` (`spell_annotations.cljc:1`, 50
   lines), which is the precedent: a 5e-semantics helper already lifted out of the print path
   into cljc. Do this one even if nothing else happens.
2. **`font` + `text` (150 lines).** Mechanical. Two constants and six fns, all public already,
   one vestigial `#'` in `pdf_test.clj:123` to delete.
3. **`canvas`, and decide the coordinate space before writing a line of it.** This is the real
   decision in the whole proposal, and it is not a refactor — it is an API change. Either
   `pdf.clj`'s inch space wins and `spellbook.clj` converts at its edges, or the spellbook's
   point space wins and the sheet and card code converts. The second is right (PDF's native
   unit is the point; `page-h` stops being implicit; `draw-svg-icon!`'s `(- 11 y size)` bug
   class disappears) and is also the one that touches more live code. **Do not start it while
   `pdf.clj` is being worked in.**
4. **`card` (300 lines), when beast cards are actually being built** and there is a second
   consumer to prove the generalisation against. Extracting it now would be guesswork: the
   frame's one consumer has shaped every signature in it.

`image` (540 lines) and `card.item` (200) are extractable at any time and forced by nothing.
Taking `image` out first is the cheapest 526-line reduction available and has zero overlap
with any planned surface — worth doing purely to shrink the file someone else is working in.

## 5. Risks

| Risk | Where | Severity |
| --- | --- | --- |
| **`pdf.clj` is live on `integration`.** 3 commits touched it on 2026-10-04/05. Any extraction conflicts with in-flight work. | `git log origin/integration -- src/clj/orcpub/pdf.clj` | **Blocking** for `canvas`; manageable for `spell-print` |
| **11 private vars are reached by `#'`/`@#'` from tests**, 8 of them in `pdf_image_fetch_test.clj` alone. A move silently breaks the var-quote at *read* time, not compile time. | §2 above | High |
| **14 `dev/` scripts** require `orcpub.pdf`, and are the sole consumers of 8 public fns. They are not on any test path, so nothing catches their breakage. | `dev/*.clj` | High, and invisible |
| **The coordinate-space split.** Inches with `11` inline (48 lines in `pdf.clj`) vs points with `page-h` (`spellbook.clj:17`). Moving `draw-text` et al. without resolving this reproduces the duplication one level down. | §3 | High |
| **`draw-svg-icon!` hardcodes an 11-inch page** (`pdf.clj:1167`) and already has a cross-space caller (`spellbook.clj:310`). | `pdf.clj:1144`–`1167` | High for any non-letter surface |
| **`draw-card-frame!` is coupled to magic-item rarity.** Its signature is `[cs x y w h rarity family]` and it looks `rarity` up in `rarity-rank` (`pdf.clj:2783`, used at `2954` and `3061`). A beast has no rarity; the parameter must become a rank integer, which is a behaviour change to the one shipping consumer. | `pdf.clj:3050` | Medium |
| **PDFBox state is threaded positionally, not in a context value.** `cs` (content stream), `doc`, `fonts` and `img` are separate parameters on every public drawing fn; `print-items` takes 11 (`pdf.clj:3105`). Nothing captures "the page being drawn", so any extraction keeps the long parameter lists. | `pdf.clj:2594`, `3095`, `3105` | Medium — raises the cost, does not block |
| **Line-width and colour are global mutable state on `cs`**, set and reset by hand: `(.setLineWidth cs (float 1))` at the end of `draw-card-frame!`, `draw-charge-track!`, `draw-attunement-badge!`; `set-text-color` reset to black inside `draw-text`. Splitting these across namespaces splits the save/restore discipline across namespaces too. | `pdf.clj:3050`–`3064`, `3070`–`3094`, `3018`–`3039`, `1964`–`1977` | Medium |
| **Geometry constants that must move with their drawers**, each read by a `dev/` measuring script: `card-layout` (`2985`, `dev/measure_item_card.clj`), `card-flourishes` (`2892`, `dev/compare_item_cards.clj`), `numeral-boxes` (`1799`, `dev/scan_numerals.clj`), `annotation-zone` (`2131`), `site-stamp-strip` (`2095`). | — | Medium |
| **`draw-grid`'s callers pass literals.** `print-spells:2603` and `print-backs:2536` hardcode `2.5 3.5`. Any new card size inherits a mis-drawn cut grid unless fixed at the same time. | `pdf.clj:2536`, `2603` | Medium, and a live latent bug |
| **`normalize-text` encodes a Type1 constraint onto Type0 consumers.** Its docstring (`pdf.clj:268`) says it exists because `PDType1Font` throws outside WinAnsi 0x20–0xFF. `spellbook.clj` draws with `PDType0Font` Vollkorn subsets, where the restriction is unnecessary — it is losing characters the font could show. Extracting it as-is freezes that. | `pdf.clj:267`, `spellbook.clj:50` | Low, but it is a correctness question, not a packaging one |
| Two dead `:require`s (`dis5e`, `options`) at `pdf.clj:13`–`14`. | — | Trivial |

**Not a risk:** no `declare`, no mutual recursion, no cycles. The definition order is already a
topological sort of the dependency DAG, which is why the group boundaries above fall on
contiguous line ranges.

## 6. What could not be determined

- **Whether the five planned surfaces need the card frame at all.** Only
  `plan-companions-and-wild-shape.md` (574 lines) and `plan-npc-statblock-customizer.md` (60)
  exist, and neither specifies a frame. The NPC statblock plan is about *derivation*
  ("The feature is derivation, not a new page", `plan-npc-statblock-customizer.md`) and may
  print nothing at all. The beast-card frame requirement is the companion plan's assertion at
  `:96`, which rests on the two-families claim this doc refutes. **The `card` extraction is
  sized but not justified by anything I could verify.**
- **Whether "beast cards", "companion cards", "encounter statblock cards" and
  "animated-object cards" are four surfaces or one parameterised surface.** The companion plan
  treats beast and companion cards as sequential slices of one card
  (`plan-companions-and-wild-shape.md:375`, `:400`); encounter and animated-object cards are
  named nowhere in `docs/kb/`. Unverified.
- **Nothing was compiled or run.** `lein` was not invoked, per the task. No claim here about
  whether an extraction compiles, and the `canvas` coordinate change in particular needs a
  visual diff of real output before anyone trusts it.
- **Whether `agents/develop`'s 3,638-line `pdf.clj` has the same group boundaries.** It is 426
  lines larger and has no `spellbook.clj`, so its caller map is different by construction. Not
  mapped.
- **Why `renumber-page-section!` is public for a `dev/` script when `fields-on-page` and
  `cantrips-hexagon-box` stay private and are reached with `#'` from `dev/` scripts.** The
  convention is inconsistent and I could find no rule in `docs/kb/` that settles it.

## 7. Follow-up, 2026-10-13: the docstrings, and what running it settled

The mapping above was done without invoking `lein`. The docstring pass on branch
`docs/pdf-api` (off `integration`, `b8035afe`) ran it. What changed:

**Documentation coverage closed.** All 23 undocumented public fns in `pdf.clj` now have
docstrings: public coverage 59/82 -> 82/82, private 57/59. The gap was almost exactly the
shared surfaces S1–S4 of section 3 — the coordinate converters, the text and box drawers, the
abbreviations, the image loaders, `print-spells`/`print-backs`.

**The API boundary is now stated in the file.** 82 public fns and 45 public defs; 31 are
called from outside. 21 by `routes.clj`, 9 by `spellbook.clj`, `report-image-egress!` by
`system.clj`. `safe-image-url?` and `safe-image-bytes` appear in `routes.clj` comments only.
This partly answers section 6's last open question — `renumber-page-section!` is not public
by a convention, it is public because nothing enforced one. The publics are accidental, so
"is it public?" carries no information about whether it is API.

**Two conventions for `y`, in one namespace.** Not in the map above and the sharper statement
of the S4 coordinate mismatch: inside `pdf.clj`, the image functions (`draw-imagex`,
`draw-svg-icon!`) route through `in-to-coord-y` and take `y` from the page TOP, while the text
functions (`draw-text`, `draw-lines-to-box`, `draw-text-to-box`) multiply by 72 directly and
take `y` from the BOTTOM. Card code flips by hand, `(- 11.0 y 0.08)`, at every text call. So
S4 is not one coordinate space that `spellbook.clj` diverged from — it is two, before
`spellbook.clj` is considered at all. An extraction has to pick one.

**`in-to-sz` and `inches-to-units` are the same function** — both `(float (* 72 inches))`,
declared 900 lines apart (`:1094`, `:1997`). Marked in both docstrings, not deduped.

**Nil handling, probed rather than assumed.** `max-len` returns nil on nil (`(count nil)` is
0, so the truncation branch never runs); `abbreviate-duration` short-circuits on nil;
`abbreviate-times` and `abbreviate-range` throw NPE. S3 is "pure and cljc-ready" as section 3
says, but two of its five fns are nil-hostile and `spellbook.clj` calls both.

Verification: `lein test` 704 tests / 5294 assertions green, `lein lint` 0 errors 0 warnings.
`comment-discipline-test` rejected 7 of the first-draft docstrings as over the 6-line house
limit — all new, no pre-existing debt in this file — so they were shortened rather than
approved as exceptions.

## 8. Executed, 2026-10-13: steps 1 and 3a, and two corrections to section 4

Branch `refactor/pdf-canvas`, off `integration b8035afe`, carrying the docstring and
geometry-test branches so all of it sits on one branch point.

**Step 1 done: `spell-print.cljc`.** The six pure functions — `spell-school-level`,
`abbreviate-times`, `max-len`, `abbreviate-duration`, `abbreviate-casting-time`,
`abbreviate-range` — are now `src/cljc/orcpub/dnd/e5/spell_print.cljc`, 69 lines, beside
`spell_annotations.cljc` as section 4 proposed. `spellbook.clj` no longer requires a
3,200-line clj file to shorten a duration string. Callers repointed: `print-spells`,
`spellbook.clj` (3), `spellbook_test.clj` (1). `max-len` in `orcbrew_validation.cljs` is a
same-named ClojureScript function, a collision rather than a caller, so untouched.

**Step 3a done: the shape paths, and section 4 was wrong to defer it.** Section 4 calls
`canvas` "not a refactor — it is an API change" and says not to start it. That is right
about re-expressing the *public* surface in points — `draw-text`, `draw-grid`, `get-page`,
21 call sites in `routes.clj` — and that part remains open. It is wrong about the
duplicated shape primitives: `polyline!`, `circle!`, `diamond!` and `circle-path!` are
`defn-` on **both** sides, so unifying them moves nothing public.

`src/clj/orcpub/pdf/canvas.clj`, 41 lines, holds `path!`, `circle-path!` and `bezier-k`.
Each caller passes a `pt` mapper — `card-pt` for inches from a card's top-left, `py` for
points from the page top — and no coordinate system changed. Both spaces measure y
downward, which is why one formula serves them.

Only the path is shared, not the whole primitive, and that was the finding that made it
free: the two terminate differently, `closeAndStroke` emitting `s` against `closePath` plus
`stroke` emitting `h S`. Sharing the terminator would have rewritten one side's content
stream for no visual change. Leaving it to the callers made the whole thing byte-identical.

Corrected from my own first reading: the two circles do **not** emit in different orders.
Both run right, top, left, bottom, counter-clockwise in PDF space. Only the terminator and
the place the mapping happens differed.

**`pdf.clj` now:** 76 public fns and 16 public defs, against 59 and 29 private; 28 names
called from outside, 21 by `routes.clj`, 6 by `spellbook.clj`, 1 by `system.clj`. Down from
82 and 32. An earlier note on this branch said "45 public defs"; `grep '^(def '` also matches
`(def ^:private`, so that number counted private ones.

**How it was proven safe**, because the cards' positions were tuned by hand over several
rounds and nothing in the suite read a coordinate before this:

- Both geometry baselines unchanged across every step — 33 card pages of full operators,
  24 spellbook fingerprints. `git status` after each change listed only source files.
- Breaking `canvas/path!` by half a point fails 15 card and ornament pages **and** all 24
  spellbook pages, so both callers route through the shared code rather than keeping a live
  copy of it.
- `lein test` 710/5402 and `lein lint` 0/0 at each step.

**Still open**, in the order section 4 gives: `font` + `text` (step 2, mechanical); the
public point-space change (the rest of step 3, and the real decision); `card` (step 4, when
beast cards exist); `image` (540 lines, forced by nothing, the cheapest size reduction
available).

## 9. The ten-namespace family is withdrawn, 2026-10-15

Section 4 proposes ten namespaces. **The owner rejected it, and the rejection is right.** Their
words: "Are you seriously making 10 files instead of one common file to share between different
layouts?" With a size rule attached — a 500-line file is fine, a 20-line file is not.

Section 4's own table concedes the cost: the family totals ~3,325 lines against 3,212 today,
because it *adds* roughly 100 lines of `ns` forms and re-stated `:import`s. It buys blast radius,
and it was written by a mapping agent as a proposal. It was then relayed to the owner as "the
roadmap", which it never was.

What the measurement actually supports is **one shared file**. The genuinely shared surface is
text measurement, SVG and icons, the shape paths, and the coordinate conversion: roughly 450-500
lines. Section 4 split that across `font` (30), `text` (120), `svg` (190) and `canvas` (200) —
and the 30-line one is exactly the file the size rule forbids.

`src/clj/orcpub/pdf/canvas.clj` as shipped in PR #63 is **41 lines**, which is the same mistake
on a smaller scale. The correction is to GROW it into the single shared file, absorbing the
coordinate converters, text measurement and SVG, not to add four siblings beside it.

Standing after this revision:

| Was | Now |
| --- | --- |
| `spell-print.cljc` (45) | **kept and done.** Pure 5e string work, no PDFBox, cljc so the builder can use it. Not a PDF-drawing concern, so not part of the shared drawing file. |
| `font` + `text` + `svg` + `canvas` (540 across 4 files) | **one file**, growing the existing `pdf/canvas.clj`. Page size becomes a parameter here. |
| `image` (540) | **still defensible on its own.** A distinct concern — fetch, validate, SSRF guards, embed — and 540 lines is a reasonable file. Not urgent; nothing blocks it. |
| `card` + `card.sheet` + `card.item` (750 across 3) | **deferred, not planned.** The frame has one consumer. Shape it when a second exists; see the TODO's card-frame section. |
| `pdf.clj` remainder | the AcroForm character sheet. |

### Why page size makes this concrete rather than cosmetic

The owner wants A5 at minimum, ideally A4 (ISO 216: A4 595x842pt, A5 420x595pt and exactly half
an A4; Letter 612x792pt).

Measured 2026-10-15: all 8 `resources/fillable-char-sheetstyle-*.pdf` are 612x792pt. The artwork
is fixed and we only fill fields, so **an A4 character sheet is an artwork task, not a code one.**

Everything we draw ourselves is the part code can reach — cards, card backs, the spellbook,
overflow pages, all bare `(PDPage.)` at PDFBox's Letter default, with 8.5 and 11 hardcoded (35
lines of `pdf.clj` flip against a literal `11`, and `in-to-coord-y` is the choke point). One
shared file with an explicit page size is what makes A4 and A5 possible, which settles the
coordinate question section 4 left open: the shared file carries page dimensions explicitly,
so neither 11 inches nor 792 points stays implicit.

## 10. Executed, 2026-10-16: the shared file and the cards split

Sections 4 and 9 are now partly built. What exists on `refactor/pdf-canvas`:

| File | Lines | Holds |
| --- | --- | --- |
| `src/clj/orcpub/pdf.clj` | 2,056 | AcroForm character sheet, image egress, site line |
| `src/clj/orcpub/pdf/draw.clj` | 590 | fonts, text measurement, SVG and icons, coordinates, shape paths, page sizes |
| `src/clj/orcpub/pdf/cards.clj` | 755 | spell and item cards, the frame, ornaments, nine-up |
| `src/clj/orcpub/spellbook.clj` | 955 | unchanged; not yet moved into the family |

Section 9 named `canvas` and the file shipped as `canvas.clj`. **It is `draw.clj`, namespace
`orcpub.pdf.draw`.** The owner caught the mismatch between the published plan and the code; the
name was changed in the code, not in the plan.

### Section 9's "cards deferred, not planned" is withdrawn

Section 9 says the card code waits for a second consumer. That conflated two different tests:

- **Extract for reuse** needs a second real caller. The card *frame* still has one, so
  `pdf.card` as its own file still waits. That part of section 9 stands.
- **Separate by subject** needs two subjects and a one-way seam. Cards passed that already.

Measured before the cut, with string literals and comments stripped so docstring prose did not
count as a reference: the card region used 6 names from the sheet region, the sheet region used
**0** from the card region. Zero back-references is what made the cut cheap.

The 6 split three ways, by consumer rather than by where they sat:

| Name | Went to | Why |
| --- | --- | --- |
| `site-stamp`, `site-stamp-size` | `draw` | the sheet, the cards AND the spellbook all draw the site line |
| `draw-spell-field`, `site-stamp-strip`, `draw-site-stamp!` | `cards` | card-only; in the sheet region by accident of file position |
| `page-prints-site-line?` | stayed | only `stamp-site-line!` calls it, and that is the sheet's |

A first pass drew the cut at `stamp-site-line!` rather than below it, which put it in the card
region. Two corrections came out of that. `stamp-site-line!` is the character sheet's — its only
caller is `routes.clj`'s sheet path — so the cut moved down 13 lines. And the scan claimed the
sheet region referenced it back, which was false: the stripper works line by line and cannot see
inside a multi-line docstring, and `sheet-masters`' docstring names it in prose.

`inches-to-units` was deleted, not moved: its own docstring claimed `draw-line-in` and the grid
code called it, and both had moved to `draw` and now use `in->pt`. Zero callers anywhere.

### Behaviour: unchanged, by measurement

All three geometry baselines — 24 card pages, 9 ornament pages, 24 spellbook pages — are
byte-identical across the shared-layer growth and the cards split. `lein test` 714 tests /
5,413 assertions green; `lein lint` 0 errors.

### The flips did not get fixed, they moved

All 31 hand-written `(- 11.0 y ...)` page-height flips are now in `cards.clj`; `pdf.clj` has
none. `draw/pt-from-top` takes a page key and nothing passes one. So A4 and A5 are still not
wired, and the work is now in one file instead of spread across two concerns — which is the
point of having done the split first.

### `dev/` was unlinted, and this refactor shipped broken scripts into it

Five `dev/` scripts went un-loadable across two commits (`b8d2b463`, `9d0a0710`). A rewriter
inserted `(require '[orcpub.pdf.draw :as draw]` as a new form where a continuation line
`         '[orcpub.pdf.draw :as draw]` was needed, orphaning the opening paren of the require
above it. Each file was left +1 unbalanced.

Nothing caught it: `lein lint` linted `src test web`, and `lein test` does not load `dev/`.
Verified after the fact that clj-kondo calls it an `error` ("Expected a ) to match ( from line
19"), so adding `dev` to the lint path would have failed the build. That is now the lint path.

`dev/` contributes ~41 warnings of its own idiom — the scripts `load-file` each other, so
duplicate-require and redefined-var are normal there — and `--fail-level error` ignores them.
Suppressing them with `:config-in-ns {user ...}` was tried and reverted: the scripts have no
`ns` form, so clj-kondo does not resolve them to `user` and it only caught 3 of the 41.

### What is left

1. **Thread a page key through the flips** — 31 sites in `cards.clj`.
2. **`orcpub.spellbook` into the family** as `pdf.spellbook`, dropping its duplicate primitives.
3. **The orchestrator** — 349 lines of assembly in `routes.clj`. Still the risky step: it is the
   live export path for every character sheet and the baselines do not cover the AcroForm sheet.
   Wants a field-level characterization test first.
4. **`pdf.image`** (394 lines) — optional, nothing blocks it.
5. **`pdf.character`** — only worth doing after 3 and 4; until then `pdf.clj` *is* the character
   sheet plus egress plus the site line, and splitting it further buys little.

## 11. The page-size work in §10 was decorative, 2026-10-16

§10 says `draw.clj` "holds the page dimensions" and that the remaining work is threading a page
key through 31 sites. Audited properly after the owner questioned the phrase "draw-text takes y in
inches and multiplies by 72 itself" — their read was that this was a missed generalisation, and it
was. Two findings, the first of which retracts §10's claim.

### `pt-from-top` is parameterised and nothing passes a page

```clojure
([pt-per-unit y]      (pt-from-top default-page pt-per-unit y))   ; all 4 callers
([page pt-per-unit y] (float (- (second (page-size page)) (* pt-per-unit y))))
```

Four call sites: `spellbook.clj:35` (`py`), `pdf.clj:885` (`in-to-coord-y`), `draw.clj:520`
(`draw-imagex`), `draw.clj:577` (`draw-svg-path!`). **All four use the 2-arity form.** The arity
that takes a page has zero callers in `src`, `test` or `dev`.

So `page-sizes` is consulted and always answers 612x792. A literal `11` was replaced with a table
lookup that always returns 11 — behaviour-neutral, which is exactly why all 57 baseline pages
stayed byte-identical and why it read as progress. `default-page` is the mechanism that makes it
decorative: because the 2-arity form exists, no caller ever HAS to pass a page, so none does.

**Page size is asserted in four places, not one:** `draw-grid` (`8.5` x2, `11.0`, lines 317-323),
`draw-svg-icon!` (`(- 11 y size)`, line 563), `default-page` (line 54, silently supplied to all
four `pt-from-top` calls), and the 30 flips in `cards.clj`. §10's "pdf.clj has none" does hold —
its one grep hit is a comment. `spellbook.clj`'s eight `8.5`s are all SVG path coordinates.

### The primitives are unit-bound, and that is the real defect

66 call sites in `src` across the shared layer, and the units do not agree:

| Convention | Primitives | src calls |
| --- | --- | --- |
| INCHES, y from **bottom** | `draw-text`, `draw-text-to-box`, `draw-lines-to-box`, `draw-halo-text` | 26 |
| INCHES, y from **top** | `draw-imagex`, `draw-imagex-alpha`, `draw-svg-icon!` | 10 |
| INCHES + whole-page | `draw-grid` | 3 |
| points, unit-agnostic | `draw-line` | **0** |

The shared drawing layer offers two opposite y conventions and one unit — and the single
unit-agnostic primitive has no external caller at all. This is why `spellbook.clj` re-implemented
its own drawing rather than using the shared layer: it works in points and the layer only speaks
inches. §3 called this "the real duplication is S4, and it is a coordinate-space mismatch". That
was right. Growing one shared file did not fix it, because the file kept the unit baked in.

### Correction: the spellbook does not shed ~90 lines

This doc (§4, §9) and the split map have said the spellbook drops ~90 lines of duplicate
primitives when it joins the family. Read all 107 lines rather than assuming:

| Lines | Kind | Names |
| --- | --- | --- |
| 28 | already delegating | `py`, `polyline!`, `circle-path!`, `wrap` |
| 29 | **pure unit adapter -- vanishes** | `text!` 18, `line!` 5, `gray!` 6 |
| 45 | **unique capability -- PROMOTE into `draw`** | `outline-text!` 30, `clip` 15 |
| 5 | superset | `spaced-width` |

It sheds **29**. `outline-text!` draws glyph outlines straight from the TTF to reach Vollkorn's
lining figures, because `showText` cannot select the `one.lf` variant — without it "15" reads as
"I5". `clip` is word-boundary ellipsis truncation, one of the seven overflow strategies, with no
equivalent in `draw`. Those 45 lines are capability the shared layer is MISSING.

### What the baselines do and do not protect

57 pinned pages: 24 card, 9 ornament, 24 spellbook. **None of the AcroForm character sheet**, and
`draw-text` has 6 callers in `pdf.clj`. So any change to a shared text primitive touches the live
character-sheet export with nothing watching it. That gap now blocks two jobs — the page-size work
and the orchestrator move — and both want the same fix: a field-level characterization test on a
filled sheet.

The plan that follows from all of this is `plan-pdf-page-sizes.md`, which also answers the owner's
question about shimming the AcroForm templates into A4 with padding (short version: height is
free, width is not, and it works for styles 3 and 4 only).
