# feature/artist-pages

**Purpose:** a public page per portrait artist (`/artists/<slug>`), controlled by the artist from My
Account, plus the review fixes to the portrait work and batched credit-change emails.
**Base:** re-homed from `claude/artist-profile-pages` (its `dbf63877` is `331f3a0c` here).
**PR into:** `integration`, #65 (repointed from the merged `feature/portrait-builder` on 2026-10-27).
**KB:** [artist-credit-change-emails.md](../../docs/kb/artist-credit-change-emails.md),
[artist-links-and-referrer.md](../../docs/kb/artist-links-and-referrer.md).

## State (updated 2026-10-28)

- Done: `integration` merged in again (`134d04ca4`, with the #70 and #66 work); the second full
  review's 12 inline comments all fixed or answered, replies on each thread name the commit
  (`4a15c78ee` source, `5cafb4736` copy, design page and probes, `4ed89a7b7` tests, each seen to
  fail against the old code). Declined with a reason on its thread: a capture round for the
  examples page (design images stay local by `.gitignore`), and the change-email switch rows
  (they show the artist's saved choices; My Account shows the locked switch).
- Checks on the final change: `lein test` 1341/0, cljs 875/0, lint 0 errors. CI was running on
  `4ed89a7b7` when this was written. No new CodeRabbit review asked: the push only fixed the last
  review's comments.
- Worktree: `/home/codeglaze/projects/orcpub-artist`.

## Next

- Check CI on `4ed89a7b7`. When the owner says merge: `integration` in with `--no-commit` and the
  checks, fold the changelog (`scripts/fold-branch-changelog.sh "Summer Patch"`), push, then the
  owner adds `ready to merge`.

## Owner decisions

- 2026-10-27: #65 repointed at `integration`; fix the second review's comments, being frugal with
  the weekly session.
- 2026-10-27: no move of the artist routes out of `routes.clj` for now (budget). Candidate for later:
  `artist-page`, `artists-page`, `artist-example-jpeg` and the example caches into their own namespace.

## Open questions for the owner

- None open. The owner says merge when CI is green.

## Gotchas for the next agent

- `artist_credit_test` sets the clock by redef'ing `orcpub.heartbeat/now`; credit emails go through
  `artist-email/credit-changed-tx` (outbox rows inside the caller's transaction), not a send.
- The art-mark API was renamed on this branch (`read-mark`, `write-mark`, `carry-marks`); code merged
  from `integration` may still say `mark-of`/`with-mark`/`composed`: grep after every merge.
- Open known bug, not this branch's: My Account is white text in the light theme
  ([my-account-light-theme-white-text.md](../../docs/kb/my-account-light-theme-white-text.md)).
- `autosave_fx.cljs` differs from `integration` on purpose: the heal re-dispatches
  `:character-updated` so the portrait draft survives (pinned by `events_test`). Keep ours in merges.
- Running a browser probe by hand needs `NODE_PATH=$(npm root -g)` and
  `NODE_OPTIONS=--require test/browser/lib/suppress-overlays-preload.js` (the "What's new" overlay
  blocks hovers otherwise); `run-browser-probes.js` sets the preload itself.
