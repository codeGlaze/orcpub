# Verification plan: merging the #669 fix branch

Plan for taking `claude/fix-custom-items-disappearing-DW8rb` (`509431f2`) into `integration`.
Written 2026-09-13 against `integration` `36766010`. The defect itself is
[filtered-list-staleness.md](filtered-list-staleness.md).

**Status: Stages 1 and 2 are DONE and green.** 2026-09-13: the merged tree compiles and the whole
cljs suite passes — **354 tests, 1699 assertions, 0 failures, 0 errors**. The toolchain was built in
this sandbox from the recipe in [cljs-headless-harness.md](cljs-headless-harness.md) (`lein` is not
preinstalled but installs fine; see that doc for the four things that stopped the harness working).
Stages 3–5 still need a person, a browser and a login. Details in *What has actually been run*.

## What is actually being merged

Five units, not one. Ranked by what they can break.

| | unit | commits | blast radius |
|---|---|---|---|
| **P5** | `reg-api-sub` HOF; 5 API-backed subs migrated to it | `d9f48630`, `0553b9c4` | **Highest.** `::mi5e/custom-items`, `::char5e/characters`, `::party5e/parties`, `::folder5e/folders`, `:user`. These are the app's primary loaders. `:user` drives auth state |
| **P1** | `filtered-items`/`filtered-spells` become reactive | `0024e0bf`, `1248e8e6` | The fix. Two subs, two events, one new helper |
| **P4** | `::mi5e/remote-items`, `::mi5e/remote-item`, `::mi5e/item` discarded | `73ce1359` | Three subscription keys stop being registered. Hard failure if anything calls them |
| **P2** | `get-auth-token` consolidated into `event_utils` | `910fd535` | Changes the login guard for all five P5 subs at once |
| **P3** | `console.warn` on the custom-items 401 | `dbc86571` | Diagnostic only |

## Does any of this fix something broken on integration? Only P1.

Asked directly, checked directly, 2026-09-13 against `integration` `36766010`. **The answer matters
because it settles what the testing is for:** P1 is a defect fix and needs proving; P2–P5 are a
refactor and need proving they changed *nothing*.

| | what it does on integration | is integration broken without it? |
|---|---|---|
| **P1** | makes `filtered-items`/`filtered-spells` reactive | **Yes — the only live defect.** Orcpub#669 |
| **P2** | moves `get-auth-token` into `event_utils.cljc` | **No.** It already exists, at `events.cljs:2586`, with the correct body `(-> db :user-data :token)`. The move is so `equipment_subs.cljs` and `subs.cljs` can require it without an import cycle |
| **P3** | `console.warn` on the custom-items 401 | **No.** Diagnostic only — the branch's own comment says "zero behavior change" |
| **P4** | discards `::mi5e/remote-items`, `::mi5e/remote-item`, `::mi5e/item` | **No, but it removes a latent footgun.** `equipment_subs.cljs:272` on integration guards with `(and (:user @app-db) (:token (:user @app-db)))`, and `db[:user]` has never held `:token` — so that guard is **always false** and the sub can never fire. It is unreachable: `clj-grep --live` finds **no** subscriber to either key, so nothing user-facing is broken by it today |
| **P5** | five API subs onto one `reg-api-sub` HOF | **No.** It collapses ~10 duplicated lines per site. Its value is preventing the P4 typo class from recurring — one canonical guard instead of five hand-written ones |

So the branch is **one bug fix plus a refactor that removes a footgun**. That is a good reason to take
it whole, and the reason the test plan below weights P5 heaviest: a refactor that silently changes
behaviour is the failure mode, and P5 touches the app's five primary data loaders.

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

**A working custom-items e2e suite already exists — on `fix/custom-item-classification`.**
`scripts/e2e/run.js` there has `login()`, `newItem(page, name, type)` and nine logged-in scenarios
against the real app and a real DB. Stage 3 is therefore much cheaper than the rest of this section
implies: the probe for M1 is a scenario function added to that file's shape, not new machinery.
That branch is unmerged, and it is the non-`claude` sibling of the custom-item work in the triage
doc — so the two custom-item efforts probably want considering together rather than separately.

**The advertised e2e regression net does not run.** `e2e/scenarios/custom-items.spec.ts` (335 lines)
is **inert by its own admission** — its header says it depends on Playwright infrastructure that
exists only on `testing/develop`. `integration` has no `e2e/` directory at all; browser tests live in
`test/browser/` (37 scripts, a different harness). So the user-facing net for #669 is **not**
included in this merge. Either port the spec to `test/browser/`, or treat manual step M1 below as the
only end-to-end coverage and say so.

**Nothing covers P5 for the other four subs.** There is no test that `::char5e/characters`,
`::party5e/parties`, or `::folder5e/folders` still load after migration. That is the largest gap.

## What has actually been run

Built in the sandbox: `lein` 2.12.0 on Java 21, `lein deps`, `lein fig:test`, then the cljs suite
headless via Playwright. The tree under test is `claude/fix-custom-items-disappearing-DW8rb`
(`509431f2`) with `origin/integration` (`36766010`) merged in and the three conflicts resolved as
described above.

| | result |
|---|---|
| Merge compiles | **yes** |
| Full cljs suite | **354 tests / 1699 assertions, 0 failures, 0 errors** |
| P5 request-path differential | **identical trace** on `integration` and on the merge — see below |
| P4's discarded subs | no build error, so nothing referenced them — confirms the static finding |

### The discrimination check — the part that matters

A green suite proves nothing on its own. To prove the tests detect the bug, the pre-fix shape was
reinstated in the merged tree (sub reads a db snapshot via `or`; the filter events write it), the
build recompiled, and the suite re-run:

**7 failures, and they are the right 7.**

| test | file | catches the bug? |
|---|---|---|
| `filter-items-event-stores-only-filter-text` | `equipment_subs_test.cljs` (branch) | yes |
| `filter-spells-event-stores-only-filter-text` | `equipment_subs_test.cljs` (branch) | yes |
| `filtered-items-updates-after-simulated-save` | `equipment_subs_test.cljs` (branch) | yes |
| `filter-items-short-text-returns-all` | `events_test.cljs` (branch) | yes |
| `filter-spells-short-text-returns-all-sorted` | `events_test.cljs` (branch) | yes |
| `new-item-appears-while-a-filter-is-active` | `filtered_list_reactivity_test.cljs` (added) | yes |
| `deleting-an-item-removes-it-while-a-filter-is-active` | `filtered_list_reactivity_test.cljs` (added) | yes |

The fix was then restored and the suite verified green again.

### One of the branch's tests is vacuous — measured, not guessed

**`filtered-items-reacts-to-custom-items-change` does not fail when the bug is present.** It never
appeared in the 7. The reason is mechanical: under the old code the stale snapshot only existed
*after* a filter event had run, and that test changes `::mi/custom-items` without dispatching one, so
the `or` falls through to the live `sorted-items` on both the broken and the fixed code.

It is not harmful, but it is not coverage either. **Any reactivity test for this bug must dispatch a
filter first.** That is the single most important thing to know before writing more of them.

### What was added, and what each one is for

`test/cljs/orcpub/dnd/e5/filtered_list_reactivity_test.cljs` — 10 tests. Two of them detect the bug
(above); the other eight are guards against plausible regressions the branch does not cover:

| | guards |
|---|---|
| `a-non-matching-new-item-stays-hidden` | the cheap wrong fix — making the list reactive by ignoring the filter would pass every reactivity test and break the feature |
| `filter-below-min-length-returns-the-whole-list` | the 3-character threshold, for `""`, `"z"`, `"zz"`. `reg-filtered-sub` takes `min-length` as a parameter and nothing else pins it |
| `filter-at-exactly-min-length-does-filter` | the boundary itself |
| `filtered-items-come-back-sorted` | `compute/filter-items` sorts by name; the sub must not disturb it |
| `parties-sub-reads-the-historical-db-key` | **P5.** `::party5e/parties` must read `db[::char5e/parties]`. "Tidying" that name is the most likely self-inflicted break in the whole merge |
| `characters-and-folders-subs-read-their-db-keys` | P5, the other two list subs |
| `migrated-subs-default-to-empty-not-nil` | `[]` not `nil` from an unset key, so `count`/`seq` at call sites stay safe |
| `logged-out-subs-do-not-touch-the-loading-counter` | the `:user` counter change — logged out, the guard must short-circuit before `:set-loading` |

None of the eight fails on either version, which is correct: they are regression guards, not bug
detectors. Stated explicitly so nobody later mistakes them for proof the fix works.

## The matrix

Run in this order. Each stage is cheap relative to the next and each one can stop the line.

### Stage 1 — build ✅ done

1. The cljs build compiles. **Verified 2026-09-13.** P4 removes three `reg-sub` registrations; a stale reference surfaces here
   or not at all.
2. **`test_runner.cljs` carries both sides of its conflict.** If the union was resolved wrong, tests
   silently drop out of the run and everything below passes vacuously. Count the namespaces before
   trusting a green result.

### Stage 2 — cljs suite ✅ done

Via the figwheel test build (`orcpub.test-runner`), **not** `lein test`. **354/1699, 0 failures,
0 errors**, and the discrimination check above. Note the count differs from the 370/1742 baseline in
[cljs-headless-harness.md](cljs-headless-harness.md) because that was measured on `agents/develop`,
which carries more test namespaces; this tree is integration + the fix branch.

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

## P5 differential — run on both trees, traces identical

**The question "does P2–P5 regress anything?" cannot be answered by a pass/fail test**, because a
refactor is supposed to change nothing. It needs a differential: the same probe against
`integration` and against the merge, comparing behaviour rather than asserting a value.

`scripts/e2e/api-sub-loaders.js` logs in as the seeded user, visits the pages that mount all five
migrated subs, and records every request to their endpoints — **name, method, whether it carried an
Authorization header, and status**. Those four facts are exactly what `reg-api-sub` took over from
the five hand-written call sites, so they are the surface a silent change would show on.

Run 2026-09-13 on a real server with a real database, `lein fig:prod` build in both cases.

```
CONTROL  integration 36766010          MERGED  integration + the branch (P1–P5)
  1x  characters GET auth=true 200       1x  characters GET auth=true 200
  1x  folders    GET auth=true 200       1x  folders    GET auth=true 200
  2x  items      GET auth=true 200       2x  items      GET auth=true 200
  1x  parties    GET auth=true 200       1x  parties    GET auth=true 200
  2x  user       GET auth=true 200       2x  user       GET auth=true 200
```

**Identical.** Same endpoints, same call counts, same auth headers, same statuses. The loading
overlay settles in both — which is the observable check on P5 adding an increment/decrement pair to
`:user` that did not exist before. No uncaught page errors in either.

`SELFTEST=1` logs in with a wrong password: **zero requests fire**, which exercises the guard that
P2 consolidated and P5 routes all five subs through. The main-path checks are known to be capable of
failing — an early version of the probe matched SPA page routes as well as API paths and reported
five FAILs plus a `parties GET auth=false`, which was the page load, not a call.

### What this does and does not establish

**Does:** the request path is unchanged — guard, headers, endpoint, method, call count, status, and
the loading counter settling. That is the P5 surface with no automated coverage, and it is the same
before and after.

**Does not:**

- **Response handling.** The seeded user owns no characters, parties, folders or items, so every
  200 carried an empty body. The `:set-event` → db-population path is not exercised. A probe that
  seeds content first would close this, and is the obvious next step.
- **The 401 paths.** No expired-token scenario, so `user-sub-on-401`'s compound behaviour is covered
  only by the unit test in `subs_test.cljs`, not end to end.
- **P1 itself.** This probe is about the refactor changing nothing; the fix working is the cljs
  suite plus manual step M1.

## P4 is the one unit that should change shape before it lands

**Why it exists:** the auth-guard audit (P2) walked every `reg-sub-raw` guard looking for the
`(:token (:user db))` typo. It found the `::mi5e/remote-item` chain carrying exactly that, and also
found the chain unreachable — nothing subscribes to it. Rather than delete it or fix it, the author
switched it off with `#_` and wrote down what it was for.

**Why that shape is wrong for this repo:** the block is **77 comment lines** wrapped around
discarded code, and its content is a design note — purpose, an ASCII diagram of the intended chain,
the server-endpoint asymmetry, open product questions ("Can viewers edit items they don't own? Can
they favorite/clone/share them?"), and a numbered list of follow-ups.
[code-comment-style.md](code-comment-style.md) is explicit that comments are "a technical manual for
how things work, not a journal", that an inline `;;` earns its place only when it "stops someone
breaking the code", and to "drop a *why* when it's history or self-justification."

Its own item 5 gives the game away: *"Add a KB entry to docs/kb/ … documenting the cross-user item
fetch chain."* The block knows where it belongs.

**It also sets a trap.** `#_` discards the next form, so the three subs grep as live code. That is
not hypothetical — the first search in this session reported five live references to keys that are
not registered, and only `scripts/clj-grep.py` showed otherwise. A 77-line discarded block is a
landmine for every future grep of this namespace.

**Suggested disposition — the only change to the branch recommended here:**

1. **Delete** the `#_` forms. Nothing subscribes to them; git has them if they are ever wanted.
2. **Record the note as SUPERSEDED, not as groundwork.** Its framing — "groundwork for viewing
   magic items owned by OTHER users" — was overtaken by `integration`, and keeping it as written
   would mislead. **Sharing shipped, by a deliberately different design:** a share link *embeds* the
   content in the URL payload (`share-url/decode-shared`, behind an input cap, a decompression-bomb
   guard, a safe reader and a structural whitelist) and lands it in an ephemeral
   `:shared-custom-items` overlay that is never persisted to the recipient's library. It does **not**
   read another user's items from the server.

   That reframes the endpoint asymmetry the note describes. `GET …/items/:id` returning any item by
   db-id regardless of owner is no longer "the server already supports what we want to build" — it is
   a cross-owner read path with no client consumer, on a codebase that chose link-embedding
   specifically to avoid needing one. Worth writing down as exactly that.
3. Leave **no** placeholder comment. The KB index is how it stays findable.

This is a shape change, not a scope change: P4's effect on the running app is identical either way,
because the code it removes could never execute.

## The sharing feature changes what P1 and P5 need to cover

`integration` gained custom-content sharing by link, and it wires straight into the chain this
branch touches:

```
::mi5e/custom-items        (P5 migrates this one)
        +
::mi5e/shared-custom-items (plain reg-sub over db :shared-custom-items — ephemeral, link-embedded)
        ↓
::mi5e/expanded-custom-items   (equipment_subs.cljs:70-77, shared appended LAST so it wins collisions)
        ↓
::char5e/sorted-items          (:91)
        ↓
::char5e/filtered-items        ← P1's fix
```

**So the #669 filtered list now includes shared items**, and nothing tests that. The cljs tests
added here seed `::mi/custom-items` only; the shared overlay is never populated in any of them.

Two consequences:

- **P5 does not need to grow.** `::mi5e/shared-custom-items` is a plain `reg-sub` reading `db`, not
  an API-backed loader, so `reg-api-sub` correctly leaves it alone. Confirmed, not assumed.
- **P1 needs a case it does not have.** A sheet viewed through a share link, with a filter active,
  where the shared items are the ones being filtered. Shared items are appended last so they win key
  collisions — which means a collision between an owned item and a shared one of the same key is
  exactly the situation where a stale snapshot would show the wrong one.

### What seeding the differential actually requires

The earlier "seed some content" note was too thin. To test the hypothesis **and** sharing:

1. **Account A (`kaylee`) with content** — so `::mi5e/custom-items` returns a non-empty body and the
   `:set-event` → db-population path finally runs. This is the gap the first differential left open.
2. **A second account with its own content** — so isolation is observable: A's items must not appear
   in B's list, and B's must not appear in A's. With one account, a `:db-key` mistake that returned
   *everyone's* items would look identical to correct behaviour.
3. **A share link from one to the other** — which needs no second session, since the content rides in
   the URL. That covers the shared-overlay path and the collision ordering above.

`dev/e2e_boot.clj` seeds exactly one user and no content, so all three need adding there — it is the
only place that can, because `datomic:mem://` lives solely in the JVM that created it.

## Take it whole — decided

**Decision (owner, 2026-09-13): keep all five units and test all of them thoroughly.** The earlier
framing of this section offered three partial takes; that is no longer the question and the options
are recorded only so the reasoning is not lost:

- P5 cannot be cleanly reverted after the fact anyway — it touches the same regions as P1.
- Splitting would leave the typo class P4 documents alive at five call sites, which is the thing P5
  exists to close.
- The unit table at the top of this doc stays useful for one purpose only: **bisecting by unit if a
  stage fails**, not for choosing a subset to merge.

What "thoroughly" requires is everything in *The matrix* above, and specifically the four stages that
have not run: Stages 3–5 plus the P5 surface that no automated test touches
(`::char5e/characters`, `::party5e/parties`, `::folder5e/folders`).

## What this plan cannot cover

- ~~Whether it compiles.~~ **It compiles, and the cljs suite is green.** What remains unproven is everything a browser does: Stages 3–5.
- **The four unmigrated-sub paths.** No automated coverage exists for `::char5e/characters`,
  `::party5e/parties`, `::folder5e/folders` post-P5. M5 is a smoke test, not a net.
- **Concurrency.** A6 approximates the counter race by hand. Nothing pins it.
- **The e2e regression net**, which is inert and stays inert unless someone ports it to
  `test/browser/`.
