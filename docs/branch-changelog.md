# Branch changelog — `consolidate/notifications-and-exports`

## Why this branch exists

Six branches were cut over one day for what is three pieces of work, and one of
them — the hiccup-rendering fix — existed only on a branch that never went back
to integration, so the break it fixed stayed live for nine hours. This branch is
the single place that work lives now, plus the developer-mode toggle and the
export coverage added after the merge.

## Highlights

Homebrew can now be rescued from a broken app. The boot shell carries a download
control that the app removes once it has actually rendered, so a missing bundle,
a failed init or a component that throws all leave it standing — and the error
screen puts it back. It reads storage at the moment you click it, not at page
load, so work done during the session is in the file.

## Added

- **Homebrew rescue in the boot shell** — downloads what is stored when the app
  itself cannot. Present by default and removed on a clean render, so nothing has
  to detect the failure (`46b6f807`, `7bd0d7bc`).
- **Developer mode** — a named switch in the footer reveals "Dump library" and
  "Debug info", off by default and remembered per device. Replaces two unlabelled
  icons that sat on every page (`c1dad978`).
- **Export coverage for the save banner's link** — a source holding one item from
  before the session and one saved seconds ago must export with both, and the file
  must re-import into a clean store (`c1dad978`, `cb40740e`).

## Fixed

- **Banners render markup again** — every message built from hiccup had been
  rendering as its own source text since `e13d2b2f`, which removed every control
  inside one, including the save banner's export link (`68c9873e`).
- **Per-source export runs the same checks as Export All** — the same source no
  longer produces two different files depending on which button you press
  (`f7b259fe`).
- **The size a rescue reports** — anything under a kilobyte read as "0 KB", which
  looks like there is nothing to save (`7ccc9d3a`).

## Changed

- **The footer's raw library dump is named and explained** — it stays reachable in
  production, because it is the way out when validation is what is broken
  (`17658bb5`, `c1dad978`).

# Branch changelog — `test/overlay-probe-user-menu-and-modals`

## Why this branch exists

Kept open while the import UI's wording is still moving. The helper every homebrew
probe depends on clicks the import modal's primary button by label, and that label
has changed three times: "Import", then "Import with these fixes" (`f7285198`),
now "Import with default fixes" (`2661be88`).

## Added

- The overlay probe walks **My Content's delete-all guard** — the quiet `Delete…`
  button, the `.mc-liftpop` it unfurls, and the `.mc-confirmbar` underneath. It
  cancels at the last step and then asserts the stored library is the same size it
  was, so a run can never be one stray click from wiping a library. None of the
  three steps is a modal, which is why the earlier version of this probe waited on
  a selector belonging to the item builder's confirmation and skipped.

## Fixed

- **The import helper follows the button's latest wording**, with the bare-word
  entries kept last so the next rewording of the same control still lands.

# Branch changelog — `fix/item-builder-save-label`

## Why this branch exists

Every builder's save button says "Save to Browser Storage", and for every builder
but one that is true. A magic item is saved to the database, like a character —
`::mi/save-item` posts to `/dnd/5e/items` with an auth header — so on that page the
label promised local storage while requiring an account, and a logged-out click
went to the login page having never said an account was needed.

## Fixed

- **The item builder's save button says "Save Item"** rather than claiming browser
  storage it does not use.

# Branch changelog — `refactor/banner-parts-and-design`

## Why this branch exists

The import banner's parts were joined with `\n` and rendered as HTML, which
collapses them — so three sentences arrived with no punctuation between them. The
previous fix made CSS honour the newlines, which treated the symptom: display text
should be structured, not a string with sentinels in it.

The banner also had no hierarchy — three bold lines of equal weight, an emoji
standing in for an icon, a saturated warning-orange slab for a *success*, a close
glyph with no touch target, and its most useful suggestion written as advice
rather than offered as an action.

## Changed

- **Import messages are structured** — `format-import-result` returns
  `{:title :details}`, one idea per line, and the banner renders each line itself.
  No newline sentinels, no `white-space: pre-line`, no regex collapsing blank
  lines. A plain string is still accepted and split at the edge, so the remaining
  legacy callers keep working while they move over.
- **The banner has a hierarchy** — a severity icon carrying the colour, a headline,
  supporting detail at a lighter weight, a tinted box instead of a filled slab,
  and a dismiss with a real touch target.
- **A successful import reads as success** — green, not warning-orange.
- **The banner says what happened and stops there.** The advice line — "To be
  safe, export all content now" — is gone rather than promoted to a button: on My
  Content it would have sat directly above the page's own Export All. A message
  pointing at a control already on screen is noise.

- **The save-validation error leads with the problem.** It opened with a line
  naming the builder you were already standing in ("Spell:"), then the problem,
  then the escape hatch — three stacked blocks for one short message. The problem
  is the headline now, with the rest beneath it.
- **Exporting from the storage warning no longer closes the card.** The banner
  dismisses on any click that reaches it, and the export link did not stop its
  own, so the surface vanished mid-action — which is how a working control comes
  to feel broken.
- **The browser-storage warning leads with the point.** It was one
  200-character sentence opening with "IMPORTANT!:" and ending with the export
  link — the part that matters, last. Now: "Spell saved — in this browser only",
  and under it "Clearing browser data loses it. Export this source to keep a copy."
- **The banner is sized to what it says** rather than to one fixed width. A flat
  cap fixed the five-word slab and then squeezed a two-sentence warning into a
  narrow column; `fit-content` with a ceiling does both jobs.
- **A save confirmation says what it saved** — "Saved “Flame Tongue”" rather than
  "Your item has been saved.", for characters too when they have a name.

## Fixed

- **A message that is markup renders as markup.** The builders' "please fill in
  X" carries a bolded field name and a clickable "Save anyway with placeholders",
  and the new banner ran `str` over it, printing the hiccup at the reader. Vectors
  now pass through untouched.

- **Callout action buttons carry a React key again** — the key was attached to the
  `let` form rather than the element it returns, so every callout with actions
  logged a missing-key warning.

# Branch changelog — `fix/my-content-source-toolbar`

## Why this branch exists

Inside an expanded source, the search box sat on a line of its own beneath the
Export and Delete buttons, running edge to edge against the panel wall. It reads
as an afterthought rather than part of that source's controls.

## Changed

- **An expanded source's search sits inline with its buttons** — search, show
  disabled, then Export and Delete on one row, with side padding so nothing is
  flush against the panel. On a phone the search takes the row and the toggle and
  buttons wrap beneath it.

# Branch changelog — `integration-local`

## Why this branch exists

Hotfixes found while testing PR #33 before the roll-up to gitea. Several repairs
ran again on every load because nothing saved them, the save banner's export link
had regressed, damaged homebrew loaded without a word and failed later at export,
and a few notices and controls misreported or covered what they sat on. Each fix
comes with a check that fails without it.

## Added

- **A check for homebrew no repair knows about** — a browser probe breaks one race on
  conversion and one only when drawn, and checks the app, the notice, the set-aside,
  Restore and import (`fa57ebbc`).

- **A check that every fix is saved** — a browser probe applies each repair through
  the app, reloads and exports, so a fix that only lives on screen fails a test
  instead of reverting on refresh (`dd2ff2b7`).

## Fixed

- **Broken keys and card lists in homebrew are repaired** — an entry whose key was not
  a keyword, or whose traits or options held something other than cards, crashed the
  import or later the export; it now imports with what can be kept (`c0b757c7`).
- **Startup only reads homebrew** — the character options autosave needs are built on
  the first save instead of on every page load, and a library that cannot be loaded is
  set aside intact with a download, so the app still starts (`fa57ebbc`).
- **Homebrew that still breaks is set aside, not left to break the page** — an entry
  that fails to build is set aside with a notice that names it and links to My Content,
  and a page that fails on homebrew checks it and loads again on its own (`fa57ebbc`).

- **Bad stored homebrew no longer stops the app from starting** — a race whose key
  was text left every page on the loading spinner. A failed startup step no longer
  stops the app mounting, and sorting keys no longer breaks on mixed types
  (`e9548269`).

- **A character is repaired on refresh too, and stays repaired** — the repair is
  saved back to the builder's draft, so it happens once instead of on every load,
  and its notice shows after the page navigates instead of being cleared by it
  (`76dd9497`, `ae6be67b`).
- **Entries set aside on load leave the stored library** — they were set aside in
  memory only and came back on the next refresh (`d3880ecb`).
- **The save banner's export link exports the source as it is now** — it exported
  the copy it remembered from when the banner appeared, dropping later edits
  (`44e97206`).
- **Damaged homebrew is repaired on import and on load** — a section stored as
  text or as a list comes back with everything in it, and a file whose whole
  content was stored as text imports instead of crashing. What nothing can read is
  set aside with Export raw and Discard (`9ef8bfe5`, `a4c69f62`).
- **A partial import shows as a warning** — it arrived in the green success card.
  Each line is now marked imported, repaired or skipped, and counts read "1 item"
  (`a89b2102`).
- **A nameless item reads "Unnamed Spell" in the missing-fields dialog** — with its
  key beneath, instead of a bare ":no-name" (`bd664a96`).
- **The import-log button no longer covers header menus** — it stays off the page
  while the log is empty and draws under an open menu (`e30e0061`).
- **The save sparkle stays on the save button** — it drifted into the gap beside
  it (`bcf713d6`).
- **A library stored as text loads again** — it loaded nothing (`0b5c05be`).
- **A stored value that isn't a library is handled once** — it was copied aside
  again on every load (`0b5c05be`).
- **Save anyway in the selection builder keeps the selection** — a name like
  "9 Lives" was saved under a key the next load set aside (`0b71b8b0`).
- **Renames chosen for existing items are kept when nothing incoming imports** —
  they were dropped along with the incoming entries (`01217a11`).
- **An entry with no source takes the name of the source it sits in** — unless its
  source field was there but blank, it was skipped on import or set aside on load.
  Only an entry with no name to take goes to Default Option Source (`1f8fef2b`).
- **An import with an entry that isn't a map no longer crashes** — the entry is
  skipped and listed in the import log (`09c22e11`).

## Changed

- **Browser probes share one way to find Chromium** — they find Playwright's
  current install without setting four variables by hand, and the runner reports
  a missing browser once as a skip (`b48d312c`).
