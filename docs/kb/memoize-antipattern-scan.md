# memoize anti-pattern: scan, trace and plan

**Status: scan and trace complete. NOTHING here is executed.** The builder-freeze fix
([perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md)) removed three sites on
`perf/homebrew-builder-loop`. Everything below is follow-up work for its own branch, because
each change needs its own verification that the app still behaves.

## The defect

`cljs.core/memoize` keeps its cache in a `PersistentArrayMap` and looks it up with `get` --
a **linear scan comparing argument lists with `=`**. So:

- any argument holding a large structure is **deep-compared on every call**, and
- that comparison **walks lazy seqs, realising them**.

The cost lands on the *lookup*, not the cached work, which is why profiles point at whatever
the comparison touches (rendering, template construction) rather than at the cache. The cache
is also unbounded and never evicts.

A memoize is safe only when its arguments are small and cheap to compare: an index, a
keyword, a string, a boolean.

## Scan

43 `(def x (memoize f))` sites parsed across the builder, views, subs, options, events and
entity. 35 take only small arguments (`id`, `key`, `i`, `path`, `event-kw`) and are fine.
Eight take arguments whose names suggest large structures; each was traced:

| Site | Args | What the arg actually is | Verdict |
| --- | --- | --- | --- |
| `entity.cljc:549` `memoized-make-path-map-aux` | `character` | -- | **DEAD** |
| `entity.cljc:663` `memoized-build-aux` | `raw-entity template` | -- | **DEAD** |
| `entity.cljc:733` `memoized-build-template-aux` | `plugins template` | -- | **DEAD** |
| `character_builder.cljs:1442` `remaining-adjustments` | `built-template character` | -- | **DEAD** (caller uses `-fn`) |
| `options.cljc:475` `memoized-spell-option` | `spells-map ... key` | the whole spell library | **FIX** -- measured 10x slower than no cache |
| `character_builder.cljs:469` `make-inventory-item` | `key item-map qty-input-width` | `item-map` is `::equip5e/weapons-map` / `armor-map` / `equipment-map` / `::mi5e/magic-*-map` -- full content maps incl. homebrew, and it is called once per inventory row inside a `map-indexed` | **FIX** |
| `views.cljs:4197` `export-pdf-handler` | `built-char id plugin-data` + 12 flags | `built-char` is the entire built character and `plugin-data` the entire homebrew library -- the largest key in the codebase | **FIX** |
| `views.cljs:2313` `toggle-spell-expanded!` | `expanded-spells k` | `expanded-spells` is an `r/atom`, and reagent's RAtom does not implement `IEquiv`, so `=` on it is reference equality -- O(1) | **LEAVE** (see below) |

### How "dead" was established (not just a grep)

A symbol grep in one directory is not a trace; Clojure has several indirect paths. All four
were checked against every one of them, across `src test dev web scripts`:

- exactly **one hit each** -- their own definition (`remaining-adjustments` has three: the
  `-fn`, the memoized `def`, and a caller that uses the `-fn` directly)
- **no `:refer :all`** anywhere -- all 11 matches are comments warning against it
- **no runtime resolution** reaches them: the 5 `resolve`/`ns-resolve` sites are a protocol
  method named `resolve` in `pdf.clj`, a test resolving `classes/*-option`, and three
  figwheel API lookups in `dev/user.clj`
- **no `defmacro`** in either file, so nothing can expand to these names
- **no `#'` var-quote** references

### Correction: `toggle-spell-expanded!` is not a deep-compare trap

It was listed as "investigate" on the strength of its argument *name*. Traced, its key is
`[<RAtom object>, keyword]` and RAtom comparison is reference equality, so the lookup is O(1).
It is still an unbounded cache -- `expanded-spells` is a fresh `r/atom` per `spells-table`
mount, so entries accumulate across mounts and never evict -- but the entries are tiny
closures and this is not the performance defect. **Do not "fix" it.**

Four of the eight are dead -- defined, never called. That is the cheapest and safest part of
the follow-up. Three of the remaining four are real; the fourth was a false positive from
reading an argument name instead of tracing it.

## `memoized-spell-option` is measurably slower than no cache

Same session, same 75 calls, the only difference being the memoize replaced by a passthrough:

```
memoized      75 calls x 21 ms
passthrough   75 calls x  2 ms      (NOMEMO=1, class_body_cost_e2e.js)
```

Its key includes `spells-map` -- the whole spell library -- so each lookup deep-compares it.
The cache makes the operation **about ten times more expensive** than simply recomputing.

Note this contradicts nothing in the earlier heap analysis: disabling it did *not* reduce
retained heap (41.4 vs 38.5 MB), because retention comes from realised option data held by
the template, not from the cache. Time and memory are separate here; the cache hurts the
first and is irrelevant to the second.

## Plan (for a separate branch)

Ordered by risk. Each step is independently revertible and needs the app exercised, not just
suites run -- these are event handlers, and the suites do not click anything.

**Step 1 -- delete the four dead memoizes.** No behaviour to verify beyond compilation and
suites, since nothing calls them. Confirm with a fresh grep at the time (dead code can come
back to life between branches).

**Step 2 -- `memoized-spell-option`.** Replace with the plain function; it is measurably
faster. Verify: spell selection still lists and picks correctly, and re-measure the class
pick (expect ~21 ms -> ~2 ms on that counter, no change in retained heap).

**Step 3 -- `make-inventory-item` and `export-pdf-handler`.** Both cache a closure keyed on a
large structure. Verify by exercising the feature: add/remove/equip inventory items; export a
PDF and confirm the output is unchanged.

**Step 4 -- `toggle-spell-expanded!`.** Measure before changing: `expanded-spells` may be a
small set, in which case leave it alone. Do not "fix" what is not slow.

## Analysis: what could break

Removing a memoize from a handler factory means a **new closure identity per render**. That
is the only behavioural change, and it matters in three places:

1. **React prop identity.** `:on-click` / `:on-change` change identity every render, so React
   re-attaches the listener. Cheap, and React does this routinely; not a correctness issue.
2. **Reagent re-render skipping.** A component that receives a handler as a prop and relies on
   `=` to skip re-rendering will now re-render. Worth watching for a component that takes a
   handler *and* renders a large list.
3. **Stateful closures.** If a factory closed over mutable state and callers depended on
   getting the *same* closure back, removing the cache changes semantics. None of the sites
   above do this -- they all return a fresh `(fn [e] (dispatch ...))` -- but check each one
   rather than assuming.

The three sites already fixed were verified: JVM 309/1704 and CLJS 240/726 green, and the
freeze gone on dev and prod. Because neither suite clicks anything, the handlers themselves
are covered by `test/browser/class_handlers_functional_e2e.js`, which drives set-class,
set-class-level, add-class and delete-class for real and asserts app-db afterwards. All five
checks pass:

```
set-class switches to Wizard        [["wizard",1]]
set-class-level sets 5 levels       [["wizard",5]]
add-class adds a second class       [["wizard",5],["artificer",1]]
delete-class removes it again       [["wizard",5]]
built-character still derives       keys=120
```

## Step 3 outcome: measured, and mostly NOT worth doing

**`make-inventory-item` — leave it.** Measured on the Equipment tab at 4x throttle
(`equipment_tab_cost_e2e.js`, mega-64):

```
1. -> Equipment   wall 570ms   longest 375ms   makeInventoryItem 7x1ms
2. -> Equipment   wall  70ms   longest 300ms   makeInventoryItem 7x0ms
3. -> Equipment   wall  99ms   longest 307ms   makeInventoryItem 7x1ms
```

Seven calls, ~1 ms. The key shape is wrong but it is called once per *selected* item, not
once per available item, so the deep comparison happens a handful of times. Nothing to win.
**A wrong-looking key is not a defect until the call count makes it one** — that is the
difference between this and `set-class`, which ran 141 times per render.

**`export-pdf-handler` — unmeasured, unchanged.** It lives on the character page
(`views.cljs:4374`), which needs a saved character behind auth, so it cannot be measured with
the builder probes. Its key is the worst in the codebase (the whole built character plus the
whole homebrew library) but it is one call per render of the export button, and the
`make-inventory-item` result shows call count decides. Left documented rather than changed
blind: this is the PDF export path, and a broken export is worse than a slow one.

### Separate observation, not this branch's scope

The Equipment tab's longest task is 300-375 ms against Race's 160-197 ms. Something there is
slow and it is **not** `make-inventory-item`. Worth its own investigation if the tab is ever
reported as sluggish.

## Known gap: no click-level coverage for spell picking

`class_handlers_functional_e2e.js` covers the class handlers. Spell picking is **not**
covered: two attempts failed identically on the pre-change build (0 -> 0), proving they were
probe bugs rather than regressions, but neither actually exercised picking a spell — the
first `.b-orange` card on the Spells tab is not a spell option. Closing this needs someone
familiar with that view.

Step 2's safety rests on the other three legs instead: `spell-option` is pure and its result
closes only over values derived from its own arguments; both suites pass; and the measurement
shows the counter dropping 21 ms -> 1 ms with no behavioural change.

## Related

- [perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md) -- the freeze and its fix
- [reagent-architecture-tenets.md](reagent-architecture-tenets.md) -- what to use instead
- [verification-discipline.md](verification-discipline.md) -- how to measure a claim like this
