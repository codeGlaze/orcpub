# Fail-soft rendering & fault isolation (character display)

How the character view avoids black-screening and, when something does break,
tells the user *which builder choice* to fix. Landed on branch
`feature/fix-black-screen-of-death` (off `develop`). All claims here were verified live
(figwheel build on 8890) on a real Ranger 15 / Hunter / Evasion character, not by
reading alone. Function names (not line numbers) are used as anchors — verify
against `src/cljs/orcpub/dnd/e5/views.cljs` and `web/cljs/orcpub/core.cljs`.

## The goals, in priority order (this ordering drove the design)

1. **Never black-screen.** A render throw must not unmount the app.
2. **Always surface an error** so user/dev knows something broke.
3. **Make the error useful** — point at the thing to fix.

These map onto nested React error boundaries (below). #1 is a placement problem,
not a cleverness problem; #3 is where the isolation work lives.

## Layered error boundaries — why three, not one

`error-boundary` (in views.cljs) is a Reagent `create-class` using React 18
`getDerivedStateFromError` (the only hook that re-renders to a fallback — a
`componentDidCatch`+atom reset does NOT, confirmed at runtime). It also stashes the
React `componentStack` in state so the fallback can show *which component* threw.
Fallback is called `(fallback error component-stack retry)`.

React catches at the **nearest** boundary, so placement controls blast radius:

| Boundary | Wraps | On throw, replaces | Message |
|---|---|---|---|
| `render-guard` (per item) | one feature in `actions-section` | just that item | dumps the item's raw data (`guard-fallback`) |
| per-tab boundary | the active tab view in `character-display` | just that tab's content | `feature-render-error`: names the section + (Features) the selection trace |
| **app-root** boundary | the whole page (`[view]` in `core.cljs/main-view`) | the whole page | `app-error-fallback`: generic "something went wrong" + reload |

**Why all three:** the root boundary alone satisfies #1 but makes #2/#3 worse — a
Features nil-name would blow away the entire page with a generic message. The inner
boundaries catch closer, so the smallest possible region dies and the most specific
message shows. Keep them nested; key each to its content (tab key / route) so
navigating away clears a stuck error.

**Coverage history (important):** before the root boundary, ONLY the active tab was
wrapped. So the original Features bug was fixed, but a throw in the summary, the
2-column left pane (`summary-details`), the banner, or the tab bar still
black-screened. The app-root boundary in `main-view` closed that. Still outside any
boundary: `main-view`'s own 3 lines (`:route` subscribe + page lookup) and the
import-log overlay — tiny/stable.

## Fault isolation by re-execution — why, and the two levels

A **stack trace cannot point at the offending datum.** For the nil-name sort, every
frame is framework (`clojure.string/lower-case` → `sort_by` → `goog.array.sort`);
by the time it throws, the bad value is an anonymous element in a comparator. The
only way to find *which* datum/choice is to **re-run with things removed and see
what stops the failure.** Two levels:

### Item level — `isolate-culprit` (used by `guarded-feature-list`, wired into `actions-section`)
- `render-coll` renders a whole collection (the sort is eager, so it throws when
  called). Re-run it over subsets to find the culprit item.
- Uses **leave-one-out** ("is this item *necessary* for the failure?"), not "does
  this item fail alone?" — because a nil sort key never throws alone (sorting one
  element compares nothing).
- **The 2-element trap (real bug we hit):** with exactly `[bad, good]`, removing
  *either* drops below the 2 elements the comparator needs, so leave-one-out calls
  *both* necessary → it falsely flagged the innocent neighbour. **Fix:** disambiguate
  by testing each suspect paired with a copy of itself (`[x x]`) — only the item
  carrying the bad value still throws.

### Selection level — `isolate-culprit-selection` (the actionable one)
- The **selection** (builder choice) is what the user re-picks, so it's the unit
  that matters. `entity/build` is a **pure** fn of `(character, template)`, so:
  prune a selection from `::entity/options`, rebuild, re-test with a `fails?`
  predicate; the **deepest** selection whose removal clears the failure is the
  culprit. `prune-sites` walks the options tree (maps + vectors) to enumerate
  removable sites; `culprit-selection-label` turns the result into a breadcrumb via
  `common/kw-to-name` + title-case.
- Verified: on a real Ranger 15 / Hunter / Evasion it returns
  `{:sel :superior-hunters-defense :choice :evasion}` → label
  *"your Ranger → Superior Hunters Defense: Evasion."* Pruning that selection (or its
  ancestors) clears it; pruning an unrelated level does not (control).
- The label is **derived, not typed** — the apostrophe-less "Hunters" (from the
  keyword `:superior-hunters-defense`, not the book name "Hunter's") is the tell.

Both are used by `feature-render-error` (the per-tab panel, Features section) and
`character-health-warning` (the proactive banner). The banner's re-test is
`has-nameless-feature?`; the panel's is `features-section-fails?` (the name sort).

## Safe building blocks (prevent the crash + surface it, in common.cljc)

These keep the common cases from ever reaching a boundary, and surface the bad
data instead of hiding it:

- `common/lower-case` — crash-safe case fold: `(s/lower-case (str x))`. Core
  `s/lower-case` calls `.toLowerCase` and dies on a non-string; this coerces first,
  so a bad sort/compare key folds to `""` instead of crashing. `aloof-sort-by` and
  `get-plugin-names` use it. **Don't redefine core `lower-case`** (global, surprising)
  — this is a wrapper with a different name.
- `common/feature-name` — display name with an obvious placeholder. Missing/blank
  name → `"[Unnamed feature]"` (shown in place and sorted deterministically, not a
  silent blank). A wrong-typed name (not a string) is a real bug: dev throws (via
  `goog.DEBUG`) so it surfaces at once, prod coerces and carries on. Use it for both
  the rendered name and the sort key so they agree. The placeholder is a better
  surface than the banner — it's in-place and unmissable.

Layering: `feature-name` handles the missing/blank-name case before it can crash;
`common/lower-case` is the backstop for any other non-string key; the boundaries
catch whatever's left.

**Coerce vs throw — the rule (don't get this backwards):** the risk is *any* fold of
a value where a string is expected but it might be missing/non-string. The response
splits on whether a non-string is acceptable *there*:
- acceptable (sort/compare keys — `:level` is a valid non-string key) → **coerce**,
  via `common/lower-case`. It never throws, on purpose; it can't know a non-string is
  wrong.
- a genuine bug (a *name*; a semantic transform like `name-to-kw`) → **don't silently
  coerce.** Throw in dev (as `feature-name` does) or guard the caller, so it surfaces.

So the dev-throw belongs in `feature-name`, not in the generic fold — putting it in
`lower-case`/`aloof-sort-by` would flag valid `:level` sorts and break their tests.

## Verified facts / gotchas (so nobody re-derives them)

- **The crash surface is narrow: cljs tolerates most bad/nil data, but string ops
  throw on non-strings.** `+`/`<`/`pos?`/`count`/`get`/`mod-str` on nil → coerce or
  nil-pun (no throw); `clojure.string/*` (e.g. `lower-case` → the sort) and `name`
  throw on a non-string. So a nil skill bonus renders blank, but a nil name crashes
  the sort. Full table + the why in the human doc `docs/clojurescript-type-tolerance.md`
  (on the feature branch).

- **Homebrew import renames missing trait names to `"[Missing Trait Name]"`.** So
  imported content can't reach the renderer truly nameless — only built-in data can.
  The nameless-feature detector therefore mostly fires on built-in bugs.
- **A built-in `mod5e/trait-cfg` conjes its literal cfg map onto `:traits`.** The
  Hunter Evasion trait is just `{:page 93 :summary "…"}` — **no `:class-key`, no
  `:level`**. So the locator for a built-in nameless feature is weak (page only);
  the rich class/level breadcrumb comes from the *selection* trace, not the trait.
  (Imported traits *do* carry class/level/source from the import.) See
  [modifier-vs-trait-slots.md](modifier-vs-trait-slots.md).
- **The selection trace is Features-only right now.** Other sections get the panel
  (section name + copy-able error) but no trace. Generalizing needs a per-section
  `fails?` or threading `override-built-char` (the subs already support it) through
  the section views.
- **Isolation is O(sites) rebuilds**, run synchronously on the error path and
  **cached** (once per affected character). May briefly hang on first load of a
  complex broken character.

## Landing (done)

Landed clean on `feature/fix-black-screen-of-death` (cut off `develop`), 12
files, authored `codeGlaze` with no agent trailer. The intentionally-broken test
fixtures used while building were all resolved in the clean branch:

- `classes.cljc` Hunter Evasion `trait-cfg` `:name "Evasion"` restored (the
  placeholder is for genuinely-unknown names, not a stand-in for the real one) — this
  is the actual root-cause fix, with a regression test in `hunter_evasion_test.cljc`.
- The dev `error-handling-demo` diagnostics page + its 4 route registrations were
  removed entirely. It was never an attack vector (client-only, no input, no writes),
  but it was dead prod surface; preserved in the WIP branch's git history if a
  dev-gated version is ever wanted.
- `system.clj` no longer hardcodes `0.0.0.0`. The dev host is
  `(or (environ/env :orcpub-http-host) "localhost")` — set `ORCPUB_HTTP_HOST=0.0.0.0`
  per-machine for a WSL→Windows-browser workflow without exposing it to the LAN by
  default. Prod is untouched (always `:prod`, binds all interfaces for containers).

One perf fix worth noting: `guarded-feature-list` originally ran `render-coll` twice
on the happy path (once to probe for a throw, once to return). It now renders once
and reuses the realized result, so only the error path pays the isolation cost.
