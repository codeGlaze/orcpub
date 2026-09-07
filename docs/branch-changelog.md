<!-- Branch changelog. Copy this file to docs/branch-changelog.md at the start of a branch,
     fill it in as work lands, and fold it into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. The fold strips the guidance and the
     "Why" section; a "## Highlights" section (if earned) survives. Undated by design. -->

# Branch changelog — `hotfix/boot-config-report`

<!-- ─────────────────────────── HOUSE STYLE (read once) ───────────────────────────
Entries are bullets under ## Added / ## Added

- The server says it started and what it is configured with. Every optional setting is
  listed with its value and where that value came from -- `set` from the environment,
  `default`, or `IGNORED` when a value was given but rejected, which is the case a bare
  number cannot tell you about. `ORCPUB_HTTP_MAX_THREADS` reports `unset / Pedestal's`
  rather than a number that would drift from Pedestal's own formula (`<pending>`).

## Fixed / ## Changed (Keep a Changelog categories).

Bullets:
  • One change per bullet. Split, don't cram three changes into one line with semicolons.
  • Succinct and plain. Not necessarily terse — but to the point.
  • Cut: AI-jargon ("seamless / robust / comprehensive / powerful / streamlined / leverage"),
    restating the same change twice, and explaining internal wiring the reader doesn't need.
  • Say WHAT changed and WHY it matters. End with the commit(s): (`shorthash`).

Highlights (optional — DELETE the section if this branch doesn't earn one):
  • Allowed ONLY for an impactful branch: a new capability or a behavioral shift that
    didn't exist before — NOT a bugfix bundle or routine polish.
  • ≤ 3 sentences, user-facing, plain. This is the one place prose is allowed, and it
    survives the fold. Decide whether the branch earns it before you open the PR.

No prose intro paragraphs anywhere else — not under this title, not under a category.
────────────────────────────────────────────────────────────────────────────────── -->

## Why this branch exists

Two things the admin raised from a production boot log.

The log printed the Datomic URI verbatim, and a `datomic:sql` URI carries the database
password in it. That is a live credential disclosure into anywhere the boot output is
kept — terminal scrollback, container logs, a log aggregator, a screenshot in a chat.

Second, the server says nothing about how it is configured. There is no "started ok",
and no way to tell from the log whether a tuning variable was picked up, ignored as a
typo, or never set. The knobs exist and are documented; nothing reports them.



## Fixed

- The database password is no longer printed at startup. A `datomic:sql` URI carries it
  in a `password=` parameter, and the URI was written to the boot log verbatim and
  embedded in three error maps. Credentials are blanked before any of that; the host,
  port, database and user still show, so the line stays useful (`<pending>`).
