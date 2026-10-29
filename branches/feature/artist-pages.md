# feature/artist-pages

**Purpose:** a public page per portrait artist (`/artists/<slug>`), controlled by the artist from My
Account, plus the review fixes to the portrait work and batched credit-change emails.
**Base:** re-homed from `claude/artist-profile-pages` (its `dbf63877` is `331f3a0c` here).
**PR into:** `integration`, #65 (repointed from the merged `feature/portrait-builder` on 2026-10-27).
**KB:** [artist-credit-change-emails.md](../../docs/kb/artist-credit-change-emails.md),
[artist-links-and-referrer.md](../../docs/kb/artist-links-and-referrer.md).

## State (updated 2026-10-28)

- Done: `integration` merged in again (`134d04ca4`); the second review's 12 comments fixed or answered
  (`4a15c78ee`, `5cafb4736`, `4ed89a7b7`). The third review (on `e466caea4`, 8 comments) fixed in
  `d2cef8692` (marks default, notice failure, locked switches, visited links, contrast, disabled
  option) with tests in `a812f907c`, each seen to fail against the old code. Declined on its thread:
  round21 captures (design images stay local). The route keeps a try/catch and a log instead of
  CodeRabbit's transactional rewrite. All seven threads answered, naming the commits.
- Checks on `a812f907c`: `lein test` 1345/0, cljs 877/0, lint 0 errors. No new CodeRabbit review asked:
  the push only fixed the last review's comments.
- Worktree: `/home/codeglaze/projects/orcpub-artist`.

## Next

- Nothing: #65 merged into `integration` on 2026-10-28 (last head `af89cf011`, changelog folded) and
  #32's body is edited. This handoff can be retired.

## Owner decisions

- 2026-10-27: #65 repointed at `integration`; fix the second review's comments, being frugal with
  the weekly session.
- 2026-10-27: no move of the artist routes out of `routes.clj` for now (budget). Candidate for later:
  `artist-page`, `artists-page`, `artist-example-jpeg` and the example caches into their own namespace.

## Open questions for the owner

- None open. The owner says merge when CI is green.

## After #65 merges (owner, 2026-10-28): DONE, #32 edited the same day

- **Edit #32's body** (`integration` into `develop`, codeGlaze/orcpub): add `Fixes` for the cited issues it
  lacks (#169, #520, #699-#701, #703-#712) and for the umbrella issues filed 2026-10-28 on Orcpub/orcpub:
  #714 portraits and artist pages, #715 Rules and SRD data, #716 email, alerts and crash evidence,
  #717 homebrew ASI spreads, #718 held choices, #719 A4, #720 AC, #721 lookups and page loads.
  Also list the branches merged into `integration` with no PR, 2026-08-27 to 2026-09-18.
- The changelog already cites #714-#721 (commit `5041c6c76`). #65 also adds `!docs/design/**` to
  `.coderabbit.yaml`: CodeRabbit skipped #65 for being over 100 files.

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
