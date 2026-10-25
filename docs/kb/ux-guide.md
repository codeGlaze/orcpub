# OrcPub UX Guide

The baseline for anything a player sees or touches in the builder: where things go, what they say,
and how designs get reviewed. Read it before designing a screen, a notice or a control. Every
picture is the real builder at phone width. Colours and themes have their own system
([frontend-redesign-parallel-work.md](frontend-redesign-parallel-work.md)); content names follow
[content-naming-style.md](content-naming-style.md). The owner reviews it as a page:
https://claude.ai/artifact/GMj4ybrucJCesuKC3AU7xp

## A floor, not a ceiling

The app's screens are meant to keep getting better. This guide records the best agreed so far, so
every change starts there and nothing agreed gets lost.

- **A better pattern replaces the old one here,** in the same change, and the log says what it
  replaced.
- **Existing screens are not precedent.** "The app already does it this way" is a starting point to
  improve on.
- **Propose improvements** with a mockup, rather than quietly matching an older screen.

## Show it beside the control that changes it

A planned pick sits by the level picker that brings it back. A heading for a choice the player
cannot make yet makes them hunt for it.

| Do | Avoid |
|---|---|
| ![Planned: Steel Will (L7) under the class row, beside the level picker](assets/ux-guide/c1-one-planned.png) | ![A Defensive Tactics heading with no options, only a Planned line](assets/ux-guide/r1-own-section.png) |
| Under the class, beside the level picker. | A heading for a section the player cannot use yet. |

## Size things by the layout, not by their content

A box takes its width from the page: it shares the edges of the column it sits in, and it keeps its
size when its text changes. Shrink-wrapping a box to its text gives every state a different,
arbitrary width.

| Do | Avoid |
|---|---|
| ![The notice at the options column's width, on its edges](assets/ux-guide/layout-sized.png) | ![The notice shrink-wrapped to "Undone", narrower than the page](assets/ux-guide/content-sized.png) |
| The column's width; the same size for every state. | Sized to its text; it changes width when the text does. |

## Nothing hides, and each fact has one home

Every stored choice can be seen, or reached in one tap, from where a player would look. Say it once,
where it belongs; do not repeat it on other sections.

| Do | Avoid |
|---|---|
| ![3 planned at higher levels, tapped open, with the level each returns at](assets/ux-guide/c3-several-open.png) | ![Planned: Steel Will repeated on the Ranger Archetype section](assets/ux-guide/r2-on-subclass.png) |
| One line per class; tapping lists every planned pick. | The same fact repeated on another section. |

## Label only what the player would miss

Every label costs attention, and labels add up. An automatic change always says what it did. A rare
case, or the leftovers of something the player removed on purpose (deleting a class, changing race),
gets no label of its own: the player expects those gone, and a notice covers a change of mind.
Before adding a marker, count what the screen already shows.

## The fallback is the exception

Wrapping and overflow handle rare cases. Design so the usual case fits on one line, and check with
long real names ("Great Weapon Fighting").

| Do | Avoid |
|---|---|
| ![select 1, Planned: Archery (L10), complete, on one line](assets/ux-guide/f1-same-line.png) | ![A long Planned label squeezing select 1 onto two lines](assets/ux-guide/c5-race.png) |
| The marker fits in the row. | A long label squeezing the row: rethink the placement. |

## Quiet, unless the player must act

Status is calm. A notice that carries an action stays until it is dealt with; a banner that
disappears is for confirmations.

| Do | Avoid |
|---|---|
| ![Notice box with a history icon and a balanced amber ring: Rogue picks restored (7) · Undo](assets/ux-guide/b1-restored.png) | ![The top banner showing Rogue picks restored (7) · Undo](assets/ux-guide/n1-banner.png) |
| A notice with Undo, in the builder's notice box. | An action in a banner that goes away by itself. |

## Mistakes cost nothing

An automatic change says what it did and can be undone; Undo then offers Redo in the same place
("Undone · Redo"). A player's experiments never lose their work.

## Words

A few glanceable words: the thing's name, then when or why. The player's words, in the present
tense. Internal terms (*hold*, *set aside*, *settle*, *ghost*, *disqualified*) stay in the code.

| Say | Not |
|---|---|
| Steel Will auto-fills at level 7. | Steel Will will be restored when its gate reopens. |
| Planned: Archery (L10) | 1 set aside · Archery held |
| 3 choices don't apply right now · Details | 3 ghost selections were found and cleaned |
| Rogue picks restored (7) · Undo | Restored 7 entries from the planned hold |
| Undone · Redo | Undo complete |

## Where things go

The app's patterns and what each is for. When a better one is agreed, it replaces the old one here.

| Pattern | For | Example |
|---|---|---|
| Top banner | a confirmation or error that needs no action, gone by itself | "Saved" |
| Notice box above the options column, at its width: an icon and a balanced gradient ring (amber on dark, the light theme's blue on light), no coloured fill, text left and action with its close mark right | a notice with an action, until dismissed; never reads as a warning | ![Notice box: 1 choice doesn't apply right now · Details](assets/ux-guide/b1-opened.png) |
| In the section row | the state of that section | "Planned: Archery (L10)" |
| Under a class row, beside the level picker | what the class's level or order is holding back | "3 planned at higher levels" |
| The panel behind a Planned line | tapping the line (a button with an arrow) opens its picks in the ringed box: a row each with Clear, saying only what the line does not. One pick: when it returns. Several at levels: name and level. A reason the line states: names only | "3 planned at higher levels" opens to "Whirlwind Attack · level 11 · Clear" |
| Details under a notice | a list behind a notice's "Details": a square band across the box with the box showing below it, like an opened shell; names in one column, when each applies in the next; an arrow on "Details" that flips when open | ![Notice box open: three picks and when each auto-fills](assets/ux-guide/notice-details.png) |
| A small outlined mark before a row | a state one row holds and another can take: lit on the one that has it, faded on the others, a tap moves it | "1st" before each class row; a tap makes that class the starting class |
| A list behind a link above the options | everything stored, where a broken pick can still be reached: the actionable rows first (missing, planned) with Clear, then the rest by section | "Choices" |
| Link button inside text | an action within a sentence | "Details" |

## Phone first, and how designs are reviewed

- Check every placement at 400px wide, with long real names.
- One line unless it truly cannot fit. Tap targets at least 44px tall.
- Mockups are real builder states reached by clicking, at phone width, with only the new parts drawn
  in. No abstract canvases: the owner reviews on a phone.
- One review page, one card per decision, two or three options each. The owner's ruling goes in the
  log below, with the date and the reason, in the same change.
- Screenshots show what a player sees. A full-page capture is fine, but release the sticky header
  first (`.sticky-header` to `position: static`): captured as-is it is painted across the middle of
  the page and reads as a bug. A phone shot uses a phone context (`isMobile`, `hasTouch`, a phone
  user agent), not just a narrow desktop window.
- Mockup pages are laid out like the screen they show: one column at phone width, scrolling the way
  the app does, desktop as a second view. Never a large free-form canvas to pan and zoom (owner,
  2026-10-22: "a bear to navigate on a phone, not much better on PC").

## Decisions log

Newest last. Date, ruling, and what it replaced.

- 2026-10-03: "Planned" is the player's word for a pick that stopped applying and will return.
  Replaced "set aside" in player copy.
- 2026-10-03: no "Keep instead" action; switching a pick is just a click.
- 2026-10-08: notice wording for testing: "Steel Will auto-fills at level 7."; "Rogue picks restored
  (7) · Undo", then "Undone · Redo"; "3 choices don't apply right now · Show details".
- 2026-10-09: content names in Title Case.
- 2026-10-11: the Planned marker sits in the section row; several become a count ("2 planned"); it
  wraps to its own line only when the row cannot fit it.
- 2026-10-11: notices with an action go in the builder's notice box, with a subtle flair so they are
  noticed.
- 2026-10-13: picks waiting on a level show once, under the class, beside the level picker.
  Replaced headings for sections that are not open yet.
- 2026-10-13: picks waiting on the starting class use the same line under that class, as a fallback
  until a better design is found.
- 2026-10-13: the notice box gets an icon and a balanced amber gradient ring, no coloured fill.
  Replaced the faint outline.
- 2026-10-13: boxes are sized by the layout, not their content; the restore notice spans the
  options column. Replaced a notice shrink-wrapped to its text.
- 2026-10-14: a toggle keeps one short label in both states ("Details"), so the notice stays on
  one line at phone width. Replaced "Show details" / "Hide details".
- 2026-10-14: a notice's details sit in a square, edge-to-edge band with the box showing below it,
  as two columns (name, then when). Replaced a plain list run on under the sentence.
- 2026-10-14: on the light theme the notice ring is the theme's blue, not amber: the light theme
  uses blue where the dark one uses amber.

- 2026-10-15: label only what the player would miss. Picks waiting on a deliberate change (a
  subrace on its race, a deleted class's picks) get no UI of their own; the designs are parked in
  `decision-gate-hidden-picks.md` on `feature/grant-rows`. Closed the open question on where a
  subrace waiting on its race shows.
- 2026-10-15: say a thing once. A Planned panel leaves out what its line says (it repeated the pick's
  name, or the starting-class reason on every row). Clear stays; the ringed box stays. A Planned
  line is a button with an arrow and a 44px tap area. Replaced the sentence-per-pick panel.
- 2026-10-16: "Make starting class" is the "1st" mark before each class row (round 4), and every
  stored choice is reachable from "Choices", missing ones clearable there (round 4).
- 2026-10-15: long forms repeat their navigation and add buttons at the bottom (character builder
  Back / Next, the homebrew builders' add buttons, Add Option). A bottom add button shows once the
  list has an item. The bottom Back / Next scroll the new page to its top, below the sticky header,
  and move focus to the page title. Replaced top-only buttons (`feature/form-bottom-buttons`).
- 2026-10-15: small spaces (a table row, an attack line, a PDF box) get a short summary of an
  effect, a word or two; the full text, SRD wording where there is some, goes in the details or
  description. First use: the Net ("restrains"; PDF "None (restrains)"; the SRD paragraph in its
  expanded details). Replaced a sentence-long summary that wrapped to four lines on a phone.
- 2026-10-16: a leftover character draft (unsaved changes from a closed tab) is offered once, in the
  builder's notice box: "Unsaved changes from Tue 14:02 · Restore" with the close mark for "not
  now"; Discard lives only in the drafts list, so the line never asks what the close mark does to
  the draft. After Restore: "Changes from Tue 14:02 restored · Undo", then "Undone · Redo". Chosen
  over Restore · Discard on the line: a shorter line. Review page:
  claude.ai/artifact/C5pLwAPXYHnmpfkrsHeNuW; rules in plan-character-drafts.md.
- 2026-10-16: a character open in another tab is held in the builder until "Edit here": "Open in
  another tab · Edit here". Chosen over a notice with only a close mark: both tabs share one draft,
  so editing in two at once loses one tab's changes.
- 2026-10-16: unsaved drafts that are not offered (out of date, or offered once and ignored) are
  listed in an "Unsaved drafts (n) · Details" box both above the builder's options and on the
  character list, each with Open and Discard. Shown only when there are any.
- 2026-10-16: when two good options both have fans (white or dark button text, damage-type dots or bars),
  one is the default and the other is an account preference, rather than forcing a winner. Contrast
  ratios are reported and weighed, not treated as rules. Settings show live examples of each choice.
- 2026-10-16: a builder Save the server refuses because the character was saved elsewhere since it
  was opened says "Saved somewhere else since you opened it · Details · Save mine · Use saved".
  Details lists each field that differs, here and saved. Save mine saves over the other; Use saved
  loads it and keeps the builder's version in Unsaved drafts. Chosen over the two choices alone:
  the player sees what they would overwrite. Review page claude.ai/artifact/C5pLwAPXYHnmpfkrsHeNuW.
- 2026-10-21 (design note, not yet designed): a section with its own display settings gets an icon
  that links to that section's customization menu (the Weapons table to its display settings, for
  example), so a player can find what changes it from where they see it.
- 2026-10-21: a "New" tag on a row is not used: it was missed even when looked for, and reads as new
  to the site. A control the player has not used yet glows instead, a few pulses slowing to a stop
  (the 1H / 2H switch's pulse), and opening it once stops it for good.
- 2026-10-22: new controls start at the sizes of the controls beside them (`.form-button.p-5`, 12-14px
  text). The app is careful with space; a 44px tap area is met with an invisible hit area, not by
  drawing the control bigger. Replaced full-width rest buttons in a box (round 8 of the rest mockups).
- 2026-10-22: an option that changes what a button does sits above the button: set it, then act.
  The result (an Undo line) sits below. First used for "dawn passes" over the rest buttons.
- 2026-10-22: the builder's "select any number / complete" line is hidden on sections with no minimum, no maximum and no held picks: it can only ever say complete (Label only what the player would miss). A switch under Developer mode, "Hide empty pick counts", brings it back; on by default. `planned-text/count-says-nothing?`, `feature/inventory-kits`.
- 2026-10-25: Data View, in a tab with no draft of its own: a list of this browser's drafts, one card
  each with Show, newest edit first (the newest is the likeliest to be the broken one); drafts open
  in another tab are listed and marked. Once one is shown, a dropdown at the top switches drafts,
  each entry with name, kind, time edited and "open in another tab". Replaced a message sending the
  player back to the builder. `feature/character-rescue`.
