# Merging a long-lived branch across a refactor: git's conflict list is not the hazard map

**References are against `integration` at `1bb3eb28` and `fix/custom-item-classification` at
`b234db2b`, checked 2026-09-20.**

A refactor that moves or inlines a function is invisible to git as *meaning*. It sees text. So when
a branch cut before the refactor is finally merged, the damage lands in two places, and git points
at neither of them usefully:

- **Where there is no conflict at all**, because the two edits never touched the same lines. The
  merge is clean and the result is broken.
- **Where there is a conflict**, but the obvious resolution — take the newer side — is the wrong
  one, because the older side was holding something the newer side deleted.

Both have now happened on this repo, three weeks apart, from the same refactor. They are recorded
together because seeing only one of them teaches the wrong lesson.

## Case 1: the clean merge that was already broken

`67a1e18f` (2026-09-18) fixed a bare `get-auth-token` call at `events.cljs:2790`, inside
`:report-character-problem`. The function had moved to `orcpub.dnd.e5.event-utils` in April
(`dbb71dce`, the #669 branch's P2). `events.cljs` requires that namespace by alias only, so the bare
call was an undeclared var: ClojureScript warns and compiles anyway, and the handler throws the
moment someone presses "email support" on the character-load recovery panel — a panel only reached
by people whose character has *already* failed to load.

The important part is that this was **not** a call site the refactor missed. That handler did not
exist when the function moved. It arrived in August on a parallel line (`d50eaf87`) where the local
`defn` was still present, and the two merged cleanly **with no textual overlap**. Nothing in the
merge was ambiguous, so nothing was flagged.

Re-running the refactor would not have found it. The defect is created by the merge, not by either
parent.

## Case 2: the conflict whose obvious resolution restores a bug

`fix/custom-item-classification` (cut 2026-07-07) extracted the custom-items fetch out of its
subscription, at `equipment_subs.cljs:33`, precisely so that something other than subscription
lifecycle could call it. Its own docstring says why:

> the raw sub only runs its handler on a cache MISS, so the fetch fired at most once per
> subscription — and never at all if the chain was first dereferenced before sign-in, which is why a
> freshly logged-in session could show empty equipment pickers until a page reload.

That is the "my items only show up after I refresh the page" report. The fix has three parts on that
branch: `fetch-custom-items!` (`equipment_subs.cljs:33`), the event `::mi/fetch-custom-items`
(`events.cljs:2346`, calling it at `:2349`), and the dispatch from `login-success`
(`events.cljs:2103`).

Meanwhile P5 of the #669 branch introduced `reg-api-sub` (`api_subs.cljs`), which registers the
same kind of subscription for five endpoints at once. It **inlines** the fetch inside the
`reg-sub-raw` handler — `reg-sub-raw` at `:60`, the guarded `go` block at `:62`–`:74`, the reaction
at `:75`. There is no separately callable fetch, and `integration` has **zero** references to
`fetch-custom-items` anywhere in `events.cljs`.

So the two sides collide on `equipment_subs.cljs`, and both available resolutions are wrong:

| Resolution | Result |
|---|---|
| Take integration's side | `events.cljs:2349` calls a function that no longer exists — build breaks |
| Take integration's side *and* its `events.cljs` | Builds fine. The sign-in dispatch is gone, and the "empty until reload" bug is back |

The second is the dangerous one: it is what a careful-looking merge produces, it compiles, and the
bug it restores is the same class as #669 itself — a list that is stale until you reload.

**The resolution is neither side.** `reg-api-sub` needs to build its fetch as a named function and
hand it back, so a call site like `::mi/fetch-custom-items` has something to call. That is a small
additive change and it belongs in the merge, on the branch, so it flows back to `integration`
afterwards rather than churning `integration` for a branch that has not landed.

## This one was self-inflicted, and that is the measurable part

Merging the #669 work into `integration` took `fix/custom-item-classification`'s conflict count from
**12 to 14**. `equipment_subs.cljs` auto-merged cleanly against `integration` at `186bf396` and
conflicts against `1bb3eb28`; `dev/e2e_boot.clj` went the same way, from byte-identical across the
two branches to an add/add conflict, because the #669 merge added account seeding to
`integration`'s copy.

Neither was noticed while merging #669, because nothing in that work looks at what it costs an
unmerged branch. That is the gap worth naming: **the blast radius of a refactor is not the set of
call sites in the tree you are standing in.** It includes every branch cut before it, and those
branches are not in the diff.

## What to do about it

Before merging a branch cut before a known refactor:

1. **Diff the conflict set against an older base.** `git merge-tree --write-tree --name-only <branch>
   <old-integration>` versus the same against current. Files that newly conflict are where a refactor
   landed on top of the branch's work — read those first, not in file order.
2. **For each symbol the refactor moved, grep the branch, not the tree.** A bare call on the branch
   that resolves there resolves to nothing after the merge. Case 1 is exactly this, and it survived
   because nobody looked.
3. **Ask what each side was holding, not which side is newer.** A conflict where one side deleted an
   extension point is not a style disagreement; the deletion is the whole conflict.
4. `#_` discards: use `scripts/clj-grep.py`, not `grep`. A call site that looks live may be switched
   off, and one that looks absent may be inside a discarded form.

For `fix/custom-item-classification` specifically, the `get-auth-token` hazard of Case 1 was tested
and **does not reproduce**: in the merge result every call is qualified, the branch's local `defn`
drops out cleanly, and no orphaned bare calls remain. `url-for-route`'s 18 bare calls resolve through
a local alias at `events.cljs:572`. Case 2 is live and must be handled.

## Related

- [filtered-list-staleness.md](filtered-list-staleness.md) — #669 itself, the same bug class as the
  one Case 2 would restore.
- [plan-669-merge-verification.md](plan-669-merge-verification.md) — what P1–P5 are, and how P5 was
  verified to change nothing observable *on `integration`*. That verification was sound and is not
  contradicted here; it simply had no view of unmerged branches.
- [handoff-669-final-pass.md](handoff-669-final-pass.md) — the final-pass handoff for that work.

## Revisions

- **2026-09-20** — written, after finding Case 2 while sizing the
  `fix/custom-item-classification` merge, and Case 1 in `integration`'s log.
