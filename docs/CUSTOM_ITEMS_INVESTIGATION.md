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
| 3 | `::mi5e/remote-item` guard uses stale `:user` key | equipment_subs.cljs:253 | **✅ confirmed** | Git blame: a0e20a8 fixed the sibling sub but missed this one; env-and-auth KB doesn't claim it was fixed |
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

### Patch P2: Move `get-auth-token` to event_utils and use it as the guard

**Files**: `event_utils.cljc`, `events.cljs`, `equipment_subs.cljs`, `subs.cljs`

**Motivation**: the 7 inline guards of the form
`(when (:token (:user-data @app-db)) ...)` are scattered across three
files, and one of them (`::mi5e/remote-item`, bug #3) was misspelled as
`:user` instead of `:user-data` from inception (commit 45ef969). The
hotfix `a0e20a8` fixed the sibling `::mi5e/custom-items` sub but missed
this one. Routing all 7 through a single function eliminates the typo
class entirely.

`get-auth-token` already exists at `events.cljs:1992` and returns the
token-or-nil. It is already suitable as a guard because nil is falsy.
**No new function is needed.** Adding `logged-in?` would be artificial
complexity — I can't name a concrete scenario where "logged in" would
mean more than "has token," and the speculative extensibility argument
doesn't justify a new name in a KISS/DRY codebase.

**Steps**:

1. Move `get-auth-token` from `events.cljs:1992` to `event_utils.cljc`
   (alongside `auth-headers`, which the sub files already import).
2. Refactor `auth-headers` in event_utils to call `get-auth-token`
   internally — currently re-reads `(-> db :user-data :token)` inline
   instead of delegating. Eliminates the duplication.
3. Update the existing `:auth-token (get-auth-token db)` callers in
   events.cljs (lines 2000, 2166) to import from event_utils if needed.
4. Replace the 7 inline guards with `(get-auth-token @app-db)`:
   - `equipment_subs.cljs:37` `::mi5e/custom-items`
   - `equipment_subs.cljs:253` `::mi5e/remote-item` ← picks up `:user`→`:user-data` fix
   - `events.cljs:1610` `:verify-user-session`
   - `subs.cljs:389` `::char5e/characters`
   - `subs.cljs:404` `::party5e/parties`
   - `subs.cljs:419` `:user`
   - `subs.cljs:452` `::folder5e/folders`

**Non-obvious benefits (to comment in-code + KB)**:

- **Typo class eliminated**: misspelled `get-auth-token` is an unresolved
  symbol error at compile time. Misspelled `:user-data` is silent nil.
  That's the exact failure mode of the `::mi5e/remote-item` bug. Routing
  all seven decisions through one function makes this class of bug
  impossible going forward.
- **DRY**: `auth-headers` currently duplicates the token path read.
  One canonical function for "where is the token stored" instead of
  two functions and seven inlined reads.

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

These were discovered during the auth-chain audit and are worth fixing
in their own patches. **Do not bundle with the #669 fix** — they touch
login flow, local-store migration, and/or have independent test surfaces.

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

- **Artificial-complexity correction** (this session): user called out
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
