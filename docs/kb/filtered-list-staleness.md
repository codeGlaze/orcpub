# A stored filter result is not a subscription — the My Items / My Spells staleness bug

**Live bug.** References are against `integration` at `36766010` (identical `src/` on
`agents/develop` `260aae1f`), checked 2026-09-12.

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
definitions appear on no other branch (see
[claude-branch-triage.md](claude-branch-triage.md)). Nothing here has been compiled or run; the
defect is verified by reading, the fix is not.

## Related

- [reframe-subscription-patterns.md](reframe-subscription-patterns.md) — `reg-sub` vs
  `reg-sub-raw`, subscribe context rules.
- [re-frame-subscribe-refactor.md](re-frame-subscribe-refactor.md) — the
  subscribe-outside-reactive-context work.
- [custom-content-lifecycle.md](custom-content-lifecycle.md) — where custom items live and how they
  reach the sheet.
