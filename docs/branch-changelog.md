<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch, fill it
     in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold moves each line into the same heading of
     the release and drops "Why this branch exists" and "Highlights". Undated by design. -->

# Branch changelog — `feature/inventory-kits` (round four)

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

Follow-up to #88. The site footer had grown into alternating paragraphs and link rows that read as
clutter on a phone; it is now labelled rows with a muted legal band. The admin docs were rewritten as
instructions and checked against the code. Manage Content rows no longer log React's missing-key
warning.

It also adds command-line database backups (live, incremental, with a full offline copy as a
fallback, stress-tested), and finishes the kits from #88: each equipment pack gets its own drawn icon,
and the kits sit in a grid of framed cards that widens to two across on desktop.

## Added

- **Server:** database backups from the command line: `./run --backup` while the site runs (each run adds only what changed), `./run --restore <folder>` and `./run --verify-backup <folder>`, and `./run --backup --offline` as a full-copy fallback.

## Changed

- **Site:** the site footer groups its links into labelled Help, Support and Legal rows (one line across wide screens), with the copyright, SRD notice, credits and version in a quieter band below.
- **Docs:** the setup and operation guides read as step-by-step instructions, list every server setting, and cover backups and email troubleshooting.

## Fixed

## Security

- **Docker:** Java starts with a stopgap flag that blocks CVE-2026-114938 (Java2Shell) on the app and Datomic containers, until the images run a patched Java 21 (`JAVA2SHELL_STOPGAP`, true or false).
- **Server:** security updates to the web server (Jetty), the JSON parser (Jackson), the PostgreSQL driver, password hashing (Bouncy Castle), the email library and Guava; an unused PDF library removed.
