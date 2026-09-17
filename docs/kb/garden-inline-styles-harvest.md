# Harvesting `refactor/garden-inline-styles` into integration

`refactor/garden-inline-styles` (tip `59c22902`, 2026-07-03) is **harvested, not merged**. This
doc records what was measured, what is being taken, what is deliberately left, and why.

Measured 2026-09-16 against `integration-local` `593cb50c`.

## Why the branch stalled

It was never abandoned. It became a **dependency instead of a deliverable**:
`docs/kb/rescued/ui-ux-plan.md` records "**Must land first**" (its Tier 1 line numbers shift when
it merges), `frontend-redesign-parallel-work.md` says the redesign is stacked on it, and
`port/redesign-on-refactor` contains it. Two branches sat on top of it, so nothing forced it to
finish; it stopped at ~60% with two `wip:` commits and a stash (`RECONCILIATION-LOG.md`
`stash@{1}`). It also never created `docs/branch-changelog.md`, writing straight into
`CHANGELOG.md`, so it recorded no parent and no return path. See
`branch-origin-convention` in the session memory for the convention it skipped.

## The branch is healthy

- Builds clean: `lein garden once` exit 0, `lein fig:build` exit 0, no warnings.
- Merges into **develop** with exit 0 and zero conflicts.
- Delivers what it claims: views.cljs inline `:style` 110 → 14, 54 new garden class selectors.

An earlier claim of "10 conflicting regions" was **wrong** — it came from grepping
`git merge-tree` output for "changed in both", which lists merge CANDIDATES, not conflicts.
Verify merges with a real `git merge --no-commit` in a temp worktree and read the exit code.

## Why NOT merge it into integration

`integration-local` is **648 commits** past the merge-base (`6dd1b550`, 2026-06-17). A real test
merge gives **exit 1, 6 conflict hunks in 2 files**:

| File | Hunks | Worst case |
|---|---|---|
| `src/clj/orcpub/styles/core.clj` | 2 | one is **256 lines** — a section both sides rewrote independently |
| `src/cljs/orcpub/dnd/e5/views.cljs` | 4 | one is **72 lines** — integration's dev-mode footer, which the branch has never seen (`dev-mode-row`: 0 on branch, 1 on integration) |

Three further reasons a merge is the wrong shape:

1. **The conversion is stale.** Integration has been *adding* inline styles for 648 commits.
   Properly parsed (brace-matched, not `grep -c`), integration carries **75 `:style` maps in 35
   distinct shapes**. The branch deleted 97 `:style` lines it knew about; integration added 23 it
   never saw. Merging converts the June set and leaves integration's newer ones — a job half done.
2. **Both sides styled the same areas independently.** `core.clj` is 1678 lines at the merge-base,
   1974 on the branch, **2702 on integration**. Integration did ~1000 lines of its own garden work.
3. **One small hunk would delete integration's deliberate work** — a filter-input `:style` added
   with a comment about aligning to the filter buttons. Exactly the silent regression a careless
   conflict resolution ships.

## What transfers: 72%

Matching by property-shape (the set of CSS properties in each inline map):

| | call sites | distinct shapes |
|---|---|---|
| Shapes the branch already solved — apply its answer | **54** | 22 |
| New on integration — need fresh decisions | **21** | 13 |

The expensive part of the original work was the judgement (what to name a class, which properties
belong together), and that judgement survives. Only the mechanical application is lost, because the
call sites moved.

The new shapes are small: `align-items` (5 sites), `user-select` (3), and assorted
border/background one-offs.

**Caveat on the 54:** shape-matching compares property NAMES, not values. Two maps with the same
properties and different values count as one shape, so expect a handful of near-misses that need a
value check rather than a blind swap.

## The plan

1. **Append the CSS.** 54 selectors exist on the branch that integration lacks. **4** collide
   (`.app-header-bar`, `.character-builder-header`, `.registration-image`, `.w-100-p`) and are left
   alone; **49** are appended to `(def props)` and **1** (`.import-log-panel`) belongs inside the
   `xs-query` media block, not at top level. DONE: `47bb73f4`.
2. **Port `bbe1f710`'s four mobile-header rules** by hand into integration's existing `xs-query`
   block. DONE: `0a089349`.
3. **Convert the call sites** in `views.cljs`, one reviewable batch at a time, reading each
   replacement out of `views-conversion.diff`. **Do NOT drive these edits from a script** — see
   the warning below.
4. **Decide the remaining shapes** integration added after the split, fresh.

### Check the utility classes exist before using them

The branch generates `m-t-*`, `m-l-*`, `w-*`, `min-w-*` and `max-w-*` from `px-prop` value lists,
NOT as individual class forms — so harvesting "every `[:.class {...}]` the branch added" silently
misses them. Two conversions reached for `.m-t-100` and `.min-w-53`, which compiled fine and
produced **no CSS at all**: integration's `margin-tops` list stops at 25 and it has no
`min-widths` list. The result renders with the margin and the width simply gone.

Both were caught by diffing the classes the conversion references against the compiled stylesheet,
which is the check to run after every batch:

```
classes referenced by the conversion vs. selectors present in styles.css -> missing: []
```

Fixed on `refactor/garden-harvest` by adding `100` to `margin-tops` and a `min-widths` list of
`[53 120 160]`, wired into the `concat` — the generator has no effect until it is in that list.

Also surfaced, and NOT fixed here: **`.h-full` is referenced twice in integration's `views.cljs`
and defined nowhere** — in neither integration's nor the branch's `core.clj`. A pre-existing dead
class. Giving it a real `height: 100%` would change how `registration-page` lays out, which a
no-visual-change refactor must not do.

### Do not automate the call-site mapping

Three attempts to build a shape → class table automatically all produced wrong answers:

- Matching on the SET OF PROPERTY NAMES collapses distinct rules: `{:min-width "53px"}` mapped onto
  `.close-btn-posn` because both are single-property maps in the same diff hunk.
- Taking the class from "any class mentioned in the hunk" yields the enclosing element's whole
  class list, not the class that replaced the style.
- Matching on exact style TEXT is better (44 exact hits) but still attributes the wrong class for
  one- and two-property maps.

The multi-property matches are reliable (`.form-submit-btn`, `.success-header`,
`.password-strength-*`, `.checkbox-border`); the short ones are noise. Read the diff per site.

Artifacts live in the session scratchpad `garden-check/salvage/`: `core-new-css.clj` (the CSS
block), `solved-map.json` (shape → class), `views-conversion.diff` (the original conversion, as
the reference for how each site was rewritten).

## What is deliberately LEFT OUT, and why

- **`63da535c` (header dropdown obscured by tab icons).** Integration already fixes this, better.
  The branch set a blunt `:z-index 10001` on `:hover` only. Integration has `:&:hover {:z-index
  200}` AND `:&:focus-within {:z-index 200}`, each showing `.header-flyout`, with a comment
  explaining why 200: it must beat `.sticky-header` (z-index 100) while the tab's own stacking
  context resolves the flyout inside it. Taking the branch's version would regress mobile
  (focus-within) and discard the reasoning. **Excluded.**
- **`fcb02f74` (CSS-only flyout replacing the atom).** Integration has the hover/focus-within CSS
  *and* keeps `fit-flyout!` deliberately — it caps flyout height on short screens (My Content),
  which CSS alone does not do. Integration also added a themed thin scrollbar for exactly that
  capped case. Removing the atom would break a live behaviour. **Excluded.**
- **The two `wip:` commits** — `cbb1f46d` (ghost buttons, orcacle glow, header controls separator)
  and `4c4ed3df` (header-bar alignment scaffolding). Unfinished visual design, not refactor. They
  belong in the design process, not smuggled in on a style conversion. Excluded by construction:
  harvesting never merges them.
- **The bulk merge itself.** See above.
- **`port/redesign-on-refactor` and `redesign/growable-option-menus`** — stacked on this branch,
  explicitly deferred by the user ("These can wait"). Harvesting does not disturb them; they keep
  their own base.

## What IS taken and is still a real fix

- **`bbe1f710` (mobile header overflow).** Integration's `xs-query` block (core.clj 1028-1053) has
  `.app-header`, `.app-header-bar`, `.app-header-menu`, `.content`, `.header-button-text` — but
  **none** of the branch's four pieces: `overflow-x: hidden` on `.app-header`,
  `.app-header-bar .w-100-p {box-sizing: border-box}`, `.app-header-bar img {max-height: 40px}`,
  `.import-log-panel {max-width: 100vw}`. Confirmed visually: the phone header's oversized logo
  crowds the search/LOGIN row on integration and is capped on the branch.
- **The SVG classes.** Integration has ZERO occurrences of `.svg-stroke`, `.svg-bar-stroke` and
  `.svg-icon-inline`, and still carries the raw inline
  `:style {:vertical-align "middle" :fill "currentColor"}` at `views.cljs:395`.

## What this does NOT fix

The character-list row. The expanded row renders **identically** on both branches, which is
correct for a no-visual-change refactor — and means the July row's phone overflow (the `WWW`
button clipped off the left edge) is untouched. That is the Fall row redesign's job.

### Check scope against the COMPILED stylesheet, not the source

Lifting a rule out of a media query silently changes when it applies. Two source-level audits of
which harvested rules were media-scoped gave contradictory answers — the first missed that a
`#_(at-media ...)` block is a reader DISCARD and not live code (the trap
`documentation-discipline` warns about); the second, after stripping discards, claimed 48 of 50
were media-scoped, which is plainly false for utilities like `.z-1` and `.svg-stroke`.

The reliable check is the branch's own compiled `styles.css`: find each `@media` block's span by
brace-matching and ask whether a selector falls inside one. That gave the true answer — **49 top
level, 1 (`.import-log-panel`) media-only, 0 in both** — and it matched the independent evidence
already in hand. Compiled output is ground truth; source-level paren-walking is not.

## Verification

Build (`lein garden once`, `lein fig:build`), `lein lint`, JVM + CLJS tests, then screenshots
before and after through `scripts/e2e/run.sh`. **The only permitted visual difference is the
mobile header.** Everything else must be pixel-identical: this is a no-visual-change refactor and
anything that moves is a bug.

### Harness traps (cost several turns to rediscover)

- `agents/develop`'s `scripts/e2e` is the OLDER two-file variant (`run.sh` + bundled `run.js`, one
  seeded user, no `lib.js`); `integration-local`'s is newer (explicit suite argument, two users,
  `find-chrome` resolution, CSP/dev-bundle handling). `START-HERE.md` points at the stale one.
- `agents/develop`'s `dev/e2e_boot.clj` **ignores `PORT`** and always binds 8890; integration's
  reads it. Mixing integration's `run.sh` with agents' boot makes run.sh poll the wrong port and
  report "Server never came up" while the server is up and seeded.
- Integration's boot seeds `:orcpub.share-expiry/*`, absent on pre-share-links branches →
  `:db.error/not-an-entity`. For an older branch, drop that third seeded character.
- agents' boot seeds the USER ONLY — no characters — so the list renders empty and any "open the
  first row" step finds nothing.
