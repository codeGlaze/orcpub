# Class-builder extraction — plan for the next branch

**Purpose.** Line up the first *incremental* view decomposition: pull the homebrew
**class builder** out of the `views.cljs` monolith into its own file, on a small branch
that merges fast — the opposite shape of the abandoned `refactor/views-extraction`.
Reuses the dependency rules and gotchas already worked out in
[`views-builders-split.md`](views-builders-split.md) and the sequencing in
[`monolith-decomposition-plan.md`](monolith-decomposition-plan.md).

## Why this shape (the lesson from `refactor/views-extraction`)

`refactor/views-extraction` was a big-bang decomposition started right after the 2026
modernization. The bug-fix stream on the mainline churned the same files; the branch
couldn't stay in sync and was abandoned. **The failure wasn't the design — it was the
cadence.** A long-lived parallel refactor racing a churning mainline always loses.

So: **one builder at a time, off `integration`, merged in days, not a parallel epic.**
Each extract is small enough to rebase trivially if it falls behind. The class builder
is the natural first target — it's the largest builder and the one actively growing
(starting-equipment just landed in it on `feat/starting-equipment`).

## Starting reality (differs from the dead branch's assumption)

`views-builders-split.md` assumed a `builders.cljs` file **already existed** (post-v2
views extraction) and split *that*. **On `integration`, none of that landed** — the
class builder and all the shared builder helpers are still inside `views.cljs`. So this
extract pulls `class-builder` **directly out of `views.cljs`**, importing the shared
helpers it still leaves behind.

## What moves vs what it imports

**Moves to `src/cljs/orcpub/dnd/e5/views/class_builder.cljs`:**
- `class-builder`, `class-builder-page` (**the page wrapper moves with the builder** —
  keeping it in `views.cljs` would force `views.cljs` to import the builder fn back,
  creating a cycle. This is the #1 gotcha from the dead branch).
- `starting-equipment-section` + its private helpers (`equipment-vocab-items`,
  `fixed-equipment-block`, `choice-equipment-block`, etc.) — already a self-contained
  block, added on `feat/starting-equipment` for exactly this portability.
- Any class-builder-only helpers (the class modifier/level/selection sub-components if
  they're not shared with other builders — verify usage first).

**Imports from `views.cljs` (shared toolkit, stays put for now):**
`builder-input-field`/`class-input-field`, `plugin-datalist`, `textarea-field`,
`dropdown`/`labeled-dropdown`, `comps/labeled-checkbox`, `option-skill-proficiency-choice`,
`option-skill-expertise-choice`, `option-level-modifiers`, `option-level-selections`,
`option-traits`, `event-value`, plus `opt/abilities`. (Confirm the exact set by grepping
`class-builder`'s body before moving.)

**Naming:** file is `class_builder.cljs` — but if a shorter builder namespace is ever
introduced, use `classes` not `class` (`class` is a JS reserved word Closure munges to
`class$` with a warning — dead-branch gotcha #1).

## Circular-dependency rule (non-negotiable)

```
views.cljs  (shared toolkit + everything else)
    ^
    |  (import only this direction)
views/class_builder.cljs
    ^
    |
router / pages  (require class_builder.cljs for class-builder-page)
```

- `class_builder.cljs` imports **from** `views.cljs`; `views.cljs` must **never** import
  `class_builder.cljs`.
- Whatever mounts `class-builder-page` (the SPA route registration) requires
  `class_builder.cljs`, not `views.cljs`. Find and repoint that one reference.
- After the move, grep to prove there is **no** `views.cljs` → `class_builder.cljs` edge.

## Steps

1. **Map the surface first.** Grep `class-builder`'s body for every referenced var;
   split into "class-builder-only" (moves) vs "shared" (imported). Grep for where
   `class-builder-page` is routed/mounted.
2. Create `views/class_builder.cljs`; move the builder + page + starting-equipment block
   + class-only helpers; add requires for the shared toolkit from `views.cljs`.
3. Delete the moved defns from `views.cljs`; repoint the route to require
   `class_builder.cljs`.
4. `lein fig:build` (or `fig:test`) — **zero new warnings**, especially no
   undeclared-var or circular-require.
5. Run JVM + cljs suites (the starting-equipment tests and `events_test` cover the
   builder's behavior; nothing should move in test results).
6. **Merge to `integration` immediately.** Don't let it sit.

## Sequencing decision to make at step 1

- **class-builder-first (default):** import the shared toolkit from the `views.cljs`
  monolith. Smallest possible step. Accepts a temporary "imports from the monolith"
  seam that a later toolkit extraction cleans up.
- **toolkit-first:** if the import surface is large/tangled, first lift the shared
  builder toolkit (the ~640-line set the dead branch enumerated) into
  `views/builders.cljs`, then extract `class_builder.cljs` on top of it.

Pick based on the step-1 grep: a *small* shared surface → class-builder-first; a *large*
one → toolkit-first. Either way it stays one reviewable branch.

## What NOT to do

- Don't revive `refactor/views-extraction` (dead, hopelessly drifted).
- Don't bundle the extraction into a feature branch (keeps review + changelog clean and
  minimizes conflict surface).
- Don't split all 10 builders at once — that's the big-bang that died. This is step 1 of
  an incremental path; the full 10-file manifest in `views-builders-split.md` is the
  eventual target, reached one merge at a time.

## Prereq

Land `feat/starting-equipment` first, so the extraction moves the *complete* class
builder (equipment section included) in one clean cut.
