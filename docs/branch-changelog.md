# Branch changelog — `feat/option-picker`

## Why this branch exists

The Equipment tab offered every item its section knew about as an `<option>` in a native
`<select>` — 1037 `<option>` elements across ~7 sections with a large homebrew library — with
no way to search them. This branch replaces that with a filtering combobox and records, in
`docs/kb/equipment-option-picker.md`, the five controls that were measured against each other
so the comparison is not re-run.

Scope note, resolved: the branch started by lifting `option_menu_views.cljs`,
`option_grouping.cljs` and `themes.cljs` from `port/redesign-on-refactor` and wiring Equipment
to `omv/option-menu`. That was reverted in favour of the combobox and the namespaces went
unreferenced. They are removed here — alongside the picker they carry theming, layout modes
and page structure, which is a site-wide redesign and not something to land in the Summer
Patch. See below for where the work survives.

## Highlights

Equipment items are now picked from a filtering dropdown instead of a 1037-option native
select: type to narrow, or scroll and arrow-key through the whole list. It is built on the
browser's own Popover API, so it drops into the top layer with light-dismiss and Escape
handled by the platform rather than by hand.

## Added

- Filtering combobox for Equipment inventory sections, built on the native Popover API —
  top layer, light dismiss, Escape and focus management come from `popover="auto"` instead of
  a z-index stack, a backdrop element and a keydown listener (`95d38f67`).
- Arrow-key navigation with Enter to pick, and the highlighted row scrolled into view, so the
  list can be walked without the mouse (`ba52a219`).
- Match highlighting: the matched substring is emphasised so a row shows why it is in the
  list, which matters when the match lands mid-word — filtering by `+1` highlights 45 rows on
  their suffix (`4082bc20`).
- A hint line carrying the item count and the key bindings, since the arrow-key navigation was
  otherwise invisible (`4082bc20`).
- Unit tests for `highlight-match` covering positions, regex-metacharacter literals, casing
  and nil input (`78deb2ad`).
- `inventory-datalist`, a native filtering dropdown kept alongside the combobox: it hands the
  dropdown to the OS, which is the better control on mobile if the Popover API proves a
  problem. Switching is one line in `inventory-adder` (`d2c5f2fa`).
- Browser probes for the combobox — anchored geometry, light dismiss, browsability, keyboard
  navigation and open cost under CPU throttle — and a census of every `<select>` in the app
  (`95d38f67`, `ba52a219`, `4082bc20`).

- `scripts/test/run-browser-probes.js` — runs every asserting browser probe and exits
  non-zero if any fails. Neither test suite invokes `test/browser/`, so nothing was checking
  the 11 probes that carry real assertions; one had been failing and exiting 1 for several
  commits unnoticed. Probes that cannot run report `SKIP` loudly rather than staying silent
  (`<pending>`). Probes are classified by the world they need — the real server, their own
  standalone harness, or the busy-export profile — because running them as though they all
  wanted the same one fails in both directions. Verified both ways: 10/10 probes pass with
  the runner exiting 0, and a deliberately failing probe exits 1 naming it (`<pending>`).

## Fixed

- A nil or non-string item name no longer throws while filtering. The render path passed the
  raw name while the filter path wrapped it in `str` (`78deb2ad`).
- `equipment_add_functional_e2e.js` was left pointed at the option-menu's selectors when the
  add control was swapped, and had been failing three assertions against a control that was
  no longer wired. Retargeted at the live control, keeping the app-db assertion that the
  picked item reaches the character entity — the part worth keeping (`<pending>`).
- `screenshots_e2e.js` silently stopped taking two of its three shots for the same reason
  (`<pending>`).
- `notifications_acceptance_e2e.js` treated a CORS block as an unexpected console error. Its
  harness serves the app from its own origin with no backend, so an XHR to the real backend
  is either refused or CORS-blocked depending on whether an unrelated server happens to be
  running — the same condition, and not something that should decide the probe (`<pending>`).
- The dropdown matches its input's width and no longer sits 14px wider; it also flips above
  the input instead of running off the bottom of the viewport (`95d38f67`).

## Changed

- The Equipment picker no longer caps what it renders. The previous 12-row cap left 294 of 306
  magic weapons unreachable unless you already knew the name, and bought nothing — opening the
  largest section measured 0 ms (`ba52a219`).
- Rows mount only while the dropdown is open, which is cheaper at rest than the capped version
  was: 1541 DOM nodes closed against 2558 for the native select it replaces (`ba52a219`).
- The dropdown menu was flat. It now has a layered shadow, a themed scrollbar, an accent bar
  on hover and on the keyboard highlight, and a 120 ms entry animation guarded by
  `prefers-reduced-motion` (`78deb2ad`).
- Removed `option_menu_views.cljs`, `option_grouping.cljs`, `themes.cljs`, the dead
  `option-menu-views` require and 448 lines of `.opt-menu` CSS — 844 source lines that
  nothing referenced. They are not lost: `option_grouping` and `themes` are byte-identical to
  `port/redesign-on-refactor`, and the 34-line divergence in `option_menu_views` (the
  `:max-rendered` / `::show-all` capping work) is in this branch's history at `2a671844` and
  `ec85c5ac`. Rewiring one selection is roughly a 20-line call site (`<pending>`).
- `inventory-picker`, the hand-rolled overlay, is deprecated. It has no behaviour the combobox
  lacks, and its full-width mobile overlay was what made it wrong (`4082bc20`).
