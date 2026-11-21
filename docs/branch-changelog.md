<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `fix/long-rest-wrapup`

<!-- ─────────────────────────── HOUSE STYLE (read once) ───────────────────────────
Say what the BRANCH did, not the commits it took. A branch that built new sign-in pages and
tightened passwords writes two lines, however many commits got it there:

  - **Accounts:** new sign-up, sign-in and account-recovery pages.
  - **Accounts:** password rules based on length, a live strength meter, and breach screening.

Rules:
  • One line per outcome a user or maintainer would notice. Open it with its area in bold
    (**Homebrew:**, **Characters:**, **Sharing:**, **Accounts:**, **Printing:**, **Site:**, **Server:**).
  • In CHANGELOG.md, lines under each heading are grouped by area, in this order: Characters,
    Builder, Character sheet, Inventory, Spells, Warlock, Magic items, Items, Equipment, Homebrew,
    Sharing, Rules, Orcacle, Reference, Monsters, Content, Printing, Accounts, Site, Server, Docker,
    Docs. The fold appends at the end of a heading: move each folded line into its area's group.
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

Release blockers found by the page-load test before the Long Rest Update release PR: every page loaded Patreon's button image from Patreon's servers, and the bundle was 15 KB over its ceiling. The images now come from this site, the ceiling is 960 KB for now (trimming is its own branch), and CI runs the page-load test on every PR.

## Added

## Changed

## Fixed

## Security

- **Site:** the Patreon button and mark come from this site, so pages no longer send visitors' addresses to Patreon.
