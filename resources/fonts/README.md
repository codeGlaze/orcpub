# Fonts embedded in exported PDFs only

Not under `public/`: nothing here is served over HTTP.

| File | Face | Used for |
|---|---|---|
| `NimbusSans-Regular.ttf` | Nimbus Sans Regular | form fields (embedded in full), site line, notice page, card captions |
| `NimbusSans-Bold.ttf` | Nimbus Sans Bold | spell-column headings, relabelled numerals, notice headings, attunement badge |
| `NimbusSans-BoldItalic.ttf` | Nimbus Sans Bold Italic | spell-card casting time, range, components, duration |

All three: URW++, AGPL-3.0 with a PDF-embedding exception (`NimbusSans-LICENSE.txt`,
`NimbusSans-COPYING.txt`). Both are upstream's `LICENSE` and `COPYING`, renamed and
otherwise verbatim, so the `COPYING` that `LICENSE` names is `NimbusSans-COPYING.txt`.

**Why Nimbus Sans.** It is URW's metric clone of Helvetica: all 224 WinAnsi advance
widths of each face are identical to the matching Helvetica's (pinned by
`nimbus-sans-is-helvetica-width`), so a box sized for Helvetica fits the same way. It
is the export's only sans, embedded, so every viewer draws the same glyphs and text
prints past Latin-1 (Polish, Turkish, Czech, Cyrillic, Greek). It has no Vietnamese.

**Why the exception matters.** It permits embedding the font in a PDF whatever the
PDF's own licence. It does not cover serving the font file, which is why it is not in
`public/`.

**Source.** `https://github.com/ArtifexSoftware/urw-base35-fonts`, `fonts/`.

| File | Fetched | SHA-256 |
|---|---|---|
| `NimbusSans-Regular.ttf` | 2026-10-21 | `df5d1ce7b86c2e74974008623612bae136123a35ba7885de8a0721b68258edbc` |
| `NimbusSans-Bold.ttf` | 2026-10-22 | `148726be75d2060a38dfd2e2637569f56c82af9f671e97131023b47add06f2cb` |
| `NimbusSans-BoldItalic.ttf` | 2026-10-22 | `f02dd8d808c9bf70323810c3f3f4ebfd4bb0d6da97b28e584869d26815afe7eb` |
