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
- Next: the owner decides whether to fix the slow key (see Open questions); then the PR when the
  owner says.

## Owner decisions

- 2026-11-09: rescue the two test files, fix the spinner here, measure homebrew search; delete
  their source branches once rescued (done).

## Open questions for the owner

- The Spells list re-renders every row when its filter clears (up to ~0.7 s with a big homebrew
  pack). Fix it (render the list in pages or on scroll) or leave it?

## Gotchas for the next agent

- `put-character` sets `:loading false`, not a decrement: a save still in flight can lose its
  spinner early. Cosmetic; not changed.
- The old `claude/app-speed-fix-K8HwV` index fed an Orcacle homebrew search `integration` does not
  have: don't port it as is, whatever the timing says.
