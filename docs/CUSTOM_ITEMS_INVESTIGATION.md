# Custom Items "Disappearing" — Investigation Notes

Working notes for Orcpub/orcpub#669 and related reports of server-persisted
custom magic items appearing blank, missing, or stale.

Branch: `claude/fix-custom-items-disappearing-DW8rb`

## Scope

**In scope**: server-persisted custom items (Item Builder → `POST /items` →
Datomic → `GET /items`). These are **not** exportable/importable — they live
only in Datomic, owned by `::mi5e/owner = username`.

**Out of scope**:
- `.orcbrew` plugin export ("M" validation errors) — tracked separately.
- Plugin-defined magic items — the spec technically allows them under
  `::plugin`'s open content-keyword, but **no subscription in this tree wires
  plugin magic items into any item map** (`::mi/magic-weapon-map`,
  `::mi/magic-armor-map`, etc). Confirmed by grep: `equipment_subs.cljs`
  contains zero `:plugins` references, and `spell_subs.cljs` has plugin
  passthrough subs for spells/monsters/races/backgrounds/etc. but not for
  items. If orcbrew content with magic items has ever worked on the fork,
  the wiring is gone today.

## Architecture map (so we don't re-discover this)

### Client ⇄ server
- `POST /api/dnd/e5/items` → `routes.clj:968 save-item` → `save-entity`
  (create or update based on `:db/id`). Owner field: `::mi5e/owner`, value
  from `(:user identity)` (always username — JWT only carries username, see
  `routes.clj:230 create-token`).
- `GET /api/dnd/e5/items` → `routes.clj:1004 item-list` → query
  `[?e ::mi5e/owner ?username]`. **Single-value lookup**, no email fallback
  (compare `character-list` at `routes.clj:1014` which does look up by
  both username AND email). Symmetric with save since both use
  `(:user identity)`, so username-only is fine.
- `DELETE /api/dnd/e5/items/:id` → `routes.clj:983 delete-item`.

### Client-side data flow
```
(reg-sub-raw ::mi5e/custom-items ...)           equipment_subs.cljs:33
  guard:   (when (:token (:user-data @app-db))) ; fixed by a0e20a8
  effect:  GET /api/dnd/e5/items, dispatch ::mi/set-custom-items
  read:    (get @app-db ::mi5e/custom-items [])

::mi5e/custom-items                              db key
  ↓
::mi5e/expanded-custom-items                     equipment_subs.cljs:54
  ↓ (expand-magic-items: weapon/armor subtype expansion + add-key)
::char5e/sorted-items                            equipment_subs.cljs:71
  = expanded + SRD sorted-items (delay-realized once)
  ↓
::mi5e/magic-weapons / magic-armor / other-magic-items
  ↓
::mi5e/magic-weapon-map / magic-armor-map / other-magic-items-map
  (via map-by-key-or-id — keyed by BOTH :key AND :db/id)
  ↓
::mi5e/all-magic-items-map                        (merge of 3 maps + static)
```

### Template selection chain (what fills the inventory dropdowns)
```
::mi5e/magic-weapon-options (magic-item-options xform)
  :<- [::mi5e/magic-weapons]
  ↓
::char5e/template-selections                      equipment_subs.cljs:289
  ↓
::char5e/template                                 equipment_subs.cljs:329
  ↓
:built-template                                   subs.cljs:300
```

All plain reg-sub — propagates reactively through the chain when
`::mi/custom-items` changes in db.

### Character build (for computed stats)
```
:built-character                                  subs.cljs:342
  reg-sub-raw with debounced-build-sub (500ms leading+trailing)
  Only fires do-build 500ms after :character or :built-template settles.
```
Relevant when evaluating costs: dropdowns use `:built-template` (immediate),
but any derived character stats go through the debounce.

## Confirmed bugs

### A. `::char5e/filtered-items` returns a frozen snapshot after first filter interaction

`subs.cljs:956-962` composes `:<- [:db]` + `:<- [::char5e/sorted-items]` and
returns `(or (::char5e/filtered-items db) sorted-items)`. The `db` branch
wins when present.

`events.cljs:2379-2387` `::char5e/filter-items` handler **unconditionally**
snapshots `compute-sorted-items` into `db[::char5e/filtered-items]` on
every filter keystroke (even a single character, even a cleared box).
Once stored, the snapshot stays in db for the rest of the session —
nothing clears it. Any subsequent `item-save-success`,
`::mi/delete-custom-item`, or edit that updates `::mi/custom-items`
mutates the live reactive chain but the item list page keeps rendering
the stale snapshot because the sub prefers the db branch.

Symptom: user edits/creates/deletes items on `/items` list page with the
filter box touched → list does not refresh → hard reload fixes it.

**`::char5e/filtered-spells` at `subs.cljs:946-954` has the identical bug
shape.** Same fix should apply.

### B. `::mi5e/remote-item` still uses the old `:user` guard

`equipment_subs.cljs:253`:
```clojure
(when (and (:user @app-db) (:token (:user @app-db))) ...)
```

Hotfix `a0e20a8` fixed the sibling `::mi5e/custom-items` guard from
`:user` to `:user-data` but missed this one. Guard is always false →
single-item remote fetch never runs. Affects item detail pages only.
One-line fix.

### Historical
- `45ef969` (Aug 27 2025): introduced the guards on both custom-items and
  remote-item with the wrong key (`:user` instead of `:user-data`).
- `a0e20a8` (Sep 7 2025): fixed custom-items guard, missed remote-item.

## Retracted / wrong hypotheses

### `change-inventory-item-quantity` stripping `::char-equip5e/name` (events.cljs:1385)

Looked like a name-fallback bug, but `::char-equip5e/name` is only ever
set by `::char5e/new-custom-item` and `::char5e/set-custom-item-name`,
both of which operate on `[:character ::entity/values ...]` (user-typed
custom items). Magic items live under `[:character ::entity/options ...]`
where `::char-equip5e/name` is never set. `select-keys` dropping it is a
no-op for magic items. **Not a bug.**

### Load-time race on `::mi/custom-items` lazy-load

Initial hypothesis: `reg-sub-raw` fires on first subscribe, might race with
user-data rehydration. **Weakened**: `web/cljs/orcpub/core.cljs:27` calls
`(dispatch-sync [:initialize-db])` synchronously before `rdc/render` at
line 123. By the time any subscription runs, user-data is already hydrated
from localStorage. The race only matters in the narrow "guest → login
without refresh" case where the reaction is cached at `[]` and doesn't
re-fire on login.

### Plan to replace `reg-sub-raw` with plain `reg-sub` + eager fetch on login

Rejected: would trade away lazy loading (a legitimate UX feature — pages
that don't use items don't pay the fetch cost) for an eager fetch that
speculatively tries to fix a path we haven't proven is the cause. The
lazy-on-first-subscribe pattern is correct; the bugs are elsewhere.

## Open questions

### Does the character builder inventory dropdown actually fail to update after item save/edit/delete?

Reported from memory. Code tracing says it **should** be reactive: the
dropdown options come from `entity/selection-options` on the current
`selection`, which is derived from `:built-template`, which chains down
to `::mi5e/custom-items` through plain reg-subs. When `::mi/custom-items`
updates via `item-save-success`, the chain should invalidate all the way
up. Needs live verification — if this is actually broken, the mechanism
is not obvious from static reading.

### Does PDF export use stale data?

PDF export's `plugin-data` is snapshotted at render time of the character
list row (`views.cljs:8065-8076`):
```clojure
plugin-data {:all-magic-items-map @(subscribe [::mi/all-magic-items-map])
             ...}
```
Subscribing to `::mi/all-magic-items-map` triggers the custom-items chain
(and its lazy-load fetch on first subscribe). If the user navigates
directly to `/characters` and clicks print *before* the async
`GET /items` response comes back, the PDF plugin-data snapshot could be
generated with incomplete custom-items. Needs repro/timing verification.

### Why are 2 of 3 #669 reporters needing manual refresh?

Candidates, in order of plausibility:
1. Filter-snapshot bug (Bug A) — if they were on `/items` page after filtering.
2. Stuck reg-sub-raw reaction after a silent 401 — if their token went
   stale mid-session and they kept browsing.
3. PDF export timing race — only if they were specifically reporting
   PDF issues.

## Confirmed non-issues

- `:initialize-db` IS dispatched synchronously before render
  (`web/cljs/orcpub/core.cljs:27`). User-data rehydration is not racing
  with first render in the common path.
- Save/load round-trip is symmetric: both use `(:user identity)` as owner,
  both serialize via `d/pull '[*]`, same namespaced keyword format.
- `item-save-success` (events.cljs:491) correctly upserts by `:db/id`
  into `::mi/custom-items`. No duplication on update.
- `from-internal-item` (magic_items.cljc:211) preserves `:db/id` in its
  `select-keys`, so edits go down the update-entity path, not
  create-entity.
- No code in the tree calls `@(subscribe [::mi5e/custom-items])` directly;
  all consumers use chained subs or read `db[::mi/custom-items]` from
  `re-frame.db/app-db` in non-reactive contexts (`options.cljc:1167`,
  `:1746`).

## Planned fixes (pending sign-off)

1. Rewrite `::char5e/filtered-items` as a pure reactive sub composing
   `[::char5e/sorted-items]` + `[::char5e/item-text-filter]`. Drop the
   snapshot from `::char5e/filter-items` event — only store the filter
   text.
2. Same fix for `::char5e/filtered-spells` / `::char5e/filter-spells`.
3. `::mi5e/remote-item`: `:user` → `:user-data` (finish `a0e20a8`).

**Not** planned for this pass:
- No conversion of `::mi5e/custom-items` from reg-sub-raw (lazy load stays).
- No fetch dispatch on `:login-success`, `:initialize-db`, or route mounts.
- No changes to `handle-api-response` or the `:on-401 (fn [])` silent path.
- No retry logic.
- No plugin → magic-items pipeline (separate, larger change).
