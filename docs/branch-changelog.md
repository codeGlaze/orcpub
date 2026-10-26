<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `fix/lookup-indexes`

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

Sign-in, the account check on every page and the list pages read every user or record to find one,
so they slow down and churn memory as the site grows (measured: 151 ms and 120 MB per email lookup
at 100k users). This branch indexes those lookups, adds a lowercase email field so email lookups can
use one, and fetches the account once per session. Plan: `plan-lookup-indexes.md` on agents/develop.

## Added

## Changed

- **Server:** the first start after upgrading builds new database indexes and gives older accounts an email lookup key, both in the background; until that finishes, an older account with a mixed-case address signs in by username rather than email.

## Fixed

- **Accounts:** follow buttons remember who you follow after the page reloads.

- **Server:** sign-in, password reset and character, party, folder and item lists no longer slow down and use more memory as the site grows.

## Security
