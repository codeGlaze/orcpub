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

### Patch P2: Move `get-auth-token` to `event_utils.cljc`, use it as reg-sub-raw guard

**Files**: `event_utils.cljc`, `events.cljs`, `equipment_subs.cljs`, `subs.cljs`

1. Move `get-auth-token` from `events.cljs:1992` to `event_utils.cljc`
   (alongside `auth-headers`).
2. Refactor `auth-headers` to call `get-auth-token` internally (DRY).
3. Update existing callers in `events.cljs` (the two `:auth-token` uses
   at line 2000 and 2166) to import from event_utils if not already.
4. Replace the five `(:token (:user-data @app-db))` inline guards in
   `reg-sub-raw` subs with `(get-auth-token @app-db)`:
   - `equipment_subs.cljs:37` `::mi5e/custom-items`
   - `equipment_subs.cljs:253` `::mi5e/remote-item` ← **picks up the `:user`→`:user-data` fix as a side effect**
   - `subs.cljs:389` `::char5e/characters`
   - `subs.cljs:404` `::party5e/parties`
   - `subs.cljs:419` `:user`
   - `subs.cljs:452` `::folder5e/folders`

**Non-obvious benefit**: the function-as-predicate form is typo-resistant.
A misspelled `get-auth-token` is an unresolved-symbol compile error; a
misspelled `:user-data` is silent nil. That's the exact failure mode of
`::mi5e/remote-item`, and routing all six guards through one function
makes the same class of bug impossible going forward.

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
  finding events.cljs:1992 — obviates my proposed `logged-in?` helper.
  Revised Patch P2 to move existing function into event_utils.cljc.
