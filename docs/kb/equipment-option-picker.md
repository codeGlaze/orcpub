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

## Reversal: the 12-row cap was wrong

The first version rendered only the first 12 matches and showed a `306 matches — keep typing`
footer. That made the control **searchable but not browsable** — you had to already know the
item's name. Characterized before changing it:

```
rows rendered       12 of 306
after scrolling to bottom, last row is "Blowgun needle +1"   <- stuck in the B's
ArrowDown -> highlighted rows 0   -> NO keyboard navigation
longest task open   0 ms (4x throttle)
```

The cap bought nothing: opening the largest section already cost 0 ms. It was removed.

Removing it alone was also wrong, though — a **closed** popover still keeps its children in
the DOM, so rendering every row all the time cost 2578 nodes and gave back the entire
advantage over a native `<select>` (2558). Rows now mount only while the popover is open.

| | closed nodes (desktop / mobile) | open | browsable | longest task on open |
| --- | --- | --- | --- | --- |
| 12-row cap | 1637 / 1178 | 1637 | 12 of 306 | 0 ms |
| uncapped, always mounted | 2578 / 2119 | 2578 | 306 of 306 | 0 ms |
| **uncapped, mounted on open** | **1541 / 1082** | 1581 | 306 of 306 | 62 ms |

Mounting on open is the cheapest of the three at rest and fully browsable. The cost moved
rather than vanished: opening the 306-item section is 62 ms at 4x throttle (roughly one frame
unthrottled) where it used to be free, paid once per open instead of on every page load.

Open state is tracked by the handlers that open and close it, plus a `beforetoggle` listener
for the two closes the browser performs itself — light dismiss and Escape. Without that
listener the rows stay mounted after a light dismiss.

## Tried and rejected: prefetch some rows, expand after open

The obvious middle ground is to keep a first chunk mounted while closed so the popover opens
with content, then mount the rest on the next frame. Built it (16 rows per section, budget
expanding to all on open, on every keystroke, and synchronously on an arrow key so the
highlight always has a row to land on). It does **not** break filtering — filtering runs over
`matches`, the data, and never over what happens to be mounted.

It also does not work. Longest task on open, 4x throttle:

```
mount-on-open (baseline)   62 ms
prefetch 16 + expand       55, 64, 53 ms across three runs
```

That is noise. The work never shrank, it only moved: the deferred chunk is still 290 rows in
one mount, so there is still one long task. Meanwhile the prefetched rows cost 112 nodes at
rest (1541 -> 1653 closed), which is worse than the 12-row cap it replaced. Reverted.

The trap to note if anyone retries this: a progressive budget must be able to reach *every*
match, and something has to expand it on a keystroke and on an arrow key. A budget that only
grows on open silently recreates the 12-row cap for filtered results, and one that lags the
keyboard lets the highlight index past the mounted rows.

**What would actually work is windowing** — mounting only the rows in view plus a buffer and
updating on scroll — because it is the only option that reduces the total work rather than
rescheduling it. Not built. Cost is roughly 0.19 ms per row at 4x throttle (58 ms / 306), so
the 62 ms is a real freeze only for sections far larger than anything in this pack: a
3000-item section would extrapolate to ~570 ms. Worth doing if such libraries turn up;
premature otherwise, and it costs find-in-page over the unmounted rows.

## Browsing

Arrow keys walk the list, `Enter` picks the highlight, and the highlighted row is scrolled
into view (`block: "nearest"`) so arrowing past the visible window works. The `.active`
highlight is stronger than `:hover` so the two stay distinguishable when the pointer rests on
a different row than the keyboard is on. Verified: 21x ArrowDown lands on "Crossbow, hand +1"
with `scrollTop=442` and the highlight in view; Enter drops the section count 306 -> 305.

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
- **`input[type=checkbox]:checked` was the wrong assertion for "did Enter add the item".**
  It read 0 before and after while the pick had plainly worked (the popover closed, which only
  `pick!` does). A picked item leaves the available list, so the section's own count is the
  assertion: 306 -> 305.
- **A hardcoded filter term made a working filter look broken.** "leather" matches nothing in
  the Weapons section, so the probe read zero rows and reported a failure. Probes derive the
  term from a row that is actually present.

## Decoration, and what was left out

The combobox is real DOM, so unlike a native `<select>` or `<datalist>` its rows can be
styled. Two additions earn their keep, both standard rather than novel:

- **Match highlighting** — the matched substring is bold and orange, so a row shows *why* it
  is in the list. This matters when the match lands mid-word: filtering magic weapons by
  `+1` highlights 45 rows on their suffix, not their name.
- **A hint line** — item count on the left, `↑↓ browse · ↵ add · esc close` on the right.
  The arrow-key navigation is otherwise invisible.

Together they cost 21 nodes (1541 -> 1562 closed).

Deliberately not built, to note that they were considered: per-row icons and rarity colours
(needs item metadata the option list does not carry, and colour-codes a list people scan by
name), sticky A-Z group headers (the list is already short once filtered), a recently-used
section (state with no obvious home), and animated open/close (the popover is opened from a
click 300 ms of the time — animation would be latency, not polish).

## The other pickers

A census of every `<select>` in the app (`test/browser/select_option_census_e2e.js`) found
exactly one other large picker:

| Page | selects | total options | biggest |
| --- | --- | --- | --- |
| combat-tracker | 4 | 972 | **969** |
| monster-builder | 37 | 807 | 36 |
| class-builder | 8 | 67 | 22 |
| magic-item-builder | 18 | 44 | 9 |
| spell-builder | 2 | 18 | 10 |

`monster-selector` (`src/cljs/orcpub/dnd/e5/views.cljs:7754`) puts all 969 monsters in one
native `<select>`, and is used by the combat tracker and the encounter builder. Everything
else is under ~40 options, where a native `<select>` is the right control. See `docs/TODO.md`.

## Which controls are kept

- `inventory-combobox` — live.
- `inventory-datalist` — kept live and working. It hands the dropdown to the OS, which is the
  better control on mobile if the Popover API ever proves a problem. Switching is one line in
  `inventory-adder`.
- `inventory-picker` — **deprecated 2026-09-06.** The hand-rolled overlay has no behaviour the
  combobox lacks, and its full-width mobile overlay was the thing that made it wrong.

Exposing the three as a user-facing preference was considered and rejected: three code paths
to test for a choice nobody has asked for.

## Verify

`node test/browser/combobox_scroll_e2e.js <pack>.orcbrew` — opens the largest section and
asserts how many rows render, whether scrolling reaches the last item, the longest task on
open at 4x throttle, that arrow keys move and scroll the highlight, and that Enter adds.

`node test/browser/combobox_shots_e2e.js <pack>.orcbrew [outdir]` — screenshots desktop and
mobile in closed, open and filtered states, and asserts the anchored geometry (left edge,
width delta, gap below the input, viewport overflow) rather than leaving it to the eye. It
also exercises light dismiss and a real pick.

Note the popover is real DOM in the top layer, so it appears in a page screenshot. A
`<datalist>`'s suggestion list is drawn by the browser and cannot be captured at all — that
control is unreviewable visually, which is a reason to be wary of it.
