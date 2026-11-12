<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `fix/pdf-export`

<!-- ─────────────────────────── HOUSE STYLE (read once) ───────────────────────────
Say what the BRANCH did, not the commits it took. A branch that built new sign-in pages and
tightened passwords writes two lines, however many commits got it there:

  - **Accounts:** new sign-up, sign-in and account-recovery pages.
  - **Accounts:** password rules based on length, a live strength meter, and breach screening.

Rules:
  • One line per outcome a user or maintainer would notice. Open it with its area in bold
    (**Homebrew:**, **Characters:**, **Sharing:**, **Accounts:**, **Printing:**, **Site:**, **Server:**).
  • Fixed is for bugs that existed before this branch. A fix to something this branch (or the same
    release) built is part of building it: no line of its own.
  • No internal work: tests, CI, refactors, tooling, code moves. They belong in the PR and the KB.
  • Cite the upstream issue (#N) when there is one. No commit hashes.
  • Plain words; no AI-jargon ("seamless / robust / comprehensive / powerful / streamlined / leverage").
  • Security fixes: one plain line under Security, with no detail an attacker could use.
────────────────────────────────────────────────────────────────────────────────── -->

## Why this branch exists

<!-- Reviewer context only. Dropped at fold; never reaches CHANGELOG.md. What problem this branch
     solves and any scope a reviewer needs. Prose is fine here. -->

Three PDF export bugs testers reported on the dev build: the download filename, PDF Options needing
scrolling, and a freeze on ticking Print Spell Cards or Print Spellbook. The filename and panel bugs
are fixed; the freeze never reproduced (prod and dev builds, 6x CPU throttle, a Wizard 20 with every
spell plus a homebrew class and race with 325 spells), and the measurements are in the branch
handoff (branches/fix/pdf-export.md on agents/develop). The owner then widened the branch: a
full-window PDF Options panel, file name choices, the character picture editor (an image from the
web beside the built portrait), a builder React-key bug found while probing homebrew for
duplicates, and the PDF Options code moved out of views.cljs (views/pdf_options.cljs).

## Added

- **Portraits:** a character can use an image from the web as well as a built portrait; both are
  kept, and the character picture editor switches which one shows on the sheet, the PDF and shares.

- **Printing:** PDF Options lets players choose what the file name shows (player, character, class,
  subclass, level), where the level goes and how it is written (5, 05, L5); kept on the account.

## Changed

- **Portraits:** Randomize rolls everything (colours, eyes, hair effects, light) within sane ranges,
  with Keep colours to hold the colours.

- **Printing:** PDF Options opens as a full-window panel with grouped options and Create PDF at the
  bottom right, on desktop and phone, and Create PDF opens a tab that says the PDF is being made.

## Fixed

- **Homebrew:** opening an entry to edit no longer risks a section of its builder showing twice or
  going missing.

- **Printing:** the bottom of PDF Options, Create PDF included, can be scrolled into view; it
  was cut off under the pinned page header unless the page was scrolled to its very end.
- **Printing:** a character with no name gets one when exported, as saving gives one, so the sheet
  and its file are named; the file is never just "character.pdf" for a character with a class.
- **Printing:** a PDF's file name keeps names with accents, curly apostrophes or non-Latin
  letters, drops emoji and characters Windows cannot save, and a player name without a character name no longer leaves an empty " -  - " slot.

- **Printing:** a spellcaster's PDF no longer opens with a "did not fit" page naming a cantrip slots
  box; cantrips have no slots, from a class, race or feat alike.
- **Printing:** a race's spell page (a Tiefling's Hellish Rebuke and Darkness) no longer shows
  the class's spell slots; each such spell says how it is cast, for example "2nd level,
  once/long rest", as do a Warlock's at-will invocations and Mystic Arcanum.
- **Printing:** a spell card whose source has a long name (Misty Step from Fey Teleportation)
  no longer prints its DC and spell mod over the casting-time icons.
- **Warlock:** Mire the Mind's Slow is cast once per long rest using a warlock spell slot, not at
  will, and a Warlock's at-will and Mystic Arcanum spells stay at their own level on a packed PDF
  spell sheet.

## Security
