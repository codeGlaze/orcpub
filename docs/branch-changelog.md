<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `<branch-name>`

<!-- ─────────────────────────── HOUSE STYLE (read once) ───────────────────────────
Say what the BRANCH did, not the commits it took. A branch that built new sign-in pages and
tightened passwords writes two lines, however many commits got it there:

  - **Accounts:** new sign-up, sign-in and account-recovery pages.
  - **Accounts:** password rules based on length, a live strength meter, and breach screening.

Rules:
  • One line per outcome a user or maintainer would notice. Open it with its area in bold
    (**Homebrew:**, **Characters:**, **Sharing:**, **Accounts:**, **Printing:**, **Site:**, **Server:**).
  • In CHANGELOG.md, lines under each heading are grouped by area, in the order the template names
    (docs/branch-changelog.template.md). The fold appends at the end of a heading: move each folded
    line into its area's group.
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

Finishes the Long Rest Update before it goes upstream: What's New rewritten for players (owner-approved wording, a new panel id so testers see it again), Bardic Inspiration on a short rest (#310), and a PDF tab fix. It also vets CHANGELOG.md: one Long Rest Update section (the Summer Patch was the same release under its earlier name), lines grouped by area under each heading, fixes to work the same release built are gone, duplicates are merged (221 bullets to 180), and lines cite the upstream issues they close, including the umbrella issues #724-#729.

## Added

- **Site:** a portrait artist's Twitch button shows when they are live, once the site sets Twitch keys.

## Changed

- **Site:** What's New lists the release as New, Improved and Fixed, in plain player terms, and links to the portrait artist's page.

## Fixed

- **Characters:** Bards regain Bardic Inspiration on a short rest from level 5 (Font of Inspiration). (#310)

## Security
