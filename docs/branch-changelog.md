<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `fix/class-picker-from-picks`

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

On the DMV dev build the class drop-down vanished after a switch from Barbarian to Wizard, leaving
"Level 0" and no way to pick a class. The Class / Level rows were drawn from the built character,
so any build without the picked class removed the row and its drop-down. They now come from the
stored picks. Why that build lost the class is not reproduced yet.

## Added

## Changed

## Fixed

- **Builder:** the class drop-down no longer disappears when the character's class isn't applied (for example, its homebrew source is turned off or couldn't load); the row stays and says why.
- **My Content:** a homebrew source no longer shows as "couldn't load" with a blank count and "No entries to repair" after all its entries have loaded again.
- **My Content:** retrying a restore after one that could not be saved no longer adds the restored entry to the library twice.
- **Site:** pages without a composed portrait (the builder, the character list, a sheet) no longer download the artists' credits.

## Security
