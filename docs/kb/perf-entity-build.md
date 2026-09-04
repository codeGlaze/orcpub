# `entity/build`: what it actually costs, and why

**Status: measured. The cost is real in the browser, and it is one quadratic loop.**

Headline: **~74% of `entity/build` is `no-incoming`, a helper inside `kahn-sort`** that
recomputes the whole graph's incoming-edge set once per node. The sort is O(V·(V+E)) where
it should be O(V+E). Nothing else in `build` is close.

---

## 1. Is it actually slow in the browser? Yes.

The brief's first unknown was whether the 16.6 ms JVM number transfers to JS. It does, and
it is worse. Measured against the real stack (`lein e2e-server`, in-memory Datomic) driving
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
