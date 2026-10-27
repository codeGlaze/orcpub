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
- That full review (2026-10-27 13:40 UTC) posted **12 inline + 1 outside-diff, all Minor**. Not
  started. The plan below is the next session's whole job; do not ask for another review until all
  13 are pushed and answered.
- Worktree used: `/home/user/orcpub-artist-pages` (container-local; recreate with
  `git worktree add /home/user/orcpub-artist-pages feature/artist-pages`).

## Next: the 13 comments, in four batches (one test run and one break run per batch)

Each fix commits before its test; each test is seen to fail by restoring only its fix's files
(`docs/TESTING.md`, "Seeing a test fail"). Reply on each thread naming the fixing commit.

**Batch A, server (`lein test`):**
1. `routes.clj:399` (outside diff) `artist-credit-body` select-keys drops the page switches from
   `:current`, so a switch the config forces off reads On in My Account. Add the four switch keys.
   Test: the route body carries `:artist/profile? false` when the config sets it.
2. `PORTRAIT-COMPOSITOR.md:116` real bug: `fork/branding.clj` `artist-override` drops every page
   switch from `PORTRAIT_ARTISTS`, so a deployment cannot force a page or section off (tests only
   passed by redef'ing `branding/portrait-artists`). Parse each switch, keeping only `false`.
   Test: parsing the env JSON with each switch off.
3. `pedestal.clj:154` the Referrer-Policy interceptor sits after the portrait guard and the static
   resources (`system.clj`), and runs before the default 404 is built, so those responses lack it.
   Move it first in the chain. Test: full chain (`io.pedestal.test/response-for`) for a static
   file and an unmatched route.
4. `styles/portrait.clj:690` `.lk-about` hover and `.ap-foot a:hover` use `--lk-glow-text` and
   `--lk-glow`, undefined since the Garden move: use `--lk-under-lit` and `--lk-halo`. Test: every
   `var(--lk-*)` in the compiled stylesheet is declared somewhere in it.
5. `portrait_art_guard_test.clj:114` / `art_mark.cljc` `jpeg-mark` stops at the first SOS, so a
   marked COM between scans (progressive JPEG) passes the guard. After SOS, skip entropy-coded data
   to the next real marker (0xFF then not 0x00 and not RST0-7), continue; no raw byte search
   (false positives). Test: pinned bytes with the COM after a scan (cljc, both platforms).
6. `portrait_render.clj:752` `jpeg-bytes` does not `.dispose` the writer when `.write` throws: put
   it in `finally`. No observable behaviour to test; say so on the thread.

**Batch B, client (`lein fig:test` + `node test/e2e/cljs-harness.js`):**
7. `components.cljc:34` the prepared-spell box (`views.cljs` ~2930) is disabled when no
   preparations remain, but its span's on-click still dispatches `::char5e/toggle-spell-prepared`.
   Check first whether that event (`events.cljs:4733`) refuses it; if not, guard the click in the
   caller. Pre-existing on `integration`? If so, a Fixed changelog line.
8. `autosave_fx.cljs:104` only merge commits touch this file here, yet the PR's diff changes 3
   lines of it: compare with `integration`; if this branch carries an older copy, take
   `integration`'s and answer that the heal logic is not this PR's.

**Batch C, the header probe (needs `lein fig:build` and the e2e server, once):**
9. `header_menus_e2e.js:194` assert the expected tab and flyout counts before the bounds loop, so
   a missing header fails instead of passing. Break: hide `.header-tab` in the stylesheet.
10. `header_menus_e2e.js:195` keep `focus()`, fix the comment that says menus open by tapping.

**Batch D, docs (no tests):**
11. `mockup.html:325` and its switch preview (~444): show the redirect, not a 404.
12. `PLAN.md:184`: item 4 still says 404; also line 17 and 205-206 ("once per server start",
    memoized: now a bounded cache with a 60 s failure cache), 149-150 test counts, 154 (51 checks;
    `probe-baseline.json` has the real count).
13. `docs/design/capture/lib.js:8`: `login` uses `E2E_BASE`, capture uses `ORCPUB_BASE`; pass the
    capture's base through (or set `E2E_BASE` from it).

Then: one full `lein test`, cljs suite and full `lein lint`; push; replies on all 13; one
`@coderabbitai full review`.

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
