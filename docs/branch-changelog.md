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
  deleted with its share data; the character stays, the owner can share again, and
  `ORCPUB_SHARE_PRUNE_DAYS` sets the window, 30 days at least. A request without the link's token does
  not count as use, and time the server was off does not count against a link (`59400455`, `110569dc`,
  `3895027b`).
- **Stop sharing** — a character's owner can stop sharing it at any time: every link made before stops
  showing its custom content, and nothing is stored again until Share link is pressed (`e3aa6d07`).
- **The owner is told when a share link expired** — the character page and list show the date beside
  Share link on every visit, until the owner shares again or dismisses the note; the note keeps only the
  character and the date (`e3aa6d07`).
- **The server records when it was running** — an hourly heartbeat kept in the database notes when the
  server was off, and scheduled jobs run after it; share link pruning is the first (`e3aa6d07`).

- **The password rules judge length and shape, not character classes** — a minimum of 12
  characters, with repeats, runs and too few distinct characters refused, and no rule about
  symbols or capitals. NIST has told everyone to stop imposing composition rules, which push
  people to Password1! and no further. The validator is shared between the browser and the
  server so neither can disagree with the other (`43a56c60`, `ad5ebcb8`, `389b0360`).

- **A strength meter that asks the validator instead of guessing beside it** — four rungs at
  12, 16, 22 and 28 characters, and it refuses to show a verdict the server would contradict.
  The username and email go into the judgement, so a password made of them does not score
  (`389b0360`).

- **Breach screening against the Pwned Passwords corpus** — the first five characters of a
  SHA-1 go to the service and the rest never leaves, so the password itself is not sent and
  cannot be inferred from the size of the reply. A password is refused only at 1000 or more
  appearances: the corpus measures commonness rather than danger to the person in front of
  us, and refusing on a single appearance turned a measure into a veto. A service that cannot
  answer is never a refusal (`b459e6b7`, `79e7a325`, `cef432af`).

- **A notice when one account is signed into from several places** — with the time, the
  browser and where from, held to one message per account per cooldown so a person with a
  phone and a laptop is not mailed all day (`b2aefc9b`, `4f75360c`, `40d6ea23`).

## Fixed

- **Blank icons show again** — the Edit button on character and item pages, New link and three
  import-log lines used Font Awesome 4 names the app does not serve, so on a phone they were empty
  buttons (`b50783d6`).
- **A mistyped `ORCPUB_SHARE_PRUNE_DAYS` is called out at boot** — it showed as set while the default
  was in use (`04cb322b`).
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

- **The mobile header no longer crowds itself** — on a phone the logo is capped, a
  full-width child no longer measures wider than the bar it sits in, the import log
  panel cannot exceed the screen, and a child that still outruns the bar is clipped
  rather than scrolling the whole page sideways (`0a089349`).

- **Login no longer says whether a username exists** — every failure answers the same way, and
  the per-address throttle is consulted BEFORE the credentials rather than computed and thrown
  away, so a spray is turned back without first being told whether it guessed a real account
  (`5f4d34ba`, `70c3f49a`).

- **Password reset no longer says whether an address is registered** — it answers 200 either
  way, including once throttled, because an endpoint that changes its answer under a limit has
  simply moved the oracle (`3570624e`).

- **Reset keys are stored as a digest and expire** — the table holds a SHA-256 of the key
  rather than the key itself, and it is good for two hours. An unknown key used to fall through
  to signing a token for username nil (`cfd39eb9`).

- **Registration is capped per host** — ten an hour, counted only once a form was otherwise
  going to succeed, so a signup that was failing anyway is not held against the address. What
  the limits turn away is counted and summarised hourly, so the numbers can be tuned against
  something (`91acfcd1`, `cc5f8653`, `a744b6d4`).

- **A margin utility no longer makes 194 places bold** — `.m-b-10` sat in a Garden selector
  group that read as a descendant chain and was not one, so a margin class carried
  `font-weight: bold` across the app. Measured rather than guessed: 194 uses in source, 11
  rendering on the logged-out pages, 7 computing a different weight, 5 actually looking
  different (`042ee596`).

- **The legal links printed twice on the register page** — the consent line links both
  documents and the footer printed the same pair under it. The footer keeps the copyright there
  and drops its copies (`ecb13193`).

- **The gryphon panel tiled** — no `background-repeat` and no `background-size`, so once the
  form column outgrew the image a second half-cropped gryphon drew below the first. It is a
  Garden class now instead of an inline style map (`ecb13193`).

- **The email help link was one word in the middle of a sentence** — "whitelist" as the whole
  clickable target, which is a small target and names no destination when a screen reader reads
  it alone. Both instances link a phrase now, and the login copy no longer reads "Didn't receive
  validation the email?" or advise resetting a password to fix a missing validation email
  (`ecb13193`).

## Changed

- **A character's sharing is one line under its title** — a status (Not shared, Shared, or Link expired
  and the date) with its actions as text buttons: Share link until a link exists, then Copy link, New
  link and Stop sharing. It replaces the share buttons in the page header, which split into extra rows
  on a phone (`f1852203`, `5e417137`).
- **The character list row shares with one Copy link button** — it copies the link, or makes the share
  and copies it when the character has none; the status, New link and Stop sharing stay on the
  character page (`33628762`).
- **Share wording** — the startup log's share settings speak of share data, the Share link button no
  longer describes server storage, and What's New describes short links, New link and Stop sharing
  (`e3aa6d07`, `567f4bdb`).
- **A party forgets a character's link when its share ends** — New link, Stop sharing, expiry and
  deleting the character delete the token the party saved; the character stays in the party
  (`3cacef7f`).
- **The share settings are documented** in `docs/ENVIRONMENT.md` (`0940636e`).
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
- **Named Garden classes replace 28 inline style maps on the registration, login and
  password pages** — 49 class definitions quarried off the stalled
  `refactor/garden-inline-styles` branch without merging it, which is 648 commits behind
  and conflicts in two files. Two of the classes it used are generated from value lists
  rather than written out, so they emitted no CSS at all and are generated here. The
  password meter keeps an inline width, because that width IS the measurement. Nothing
  renders differently (`47bb73f4`, `2c9c553d`).
- **The nine auth pages share one heading** — `registration-page` was always the shared shell,
  but six pages carried their own copy of the same orange drop-shadowed heading. The shell owns
  it now, as ink with an amber rule, so it changes in one place (`3b1ac947`).

- **The auth fields are notched outlines with a real label** — the label rides the input's
  border rather than sitting in a placeholder that vanishes the moment somebody types, so the
  field never stops saying what it is (`c9c6732b`).

- **Errors read in GOV.UK order** — the label lifts out of the notch, the message sits under it
  and the input follows, with a rail down the group, a 1px border and a soft tint instead of a
  heavy outline plus a boxed message. A summary above the form counts fields rather than
  messages, and each line focuses its field (`70b6a410`, `935c996f`).

- **A password can be revealed, and the confirm box retires when it is** — two boxes while it
  is masked, one while it is not, and `display: none` rather than dimmed so a submit cannot
  fail pointing at a field nobody can see (`141894fd`).

- **The email is confirmed, not the password, and a mistyped domain is offered a fix** — a
  mistyped password is recoverable; a mistyped address makes a dead account holding the username
  its owner wanted and mails a stranger on the way. Within two edits of a known domain, a
  correction is offered under the field (`bd434342`).
