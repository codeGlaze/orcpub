# The Equipment tab's option control

The Equipment tab has ~7 inventory sections. Each offered every item its section knows about
as an `<option>` in a native `<select>` — 1037 `<option>` elements across the tab with a large
homebrew library loaded, and no way to search them.

Four controls were built and measured. All four are still in the tree; `inventory-adder` in
`src/cljs/orcpub/character_builder.cljs` picks one in a single line.

## Measured

Desktop is a 1500x1000 viewport; mobile is a Pixel 5 device descriptor (`device-type` is
User-Agent based — a narrow desktop window gets the desktop layout, see docs/TODO.md).

| Control | DOM nodes (desktop / mobile) | Page height | Filter | Theming | Mobile shape |
| --- | --- | --- | --- | --- | --- |
| native `<select>` | 2558 / 2422 | 2422px | none | input only | OS picker |
| inline grid | ~5000+ | 24273px | yes | full | unusable |
| hand-rolled popover | 7 buttons closed / 1082 open | — | yes | full | full-width overlay |
| `<datalist>` | 2571 / 2112 | 2422px | native | input only | OS autocomplete |
| **Popover-API combobox** | **1637 / 1178** | 2422px | yes | full | anchored dropdown |

The combobox is the lightest of the five because it renders at most 12 rows per section
(~83 nodes total) instead of one element per item. The others all put every item in the DOM.

Longest task on the Equipment tab, 4x CPU throttle, normalised to the Race control in the
same run: cap 25 ~1.33x | cap 100 ~1.56x | native 1.65x | cap 250 ~2.77x | uncapped ~2.71x.

## Why the Popover API and not the hand-rolled popover

The hand-rolled `inventory-picker` implemented, by hand and worse, four things the platform
gives away: the top layer (it used z-index 40/41), light dismiss (a backdrop `<div>`), Escape
(a `keydown` listener), and focus management. Chrome 141 was capability-checked before the
rewrite — `popover`, `showPopover`, `:popover-open`, `anchor-name`/`position-anchor`,
`position-try-fallbacks`, `anchor-size()`, and also `appearance: base-select` and
`::picker(select)`, all present.

**Reversal worth recording:** an earlier claim here that datalists cannot be styled was wrong.
Customisable `<select>` (`appearance: base-select`) is available and would style the *native*
dropdown. It was not chosen only because a native `<select>` still needs every option in the
DOM; the styling objection was not the real one.

## Defects found while building it, and the fixes

- **Opening on `focus` does not work.** Focus fires on mousedown; light dismiss then treats
  that same pointer sequence's click as an outside click and closes the popover again.
  Measured `popoverOpen=0` right after a click. Open on `:on-click`.
- **`{:width "320px" :width "anchor-size(width)"}` will not compile** — duplicate keys are
  illegal in a Clojure map literal. The fallback and the override need separate rules.
- **`anchor-size()` alone does not match the input's width.** The popover was content-box, so
  padding and border added 14px. It needs `box-sizing: border-box` too.
- **A 300px list never fit below its input,** so `flip-block` threw the dropdown up over the
  page header on every open. 230px sits below on desktop and still flips on a short phone
  viewport, which is what the fallback is for.
- **A hardcoded filter term made a working filter look broken.** "leather" matches nothing in
  the Weapons section, so the probe read zero rows and reported a failure. Probes derive the
  term from a row that is actually present.

## Verify

`node test/browser/combobox_shots_e2e.js <pack>.orcbrew [outdir]` — screenshots desktop and
mobile in closed, open and filtered states, and asserts the anchored geometry (left edge,
width delta, gap below the input, viewport overflow) rather than leaving it to the eye. It
also exercises light dismiss and a real pick.

Note the popover is real DOM in the top layer, so it appears in a page screenshot. A
`<datalist>`'s suggestion list is drawn by the browser and cannot be captured at all — that
control is unreviewable visually, which is a reason to be wary of it.
