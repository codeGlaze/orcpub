# A stored filter result is not a subscription — the My Items / My Spells staleness bug

**Live bug.** References are against `integration` at `36766010`, checked 2026-09-12.

> **Pin warning, 2026-09-13.** These references are against **`integration` `36766010`** and were
> correct there when written. They are **no longer correct against this branch's own working tree**:
> `a1d16fc7` merged `feature/grant-rows` into `agents/develop`, so `src/` here now differs from
> `integration` in 42 files. Check these line numbers against `integration`, not against a checkout
> of `agents/develop`. The symbols named are still the right symbols; only the line numbers moved.

The My Items and My Spells lists show a result computed when the user last typed, not the current
content. Save an item, delete one, or let a fetch land, and the list keeps showing the old set until
the filter text changes again. Reported upstream as Orcpub/orcpub#669 ("custom items disappearing"),
which is the same defect seen from the user's side: an item they just saved is not in the list.

## The mechanism

The filter result is **written into `app-db` by an event** and **read back by a subscription** that
does not recompute it.

Write side — `events.cljs:3004-3007`:

```clojure
::char5e/item-text-filter filter-text
::char5e/filtered-items   (if (>= (count filter-text) 3)
                            (filter-items filter-text sorted)
                            sorted)
```

`sorted` is captured **at the moment the keystroke is handled**. Read side —
`subs.cljs:1053-1058`:

```clojure
(reg-sub
 ::char5e/filtered-items
 :<- [:db]
 :<- [::char5e/sorted-items]
 (fn [[db sorted-items] _]
   (or (::char5e/filtered-items db)
       sorted-items)))
```

The `:<- [::char5e/sorted-items]` signal is **declared but only used as the fallback**. Once
`::char5e/filtered-items` is present in `db` — which it is from the first keystroke onward — the
`or` short-circuits and `sorted-items` is never consulted again. The subscription re-runs when
`sorted-items` changes, and returns the same stale vector.

`::char5e/filtered-spells` has the identical shape at `subs.cljs:1045-1050`, written at
`events.cljs:2994`.

Both are consumed by the list views — `views.cljs:10212` (items) and `:10137` (spells).

## The precondition — this is why it is not constantly obvious

`::char5e/filtered-items` is **absent from `db` until the user types in the filter box**. Until
then the `or` falls through and the subscription returns the live `::char5e/sorted-items`. The
staleness begins at the **first keystroke in the filter box** and persists for the rest of the
session, because nothing ever removes the key — `dissoc` is never called on it anywhere in
`src/cljs/`.

So a user who never touches the filter never sees the bug, and a user who types once sees a list
frozen from that moment. Below three characters the event stores `sorted` *unfiltered*, so even
typing and deleting a single character freezes the full list.

## Why it is shaped this way — do not "fix" it back

The snapshot-in-`db` shape is deliberate. The comment above both events says so:

```clojure
;; Filter spell list by name. Computes sorted spells from db directly
;; (avoids subscribe outside reactive context).
```

Calling `subscribe` inside an event handler is the antipattern
[re-frame-subscribe-refactor.md](re-frame-subscribe-refactor.md) exists to stamp out, so computing
from `db` directly in the handler was the right move for *that* problem. The mistake is what happened
next: having computed the value in the handler, it was stored and then read back as if it were
derived state.

**Computing in the subscription does not reintroduce the subscribe-outside-reactive-context bug.**
A `reg-sub` computation function runs in reactive context by construction; the rule being avoided is
about calling `subscribe` from an *event handler*. That is why the fix below is safe.

## Why it is easy to miss in review

The subscription *looks* correct: it declares its input signals and takes them as arguments. The
defect is that one of the declared signals is dead in the path that actually runs. A reviewer
checking "does this sub declare its dependencies?" gets a yes.

The general rule: **a value the event layer computes and stores is a cache, and a `reg-sub` that
reads that cache inherits the cache's staleness, not the freshness of its declared signals.** If the
result is derived, derive it in the subscription.

## The fix that exists

`claude/fix-custom-items-disappearing-DW8rb` (`509431f2`) replaces both with a `reg-filtered-sub`
helper that computes from the signals — `subs.cljs:1003-1007` on that branch:

```clojure
(reg-filtered-sub ::char5e/filtered-items
                  [::char5e/sorted-items]
                  [::char5e/item-text-filter]
                  compute/filter-items
                  3)
```

That branch also carries `equipment_subs_test.cljs` pinning the regression, and `api_subs.cljs`
with a `reg-api-sub` sibling. **None of it has landed** — the branch is unmerged and its 20 new
definitions appear on no other branch (see [claude-branch-triage.md](claude-branch-triage.md)).

## The merge is cheap — measured, 2026-09-13

The branch is based at `d42e05d1` (2026-04-09) and `integration` has moved **579 commits** since, so
it looks daunting. It is not. A test merge of `origin/integration` into
`claude/fix-custom-items-disappearing-DW8rb` (`509431f2`) produced **three conflict hunks in two
files**, all of them additive lists:

| file | hunks | what |
|---|---|---|
| `subs.cljs` | 1 | the `:require` on `re-frame.core` — branch adds `[re-frame.db]`, integration adds `reg-event-db` to `:refer` |
| `test_runner.cljs` | 2 | each side registers different test namespaces, in the `:require` and in `run-tests` |

**The reason it is this clean: `integration` has not touched the defect.** Of the 21 commits that
changed `subs.cljs` since the base, **zero** touch `filtered-items` (`git log -S'filtered-items'`).
The buggy `(or …)` shape was already present at `d42e05d1` and is unchanged today, so the fix still
applies to exactly the code it was written against.

**One of the three conflicts is a trap.** The `subs.cljs` one *looks* like a list union, but
resolving it line-wise yields two `(:require` forms and a broken `ns`. The refer vector has to be
unioned — keep the branch's `[re-frame.db]` and add integration's `reg-event-db` to the existing
`:refer` list.

Checked on the merged tree, statically:

- exactly **one** definition of `::char5e/filtered-items`, the reactive `reg-filtered-sub` one;
- the snapshot write is **gone** from `events.cljs` — `::char5e/filter-items` now stores only
  `::char5e/item-text-filter`, and a comment in place says *"Do NOT write
  `::char5e/filtered-items` into db — the sub composes"*;
- every symbol the fix calls resolves: `compute/filter-items` (`compute.cljc:87`),
  `compute/filter-spells` (`:80`), `reg-filtered-sub` (`subs.cljs:1068`),
  `event-utils/get-auth-token` (`event_utils.cljc:29`);
- parens and brackets balance in the merged `subs.cljs`.

**Nothing was compiled or run** — this sandbox has `node` but no `lein` or `clojure`, and the cljs
suite runs through the figwheel test build (`orcpub.test-runner`), not `lein test`. Static
resolution is not a build.

### What to actually test after merging

Full stage-gated matrix, contingencies and partial-take options:
[plan-669-merge-verification.md](plan-669-merge-verification.md). In short:

1. The cljs suite via the figwheel test build, with `test_runner.cljs` carrying **both** sides'
   namespaces — that conflict is the one most likely to silently drop tests from the run.
2. `equipment_subs_test.cljs`, the branch's own regression pin.
3. By hand: open My Items, type three characters, delete them, save an item, confirm it appears.
   That is the falsifier for this whole doc.

**Scope note.** The branch is not only the #669 fix: it also moves `get-auth-token` into
`event_utils`, adds a 401 observability breadcrumb, and migrates five API-backed subs to a
`reg-api-sub` HOF. Those ride along and want their own coverage. Given how clean the merge is,
taking the branch whole is defensible; extracting just the two `reg-filtered-sub` calls is the
smaller-blast-radius alternative.

## Related

- [reframe-subscription-patterns.md](reframe-subscription-patterns.md) — `reg-sub` vs
  `reg-sub-raw`, subscribe context rules.
- [re-frame-subscribe-refactor.md](re-frame-subscribe-refactor.md) — the
  subscribe-outside-reactive-context work.
- [custom-content-lifecycle.md](custom-content-lifecycle.md) — where custom items live and how they
  reach the sheet.
