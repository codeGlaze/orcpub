# Branch changelog — `feature/content-library-management`

Staged entries for this branch, in the root `CHANGELOG.md` format. Fold these into
the next release section when the branch is promoted; keep this file the running
record until then.

## Why this branch exists

Make the **My Content** homebrew library actually manageable and resilient — the
slice of the homebrew/content initiative that stands on its own and needs **no
feat-pool rewrites**. Split off from `feature/homebrew-data-preservation` so it can
be reviewed and land independently: content authors can see, organize, disable,
move, and de-conflict their homebrew, and imports/exports stop producing silent
duplicates or lying warnings. North stars: **stability** (nothing you already have
is overwritten or misfiled) and **flexibility** (the library bends to the user, not
the reverse).

---

## Added

- **Move / copy content between sources** — one select-mode mechanism for single or
  bulk; clobber-free key policy (move keeps the key unless taken; copy always mints a
  fresh one) (`903f44cb`).
- **Four-level disable hierarchy** — global / source / section / item, checked as an
  OR. The two new levels (global "all homebrew" + per-section) live in a local overlay
  store, so they're a per-device view preference that never mutates `.orcbrew` data or
  travels with an export (`95426d8c`).
- **Passive library health-status card** — surfaces unresolved key conflicts,
  missing-required-fields, and export blockers; one line per problem *type* with a
  count (a big mess can't flood the page). Warning-yellow for attention, red for
  broken; always-on on the My Content hub, dismissable-and-remembered elsewhere,
  centralized across content views (`b58fe80b`, `79982e03`, `d0338049`, `e5372fed`,
  `e7040f4a`).
- **Opinionated, summary-first import** — safe defaults resolve conflicts up front
  with a one-click Import; the full per-conflict panel becomes "Review" (`e90466c1`).
- **Richer duplicate-key resolution** — severity split with honest labeling for the
  collapse-risk types, "keep both, turn one off" for a deterministic winner, rename the
  *existing* item, and an internal keeper-picker (`87512e47`, `052e6e55`, `0c30a022`,
  `862d9b26`).
- **Mutual-exclusion legibility** — per-row twin notes, a library banner, disabled-
  content badges colored by reason, and swap-on-enable keeping ≤1 enabled twin
  (`8543d8f6`, `d94973a6`).
- **My Content toolbar redesign** — two-zone (content vs library actions), select
  mode, and a 3-step delete guard (`49f2aafe`, `8fc497d9`).
- **Disabled-item visibility** — a count, a show/hide toggle, and search within a
  source (`47758423`).
- **Share a character with its homebrew embedded** — view-only, with a keep-in-library
  option and collision notice; custom magic items included (`4cae54e7`, `7bf4516a`,
  `35539c4c`).
- **Source-name-choice modal on import** — when a single-source file's name
  meaningfully differs from the source its content declares, ask whether to rename or
  keep, instead of silently guessing (`fa5909cf`).
- **Number→word name repair** for keyword-trap recovery ("9 Lives" → "Nine Lives")
  (`4c128a66`).

## Fixed

- **Single-source export/import no longer spawns a duplicate source** — the source is
  recovered from the content's `:option-pack`, not the browser-mangled filename; a
  last-resort dedup-suffix strip covers files with no declared source (`40413f17`,
  `e53a8b71`).
- **"Skip this one" in the conflict modal actually skips** — it was a no-op that
  imported the colliding item anyway (`47b57793`).
- **The `:route` handler no longer crashes on an unmatched (nil) URL** (`dab319a0`).
- **Dark-on-dark text in the conflict-modal body** (`b100b927`).
- **Custom item save persists the shown type** instead of blanking it (`52c0e40a`).
- **Stale `:key` after a rename** — the item's own `:key` is rewritten so a double
  rename is a no-op (`0c30a022`).
- **Recovery panel "Fix & Restore" auto-names invalid entries** in one click
  (`5e196348`, `898478b0`).
- **One home for source-less content** — folded the stray "Unsorted Homebrew" default
  into "Default Option Source"; "Unnamed Content" stays separate on purpose (nameless
  sources, for findability) and is now documented (`a5d18e2f`, `b5ba38d0`).

## Changed / internal

- **Health detectors are memoized subscriptions** — one library walk per plugins
  change instead of dozens per render (and per keystroke) (`47b57793`).
- **Conflict/export modals aligned to the health-card severity vocabulary**
  (`6cbd890f`).
- **Dead-code sweep** — removed verified-dead helpers; pre-existing dead code restored
  with dated investigation markers rather than deleted on a feature branch (`47b57793`,
  `874d57d5`).
- **Data-driven library list** — empty content-type categories hide; the list is
  derived, not hardcoded (`e3023cd3`).
- **Gitignore deploy-injected static assets** (font-awesome) (`d8331619`).

## Docs

- Library-management KB turned into reference documentation; roadmap moved to
  `docs/TODO.md` (`8f3bc544`).
- `docs/kb/branching-model.md` — branching, authorship & commit conventions
  (`d82c2cf3`).
