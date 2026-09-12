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

**The three-character threshold matters for reproduction.** Below three characters the event stores
`sorted` unfiltered, so the staleness is present but invisible while the list is short. Type three
or more characters, then change the underlying content, to see it.

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
