# Plan: normalize CR to one number

*The execution plan for [decision-cr-representation.md](decision-cr-representation.md). Six steps,
each its own commit with its acceptance green before the next, docs synced per commit. Written
2026-09-16 on `feature/extras-companions`. Nothing here is built.*

This is step 1 of the Extras silo (`docs/TODO.md` Part 1). It exists because the creature query
buckets A and B share needs a CR that can be compared, and today it cannot be.

## Step 0 — characterize

New test pinning current behaviour, before anything moves:

- the XP value looked up for every one of the 317 SRD monsters
- the order `sort-by :challenge` produces over the full list
- both formatters' output for every one of the 28 distinct CRs
- the rendered druid Wild Shape action summary, as a literal string, at levels 2/4/8

**Acceptance — met 2026-09-16.** `cr_characterization_test.clj`, 6 tests / 16 assertions, green.
Proven able to fail: flipping one monster's `:challenge` from `(/ 1 4)` to `(/ 1 2)` in the real
data turns it red on two independent assertions (per-CR counts, total XP), then restored clean.

Two things the sensitivity probe exposed while writing it, both recorded rather than smoothed over:

- **The sort pin only guards the ends of the list.** Perturbing a mid-CR monster moves the totals
  but not the first five, so the first attempt at the probe passed when it should not have. The
  counts and total-XP assertions are what actually catch a mistyped row.
- **`ratio-keys-today` is SUPPOSED to go red at step 2.** It pins the JVM `Ratio` type and the
  `nil` lookup by double. Its failure is the signal the conversion landed, not a regression.

## Step 1 — the formatter pair

`cr->label` and `label->cr` into `orcpub.dnd.e5.display`. It is already the display-strings
namespace, already `.cljc`, and the require graph is clear: `display` pulls `weapons`, `common`
and `character.equipment`, none of which reach `classes` or `display`, so `classes.cljc` can
require it without a cycle (checked).

No callers yet.

**Acceptance — met 2026-09-16.** `cr_label_test.cljc`, 5 tests / 29 assertions. All 28 distinct
SRD CRs round-trip; unreadable input (`nil`, `""`, `"abc"`, `"1/0"`, a keyword, a vector) gives
nil rather than throwing, because the callers are a text field and an import.

Run in **both** runtimes, which is the point of putting it in `.cljc`:

- JVM: 733 tests / 5491 assertions, 0 failures.
- CLJS via `lein fig:test` + `node test/e2e/cljs-harness.js`: 417 tests / 1887 assertions, 0
  failures. `long` and `parse-double` behave identically there, which was the open question —
  a missing var in CLJS is a warning, not an error, so compiling proves nothing on its own.

**`cr->label` accepts a Ratio as well as a double**, so it reads the SRD data before *and* after
step 2. Step 3 is therefore not order-coupled to step 2 and either can land first.

Two process notes worth keeping:

- The test was written `.clj` first, so it would never have run in CLJS at all. `.cljc` plus a
  line in `test/cljs/orcpub/test_runner.cljs`.
- Adding the `:require` there is not enough — `-main` runs an explicit `run-tests` list, and a
  namespace missing from it is silently skipped. The count going 412 -> 417 is how you know.

## Step 2 — convert the data

`(/ 1 4)` -> `0.25` in the `challenge-ratings` keys and the 77 fractional monster rows. They are
written as forms rather than `1/4` literals; the semantics and the per-runtime split are the same.

**Acceptance:** step 0's test shows XP values and sort order **unchanged**, plus a new assertion
that a JVM lookup by double now succeeds where it returned `nil`.

**This is the only step with real risk.** A typo in those rows changes a monster's XP with no
error and no visible symptom. Everything before it exists to make that visible.

## Step 3 — collapse the two formatters

`views.cljs:1394` (hardcoded `case`) and `views.cljs:8374` (computed) both call
`display/cr->label`. The builder dropdown's `:on-change` keeps parsing to a number.

**Acceptance:** `lein fig:build` clean; the monster-builder e2e gallery shot unchanged.

## Step 4 — the character side

`?wild-shape-cr` becomes numeric; the druid action summary calls `cr->label`.

**Acceptance:** the summary string pinned in step 0 is byte-identical. The `#_`-discarded Circle
of the Moon, which already sets this attribute to an integer, stops being the odd one out — note
it in the commit, do not un-discard it (it is non-SRD, licensing not incompleteness).

## Step 5 — spec it

A CR spec so homebrew cannot carry a string, wired into the monster save/load specs in
`content_specs.cljc`. There is no spec for `:challenge` today (checked).

**Acceptance:** generative test — a string CR fails, a double passes, and `save ⊆ load` still holds.

## Blast radius

Checked rather than assumed; the table is in the decision doc. Short version: the encounter
builder stores creatures as **keyword references**, not copies, so it never sees a CR. The custom
monster builder already writes doubles. Nothing JVM-side reads CR at all. A 636-monster real
`.orcbrew` pack stores every CR as a plain decimal, so there is no user-data migration.

## Out of scope

Whether the character-side *bound* is stored as CR at all. Polymorph compares a **level** to a CR;
bucket B trades **count against CR** as a table, not a scalar. Those are query-shape questions for
the creature-query decision, not representation questions.
