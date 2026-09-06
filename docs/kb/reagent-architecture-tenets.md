# Architecture tenets for this re-frame / reagent app

Derived from the homebrew-performance investigation (`perf-homebrew-builder-loop.md`), where
a 2.15-second freeze and a 4x heap came almost entirely from violating the first two of
these. Each tenet names the concrete thing in this codebase that breaks it, so they are
checkable rather than aspirational.

## 1. Build view markup in the view, never in the data layer

Hiccup is a description of DOM. Anything that builds hiccup outside a component has put it
permanently beyond React's reach — React cannot decline to render something that was already
built.

*Broken by:* `spell-option` (`options.cljc:439`) calls `(spell-help spell)` at
template-build time. `spell-help` (`:406`) renders the spell's **entire** description into a
`[:p]` per paragraph, for every spell, whether or not anyone ever opens the peek. Measured:
78% of the cost of building a spell option, and — via the class-keyed memo — the same 258 KB
of description text rebuilt and retained once per class.

*The rule:* the data layer holds the spell. The component turns it into markup when it is on
screen.

## 2. Derive lazily; do not materialise the world and filter afterwards

*Broken by:* `class-option` (`options.cljc:2732-2750`) builds spell selections for every
level 1–20, then attaches a `::t/prereq-fn` that hides the ones the character has not
reached. The gate exists — it just runs after all the work. A level-1 character pays for
twenty levels of every class in the library.

*The tell:* a `prereq-fn`, `filter`, or `when` applied to something that was expensive to
construct. Gate the construction, not the display.

## 3. Keep subscriptions granular

One subscription computing one enormous value means any input change rebuilds all of it, and
none of it can be partially reused.

*Broken by:* `::char5e/template-selections` (`equipment_subs.cljs:309`) takes twelve inputs
and produces the entire builder template as a single value. A subscription keyed per spell,
or per class, lets re-frame's caching do real work instead of all-or-nothing.

## 4. Memoize on the smallest correct key, and bound the cache

`clojure.core/memoize` never evicts: everything it builds lives as long as the page.

*Broken by:* `memoized-spell-option` (`options.cljc:469`) includes **class name** in its key.
The most expensive part of what it builds — `:help` — depends only on the spell. So a value
that should exist once per spell exists once per (spell x class): 41,470 objects and 2.39
million hiccup nodes at 130 classes.

*The rule:* if part of a memoized value does not depend on part of the key, that part is in
the wrong place.

*And prefer not to hold the cache yourself.* re-frame already manages subscription lifetime —
`subs.cljc` `cache-and-return` registers `add-on-dispose!` on each cached subscription, and
disposes it when nothing subscribes. A top-level `(def x (memoize f))` opts out of that
entirely: it is a global map the framework cannot see into or reclaim. Deriving the value in
a subscription keyed by the thing it varies with gets eviction for free, with no LRU to
hand-roll — which matters here because `clojure.core.cache` is `.clj`-only and unavailable to
shared `.cljc`.

## 5. Virtualise long lists

200 homebrew subraces is 200 cards in the DOM, each with its own props and reconciliation
cost, whether or not they are on screen.

## 6. Optimise the longest task, not the total

Three seconds spread over sixty tasks is invisible. **2.15 seconds in one task is a frozen
tab** — no paint, no input, nothing. Totals and averages hide exactly the thing users
report.

*Measure it with* `PerformanceObserver` longtask entries, not wall clock around an action.
Heavy work belongs chunked or behind an idle callback so the browser can paint between
pieces.

## 7. Reagent: reactions dedupe on `=`, so returning fresh-but-equal values defeats caching

A subscription that rebuilds an equal-but-not-identical map on every call forces every
downstream reaction to re-run.

*Related, and measured:* the `built-character` debounce (`subs.cljs:325`) registers its
watch on **both** `char-sub` and `tmpl-sub`. A race click changes both, so one watch fires
the leading-edge build and the other schedules a trailing one — **every character change
costs two full rebuilds**, half a second apart.

*Prefer* `reg-sub` with declared signals over `reg-sub-raw` with hand-rolled watches;
the machinery already de-duplicates what hand-rolled watches do not.

## 8. Load-bearing order must be pinned by a test, not assumed

Where output order feeds behaviour, an "equivalent" rewrite is a behaviour change.

*Example:* `entity/kahn-sort`'s node order becomes modifier application order via
`order-modifiers`, so two valid topological orders are not interchangeable. Pinned in
`entity_build_perf_test.clj` (JVM) **and** `kahn_sort_order_equivalence_e2e.js` (cljs — sets
of <= 8 elements iterate in insertion order there and the JVM cannot see that class of bug).

---

## What to do instead of reaching for another cache

Nothing in this app is network-bound. The whole library is in browser memory the moment it
loads, so **every millisecond of a freeze is self-inflicted computation** — there is no I/O
to hide behind, and "instant" is achievable in principle.

That reframes what the caching here is actually doing. The builder constructs every class's
every level's every spell option up front, then memoizes the results so it never has to redo
it. **The cache is compensating for eager work.** Build only what is on screen and there is
almost nothing left worth caching.

So the pathology is not "memoize bad" — most of the ~45 memoize calls in this codebase are
correct (see below). It is *cache-as-substitute-for-not-doing-the-work*.

Ranked by what the measurements in `perf-homebrew-builder-loop.md` support:

1. **Do less rather than cache more.** 130 classes' spell options exist only because
   everything is built. Build the selected class's and the problem and its cache both go away.
2. **Let re-frame own derived state.** Subscriptions are caches with dependency tracking and
   disposal. Every hand-rolled cache is a subscription somebody did not write.
3. **Index once at import, not per render.** The cost is not fetching the data, it is
   reshaping it into view structures. A class -> spell-keys index built once when content
   loads makes lookups O(1) with nothing to evict.
4. **Virtualise long lists.** 200 homebrew subraces is 200 cards in the DOM. *Unmeasured
   here* — listed for completeness, not on evidence.
5. **Yield.** A 2.1 s block is one unbroken task; the same work chunked across frames is
   invisible even at an identical total. Often the cheapest "feels instant" win available.
6. **Keep measuring the longest task, not the total.** 3.4 s over 50 tasks and 31.9 s over
   138 are the same app on different hardware; only the second reads as chuggy.

### When memoize IS right, so this is not read as "rip it out"

An audit of every `memoize` in `src/` found ~45 of them are handler memoization:

```clojure
(defn set-class-fn [i options-map]
  (fn [e] (dispatch [:set-class (keyword (.. e -target -value)) i options-map])))
(def set-class (memoize set-class-fn))
```

That returns the **same function identity** across renders. Without it every render hands
React a fresh closure, React sees a changed prop, and the subtree re-renders. It is the CLJS
equivalent of `useCallback` and it cooperates with Reagent. Removing these would make the app
worse.

The distinction is the key space, not the technique:

| memoizing | key space | verdict |
|---|---|---|
| a callback, keyed on UI position | closed | correct, keep |
| data keyed on SRD content (12 classes, 319 spells) | closed | fine — a lookup table that fills once |
| data keyed on **user-imported** content | **open** | needs a lifetime, not a global map |

`memoized-spell-option` is the third row. It was the second row when it was written — the app
was SRD-only. Homebrew changed the premise; the code did not. Not a bad decision, an expired
one.

## Related

- [perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md) -- the freeze these tenets
  predicted: three `cljs.core/memoize` sites keyed on the whole class library, whose cache
  LOOKUP (an array-map linear scan comparing arg lists with `=`) cost ~1 s per render.
- [verification-discipline.md](verification-discipline.md) -- how to measure a claim like
  this instead of arguing it.
- [README.md](README.md) -- KB index.
