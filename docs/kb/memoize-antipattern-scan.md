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

| Site | Args | Traced | Verdict |
| --- | --- | --- | --- |
| `entity.cljc:549` `memoized-make-path-map-aux` | `character` | **no call sites** | DEAD -- delete |
| `entity.cljc:663` `memoized-build-aux` | `raw-entity template` | **no call sites** | DEAD -- delete |
| `entity.cljc:733` `memoized-build-template-aux` | `plugins template` | **no call sites** | DEAD -- delete |
| `character_builder.cljs:1428` `remaining-adjustments` | `built-template character` | callers use `remaining-adjustments-fn` directly (line 1431) | DEAD -- delete |
| `options.cljc:475` `memoized-spell-option` | `spells-map ... key` | hot; **measured 10x slower than not caching** | FIX -- see below |
| `character_builder.cljs:455` `make-inventory-item` | `key item-map qty-input-width` | called per inventory row (line 494) | LIKELY FIX |
| `views.cljs:4143` `export-pdf-handler` | `built-char id plugin-data ...` | called on character-page render (line 4274) | LIKELY FIX |
| `views.cljs:2304` `toggle-spell-expanded!` | `expanded-spells k` | called per spell row (line 2363) | INVESTIGATE |

Four of the eight are dead code -- defined, never called. That is the cheapest and safest
part of the follow-up.

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

## Related

- [perf-homebrew-builder-loop.md](perf-homebrew-builder-loop.md) -- the freeze and its fix
- [reagent-architecture-tenets.md](reagent-architecture-tenets.md) -- what to use instead
- [verification-discipline.md](verification-discipline.md) -- how to measure a claim like this
