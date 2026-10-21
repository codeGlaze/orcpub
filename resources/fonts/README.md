# Fonts embedded in exported PDFs only

Not under `public/`: nothing here is served over HTTP.

| File | Font | Licence |
|---|---|---|
| `NimbusSans-Regular.ttf` | Nimbus Sans, URW++ | AGPL-3.0 with a PDF-embedding exception (`NimbusSans-LICENSE.txt`, `NimbusSans-COPYING.txt`) |

**Why Nimbus Sans.** It is URW's metric clone of Helvetica: all 190 Latin-1 advance
widths are identical to Helvetica's, so a character-sheet field sized for Helvetica
fits the same way. It fills the fields Helvetica cannot draw (Polish, Turkish, Czech,
Cyrillic, Greek). It has no Vietnamese.

**Why the exception matters.** It permits embedding the font in a PDF whatever the
PDF's own licence. It does not cover serving the font file, which is why it is not in
`public/`.

**Source.** `https://github.com/ArtifexSoftware/urw-base35-fonts`, `fonts/NimbusSans-Regular.ttf`,
fetched 2026-10-21, SHA-256 `df5d1ce7b86c2e74974008623612bae136123a35ba7885de8a0721b68258edbc`.
