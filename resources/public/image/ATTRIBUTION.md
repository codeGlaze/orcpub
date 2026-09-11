# Icon attribution

Most of the icons in this directory come from **[game-icons.net](https://game-icons.net)**
and are used under the **[Creative Commons Attribution 3.0 Unported](https://creativecommons.org/licenses/by/3.0/)**
licence, which requires that the authors be credited wherever the icons appear.

The four vendored for the PDF spell cards:

| Icon | Author |
|---|---|
| `arrow-dunk` | Lorc |
| `magic-swirl` | Lorc |
| `sands-of-time` | Lorc |
| `shiny-purse` | Lorc |
| `clockwise-rotation` | Delapouite |

The remainder of the set predates this file and the per-icon authorship was not
recorded when it was added. The great majority of game-icons.net is the work of
Lorc, Delapouite and Skoll, all under the same licence. Anyone adding an icon
should record its author in the table above; the author is named on the icon's
page on the site.

## Which files the PDF export reads

The card icons are read as **`.svg`** and filled as vector paths, so they print at
the device's resolution rather than at the 32 pixels the old rasters carried. The
`.png` copies that remain are for the web UI. `orcpub.pdf/draw-svg-icon!` falls back
to a `.png` of the same name when no `.svg` is vendored, so either will work, but a
new icon should be added as SVG.

Colour is applied at the draw site rather than baked into the file, which is why
there are no longer `-bw` duplicates of these five: one path fills red, solid black
or 40% black as the sheet style asks.
