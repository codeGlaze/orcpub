# test/rescued-claude-tests

**Purpose:** the one branch for what the 2026-11-09 `claude/*` clean-up found worth keeping (owner:
no micro branches from it). **Base:** `integration` at `7d4c23d2a`. **PR into:** `integration`, not
opened yet. **KB:** [branch-inventory.md](../../docs/kb/branch-inventory.md), "`claude/*` content check".

## State (updated 2026-11-09)

- Done, pushed: the stuck spinner fix (`8d623e0e1`, tests `aa0beed7c`): every page change resets
  `:loading`, and a failed Random character lowers it and keeps the current character. Rescued
  tests (`1d870fbaf`): 24 magic-item effect tests and 9 PDF-route tests, all seen failing first.
  Branch changelog started (one Fixed line).
- Measured 2026-11-09 (production bundle, headless Chromium, `scripts/e2e/server.sh`, packs from
  `dev/scale_orcbrew_pack.clj`; script and logs were in the session scratchpad, not kept): typing is
  ~30 ms a key (two frames) in My Content's "search this source…" at pak-c1 and pak-c4, and on
  the Spells list (`/pages/dnd/5e/spells`; the builder has no spell text filter). **One slow key:**
  on the Spells list, the backspace that takes the text under 3 characters re-renders the whole
  list: 116-127 ms clean (319 rows), 196 ms pak-c1 (485), 578-699 ms pak-c4 (983). It is render
  cost, not filtering, so the old `claude/app-speed-fix-K8HwV` index would not help.
- Done 2026-11-10, pushed: reference pages moved to `views/reference.cljs` (`4699ad547`); ghost
  rows fixed (rows and expanded state keyed by each entry's `:key`, not its name: same-named
  homebrew spells showed 983 -> 1491 rows after clearing a search), routes built on click, one
  shared `progressive-rows` for the three lists (`719631230`); browser test
  `test/browser/reference_lists_e2e.js` (`fbbbae0d1`, 5/10 fail on the old code, 10/10 pass).
- 2026-11-10: source-tag mockup published for review: https://claude.ai/artifact/Jio8hodHEUpQma5GApGx1n
  (placement A/B/C, icon kept or replaced, short names). The temporary variant code is not on
  the branch.
- Done 2026-11-10, pushed: source tags built as decided (A, right of the row, replacing the icon;
  `8772d53ab`), browser checks extended to 18 (`4c994803d`, 8 fail on the old code). Full checks
  green: lein test 1576, cljs 1013, lint 0. **Next: the PR into `integration` when the owner says.**
- Superseded: the source-tag mockup for the reference lists (owner said yes: phone screenshots on one
  review page, SRD vs homebrew colour, `common/source-abbreviation`), then build it; the PR when
  the owner says.

## Owner decisions

- 2026-11-09: rescue the two test files, fix the spinner here, measure homebrew search; delete
  their source branches once rescued (done).

## Gotchas for the next agent

- The scaled packs from `dev/scale_orcbrew_pack.clj` repeat every name across clones: realistic
  for name collisions (it is what exposed the ghost rows), but a row count taken from them is
  only right when rows are keyed by `:key`.
- `run.sh` needs `NODE_PATH` pointing at a checkout with Playwright installed
  (`/home/codeglaze/projects/orcpub-pwd/node_modules` here).

- `put-character` sets `:loading false`, not a decrement: a save still in flight can lose its
  spinner early. Cosmetic; not changed.
- The old `claude/app-speed-fix-K8HwV` index fed an Orcacle homebrew search `integration` does not
  have: don't port it as is, whatever the timing says.
