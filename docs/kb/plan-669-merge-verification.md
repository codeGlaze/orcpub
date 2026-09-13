# Verification plan: merging the #669 fix branch

Plan for taking `claude/fix-custom-items-disappearing-DW8rb` (`509431f2`) into `integration`.
Written 2026-09-13 against `integration` `36766010`. The defect itself is
[filtered-list-staleness.md](filtered-list-staleness.md).

**Status: nothing has been run.** This sandbox has `node` but no `lein` or `clojure`. Everything in
*Pre-flight* below was verified statically and the results are recorded; everything in *The matrix*
is for whoever has a toolchain. **Static resolution is not a build.**

## What is actually being merged

Five units, not one. Ranked by what they can break.

| | unit | commits | blast radius |
|---|---|---|---|
| **P5** | `reg-api-sub` HOF; 5 API-backed subs migrated to it | `d9f48630`, `0553b9c4` | **Highest.** `::mi5e/custom-items`, `::char5e/characters`, `::party5e/parties`, `::folder5e/folders`, `:user`. These are the app's primary loaders. `:user` drives auth state |
| **P1** | `filtered-items`/`filtered-spells` become reactive | `0024e0bf`, `1248e8e6` | The fix. Two subs, two events, one new helper |
| **P4** | `::mi5e/remote-items`, `::mi5e/remote-item`, `::mi5e/item` discarded | `73ce1359` | Three subscription keys stop being registered. Hard failure if anything calls them |
| **P2** | `get-auth-token` consolidated into `event_utils` | `910fd535` | Changes the login guard for all five P5 subs at once |
| **P3** | `console.warn` on the custom-items 401 | `dbc86571` | Diagnostic only |

## Pre-flight — done, with results

These are cheap, repeatable, and already cleared. Re-run them after the merge, not before.

| check | result |
|---|---|
| Merge conflicts | **3 hunks, 2 files**, all additive lists (`subs.cljs` require, `test_runner.cljs` ×2) |
| Has `integration` touched the defect since the base? | **No.** 0 of the 21 commits that changed `subs.cljs` since `d42e05d1` mention `filtered-items` |
| Post-merge: one definition of `filtered-items`? | Yes, and it is the reactive one |
| Post-merge: is the old db write gone? | Yes — `::char5e/filter-items` stores only `item-text-filter`, with a comment saying not to reinstate |
| Do the fix's symbols resolve? | Yes — `compute/filter-items`, `compute/filter-spells`, `reg-filtered-sub`, `event-utils/get-auth-token` |
| **P4: is anything still calling the discarded subs?** | **No live references.** Every remaining mention is inside a `;;` comment or a docstring |
| **P2: is the new guard equivalent?** | Yes. `get-auth-token` reads `[:user-data :token]`; the old custom-items guard was `(and (:user-data db) (:token (:user-data db)))` — the extra clause is subsumed |
| **P5: does an explicitly-passed `nil` handler differ from an absent one?** | **No.** `handle-api-response` uses `(if on-401 … default)`, and `nil` is falsy, so omitted handlers still get the defaults. This was a real candidate for a silent break; it is clear |

### Two traps found in pre-flight

**The `subs.cljs` conflict is not a line union.** It looks like one. Resolving it line-wise produces
two `(:require` forms and a broken `ns`. Union the *refer vector*: keep the branch's `[re-frame.db]`
and add `reg-event-db` to the existing `:refer` list. (Hit and backed out during the test merge.)

**P4 uses `#_`, not `;;`.** The three discarded subs read as live code to `grep`. Audit them with
`scripts/clj-grep.py`, per [documentation-discipline.md](documentation-discipline.md). A plain grep
reports five live references that do not exist.

## Two behaviour changes that are not the fix

Both are in P5. Neither is a defect; both are things a tester should expect to see so they are not
mistaken for one.

**1. The `:user` sub now moves the loading counter.** The pre-merge `:user` sub
(`subs.cljs:475-487` on `integration`) has no `:set-loading` dispatch at all. `reg-api-sub` always
brackets its request with `[:set-loading true]` / `[:set-loading false]`.

- *Why it is safe:* `set-loading` clamps — `(max 0 (dec current))` — so the counter cannot go
  negative even if `:route-to-login` resets it to `0` mid-flight (`events.cljs:6451`). The decrement
  also runs *before* `handle-api-response`, so the reset happens after, not during.
- *What to watch for:* a loading-overlay flash on pages that subscribe to `:user` and previously had
  none. `views.cljs:1592` guards correctly with `(pos? (or … 0))`.
  **`character_builder.cljs:2651` binds `loading` raw** and is the place to check for the
  ClojureScript `0`-is-truthy gotcha documented in
  [reframe-subscription-patterns.md](reframe-subscription-patterns.md).

**2. The `:user` 401 handler now reads the global app-db.** Was a closure over `reg-sub-raw`'s
`app-db` argument; is now `(:user-data @re-frame.db/app-db)`. The same atom in practice, so
equivalent — but it is a new hard dependency on the global, and the read happens at 401-time rather
than at subscribe-time. The branch pins the decomposed logic in `subs_test.cljs`
(`user-sub-on-401-actions`), which is the right guard.

## What the branch's own tests do and do not cover

| test | covers |
|---|---|
| `equipment_subs_test.cljs` (+170) | The P1 regression: custom-items guard gap and filter reactivity |
| `subs_test.cljs` (+73) | `user-sub-on-401-actions` — the compound `:user` 401 behaviour across the P5 migration |
| `event_utils_test.cljc` (+37) | `get-auth-token` |
| `events_test.cljs` (+28/−23) | Adjusted for the removed db write |

**The advertised e2e regression net does not run.** `e2e/scenarios/custom-items.spec.ts` (335 lines)
is **inert by its own admission** — its header says it depends on Playwright infrastructure that
exists only on `testing/develop`. `integration` has no `e2e/` directory at all; browser tests live in
`test/browser/` (37 scripts, a different harness). So the user-facing net for #669 is **not**
included in this merge. Either port the spec to `test/browser/`, or treat manual step M1 below as the
only end-to-end coverage and say so.

**Nothing covers P5 for the other four subs.** There is no test that `::char5e/characters`,
`::party5e/parties`, or `::folder5e/folders` still load after migration. That is the largest gap.

## The matrix

Run in this order. Each stage is cheap relative to the next and each one can stop the line.

### Stage 1 — build

1. The cljs build compiles. P4 removes three `reg-sub` registrations; a stale reference surfaces here
   or not at all.
2. **`test_runner.cljs` carries both sides of its conflict.** If the union was resolved wrong, tests
   silently drop out of the run and everything below passes vacuously. Count the namespaces before
   trusting a green result.

### Stage 2 — cljs suite

Via the figwheel test build (`orcpub.test-runner`), **not** `lein test`. All four test files above,
plus the existing suite.

### Stage 3 — manual, logged in, with custom content

| | scenario | expected |
|---|---|---|
| **M1** | **#669 itself.** My Items → type 3 characters → delete them → save a new item | The new item appears without a reload. *This is the falsifier for the whole finding.* |
| **M2** | Same on My Spells | Same |
| **M3** | Filter with fewer than 3 characters, then change content | List stays current. Pre-merge this froze the *unfiltered* list |
| **M4** | Sort order after filtering | Unchanged — `reg-filtered-sub` composes over `sorted-items` |
| **M5** | Load the character list, parties, folders | All populate. **P5's untested surface** |
| **M6** | Reload with a valid session | No login bounce; no stuck spinner |

### Stage 4 — auth, the P5 risk surface

| | scenario | expected |
|---|---|---|
| **A1** | Log out, visit a page that subscribes to `:user` | No HTTP fired — the guard short-circuits |
| **A2** | Expire/corrupt the token, load My Items | **Silent.** Console shows `custom-items fetch rejected`; **no** login bounce. A login loop here is P3/P5 regressing the deliberate silent-401 |
| **A3** | Expire the token, load the character list | Bounces to login (`:on-401` → `:route-to-login`) |
| **A4** | Expire the token with `required?` true on `:user` | Login state cleared **and** bounce. `:theme` and other non-login fields survive |
| **A5** | Expire the token with `required?` false | State cleared, **no** bounce |
| **A6** | Trigger several 401s at once | Overlay does not stick. Counter is clamped, but this is where a drift bug would show |

### Stage 5 — the orphaned chain (P4)

| | scenario | expected |
|---|---|---|
| **O1** | Visit `/items/<id>` for an item you own | Renders. Goes through `::mi/custom-item`, untouched by P4 |
| **O2** | Visit `/items/<id>` for an item you do **not** own | "Not found" — unchanged. This path was already dead before the merge; P4 only makes that explicit |

## Contingencies

| if | most likely cause | do |
|---|---|---|
| Build fails on an unresolved sub key | P4 discarded something still referenced, or the `#_` swallowed one form too many | `clj-grep.py --live` for the key. `#_` discards the *next form*, so a stray one eats the following `reg-sub` too |
| Build fails in `subs.cljs` `ns` | The require conflict resolved line-wise | Re-resolve: one `(:require`, `[re-frame.db]` kept, `reg-event-db` added to `:refer` |
| Suite green but suspiciously fast | `test_runner.cljs` union dropped namespaces | Count registered namespaces against both parents |
| **M1 fails — item still does not appear** | The fix is not the whole story; there is a second staleness layer between the sub and the view | **Stop.** The finding was verified by reading, not running. Capture `db` and the sub's value at that moment before assuming the fix is wrong |
| M5 fails — characters/parties/folders empty | P5 got a `:db-key` wrong | Check `::party5e/parties` first: its `db-key` is deliberately `::char5e/parties` (historical naming, flagged in a comment). A "fix" to that line is the most likely self-inflicted break |
| A2 bounces to login | The deliberate silent 401 was lost | `::mi5e/custom-items` must pass an `:on-401`; omitting it inherits the `:route-to-login` default and causes a login loop racing `:verify-user-session` |
| A4 loses the theme | The `dissoc` list changed | Must be `(dissoc user-data-map :user-data :token)` — nothing more |
| Spinner sticks or never shows | The `:user` counter change meeting the `0`-is-truthy gotcha | Check `character_builder.cljs:2651`, which binds `loading` raw |
| Something breaks and the cause is unclear | Five units landed together | Bisect **by unit, not by commit** — the table at the top maps units to commits |

## If it has to come out

The units are separable, in increasing order of what you keep:

1. **P1 only** — cherry-pick `0024e0bf` and `1248e8e6`. Two `reg-filtered-sub` calls plus the helper
   and its tests. This is the #669 fix with none of the refactor. Smallest blast radius.
2. **P1 + P2 + P3** — adds the guard consolidation and the breadcrumb. Low risk, no HOF.
3. **Everything** — the recommendation, *if* Stage 4 passes. P5 is good work: it closes a real typo
   class (`(:token (:user db))` against a `db[:user]` that has never held a token) at five call sites
   at once, and that is worth more than it costs.

Reverting P5 alone after the fact is awkward — it touches the same regions as P1. Decide before
merging, not after.

## What this plan cannot cover

- **Whether it compiles.** Nothing here was built. Every "resolves" above is a static symbol check.
- **The four unmigrated-sub paths.** No automated coverage exists for `::char5e/characters`,
  `::party5e/parties`, `::folder5e/folders` post-P5. M5 is a smoke test, not a net.
- **Concurrency.** A6 approximates the counter race by hand. Nothing pins it.
- **The e2e regression net**, which is inert and stays inert unless someone ports it to
  `test/browser/`.
