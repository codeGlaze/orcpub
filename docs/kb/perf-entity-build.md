# `entity/build`: what it actually costs, and why

**Status: measured, then fixed. `kahn-sort` was O(V·(V+E)); it is now O(V+E).**

> Ported to `perf/homebrew-builder-loop` (off `integration`), where it turned out to be
> 76–86% of the character rebuild at *every* homebrew size — see
> `perf-homebrew-builder-loop.md`. The numbers below are from `perf/entity-build`, whose
> fixture is a different character; the integration numbers are in that doc.

Headline: **74% of `entity/build` was `no-incoming`**, a helper inside `kahn-sort` that
recomputed the whole graph's incoming-edge set once per node. Replacing that with
decremented in-degrees, with the resulting node order proved identical in both runtimes:

| | before | after | |
|---|---|---|---|
| `entity/build`, browser microbench (live character + template) | 25.2 ms | **4.9 ms** | 5.1x |
| `entity/build`, JVM (`[LOADOUT]` probe) | 23.02 ms | **3.04 ms** | 7.6x |
| `kahn-sort`, browser | 19.8 ms | **0.87 ms** | 23x |
| `kahn-sort`, JVM | 20.4 ms | **0.62 ms** | 33x |
| `entity/build` share of non-idle CPU during real race clicks | 57% | **26%** | |

No crossover: the new sort is at least as fast at every graph size measured, down to n=2.
The remaining cost is `collect-modifiers-2` (3.4 of the 4.9 ms) — see *What is left*.

---

## 1. Is it actually slow in the browser? Yes.

The brief's first unknown was whether the 16.6 ms JVM number transfers to JS. It does, and
it is worse. All numbers in this section are **before** the fix. Measured against the real stack (`lein e2e-server`, in-memory Datomic) driving
the real character builder in headless Chromium — clicking race cards, no re-frame poking.

| measurement | value |
|---|---|
| `entity/build` per race click (wall clock, unprofiled) | **25–37 ms**, ×2 calls = **53–73 ms** |
| `entity/build` subtree, CDP CPU profile over 8 real clicks | 654 ms / 8 = 82 ms per click |
| `kahn_sort` subtree | 515 ms = **79% of build** |
| `no_incoming` | 482 ms = **74% of build** |
| `collect_modifiers_2` | 117 ms = 18% of build |
| `apply_modifiers` | 7.8 ms = 1% of build |
| share of all non-idle CPU during those clicks | **57%** |

The profiled number (82 ms/click) is higher than the unprofiled wall clock (53–73 ms)
because sampling at 50 µs costs something. Trust the wall clock for magnitude and the
profile for the split. Either way this is 3–4 dropped frames per click.

JVM, same character, for comparison — `lein test orcpub.dnd.e5.ac-reconciliation-test`:

```
[LOADOUT] character rebuild 23.02 ms | AC search over 12 armors x 2 shields (39 combos) 1.09 ms
```

After the fix the same probe reads `3.04 ms`, and the AC search — 4.7% of a toggle before —
is now 36% of one. Nothing about the search changed; the thing it was being compared against
got 7.6x cheaper.

## 2. Where the time goes (JVM phase split of `apply-options`)

Each phase timed standalone, warmed, min of 5 reps × 40 iterations:

```
entity/build (whole)                       22.308 ms
1 flatten-options                           0.003
2 collect-modifiers-2 (TOTAL)               1.149      2a make-path-map 0.047
3 sort-by ::mods/order                      1.191      2b get-all-selections-aux-2 0.519
4 deps reduce                               0.011      2c make-template-option-map 0.485
5 kahn-sort                                20.405   <-- 91%
6 order-modifiers                           0.062
7 mods/apply-modifiers                      0.078

graph: 109 keys / 121 edges -> 122 nodes after normalize
```

The browser agrees on the shape (79% vs 91%); JS spends relatively more in
`collect-modifiers-2`.

`kahn-sort` calls `no-incoming` **once per node** — 123 times for this graph — and each
call rebuilds `(set (keys g))` and `(apply union (vals g))` over the *entire* graph. One
`no-incoming` costs 0.184 ms on the JVM and 0.311 ms in the browser; 123 × 0.184 = 22.6 ms,
i.e. essentially all of `kahn-sort`.

Confirmed quadratic on synthetic chain graphs — each doubling of n quadruples the time:

```
n=  25    1.14 ms
n=  50    4.40 ms   (3.9x)
n= 100   16.98 ms   (3.9x)
n= 200   69.60 ms   (4.1x)
n= 400  267.46 ms   (3.8x)
```

This matters beyond the test fixture: the graph is `(merge-with union modifier-deps
(::es/deps base))`, and the base's dep set grows with the template's content. Quadratic
means a bigger character costs disproportionately more.

## 3. How often does `build` run per user action? Twice.

Counted by wrapping `orcpub.entity.build` with a timing shim and driving the real UI:

```
click race Elf         builds=2  total= 52.7 ms  each=[25.1, 27.6]
click race Half-Orc    builds=2  total= 54.2 ms  each=[28.4, 25.8]
click race Tiefling    builds=2  total= 73.1 ms  each=[36.5, 36.6]
open Class/Level tab   builds=0
open Equipment tab     builds=0
open Race tab          builds=0
```

Tab switches are free — good. But every character change builds **twice**, and the reason is
the debounce itself (`subs.cljs:325-347`): `on-change` is registered as a watch on **both**
`char-sub` and `tmpl-sub`, and a race click changes both (`:built-template` derives from
`:selected-plugin-options`, which derives from the character). The first watch fires the
leading edge and builds immediately; the second sees <500 ms since `last-run` and schedules a
*trailing* build that lands 500 ms later. So the user pays for the same build twice, half a
second apart.

Recorded, not fixed. The debounce is load-bearing and the doubling is a second-order cost
next to the quadratic sort; de-duplicating it is a separate change with its own risk.

## 4. Why the memoized wrappers were abandoned

`git log -S` on a **shallow** clone gave the wrong answer (it reported only the branch's own
commits). After `git fetch --unshallow`, the history is unambiguous:

| commit | date | what it did |
|---|---|---|
| `bfc98d44` "memoize some slow functions in the entity namespace" | 2017-03-06 | wired `memoized-make-modifier-map` + `memoized-build-template-aux` |
| `8ef1731c` "add weapons to pdf" | 2017-03-22 | wired `memoized-build-aux` into `build` |
| `ca145061` "finish basic monster builder" | 2017-11-12 | **unwired** `memoized-build-aux` and `memoized-make-modifier-map` |
| `57be9085` "fix ranger favored enemy selection" | 2017-11-13 | **unwired** `memoized-build-template-aux` and `memoized-make-path-map-aux` |

They were wired, then deliberately unwired — and `57be9085` unwired two of them inside a
**bug-fix** commit whose subject is a specific wrong selection result. That is the signature
of a stale-cache bug, not of an abandoned experiment. Nobody left a note, so "the memoization
was returning stale results" stays a strong inference rather than a proven cause; what *is*
proven is that removing them was deliberate and change-of-behaviour motivated.

There is also a 2026 attempt to re-wire `memoized-build-aux` — `56ad86fb` "Optimize character
builder performance to fix freezing with large custom content", plus `0c5c7628` documenting
it in `PERFORMANCE_LESSONS.md`. **It never landed.** Both live only on
`origin/claude/app-speed-fix-K8HwV`, are not ancestors of this branch, and
`PERFORMANCE_LESSONS.md` does not exist in the tree here.

**Conclusion: do not wire them.** Beyond the history, the objection in the brief stands —
`memoize` on a function taking a whole character map is an unbounded cache keyed by a large
value. And it is now moot: the cost is a quadratic loop, and fixing the loop is cheaper,
safer, and helps the cold path too, which no cache does.

## 5. The order produced by `kahn-sort` is load-bearing

`apply-options` feeds the sort's result to `order-modifiers` (`entity.cljc:419`), which sorts
the modifiers by their key's index in it. Two modifiers writing the same key are applied in
that order. So a *different but still valid* topological order can change a computed value —
a faster sort that reorders is a behaviour change, not a speedup.

`test/cljc/orcpub/entity_build_perf_test.clj` pins this. It keeps a verbatim copy of the
pre-rewrite `kahn-sort` as `reference-kahn-sort` and asserts the production one returns
exactly `=` to it (vectors — order-sensitive) on the real build graph, 500 seeded DAGs, 300
seeded cyclic graphs (both must give `nil`), and the degenerate shapes. One assertion does
not lean on the reference at all: every edge must point forward in the result.

## 6. The fix, and the trap in it

`kahn-sort` now decrements an in-degree map as it consumes edges, instead of recomputing
`no-incoming` over the entire residual graph once per node. Same algorithm, same node
choice — `(first s)` of the same frontier set at every step — different bookkeeping.

**The trap.** Reproducing the order exactly is harder than "compute the same members." The
frontier is a set and the next node is `(first s)`, so the *set's iteration order* picks it.
On the JVM that order is a pure function of the contents (hash-ordered), but **in
ClojureScript a set of <= 8 elements is `PersistentArrayMap`-backed and iterates in
insertion order.** A first attempt built the frontier addition with `(into #{} (filter ...) m)`
instead of `clojure.set/intersection`. It passed everything on the JVM — the real build
graph, 500 random DAGs, 300 cyclic graphs, the whole suite, the AC parity sweep at 0 — and
it also matched on the live browser character. Running the same randomized comparison
*inside the browser* showed **159 divergences out of 808 graphs**.

The live character matching was luck, and a green JVM suite could not have caught this. It
is `verification-discipline.md` lesson 5 (JVM-isms in `.cljc` only surface in an actual cljs
run) arriving from the other direction: a **cljs-ism**, invisible to `lein test`.

The shipped version instead inlines `clojure.set/intersection`'s own two branches, with the
in-degree map standing in for the no-incoming set: `(contains? no-incoming x)` is exactly
`(zero? (indeg x))`, and a maintained `zero-count` picks the same branch `intersection`
would have. The second branch needs the set materialized, so it is built there; it is
reachable only when a node's out-degree exceeds the number of nodes already emitted, which
real dependency graphs do not do.

### How the order was verified

| check | runtime | result |
|---|---|---|
| real build graph, node for node vs the pre-rewrite implementation | JVM | identical (hash `1260276325`) |
| 500 seeded DAGs + 300 seeded cyclic graphs + 8 degenerate shapes | JVM | 0 divergences |
| the same 808 graphs, pre-rewrite implementation re-run in the page | CLJS | 0 divergences |
| live build graph in the running app | CLJS | identical |
| 11 real characters driven through the real UI: sort output **and** computed AC / HP / initiative / proficiency / speed | CLJS | byte-identical before vs after |
| independent check: every edge points forward in the result | JVM | holds |
| `lein test` | JVM | 451 tests, **1 failure** (`audit-specs-match-the-registry`, pre-existing) |
| AC parity sweep | JVM | **0 divergences** |
| `lein lint` | — | 5 errors / 38 warnings, identical before and after; none in the changed files |

The JVM checks live in `test/cljc/orcpub/entity_build_perf_test.clj` and run under
`lein test`. The CLJS checks were run against `lein e2e-server` through the real UI; they
are not automated, because there is no cljs test runner wired into the gate. **If
`kahn-sort` is touched again, re-run the browser comparison — the JVM suite cannot see this
class of bug.**

## 7. Measured after the fix

Browser, CDP profile over 8 real race clicks (same script, same actions):

| | before | after |
|---|---|---|
| `entity.build` subtree | 654 ms (82/click) | 159 ms (**19.8/click**) |
| `kahn_sort` subtree | 515 ms (64/click) | 30 ms (**3.7/click**) |
| `collect_modifiers_2` | 117 ms | 108 ms |
| idle | 83.2% | **91.0%** |

Wall clock through the timing shim, per real race click (two builds each, as in section 3):
53-73 ms before, **15-32 ms** after.

No crossover, in either runtime — the new sort wins at every size measured:

```
chain n     JVM before   JVM after    CLJS before   CLJS after
     2        0.012 ms    0.011 ms       0.010 ms     0.009 ms
     8        0.148       0.053          0.120        0.057
    32        1.934       0.191          1.920        0.253
   128       26.631       0.699         26.000        0.947
   256      110.656       1.464        103.040        1.950
```

## 8. What is left, and what was left alone

`collect-modifiers-2` is now the largest piece of `build` — 3.4 ms of 4.9 ms in the browser
(69%), split roughly evenly between `get-all-selections-aux-2` and `make-template-option-map`.
Not investigated. It is ~3 ms, not ~20, and it is not obviously quadratic.

Deliberately not done:

- **The memoized wrappers stay unwired.** See section 4. The history says they were removed
  on purpose, twice, once inside a bug fix; and the cost they were meant to hide is now gone.
- **The 500 ms debounce stays.** It is load-bearing and the brief said not to remove it
  speculatively. Whether 500 ms is still the right number for a 5 ms build is a question
  worth asking, with a UI check, not a change to make blind.
- **The double build per change stays.** Section 3. It is a real doubling and the cause is
  identified (two watches on one debounce), but de-duplicating it is a change to the
  subscription's semantics and belongs in its own commit with its own test.

## Ground rules (unchanged)

- **Characterization first**, then flip as a visible diff.
- Safety net: `lein test` stays at **1 failure** (`audit-specs-match-the-registry`,
  pre-existing and unrelated) and the **AC parity sweep at 0 divergences**.
- **Measure, do not reason.** Warm the JIT. Time things; do not count operations.
- **Find the crossover.**
- Browser numbers come from the real stack (`lein e2e-server`) and the real UI.

## Where the useful context lives

`docs/kb/built-character-representation.md` (the built character is NOT a flat map — derived
values are deferred fns read with `es/entity-val`) · `docs/kb/armor-class-refactor.md` ·
`docs/kb/verification-discipline.md` (benchmark rules).
