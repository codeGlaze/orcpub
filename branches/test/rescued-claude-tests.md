# test/rescued-claude-tests

**Purpose:** the one branch for what the 2026-11-09 `claude/*` clean-up found worth keeping (owner:
no micro branches from it). **Base:** `integration` at `7d4c23d2a`. **PR into:** `integration`, not
opened yet. **KB:** [branch-inventory.md](../../docs/kb/branch-inventory.md), "`claude/*` content check".

## State (updated 2026-11-09)

- Done, pushed: the stuck spinner fix (`8d623e0e1`, tests `aa0beed7c`): every page change resets
  `:loading`, and a failed Random character lowers it and keeps the current character. Rescued
  tests (`1d870fbaf`): 24 magic-item effect tests and 9 PDF-route tests, all seen failing first.
  Branch changelog started (one Fixed line).
- In progress: timing of the homebrew search boxes (My Content "search this source…", the spell
  picker) at clean / pak-c1 / pak-c4, to decide whether search needs a debounce or cache.
- Next: report the timings; fix only if a keystroke is over ~50 ms; then the PR when the owner says.

## Owner decisions

- 2026-11-09: rescue the two test files, fix the spinner here, measure homebrew search; delete
  their source branches once rescued (done).

## Gotchas for the next agent

- `put-character` sets `:loading false`, not a decrement: a save still in flight can lose its
  spinner early. Cosmetic; not changed.
- The old `claude/app-speed-fix-K8HwV` index fed an Orcacle homebrew search `integration` does not
  have: don't port it as is, whatever the timing says.
