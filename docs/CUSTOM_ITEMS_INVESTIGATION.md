# Custom Items "Disappearing" — Branch Investigation Log

Working notes for Orcpub/orcpub#669 and related reports of server-persisted
custom magic items appearing blank, missing, or stale on the character sheet,
in the item list, and in PDF output.

Branch: `claude/fix-custom-items-disappearing-DW8rb`

**This is branch-specific investigation state.** Reusable findings get
reconciled back to `agents/develop` KB at the end. See
[Reconciliation plan](#reconciliation-plan-for-agentsdevelop) at the bottom.

## Scope clarification from repo owner

- **In scope**: server-persisted custom items (Item Builder → `POST /items`
  → Datomic → `GET /items`). These are *not* exportable/importable — they
  live only in Datomic, owned by `::mi5e/owner = username`.
- **Out of scope**: orcbrew export ("M" validation errors). Tracked
  separately.
- **Out of scope**: plugin-defined magic items. The spec allows them under
  `::plugin`'s open content-keyword but no subscription wires them into
  item maps. If orcbrew content with magic items ever worked, the wiring
  is gone today. Separate, much larger concern.

## Existing KB — relevant entries on `agents/develop`

**I should have read these first. Lesson learned.** The KB already covers
most of what I was re-deriving. Entries consulted for this investigation:

| KB file | Coverage |
|---|---|
| `docs/kb/reframe-subscription-patterns.md` | `reg-sub-raw` HTTP pattern; auth guard placement; `:user-data` canonical token path; loading counter as int; "stale data after login" gotcha verbatim |
| `docs/kb/re-frame-subscribe-refactor.md` | Phase 1 (12 subscribe-outside-reactive fixes), including `::char5e/filter-items` and `::char5e/filter-spells` scoped to subscribe warnings, NOT cache staleness |
| `docs/kb/subscribe-refactor-phase2.md` | Phase 2 fixes including `equipment_subs.cljs::mi5e/item` conversion to `reg-sub-raw` — does NOT address the `::mi5e/remote-item` `:user` guard typo |
| `docs/kb/env-and-auth.md` | Canonical token path `[:user-data :token]`; historical `:user` sub bug fix in `subs.cljs`. **Does not claim `::mi5e/remote-item` was fixed** — my assumption it had been was wrong |
| `docs/kb/http-fx-patterns.md` | `:http` effect handler contract — consulted to confirm dispatch-vector semantics |

## Auth chain map (full)

**db shape:**
```
db
├── :user-data                   ← real auth state
│   ├── :token                   ← JWT string from login response
│   ├── :user-data               ← nested literal same key — actual user record
│   │   ├── :username
│   │   ├── :email
│   │   ├── :pending-email
│   │   ├── :send-updates?
│   │   └── ...
│   └── :theme                   ← persists across logout
│
└── :user                        ← NEAR-DEAD STORAGE (see below)
    └── :following               ← only attribute ever populated
```

### Why the double-nested `:user-data` exists

Server `:login-success` response body is `{:token "..." :user-data {:username :email ...}}`.
Client merges the whole body into `db[:user-data]`:
```clojure
(update db :user-data merge (-> response :body))
```
Result: `db[:user-data] = {:token "..." :user-data {...}}`.

**This is fixable.** Unwrap at the merge site — flatten into `:token`
and `:profile` (or similar) at the same level, or use explicit
`assoc-in`. Touches login-success, set-user-data, clear-login, the
user-data subs (subs.cljs:253-270), views.cljs:1525 lifecycle read,
local-store interceptor, and the localStorage format (migration needed
for existing sessions). **Not in scope for this patch.** Logged as a
follow-up cleanup at the bottom of this file.

### Why `db[:user]` exists (and why it's near-dead)

`db[:user]` is read/written by:
- `events.cljs:1110 :follow-user` — `(update (:user db) :following conj username)` → `:set-user`
- `events.cljs:1205 :unfollow-user` — same shape
- `events.cljs:1622 :set-user` — `(assoc db :user user-data)` — only caller is above
- `subs.cljs:416 :user` reg-sub-raw — fetches `/user`, but **on-success is `(fn [])` — a no-op**. Response discarded.
- `subs.cljs:429` — reaction reads `(get @app-db :user [])`
- `subs.cljs:432 :following-users` — derived `(set (:following user))`

**So `db[:user]` is only populated by the follow/unfollow flow, starting
from nil ghost state.** The `:user` reg-sub-raw fetches `/user` but
throws the response away. Whatever data the `/user` endpoint returns
(profile, following list, etc.) is never installed. The `:following`
list gets locally assembled via `:follow-user` dispatches that do
`(update nil :following conj username)` → `{:following (...)}`.

**Implication for `::mi5e/remote-item`'s broken guard:**
```clojure
(when (and (:user @app-db) (:token (:user @app-db))) ...)
```
`(:user @app-db)` returns the ghost `{:following ...}` map or nil.
`(:token ...)` has **never** been true because `db[:user]` has never
contained a `:token`. The guard was not "regressed" — it was wrong from
inception in 45ef969 (the commit that introduced the guards). `a0e20a8`
fixed the typo on the sibling `::mi5e/custom-items` sub but missed this
one.

**This is not `:user` vs `:user-data` as a var-renaming collision — they
are two genuinely separate db keys.** But `db[:user]` is near-dead
storage that could be collapsed into `db[:user-data]` alongside the real
user data. Logged as a follow-up cleanup at the bottom of this file.

**Canonical paths in current code:**
- `(-> db :user-data :token)` → JWT
- `(-> db :user-data :user-data :username)` → username (double traversal, see above)
- `(-> db :user-data :theme)` → theme (survives logout)
- `(-> db :user :following)` → follow list (ghost-state storage)

**All auth-related functions and call sites in the codebase:**

| Location | Shape | Role |
|---|---|---|
| `events.cljs:1992 get-auth-token` | `(-> db :user-data :token)` | Retrieves token (2 callers inject to `:http :auth-token`) |
| `event_utils.cljc:29 auth-headers` | `(let [token (-> db :user-data :token)] ...)` | Builds `Authorization` header; re-reads path inline instead of calling `get-auth-token` (DRY miss) |
| `events.cljs:430 authorization-headers` | `(def ... event-utils/auth-headers)` | Alias used by ~25 callers in events.cljs |
| 7× inline `(when (:token (:user-data @app-db)) ...)` | Guard | `::mi5e/custom-items`, `::mi5e/remote-item` (BROKEN — uses `:user`), `:verify-user-session`, `::char5e/characters`, `::party5e/parties`, `:user`, `::folder5e/folders` |
| `events.cljs:1594 :set-user-data` | `(update db :user-data merge ...)` | State mutation |
| `events.cljs:1599 :clear-login` | `(update db :user-data dissoc :user-data :token)` | Removes both nested user-data AND token; theme survives |
| `events.cljs:1795 :login-success` | `(update db :user-data merge (-> response :body))` | Installs token + nested user-data atomically |
| `subs.cljs:247-270` user-data subs | `(-> db :user-data :user-data :username)` etc. | User data field readers |
| `subs.cljs:424 :user sub on-401` | Same action as `:clear-login`, inlined | Only sub that clears login state on 401 |
| `events.cljs:1110,1205` `:follow-user`/`:unfollow-user` | `(update (:user db) :following ...)` | The ONLY live writers of `db[:user]` |
| `events.cljs:1622 :set-user` | `(assoc db :user ...)` | Setter (only called from follow/unfollow) |
| `subs.cljs:416 :user` reg-sub-raw | Fetches `/user`, on-success is `(fn [])` | Response discarded — reg-sub-raw is effectively fire-and-forget |

**State transitions — verified:**

- `:login-success` installs token + nested user-data atomically. No state
  with one but not the other.
- `:clear-login` (and `:user` sub's on-401 handler) removes both atomically.
- Page reload: `user->local-store-interceptor` persists `(:user-data db)`
  after every mutation; `:initialize-db` rehydrates.

**Conclusion**: "has token" and "logged in" are currently equivalent.
`db[:user]` is not part of the login decision — it's ghost state fed by
the follow/unfollow flow. The auth check is always against
`db[:user-data][:token]`.

## Architecture map (kept for branch convenience)

### Client ⇄ server (all confirmed symmetric)
```
POST /api/dnd/e5/items   → routes.clj:968  save-item   → save-entity (create/update by :db/id)
GET  /api/dnd/e5/items   → routes.clj:1004 item-list   → query [?e ::mi5e/owner ?username]
DEL  /api/dnd/e5/items/:id → routes.clj:983 delete-item
```
Owner field `::mi5e/owner`, populated from `(:user identity)` which always
carries username (JWT only encodes username — see `routes.clj:230 create-token`).

### Client subscription chain
```
(reg-sub-raw ::mi5e/custom-items ...)                     equipment_subs.cljs:33
  guard:   (when (:token (:user-data @app-db)))           ; fixed by a0e20a8
  effect:  GET /items, dispatch ::mi/set-custom-items
  read:    (get @app-db ::mi5e/custom-items [])
  ↓
::mi5e/expanded-custom-items     equipment_subs.cljs:54
  ↓ (expand-magic-items: weapon/armor subtype expansion + add-key)
::char5e/sorted-items            equipment_subs.cljs:71   = expanded + SRD delay
  ↓
::mi5e/magic-weapons / magic-armor / other-magic-items   reg-sub, plain
  ↓
::mi5e/magic-weapon-map / magic-armor-map / other-magic-items-map  (map-by-key-or-id)
  ↓
::mi5e/all-magic-items-map     (merge of 3 maps + static)
```

### Template chain (what fills inventory dropdowns)
```
::mi5e/magic-weapon-options  (via magic-item-options xform)
  :<- [::mi5e/magic-weapons]
  ↓
::char5e/template-selections                 equipment_subs.cljs:289
  ↓
::char5e/template
  ↓
:built-template   subs.cljs:300   — propagates reactively to the dropdown
```

### Character build (debounced — affects stats, not dropdown options)
```
:built-character   subs.cljs:342
  reg-sub-raw with debounced-build-sub (500ms leading+trailing)
```

## Bugs — status table (live-updated)

| # | Bug | File:line | Status | Confirmed by |
|---|---|---|---|---|
| 1 | `::char5e/filtered-items` snapshot-to-db staleness | subs.cljs:956 + events.cljs:2379 | **✅ confirmed** | Static reading of filter-items event handler + sub fallback logic |
| 2 | `::char5e/filtered-spells` same shape | subs.cljs:946 + events.cljs:2367 | **✅ confirmed** | Identical pattern to #1 |
| 3 | `::mi5e/remote-item` guard uses stale `:user` key | equipment_subs.cljs:253 | **✅ confirmed** + **orphaned** | Broken from inception in 45ef969; also the sub has zero live subscribers. Resolution via P4: comment out the whole chain, fix the guard inside the discarded form so future restorer gets it right. |
| 4 | Reg-sub-raw reaction cached at `[]` after guest→login transition | equipment_subs.cljs:33 | **Known/documented** in `reframe-subscription-patterns.md`. Current workaround: navigate away and back. Whether to fix is a design decision, not a clear bug. |
| 5 | Silent `:on-401 (fn [])` swallow with no retry | equipment_subs.cljs:44 | **Intentional** per auth-cleanup work. Changing to `:route-to-login` risks login loops. Adding console.warn is safe. |
| 6 | PDF first-click lazy-load race | views.cljs:8065 | **Speculative**. Only affects users who land on character list and click print before `GET /items` returns. No code change proposed without repro. |

## Retracted hypotheses

Listed here so I don't re-discover them.

- **`change-inventory-item-quantity` strips `::char-equip5e/name`** (events.cljs:1385) — *retracted*. `::char-equip5e/name` is only set on user-typed custom items under `::entity/values`. Magic items under `::entity/options` never have this key set. `select-keys` dropping it is a no-op.
- **Load-time race between `:initialize-db` rehydration and first subscribe** — *weakened*. `web/cljs/orcpub/core.cljs:27` calls `dispatch-sync :initialize-db` synchronously before `rdc/render` at line 123. User-data is hydrated before any subscription runs. The race only exists in the narrow guest→login case (see bug #4).
- **Plan to replace `reg-sub-raw` with plain `reg-sub` + eager fetch on login** — *rejected*. Trades away lazy loading (pages that don't need items don't pay the fetch cost) for a speculative recovery path. Lazy load is a legitimate UX feature.
- **Introduce new `logged-in?` helper** — *superseded*. `get-auth-token` at `events.cljs:1992` already exists and is the correct function; it just needs to live in `event_utils.cljc` so the sub files can use it.

## Confirmed non-issues

- `:initialize-db` IS dispatched synchronously before render. Common path is not racing.
- Save/load round-trip is symmetric: both use `(:user identity)`, both serialize via `d/pull '[*]`.
- `item-save-success` correctly upserts by `:db/id` into `::mi/custom-items`. No duplication.
- `from-internal-item` preserves `:db/id` in its `select-keys`, so edits go to `update-entity`.
- No `@(subscribe [::mi5e/custom-items])` callers anywhere. All consumers are chained subs or non-reactive `@re-frame.db/app-db` reads (`options.cljc:1167`, `:1746` — the latter is the canonical pattern for non-reactive contexts per KB Pattern 5).

### What the existing `subs_test.cljs` tests ACTUALLY cover

**Correction**: earlier I said existing tests are "most of" what P5
needs. User pushed back and asked me to verify. Audit result:

| Coverage | Subs |
|---|---|
| Guard skips fetch when no token | characters, parties, :user, folders |
| `login-optional?` doesn't bypass guard | characters, parties |
| Stale `:user` key doesn't accidentally pass | :user |
| Reaction returns default when db empty | all 4 |
| Loading counter not incremented on skip | characters, parties, folders |

**Not covered**:
- On-success dispatches `set-event` with body
- On-401 dispatches correct event(s) (silent / default / conditional /
  compound)
- On-500 dispatches correct event(s)
- Reaction reads cached value once db populated
- `::mi5e/custom-items` at all — coverage gap in current code

**Implication for P5**: existing tests catch **guard regressions**
post-refactor. They do NOT catch **response-handling regressions**.
The `:user` sub's compound on-401 is the highest-risk refactor in P5
and is **not** currently covered. For real P5 verification of
response-handling equivalence, need new tests that stub `:http` and
assert on dispatch paths. KB confirms `reg-fx` stubbing works without
`re-frame-test`.

### DB mocking concern — not needed for guard tests

User raised: "lein-based custom-items test is complicated by the need
for a DB unless one can be mocked." Verified: existing `subs_test.cljs`
avoids the DB question entirely by only testing guard behavior. From
the test file comment (line 58-59):

> The go block will fire and try HTTP (which will fail in test env),
> but the reaction should still return [] since no data is cached yet.

Tests accept silent HTTP failure and only assert on guard-skip /
guard-pass behavior + reaction defaults. No DB needed.

For P1 filter-reactivity tests: no HTTP or DB needed either — just
`reset!` app-db with test data in `::mi/custom-items`, dispatch
filter events, assert sub output.

For P5 response-handling tests: DB not needed, but **HTTP stubbing
is needed** via `(re-frame.core/reg-fx :http (fn [_] ...))` to
return controlled responses and assert the dispatch paths. More work
than the guard-only pattern but achievable without `re-frame-test`.

### Branch restriction — confirmed

System prompt: "DEVELOP all your changes on the designated branch
above. NEVER push to a different branch without explicit permission."
Designated branch: `claude/fix-custom-items-disappearing-DW8rb`.

**Cannot push to `testing/develop`.** Playwright spec must be
staged on the fix branch for later cherry-pick.

**Staging strategy**: put the Playwright spec at its target path
(`e2e/scenarios/custom-items.spec.ts`) on the fix branch. File won't
run without `e2e/` infrastructure (package.json, playwright.config,
fixtures) but it's inert text until it lands on `testing/develop`.
Header comment documents the transfer path:

```typescript
/**
 * STAGED FOR testing/develop
 *
 * This spec depends on e2e/ infrastructure that only exists on
 * testing/develop. To activate: cherry-pick this file onto
 * testing/develop and run Playwright there.
 */
```

Transfer = one cherry-pick or file copy.

## Test strategy

**Correction from earlier in the investigation**: I was hand-wringing
about P5's refactor risk under "no e2e means no verification." That
was wrong — both `lein test`/`lein fig:test` and Playwright exist.

### Available infrastructure

- **`lein test`** (JVM, Clojure): runs `test/clj/` + `test/cljc/`.
  123 tests + 332 assertions per `docs/kb/testing-infrastructure.md`
  on `agents/develop`.
- **`lein fig:test`** (CLJS → browser): compiles `test/cljs/` tests;
  runs interactively in browser (no headless runner configured).
- **Playwright E2E**: lives on `testing/develop` branch. Full
  `e2e/` directory with `playwright.config.ts`, fixture utilities
  (`waitForAppReady`, `setupConsoleCapture`), scenario files,
  agent-friendly JSON reporter, and local + Codespace runner
  scripts. **No existing spec for item-builder / custom-items** —
  would need to be added.
- **Existing `test/cljs/orcpub/dnd/e5/subs_test.cljs`**: 11 tests
  covering the token-guard behavior for 4 of the 5 API subs I'm
  touching (`::char5e/characters`, `::party5e/parties`, `:user`,
  `::folder5e/folders`). **Missing**: `::mi5e/custom-items` tests
  (lives in `equipment_subs.cljs`, no corresponding test file).
- **re-frame.test NOT available** per the KB — testing uses
  `dispatch-sync` + `reset! app-db` + direct deref assertions.

### Per-patch test coverage to add

- **P1** (filter HOF): new regression tests proving
  `filtered-items`/`filtered-spells` recompute when
  `::mi/custom-items` / `::mi/plugins` (for spells) change, plus
  tests that the filter event no longer writes a snapshot to db.
  New file `test/cljs/orcpub/dnd/e5/equipment_subs_test.cljs` or
  extend `subs_test.cljs`.
- **P2** (`get-auth-token` consolidation): existing `subs_test.cljs`
  tests already cover guard behavior for 4 of 5 sites — they'll
  continue passing post-refactor as built-in regression checks.
  Add `::mi5e/custom-items` guard tests (fills a pre-existing
  coverage gap). Add a cljc test for `get-auth-token` itself in
  `test/cljc/orcpub/dnd/e5/event_utils_test.cljc`.
- **P3** (console.warn observability): minimal test surface. One
  test asserting 401 path doesn't dispatch `:route-to-login`
  (preserving current silent behavior).
- **P4** (comment out remote-item): nothing to test — discarded
  forms aren't evaluated, so no subs are registered. Could assert
  `(rf/subscribe [::mi5e/remote-item 1])` doesn't find a registered
  sub, but that's just asserting discarded code is discarded. Skip.
- **P5** (`reg-api-sub` HOF): the critical test surface.
  - Existing subs_test.cljs covers 4 of 5 post-migration sites.
  - Add tests for the `:user` sub's compound `on-401` (dispatches
    `:set-user-data` AND conditionally `:route-to-login`) — the
    trickiest variant.
  - Add a test for the HOF itself: register a throwaway sub via
    `reg-api-sub`, manipulate `app-db`, assert correct response
    shape. Tests the abstraction, not just its applications.

### E2E coverage for #669 (the user-facing symptom)

Playwright spec in `e2e/scenarios/custom-items.spec.ts` on
`testing/develop`:

- `item list refreshes after creating a new custom item`
- `item list refreshes after editing a custom item`
- `item list refreshes after deleting a custom item`
- `filter-then-create still shows new item`

These are the user-visible regressions from #669. Unit tests prove
the data layer; Playwright proves the UI actually re-renders.

### Branch strategy for tests (pending user decision)

Three options:
- (A) Pull e2e infrastructure from `testing/develop` onto
  `claude/fix-custom-items-disappearing-DW8rb`. Bundles everything
  in one branch; cross-cuts are harder to review.
- (B) cljs unit tests on fix branch, e2e spec on `testing/develop`
  as a separate commit after the fix merges. Gap between merge and
  e2e coverage.
- (C) Both — cljs unit tests land with the fix on the fix branch;
  Playwright spec lands on `testing/develop` after merge. Each
  branch stays focused. Cleanest story.

Leaning (C).

### Revised commit sequence (assuming option C)

1. `test: add equipment-subs test file covering custom-items guard + filter reactivity`
2. `fix: make filtered-items/filtered-spells reactive (P1)` — regression tests from step 1 now pass
3. `refactor: move get-auth-token to event_utils, consolidate inline guards (P2)`
4. `fix: log 401 on custom-items fetch for session-stale diagnosis (P3)`
5. `refactor: comment out orphaned remote-item chain with explainer (P4)`
6. `refactor: extract reg-api-sub HOF, migrate 5 API subs (P5)` — existing subs_test.cljs + new custom-items tests verify equivalence
7. (On `testing/develop` after merge) `test: add e2e spec for custom items create/edit/delete refresh`

Each commit independently revertable. Each with tests at the
appropriate layer (unit for data-layer changes, e2e for
user-visible symptom).

## Planned fix — current state

Five patches, all on `claude/fix-custom-items-disappearing-DW8rb`.

### Patch P1: `::char5e/filtered-items` + `::char5e/filtered-spells` via HOF

**Files**: `subs.cljs`, `events.cljs`

Introduce a HOF `reg-filtered-sub` that takes a sorted-sub vector, a
filter-text-sub vector, a filter function, and a min-length. Both subs
use it. The HOF's docstring is the canonical anti-pattern documentation:
storing filtered results in db breaks reactivity when the underlying
list changes.

Drop the snapshot write from both `::char5e/filter-items` and
`::char5e/filter-spells` events — they only store the filter text from
here on.

**Non-obvious benefit (to document in-code + KB)**: per-keystroke filtering
becomes strictly cheaper. The current event-handler path calls
`compute-sorted-items`/`compute-sorted-spells` from scratch every keystroke,
bypassing re-frame's sub cache. Reactive path memoizes the upstream chain
and only re-runs the filter step per keystroke. Counterintuitive unless
you know re-frame memoization rules.

### Patch P2: Move `get-auth-token` to event_utils, use it as the guard

**Files**: `event_utils.cljc`, `events.cljs`, `equipment_subs.cljs`, `subs.cljs`

**Motivation**: the 7 inline guards of the form
`(when (:token (:user-data @app-db)) ...)` are scattered across three
files, and one (`::mi5e/remote-item`, bug #3) is misspelled as `:user`
instead of `:user-data` from inception in 45ef969 (Aug 2025). Verified
by exhaustive grep:

- Only writer of `db[:user]` is `:set-user` at `events.cljs:1624`
- Only callers of `:set-user` are `:follow-user` / `:unfollow-user`,
  both passing `{:following [...]}` shapes — never `:token`
- No macro touches `db[:user]`
- `user->local-store-interceptor` persists `:user-data` only
- `:initialize-db` hydrates `:user-data` only
- `:user` reg-sub-raw's on-success is `(fn [])` — response discarded
- Grep across src/cljs, src/cljc, test/, web/ confirms no other writers

So `db[:user]` has never contained `:token`. Guard has been false 100%
of the time since the commit that introduced it.

**Additional finding**: `::mi5e/remote-item` has ZERO live callers.
Only in-tree reference is at `equipment_subs.cljs:272`, inside a `#_`
reader-discard at lines 268-273 (the commented-out `::mi5e/item`
dispatcher). The broken guard has zero observable effect today —
nothing subscribes to this sub. Fix is hygiene (clean up latent bug
before someone un-comments or adds a caller), not active bleeding.

### Why `get-auth-token` directly (not a wrapper) — SSOT via docstring

Considered adding a `logged-in?` predicate as `(some? (get-auth-token db))`.
Dropped: the wrapper is a one-liner that doesn't do anything the
underlying function doesn't already do. `(when (get-auth-token db) ...)`
is idiomatic Clojure — nil is falsy, retrieval-as-predicate is standard.

**SSOT analysis** — two concerns, one function:

- **Path SSOT** ("where is the token stored?"): `get-auth-token` already
  captures `(-> db :user-data :token)` in one place. Move it to
  `event_utils.cljc` and refactor `auth-headers` to call it and you've
  got one canonical location for the path.
- **Check SSOT** ("what does it mean to be logged in?"): today the
  check is `(some? (get-auth-token db))`, used at all 7 guard sites
  via `when`. This is **SSOT by convention** — uniform call-site
  pattern, not code-enforced. A wrapper would upgrade to code-SSOT,
  but the check is currently trivial enough that convention-SSOT is
  sufficient.

**What would change the calculus**: if the logged follow-up cleanups
(`db[:user]` dead storage, `db[:user-data][:user-data]` double-nesting)
ever land, the definition of "logged in" could naturally become a
compound check (`token present AND profile map present AND not stale`).
At that point, inlining the compound check at call sites would violate
SSOT — that's when `logged-in?` would start earning its keep.

**Decision**: no wrapper today. Strong docstring on `get-auth-token` in
`event_utils.cljc` that documents:

1. The canonical path (`(-> db :user-data :token)`)
2. The dual use (retrieval for HTTP; predicate under `when` for guards)
3. The promotion trigger — if a compound check becomes needed, promote
   the guard usage to a dedicated `logged-in?` predicate at that point;
   don't inline the additional logic at call sites.

The docstring IS the SSOT for the convention. Greppers who land on
`get-auth-token` see the rules immediately.

**Steps**:

1. Move `get-auth-token` from `events.cljs:1992` to `event_utils.cljc`.
   The one live caller (`events.cljs:2166 reset-password`) gets its
   import updated; events.cljs:2000 is commented out and ignored.
2. Refactor `auth-headers` in event_utils to call `get-auth-token`
   internally — eliminates the duplicated path read.
3. Replace the 7 inline guards with `(get-auth-token @app-db)`:
   - `equipment_subs.cljs:37` `::mi5e/custom-items`
   - `equipment_subs.cljs:253` `::mi5e/remote-item` ← fixes bug #3 as side effect
   - `events.cljs:1610` `:verify-user-session`
   - `subs.cljs:389` `::char5e/characters`
   - `subs.cljs:404` `::party5e/parties`
   - `subs.cljs:419` `:user`
   - `subs.cljs:452` `::folder5e/folders`
4. Add `get-auth-token` to the `:refer` lists in `equipment_subs.cljs`,
   `subs.cljs`, `events.cljs` as needed.

**Blast radius** (measured, not estimated):

- Function moves/adds: `get-auth-token` move (1 live caller to update),
  `auth-headers` internal refactor (0 callers affected — signature
  unchanged).
- Guard replacements: 7 line-level changes.
- `:refer` updates: 2-3 files.
- **Tests/web references**: ZERO. Confirmed by grep across `test/` and
  `web/`.
- **Downstream consumers affected**: ZERO. All changes are internal
  function moves + behavior-preserving substitutions. The only
  behavior change is `::mi5e/remote-item` (dead code today).
- **Total diff**: ~15 changed lines, ~5 added lines.

**Non-obvious benefits (to comment in-code + KB)**:

- **Typo class eliminated**: misspelled `get-auth-token` is an
  unresolved-symbol compile error. Misspelled `:user-data` is silent
  nil — the exact failure mode of the `::mi5e/remote-item` bug.
  Routing all seven decisions through one function makes this class
  impossible.
- **DRY**: `auth-headers` currently duplicates the token path read.
  After the refactor, one canonical function holds the path.

### Patch P3: `:on-401` console.warn observability

**Files**: `equipment_subs.cljs`

Change the two `:on-401 (fn [])` no-ops on `::mi5e/custom-items` and
`::mi5e/remote-item` to log a one-line console warning like:
```
"custom-items: 401 fetch rejected; session may be stale"
```

**No behavior change**: no dispatch, no state mutation, no UX change.
**Value**: future debugging has a greppable breadcrumb. Currently users
reporting "items missing" give us nothing to correlate with logs.

**NOT** changing to `:route-to-login` — KB `reframe-subscription-patterns.md`
and the breaking/ work explicitly avoid that because of login-loop risk.

### Follow-up cleanups (logged but not in scope)

These were discovered during the auth-chain audit and the pattern
smell analysis. They are worth fixing in their own patches. **Do not
bundle with the #669 fix** — they touch login flow, local-store
migration, or broad refactors with independent test surfaces.

1. **`db[:user]` dead-storage cleanup**. The `:user` reg-sub-raw fetches
   `/user` but its on-success is `(fn [])` — the response is discarded.
   The only writers are follow/unfollow events that build ghost state
   from nil. `:following` could live under `db[:user-data][:user-data]`
   alongside the real user data. Candidate cleanup: wire the `:user`
   sub's on-success to dispatch `[:set-user (:body response)]`, OR
   collapse `db[:user]` entirely and move `:following` into the real
   user-data path. Touches: subs.cljs:416-429, events.cljs:1110,1205,1622,
   anywhere `:following-users` is used.

2. **`db[:user-data][:user-data]` double-nesting fix**. Caused by
   `:login-success` doing `(update db :user-data merge (-> response :body))`
   with a body shaped `{:token ... :user-data {...}}`. Fix at the merge
   site — unwrap token and profile into sibling keys (e.g., `:token` +
   `:profile`), or assoc-in explicitly. Touches login-success,
   set-user-data, clear-login, all user-data field subs
   (subs.cljs:253-270), views.cljs:1525, local-store interceptor, and
   needs a migration for existing localStorage entries that carry the
   old shape.

3. **`reg-api-sub` template extraction**. Five reg-sub-raw API-backed
   subs (`::mi5e/custom-items`, `::mi5e/remote-item`, `::char5e/characters`,
   `::party5e/parties`, `::folder5e/folders`, plus `:user` which is
   similar) all follow the same ~10-line boilerplate: auth guard,
   loading counter increment, `http/get` with `auth-headers`, loading
   decrement, `handle-api-response` with success dispatch + 401
   handler + context, wrapped in `ra/make-reaction` reading a db key.

   Only ~5 elements vary: sub-key, URL, success event, on-401
   behavior, db-key for the reaction read. Each site could collapse
   from ~14 lines to ~5 lines of configuration.

   **Why it's a separate patch, not bundled**:

   - Subs have subtle variations that fight a simple HOF. `::mi5e/remote-item`
     stores to a keyed nested map and dispatches a merge event instead
     of replacing at a top-level key. `:user` has a custom on-401 that
     mutates login state. `::char5e/characters` / `::party5e/parties`
     have conditional on-401 gated on a `login-optional?` query arg.
     A fully-generic HOF needs 6-8 parameters and savings per site
     shrink as the parameter count grows.
   - The right abstraction may not be a HOF. Candidates: a `reg-api-sub`
     HOF, a `defapi-sub` macro, or a smaller helper that wraps just
     the "go + fetch + handle-response + loading counter" block and
     leaves `reg-sub-raw` and `ra/make-reaction` explicit at call
     sites. Picking between those is its own design pass.
   - The 5 sites aren't going anywhere. Parking this is cheap; doing
     it right later is not blocked by anything.

   **When to revisit**: after the #669 fix ships, either as a dedicated
   pattern-extraction session or piggybacked on whoever touches these
   files next for another reason. Worth a KB entry in `docs/kb/` on
   `agents/develop` documenting the decision tree between HOF / macro
   / helper before the refactor starts.

### Patch P4: Document-and-comment the orphaned `::mi5e/remote-item` chain

**Files**: `equipment_subs.cljs`, `events.cljs`

The `::mi5e/remote-item` machinery is groundwork for cross-user item
viewing (item sharing). The bulk `GET /items` endpoint only returns
items the current user owns; the server has `GET /items/:id` which
returns any item by db-id regardless of owner, and this client-side
chain was meant to consume it. The live `views/item-page` at
`views.cljs:3874` punts on this — it subscribes to `::mi/custom-item`
directly, which only works for items you already own.

**Status confirmed orphaned**:
- `::mi5e/remote-items` (plural) reg-sub: registered, zero subscribers
- `::mi5e/remote-item` (singular) reg-sub-raw: registered, zero subscribers
- `::mi/add-remote-item` event: registered, only dispatched from inside
  the unsubscribed reg-sub-raw
- `::mi5e/item` dispatcher: already `#_` reader-discard, labeled
  "Groundwork... restore when needed"

**Item sharing is on the roadmap but not at the top** (per repo owner).
The groundwork should be preserved, not deleted.

**Action**: wrap all four orphaned forms in `#_` reader-discards with
a block-comment header explaining what they do, the chain, why they're
commented out, and a concrete restore checklist. **Inside** the
commented `::mi5e/remote-item`, fix the guard from `(:user @app-db)`
to `(get-auth-token @app-db)` so the future restorer doesn't hit the
same typo that broke it originally.

**Block-comment contents (for equipment_subs.cljs above the #_ block)**:

```
ORPHANED: Cross-user item detail fetch — commented out, kept for reference.

PURPOSE: Groundwork for viewing magic items owned by OTHER users
(item sharing feature, roadmap, not yet prioritized).

Why this exists: bulk GET /items returns only items where
::mi5e/owner = current username. Server also has GET /items/:id
(routes.clj:976 get-item) which returns ANY item by db-id.
This chain was intended as the client-side consumer for the
by-id endpoint. The live views/item-page (views.cljs:3874)
bypasses it by subscribing to ::mi/custom-item directly, so
visiting /items/<id> for an item you don't own silently falls
back to "not found."

CHAIN (when restored):
  views/item-page calls (subscribe [::mi5e/item item-key])
    ↓ ::mi5e/item dispatcher (below)
    ├── int key:  (subscribe [::mi5e/remote-item id])
    │     ↓ fires GET /api/dnd/e5/items/:id with auth headers
    │     ↓ dispatches [::mi5e/add-remote-item (:body response)]
    │     ↓ stores under db[::mi5e/remote-items][id]
    │     ↓ reaction reads that path
    └── kw key:  (get mi5e/all-equipment-map key) via ra/make-reaction

COMMENTED OUT because:
- Half-alive: registered in signal graph but no live subscribers.
- Broken from inception (45ef969 Aug 2025): auth guard was
  (:token (:user @app-db)) but token lives at [:user-data :token].
  Guard has been false 100% of the time, so the fetch never fired
  even if a subscriber had existed. Fixed below so future restorer
  doesn't hit the same trap.
- Leaving registered with no consumers was confusing to auditors
  (re-discovered repeatedly).

TO RESTORE when item sharing is implemented:
1. Uncomment the four forms below and the ::mi/add-remote-item
   event in events.cljs (~line 2673).
2. Update views/item-page (views.cljs:3874) to subscribe to
   [::mi5e/item key] instead of [::mi/custom-item item-key], so
   numeric id keys route through the remote fetch.
3. Consider whether the remote fetch should be an explicit event
   on route-mount rather than a reg-sub-raw side effect — the
   modern pattern. See docs/kb/reframe-subscription-patterns.md
   on agents/develop.
4. Product decisions needed: can viewers edit items they don't
   own? Favorite? Clone? Decide before wiring the UI.
5. Add a KB entry to docs/kb/ on agents/develop documenting the
   cross-user item fetch chain once it's wired and working.
```

**Back-reference comment** at events.cljs:2673-2676 (where
`::mi/add-remote-item` lives) pointing at the explainer:

```
;; See equipment_subs.cljs ::mi5e/remote-item block-comment —
;; this event is part of the orphaned cross-user item fetch
;; chain. Kept as groundwork, commented out together with the
;; rest of the chain. Do not remove in isolation.
```

**Blast radius**: three #_ insertions, one block comment header,
one back-reference comment. ~60 lines total touched (mostly comment
text, not code). Zero behavior change. The `::mi5e/remote-item`
guard fix is inside the discarded form so it's inert today.

### Dropped from plan

- **Patch 4 (`:login-success` dispatches fetch)** — the KB documents the
  stale-reaction-after-login behavior as known with a "navigate away and
  back" workaround. Adding a login-success dispatch would change that
  from accepted behavior to a fix. Not a clear call; deferred pending
  explicit user decision.
- **PDF first-click lazy-load race** — speculative without repro.
- **Plugin → magic-items pipeline** — different, larger problem.

## Reconciliation plan for `agents/develop`

At the end of this investigation, reconcile findings into the KB per
project convention (docs/kb is the OrcPub Agent Knowledge Base).

### New KB entries to add

1. **`docs/kb/filter-sub-anti-pattern.md`** — document the snapshot-to-db
   anti-pattern, the `reg-filtered-sub` HOF fix, and why phase-1 subscribe
   refactor (`re-frame-subscribe-refactor.md`) preserved the anti-pattern
   (its scope was warnings, not cache staleness). Cross-link from the
   existing subscribe-pattern docs.

### KB entries to update

1. **`docs/kb/env-and-auth.md`** — explicit mention of `::mi5e/remote-item`
   as the remaining typo instance after `a0e20a8`, or mark it as fixed
   once the patch lands. Currently the KB is silent on this sub and a
   casual reader might assume all siblings were fixed together.

2. **`docs/kb/reframe-subscription-patterns.md`** — add `get-auth-token`
   as the canonical guard function (not just the inline pattern). Cross-
   link to `env-and-auth.md`.

3. **`docs/kb/http-fx-patterns.md`** — brief note on silent `:on-401`
   swallow as an intentional pattern to avoid login loops, with the
   caveat that adding console.warn for diagnostics is zero-risk.

### Process lesson to capture

Add to `DOC-CONVENTIONS.md` or a new onboarding section: **"Read the
docs/kb/ index on agents/develop before investigating domain issues."**
The KB is already comprehensive; investigations that skip it end up
re-deriving documented patterns and risk missing nuances.

## Session activity log

Appended live as the investigation proceeds.

- **Initial reading**: static analysis of equipment_subs.cljs, events.cljs,
  routes.clj; git history of custom-items hotfixes.
- **First (over-broad) plan**: proposed converting reg-sub-raw to plain
  reg-sub + eager fetch on login. Rejected by repo owner — lazy loading
  has UX value.
- **Retraction**: `change-inventory-item-quantity` name-stripping
  hypothesis withdrawn after re-reading `::char-equip5e/name` call sites.
- **Narrowed scope**: scope limited to server-persisted custom items per
  repo owner. Orcbrew export is separate.
- **Found filtered-items bug**: user prompt about item list not
  refreshing → confirmed in static reading of `::char5e/filter-items`
  event + `::char5e/filtered-items` sub.
- **Found dropdown/PDF chain distinction**: user prompt led to tracing
  `:built-template` (reactive, immediate) vs. `:built-character`
  (debounced) vs. PDF `plugin-data` capture (snapshot at render).
- **Discovered agents/develop KB**: user corrected "agents/develop is a
  branch, not a path." Fetched branch, read 5 relevant KB entries.
  Reconciled findings — filtered-items bug is genuinely new, remote-item
  typo is real, most other analysis was duplicative.
- **Discovered get-auth-token already exists**: user prompt led to
  finding events.cljs:1992 — first-pass revision of Patch P2 to reuse
  it as the guard predicate.
- **Deeper auth-chain audit** (earlier this session): user pushed back
  on the "just reuse `get-auth-token`" plan and asked whether
  `logged-in?` is more semantic. Exhaustive grep of auth-related sites
  revealed 7 inline guards (one with the `:user` typo from
  `::mi5e/remote-item`), plus `get-auth-token`, `auth-headers`,
  `authorization-headers` alias, login/logout state mutations.
  Initial revision: add `logged-in?` alongside `get-auth-token` as
  separate predicate, with extensibility and semantic intent arguments.

- **`logged-in?` final drop** (earlier, this session): user asked
  "so `logged-in? db` is just going to swap out `get-auth-token db`?"
  — correct instinct. The wrapper is a one-liner `(some? ...)` that
  doesn't add any check the underlying function doesn't already
  provide. The "grep role separation" and "reading clarity" arguments
  are real but tiny and don't clear the "legitimate reason to exist"
  bar. Dropped.

- **Test-strategy correction** (latest session): user called out
  that I was hand-wringing about "no e2e means no verification" when
  both `lein fig:test` (cljs) and Playwright (`testing/develop`) are
  available. Existing `test/cljs/orcpub/dnd/e5/subs_test.cljs`
  already covers the token-guard behavior for 4 of the 5 API subs
  I'm refactoring — those tests are built-in regression checks for
  P2/P5. Need to add: `::mi5e/custom-items` guard coverage (fills a
  pre-existing gap), filter-reactivity regression tests for P1,
  compound on-401 test for the `:user` sub variant in P5, and a
  test for the `reg-api-sub` HOF itself. E2E for #669's user-facing
  symptom goes to `testing/develop` as a separate follow-up commit.
  Full test strategy documented above (see "Test strategy" section).

- **`::mi5e/remote-item` scope decision** (earlier, this session): user asked
  whether the sub is orphaned and what it's supposed to do. Verified
  all four pieces (remote-items plural, remote-item singular,
  add-remote-item event, #_'d ::mi5e/item dispatcher) — registered
  but zero live subscribers. Purpose: groundwork for cross-user item
  viewing (item sharing roadmap feature, not top priority). Server
  endpoint GET /items/:id exists and returns any item by id; client
  consumer was half-written. Live item-page bypasses it via
  ::mi/custom-item, so cross-user URLs silently fail.

  Decision: **Option C-plus** — fix the guard AND comment out the
  whole chain with a block-comment explainer. Preserves groundwork,
  removes the confusing "registered but no consumers" state,
  documents the chain and restore checklist so future implementer
  (or agent re-auditing the code) has full context without needing
  to reconstruct it. Became new Patch P4.

- **Pattern smell — reg-sub-raw API template** (earlier, this session): user
  owned their "noodle around when I see the same thing repeated"
  instinct and asked whether there's a deeper template underneath the
  `(some? (get-auth-token db))` idiom. Traced the five API-backed
  reg-sub-raw subs and confirmed: yes, they all follow the same
  ~10-line boilerplate (guard, loading counter, http/get with
  auth-headers, handle-api-response, reaction wrapping). Extracting
  a `reg-api-sub` helper would collapse each site from ~14 lines to
  ~5 lines. BUT: the 5 subs have enough subtle variation
  (`::mi5e/remote-item` uses a nested db key and a merge-dispatch,
  `:user` has a custom on-401 that mutates login state, characters/
  parties have conditional on-401 via `login-optional?`) that a clean
  HOF needs 6-8 parameters and the right abstraction might be a
  macro, not a HOF. Decision: log as follow-up #3, do not bundle
  with #669. The noodle was finding a real pattern, just bigger than
  the `(some? ...)` layer and too much to execute mid-patch.

- **SSOT follow-up** (earlier, this session): user asked whether
  `(some? (get-auth-token db))` can be made SSOT. Clarified two
  concepts: PATH SSOT (already handled by `get-auth-token` — it owns
  `(-> db :user-data :token)`) vs CHECK SSOT ("what does 'logged in'
  mean?", currently SSOT-by-convention at 7 uniform call sites). A
  `logged-in?` wrapper would upgrade convention-SSOT to code-SSOT but
  is marginal today because the check is trivial. Landed on: add a
  strong docstring to `get-auth-token` that explicitly documents the
  dual use and the promotion trigger (if compound checks become
  needed after the `db[:user]` / double-nesting follow-ups, promote
  to `logged-in?` at that moment). The docstring IS the SSOT for the
  convention — no code added, rules stay visible to anyone grepping
  the function.

- **Pragmatic reasoning correction** (earlier this session): user pushed
  back on both earlier positions — (a) don't roll over on pushback
  without reasoning, (b) verify the `:user` vs `:user-data` claim
  rigorously because macros/interceptors could be hiding things, (c)
  quantify blast radius. Did the work:

  1. **`logged-in?` decision (superseded above)**: re-weighed honestly
     with grep-role-separation and reading-clarity arguments. User
     subsequently correctly observed the wrapper was still thin enough
     to drop.

  2. **Guard-broken claim verified**: grep for all writers of `db[:user]`
     found only `:set-user` at events.cljs:1624, called only from
     follow/unfollow with `{:following ...}` shapes. No macros touching
     db[:user]. `user->local-store-interceptor` persists `:user-data`
     only. `:initialize-db` hydrates `:user-data` only. `:user`
     reg-sub-raw's on-success is `(fn [])` — response discarded.
     VERIFIED: `db[:user]` has never contained `:token`.

     BUT ALSO DISCOVERED: `::mi5e/remote-item` has zero live callers.
     Only in-tree reference is inside a `#_` reader-discard at
     `equipment_subs.cljs:272`. The broken guard has zero observable
     effect today. Fix is hygiene, not bleeding.

  3. **Blast radius measured**: function moves touch 1 live caller
     (reset-password). 7 guard call sites replaced. 2-3 `:refer`
     updates. Zero test/ or web/ references. Zero downstream
     consumers affected. Total ~20 changed lines.

  Earlier "artificial-complexity" entry below remains valid for the
  retraction of the extensibility argument — that retraction stands.
  What changed is: I over-corrected to dropping `logged-in?` entirely,
  and the user pushed back on THAT. Restored with honest reasoning.

- **Artificial-complexity correction** (earlier session): user called out
  (a) the extensibility argument as speculative — I couldn't name a
  concrete scenario where "logged in" would grow beyond "has token";
  (b) my "note the double traversal — bad upstream naming, but it's
  the reality" shrug as non-KISS resignation; (c) not finishing the
  `:user` vs `:user-data` trace before labeling them "separate keys."

  Corrections:
  1. Dropped `logged-in?`. Revised Patch P2 to move `get-auth-token` to
     event_utils.cljc and use it directly as the guard at all 7 sites.
     No new predicate. KISS.
  2. Finished tracing `db[:user]`: it's NOT a rename collision with
     `:user-data`, they are separate top-level keys. But `db[:user]` is
     NEAR-DEAD storage — the `:user` reg-sub-raw fetches `/user` but
     its on-success is `(fn [])`, discarding the response. The only
     writers are `:follow-user`/`:unfollow-user` which build
     `{:following ...}` from nil ghost state. The `::mi5e/remote-item`
     guard has been broken since inception (45ef969), not regressed
     from a rename — it was checking for `:token` under a key that
     has NEVER contained a token.
  3. Flagged `db[:user-data][:user-data]` double-nesting as fixable
     (unwrap at `:login-success` merge site) rather than immutable.
     Added as a follow-up cleanup, not in current scope.
  4. Flagged `db[:user]` dead-storage as a follow-up cleanup.

  Updated auth chain map to reflect `db[:user]` as a separate branch
  with its (near-dead) status and actual writers. This is the state
  that should reconcile back to `agents/develop` KB — the existing
  `env-and-auth.md` doesn't capture any of it.
