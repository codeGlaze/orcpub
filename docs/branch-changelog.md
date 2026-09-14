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

- **A character brings its custom items from the server** — whoever can open a character now sees the
  custom items it has equipped, as they are now. Share links no longer carry items, so they are
  shorter; homebrew still travels in the link, and older links still open (`7f375829`).

- **Short share links** — a character's owner gets a link of about 85 characters that stays the
  same and always shows the character's current homebrew, which the server keeps and checks; New link
  revokes every earlier link, nothing is stored until the owner presses Share link, the limits are
  config settings, and anyone else's link still carries the homebrew itself (`20179535`, `191b612e`,
  `eab46b9f`, `e43b41ca`).

- **A party keeps a shared character's homebrew** — a character added to a party from its share link
  shows its homebrew on the party page, until its owner makes a new link (`c69dcf9d`).
- **Unused share links expire** — a share link nobody opens for 180 days of the server running is
  deleted with the server's copy of its homebrew; the character and the owner's homebrew stay, the owner
  can share again, and `ORCPUB_SHARE_PRUNE_DAYS` sets the window. A request without the link's token
  does not count as use, and time the server was off does not count against a link (`59400455`,
  `110569dc`).

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

- **The email-support button on a character that will not load sends the report again** — clicking
  it threw after the login change, so no report went out (`476e948c`).
- **A login the server has stopped accepting now logs you out** — the app kept showing you signed in
  while every request failed, until you logged in again or reloaded (`6f1e6309`).
- **Share links no longer carry item ids or your username** — each custom item went into the link as
  stored, with its database ids and its owner; links made before this drop both when opened
  (`c34075f2`).
- **Only its owner can read a custom item by its id** — anyone with the id could fetch the item and its
  owner's username; everyone else now gets "not found" (`2cae044b`).
- **A character page never shows an email address** — characters saved during nine days in May 2017
  named their owner by the email used to log in (`2cae044b`).
- **Logins, API calls and PDF downloads go to the server that served the page** — every
  http://localhost page sent them to port 8890, which broke the Docker setup opened at
  http://localhost and a server on any other local port (`d72bcc39`).

- **Copy link shares the character it sits beside** — on the character page and in the character list
  it carried the homebrew of whatever character was open in the builder (`a92b47b7`).
- **Pasted images and video are left out of shared homebrew** — a data: URI in any text field is
  emptied before sharing and again when a link is opened (`ac2e45bf`).

- **Only characters can be added to a party** — adding checked nothing, so any id could be added
  (`c69dcf9d`).

## Changed

- **Browser probes share one way to find Chromium** — they find Playwright's
  current install without setting four variables by hand, and the runner reports
  a missing browser once as a skip (`b48d312c`).
- **The e2e suites are named, and run.sh runs them on any machine** — the PDF check is now
  export-character-pdf.js; run.sh lists the suites, honours E2E_PORT, finds Chromium the way the
  probes do, and says when it turns CSP off for a development bundle; the PDF check hides the What's
  New panel that had been taking its clicks (`eb8171d8`).
- **A browser check that a character shows its owner's items to others** — it opens a seeded character
  logged out, as another account and as the owner, and checks the item reaches the sheet each time;
  run.sh now waits for the test accounts to be seeded before a suite starts (`359095b8`).
