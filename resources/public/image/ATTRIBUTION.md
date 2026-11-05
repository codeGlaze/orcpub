# Icon attribution

Most of the icons in this directory come from **[game-icons.net](https://game-icons.net)**
and are used under the **[Creative Commons Attribution 3.0 Unported](https://creativecommons.org/licenses/by/3.0/)**
licence, which requires that the authors be credited wherever the icons appear.

The five vendored for the PDF spell cards:

| Icon | Author |
|---|---|
| `arrow-dunk` | Lorc |
| `magic-swirl` | Lorc |
| `sands-of-time` | Lorc |
| `shiny-purse` | Lorc |
| `clockwise-rotation` | Delapouite |

The class emblems on the spellbook pages, in `emblems/`. A player picks one per class from
the pools in `orcpub.dnd.e5.emblems`; each is drawn inside a ring the code draws itself:

| Icon | Author |
|---|---|
| `emblems/acorn` | Lorc |
| `emblems/all-seeing-eye` | Delapouite |
| `emblems/ankh` | Lorc |
| `emblems/arrow-flights` | Lorc |
| `emblems/bow-arrow` | Delapouite |
| `emblems/bowman` | Lorc |
| `emblems/broadhead-arrow` | Lorc |
| `emblems/burning-eye` | Lorc |
| `emblems/campfire` | Lorc |
| `emblems/candle-skull` | Lorc |
| `emblems/checked-shield` | Lorc |
| `emblems/cross-shield` | Delapouite |
| `emblems/crosshair-arrow` | Lorc |
| `emblems/crystal-ball` | Lorc |
| `emblems/crystal-shine` | Lorc |
| `emblems/crystal-wand` | Lorc |
| `emblems/daemon-skull` | Lorc |
| `emblems/deer-track` | Delapouite |
| `emblems/dragon-head` | Faithtoken |
| `emblems/dragon-orb` | Delapouite |
| `emblems/dragon-spiral` | Lorc |
| `emblems/drama-masks` | Lorc |
| `emblems/evil-moon` | Lorc |
| `emblems/falcon-moon` | Delapouite |
| `emblems/feather` | Lorc |
| `emblems/fire-ray` | Lorc |
| `emblems/fire-spell-cast` | Delapouite |
| `emblems/fluffy-flame` | Lorc |
| `emblems/flute` | Delapouite |
| `emblems/fox-head` | Lorc |
| `emblems/french-horn` | Caro Asercion |
| `emblems/guitar` | Lorc |
| `emblems/harp` | Delapouite |
| `emblems/heraldic-sun` | Caro Asercion |
| `emblems/holy-symbol` | Lorc |
| `emblems/holy-water` | Delapouite |
| `emblems/hunting-horn` | Lorc |
| `emblems/interlaced-tentacles` | Lorc |
| `emblems/jerusalem-cross` | Delapouite |
| `emblems/jeweled-chalice` | Lorc |
| `emblems/leaf-swirl` | Lorc |
| `emblems/lightning-helix` | Lorc |
| `emblems/linden-leaf` | Lorc |
| `emblems/lyre` | Lorc |
| `emblems/magic-portal` | Lorc |
| `emblems/magic-shield` | Lorc |
| `emblems/magic-swirl` | Lorc |
| `emblems/moon` | Lorc |
| `emblems/moon-bats` | Delapouite |
| `emblems/mounted-knight` | Skoll |
| `emblems/mushrooms` | Delapouite |
| `emblems/music-spell` | Lorc |
| `emblems/musical-notes` | Delapouite |
| `emblems/oak-leaf` | Delapouite |
| `emblems/orb-wand` | Willdabeast |
| `emblems/pan-flute` | Delapouite |
| `emblems/paw-print` | Lorc |
| `emblems/pentagram-rose` | Lorc |
| `emblems/pine-tree` | Lorc |
| `emblems/pointy-hat` | Lorc |
| `emblems/quill-ink` | Lorc |
| `emblems/raven` | Lorc |
| `emblems/rod-of-asclepius` | Delapouite |
| `emblems/sands-of-time` | Lorc |
| `emblems/scroll-unfurled` | Lorc |
| `emblems/shining-sword` | Lorc |
| `emblems/spell-book` | Delapouite |
| `emblems/spiked-halo` | Lorc |
| `emblems/sprout` | Lorc |
| `emblems/stag-head` | Lorc |
| `emblems/sun-priest` | Delapouite |
| `emblems/sun-spear` | Delapouite |
| `emblems/sunbeams` | Lorc |
| `emblems/sword-altar` | Delapouite |
| `emblems/swords-power` | Delapouite |
| `emblems/tree-roots` | Delapouite |
| `emblems/visored-helm` | Lorc |
| `emblems/warlock-eye` | Delapouite |
| `emblems/warlock-hood` | Delapouite |
| `emblems/water-drop` | Sbed |
| `emblems/winged-sword` | Lorc |
| `emblems/wizard-face` | Delapouite |
| `emblems/wizard-staff` | Lorc |
| `emblems/wolf-head` | Lorc |
| `emblems/wolf-howl` | Lorc |

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
