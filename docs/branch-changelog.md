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
