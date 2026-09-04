# Investigation brief: `entity/build` is 16–22 ms and dominates every character change

**Status: not started. This doc is the handoff — read it before touching anything.**

## The finding, and how it was found

While deciding whether to optimise the AC outer loop, the AC search turned out to be **4.4% of a
loadout toggle**. The other 95% is `entity/build`:

| | |
|---|---|
| `entity/build` (a barbarian/monk with a homebrew race) | **16.6–22.5 ms** |
| AC search across a 12-armor, 2-shield wardrobe (39 combos) | 0.73 ms |

Reproduce with `loadout-toggling-is-dominated-by-the-character-rebuild` in
`test/cljc/orcpub/dnd/e5/ac_reconciliation_test.clj`, which prints both numbers. Measured on the
JVM; the app runs this in JS, so treat the **ratio** as transferable and the absolute numbers as
not. **Re-measure in the browser before optimising anything** — see Unknowns.

`entity/build` runs on every character change: `subs.cljs:316`, `events.cljs:312,363,497`,
`views.cljs:1961`.

## Three concrete leads

**1. Three memoized wrappers exist and NOTHING calls them.**

| defined | callers |
|---|---|
| `entity.cljc:618` `memoized-build-aux` | **0** |
| `entity.cljc:628` `memoized-make-modifier-map` | **0** |
| `entity.cljc:693` `memoized-build-template-aux` | **0** |

`build` (620) calls the un-memoized `build-aux`; `build-template` (695) calls the un-memoized
`build-template-aux` *and* `t/make-modifier-map` directly. Someone built this optimisation and
never wired it, or deliberately unwired it. **Find out which before wiring it** — `memoize` on a
function taking a whole character is an unbounded cache keyed by a large value, which is a memory
leak if the key changes on every keystroke. That may be exactly why it was abandoned.

**2. The slowness is already known and worked around, not fixed.** `subs.cljs:318-345` wraps
`entity/build` in a hand-rolled 500 ms leading+trailing debounce, with the comment "rapid keystrokes
batch". That is a mitigation for this cost. A real fix might let that debounce shrink or go — but
the debounce is load-bearing today, so do not remove it speculatively.

**3. Nothing has been profiled below the top level.** The 16.6 ms is `build` as a whole. The split
between `apply-options`, modifier application, and entity-spec fn construction is unmeasured. Start
there; do not guess.

## Ground rules

- **Characterization first.** This codebase's refactor discipline is: pin what IS with a test,
  then flip deliberately as a visible diff. A perf change that alters a computed number is a bug,
  so the safety net is the existing suite — `lein test` must stay at **1 failure**
  (`audit-specs-match-the-registry`, pre-existing and unrelated) with the **AC parity sweep at 0
  divergences**. If that sweep moves off 0, stop.
- **Measure, do not reason.** Benchmarks here must warm the JIT first — a run without warmup
  produced a non-monotonic curve and the opposite conclusion. And time things rather than counting
  operations: an earlier decision on this codebase was nearly made on evaluation counts, which
  turned out to be a poor proxy for cost. See `verification-discipline.md`.
- **Find the crossover.** "Faster" is not a result; "faster above N, slower below, and real
  characters are always under N" is.
- Full stack runs locally with `lein e2e-server` (in-memory Datomic, no transactor) — see
  `CLAUDE.md`. Use the real app for browser measurement; do not drive re-frame events directly.

## Unknowns worth resolving early

- **Is this actually slow in the browser?** JVM and JS differ enormously on allocation and
  megamorphic dispatch. The whole investigation is moot if the JS number is 2 ms.
- **How often does `build` really run per user action?** The debounce means it is not once per
  keystroke, but the subscription graph may re-run it more than expected.
- **Why were the memoized wrappers abandoned?** `git log -S memoized-build-aux` is the first thing
  to run.

## Where the useful context lives

`docs/kb/built-character-representation.md` (the built character is NOT a flat map — derived values
are deferred fns read with `es/entity-val`) · `docs/kb/armor-class-refactor.md` (the discipline this
branch's work followed, and the measurement mistakes made along the way) ·
`docs/kb/verification-discipline.md` (benchmark rules).
