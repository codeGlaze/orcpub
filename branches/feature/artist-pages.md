# feature/artist-pages

**Purpose:** a public page per portrait artist (`/artists/<slug>`), controlled by the artist from My
Account, plus the review fixes to the portrait work and batched credit-change emails.
**Base:** re-homed from `claude/artist-profile-pages` (its `dbf63877` is `331f3a0c` here).
**PR into:** `integration`, #65 (repointed from the merged `feature/portrait-builder` on 2026-10-27).
**KB:** [artist-credit-change-emails.md](../../docs/kb/artist-credit-change-emails.md),
[artist-links-and-referrer.md](../../docs/kb/artist-links-and-referrer.md).

## State (updated 2026-10-27)

- Done: `integration` merged in (`155fd259`); the first review's 7 comments fixed and answered
  (`04becd21`..`406d4e19`); CI green, merges clean; `@coderabbitai full review` asked once.
- That full review (2026-10-27 13:40 UTC) posted 12 inline + 1 outside-diff, all Minor: all 13
  fixed or answered (`4784ea78`..`6acf7c4e`, pushed), replies name the commits, the outside-diff
  one in a PR comment. `lein test` 1329/0, cljs 740/0, lint 0 errors, header probe 52/52.
  `@coderabbitai full review` asked once more after that push.
- Worktree used: `/home/user/orcpub-artist-pages` (container-local; recreate with
  `git worktree add /home/user/orcpub-artist-pages feature/artist-pages`).

## Next

- Wait for the full review asked for on 2026-10-27 (after `6acf7c4e`). Then work its comments under
  the merge gate: fix each with a push that does nothing else and reply naming the commit, or answer
  it on its thread. A push that only fixes those comments needs no new review.
- Before any merge: `integration` in with `--no-commit`, then the checks.

## Owner decisions

- 2026-10-27: #65 repointed at `integration`; fix the second review's comments, being frugal with
  the weekly session.
- 2026-10-27: no move of the artist routes out of `routes.clj` for now (budget). Candidate for later:
  `artist-page`, `artists-page`, `artist-example-jpeg` and the example caches into their own namespace.

## Open questions for the owner

- None yet. When the 13 are done and CI and review are clean: the owner says merge, the agent folds
  `docs/branch-changelog.md` (`scripts/fold-branch-changelog.sh "Summer Patch"`), and the owner adds
  `ready to merge`.

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
