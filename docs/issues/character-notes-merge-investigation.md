# Character notes merge/transfer investigation

## Status

- **Symptom**: reporter has two clones of the same character (different levels); lower-level's notes appear empty, higher-level's notes hold what lower used to have.
- **Reproducible**: yes — recurred after the user rebuilt notes on both characters.
- **Root cause**: not conclusively identified. Leading hypothesis is multi-tab contamination of the floating `:character` slot. The code audit found two corroborating smells (stale-db in `level-up`, builder-vs-sheet slot divergence) but no single smoking gun.
- **Recommended action**: ship the three-layer fix in the "Fixes" section below; they are independently valuable and together close every mechanism we've identified.

## Symptom mechanics

Notes live on a per-character component sub-entity:

- Datomic: `::se/values` is `{:db/valueType :db.type/ref, :db/cardinality :db.cardinality/one, :db/isComponent true}` (`schema.clj:182-185`).
- `::char5e/notes` is a string attribute of that sub-entity (`schema.clj:234-237`).

For lower's notes content to appear on higher, one of these must have happened:

1. Higher's `::se/values` entity was written with lower's text (normal save path, but the payload came from the wrong source).
2. Higher and lower reference the same `::se/values` sub-entity in Datomic (shared `:db/id`).
3. A save for one character routed its payload to the other character's `:db/id` at the top level.

Mechanism 2 is the most "persistent looking," but it produces *both characters showing the same notes* — not "one empty, one populated." That steady state only fits mechanism 1 or 3 plus an observation-timing artifact.

## Leading hypothesis: multi-tab contamination of the floating `:character` slot

**Setup.** The app has two places a character lives in app-db:

| Slot | Key | Written by |
|------|-----|-----------|
| `:character` (single floating edit) | n/a | builder edits via `character-interceptors`; `:set-character`; save-success; clone; level-up |
| `[::char5e/character-map int-id]` | Datomic `:db/id` | `::char5e/set-character`; `update-character-fx`; fetch sub responses |

The character **sheet** (`/characters/:id`) and the inline **list-expand** view both read and write by id through `character-map`. These paths are correctly isolated per character.

The **builder** is the anomaly:

- Its URL (`/character-builder`, `route_map.cljc:177`) has **no id**.
- It renders `[character-display nil true 1]` (`character_builder.cljs:1893,1908`), so the notes textarea calls `(set-notes-handler nil)`, and `update-character-fx` with `id=nil` (`events.cljs:2432`) writes to `:character`. **No autosave is queued.** Manual save reads `(:character db)` and posts with whatever `:db/id` is in there.
- `:character` is persisted to `localStorage` key `"character"` via `db-char->local-store` (`events.cljs:105`). The app does NOT listen for the DOM `storage` event (no cross-tab sync in memory).

**The guard** in `::char5e/set-character` (`events.cljs:2063-2065`) only replaces `:character` with an incoming server result when the incoming id matches `:character`'s existing id AND `:character` has no unsaved changes. It protects unsaved builder edits. It does **not** reconcile `:character` against "which character is this tab supposed to be editing."

**Contamination sequence.** Two tabs on the builder for different characters:

1. Tab A saves lower → `localStorage["character"] = lower`. Tab B saves higher → `localStorage["character"] = higher`.
2. Tab A reloads (crash, sleep, accidental refresh). `:initialize-db` reads `localStorage` → Tab A's `:character` is now **higher**.
3. Builder in Tab A has no id in the URL; it just renders `:character`. Tab A's user sees higher's data in what they think is lower's tab.
4. User types notes in Tab A thinking they're editing lower. `(set-notes-handler nil)` writes to `:character` (which is higher).
5. Manual save posts `:character` → server writes lower's intended text onto higher's entity.
6. End state: higher has lower's content, lower is unchanged (or empty, if the user had already cleared it expecting to rebuild).

This reproduces the reported symptom without any shared sub-entity ids in Datomic. It also plausibly explains why the bug recurred after the user "rebuilt" notes — the multi-tab pattern is the DM's normal workflow.

## Why id-in-URL alone is not the point

The **character list page** (`/pages/dnd/5e/characters`) displays many characters and lets the user expand each to edit notes inline. Each expanded row calls `[expanded-character-list-item id …]` (`views.cljs:8065`) which renders `[summary-details num-columns id]` with a real id. That path writes through `character-map[id]` and is correctly isolated per character — many characters edited from one URL works fine, no id in the URL required.

The list view isn't safe *because* of its URL. It's safe because the id comes from the **data**: the `::char5e/characters` subscription returns a list of characters, each row receives its id as a component prop, and every downstream sub/event uses that id. Reload: sub re-fetches, rows re-materialize with their ids. No per-tab identity signal needed because no single "which character" decision exists — there are many rows, each with its own id.

The **builder** edits exactly one character. There's no enumerated data source handing it an id; it has to *know* which character. Today it knows because `:character` in app-db holds it (set by `:edit-character`, clone, level-up, or restored from `localStorage` on init). That works in-memory per tab.

The failure mode is purely the **reload-survival signal**: across a reload, "which character was this tab editing?" has to come from somewhere that doesn't collide across tabs. Today that signal is `localStorage["character"]` — a single shared slot, last writer wins, which is exactly the contamination path.

The natural fix is to give every draft an id (UUIDs for unsaved-new-character drafts, real `:db/id`s for everything else) and key the draft store by that id — same model the list view uses for saved characters. Once each tab's draft has a unique key, contention disappears the same way it disappeared for the list view: unique ids → no shared slot → no contention. No need for per-tab storage classes.

## Existing guards

| Layer | Guard | File:line | What it protects |
|-------|-------|-----------|------------------|
| Server | `owns-entity?` | `routes.clj:841-848` | 401 if authed user doesn't own the character's top-level `:db/id`. |
| Server | `spec/explain-data ::se/entity` | `routes.clj:946` | 400 on structurally malformed payloads. |
| Server | Current-entity spec check | `routes.clj:872-879` | Falls back to "retract and replace" when stored character is spec-invalid. |
| Client | `check-spec-interceptor` | `events.cljs:101,146,213` | Throws if `:character` fails `::entity/raw-entity` after a `character-interceptors` event. |
| Client | `::char5e/set-character` id-match + `:changed` check | `events.cljs:2063-2065` | Prevents background fetch from clobbering in-progress builder edits. Does NOT reconcile `:character` against current tab context. |
| Routes | `check-auth` interceptor | `routes.clj:1450` | Requires valid session token. |

## Missing guards

| # | Gap | What it prevents |
|---|-----|------------------|
| G1 | Server does not verify sub-entity `:db/id`s belong to the target character's current tree | A save for character A carrying character B's `::se/values :db/id` would cause orphan-id logic to retract A's values entity and re-point A's `::se/values` ref at B's. All future edits on either would then share one entity. |
| G2 | Server does not reject incoming sub-entity `:db/id`s on `create-new-character` | A clone that skipped `remove-ids` would create shared ids on first save. |
| G3 | Client does not invariant-check `(= (:db/id character) (js/parseInt id))` before writing `character-map[id]` | Out-of-band mispairings. Audit found no current caller that mispairs. |
| G4 | Client does not reconcile `:character` against current tab context | Multi-tab contamination (the leading hypothesis). |

## Scope of the storage-keying change across the project

The character draft is one of 18 `localStorage` keys in `db.cljs:32-49`. Classified by cross-tab semantics:

**Group A — single floating builder drafts (same bug pattern as `character`):**

`character`, `magic-item`, `spell`, `monster`, `encounter`, `background`, `language`, `invocation`, `boon`, `selection`, `feat`, `race`, `subrace`, `subclass`, `class`.

Each has its own interceptor (`events.cljs:152-162`) that writes to its own singleton key on every edit. Each is a single slot edited by one builder. Each reloads from localStorage on init. **Every one carries the same multi-tab contamination hazard as the character case.** The character is the most visible because DMs juggle characters; the other builders are less commonly multi-tabbed, but the failure mode is identical.

The id-keyed-drafts approach generalizes naturally: each builder type gets a keyspace prefix (`"character-draft-{uuid-or-id}"`, `"magic-item-draft-{uuid-or-id}"`, etc.). Multiple drafts of the same type can coexist; multiple tabs can edit different drafts of the same type without contention.

**Group B — cross-tab sharing is the desired behavior (keep `localStorage`):**

- `user` — auth token, username, email. Log in on one tab → all tabs authed. Cross-tab sharing is the point.
- `plugins` — loaded homebrew content. Same homebrew rules should be visible in every tab.
- `combat` — combat tracker. Probably keep; a DM may want cross-tab persistence. Revisit if issues surface.

**Group C — not involved:**

- Persistent server data (saved characters, items, spells, folders, parties) — lives in Datomic, reaches client via subs, never touches localStorage.
- Built-in content (core classes/races/items/spells) — pure Clojure constants.
- Homebrew *content* loaded via plugins — referenced from memory after the plugins slot hydrates; the plugins slot itself is Group B.

**Butterfly impact is narrow and uniform:**

- Touches only the 15 Group A slots — change the keying, not the storage class.
- Leaves auth/session (Group B) alone — they're shared across tabs by design.
- Leaves every persistent or built-in data path (Group C) alone.

**Minimum shipping scope:** the character slot alone, as the patch for the reported bug. If it lands cleanly in production, the other 14 Group A slots follow the same pattern.

**Crash / unsaved-draft protection improves**, doesn't regress: today's `localStorage` is preserved across browser close, but only one draft per builder type. Per-id keys preserve every in-progress draft across browser close — closing/reopening can restore any combination of unsaved drafts (with an explicit "you have unsaved drafts: [list]" prompt rather than silent restoration of whichever was edited last).

## Recommended fixes

Ship order:

1. **UUID-keyed builder drafts** *(the structural fix)*. When the user starts a new draft, generate a client-side UUID (`cljs.core/random-uuid`). Treat the draft like a saved character with that id: store at `character-map[:draft/{uuid}]` in app-db, persist as `localStorage["character-draft-{uuid}"]`. Saved characters keep using their `:db/id` as today. Builder route becomes `/character-builder/:id` and accepts both real `:db/id`s and `:draft/{uuid}` segments; on first save the server returns a real `:db/id`, the client copies the slot under the new key, drops the draft slot, and redirects the route. From then on the character is edited through `character-map[real-id]` like everything else.

   Multi-tab safe by construction (each draft has a unique key — no contention even on shared `localStorage`). Crash-recoverable (every draft survives browser close in its own slot). Supports multiple unsaved drafts of the same type. Eliminates the floating `:character` slot rather than papering over it. Net code reduction long-term — drops a special case.

   Apply the same pattern to the other 14 Group A slots if/when they show similar issues — same shape of fix.

2. **Server-side G1 guard** *(permanent data-layer safety net)*. In `update-character`, compute `incoming-ids = entity/db-ids` on the incoming payload and reject with 400 if any id (other than the top-level character id) is not in `entity/db-ids` of the currently-stored character. One `clojure.set/difference` per save. Independent of any client-side fix.

3. **Instrumentation**. Log `{:char-id, :values-id, :selection-ids}` on every save (server) and on every autosave dispatch (client). Turns any future recurrence into diagnostic evidence regardless of code path.

**Rejected options:**

- *sessionStorage swap* — earlier proposed as the minimal correctness fix. Works for multi-tab, but doesn't survive browser crash, can't hold multiple drafts of the same type, and doesn't address the underlying single-slot design. The id-keyed approach above subsumes its multi-tab benefit and adds crash protection.
- *Save-time id reconciliation guard* — detection only, requires the tab to know its context, made unnecessary by URL ids.
- *Per-id sessionStorage drafts* — same multi-tab safety as fix (1) but loses crash protection.

## Timeline

| Date | Event |
|------|-------|
| 2026-02-25 | Modernization PR #649 merged to `develop` |
| 2026-03-13 | Modernization PR #661 merged to `develop` |
| 2026-03-15 | Reporter noticed the bug (production was still on pre-modernization code) |
| 2026-04-08 | Modernization went live in production |
| 2026-04-20 | Bug reproduced by reporter after rebuilding notes; investigation |

The modernization was NOT live when the bug was first noticed, so it cannot be the cause. The bug is rooted in pre-modernization design (the floating `:character` slot) that predates the 2026 stack work.

## Open questions

1. Does the user actually use multiple tabs when managing the party? (If yes → hypothesis confirmed behaviorally; if no → look elsewhere.)
2. Do *other* fields (HP, XP, description) transfer between the two characters, or only notes? If only notes, the multi-tab hypothesis is wrong and we should look at a notes-specific path.
3. A `d/pull` of both characters would show whether `::se/values :db/id` is shared (mechanism 2) or not (mechanism 1 or 3). Conclusive in one query.
4. Was the original clone made in-app via the Clone button, or imported from `.orcbrew`? The import path hasn't been audited; it could preserve ids.

## Code touchpoints (for implementers)

- `src/cljs/orcpub/dnd/e5/events.cljs`
  - `:initialize-db` (line 206) — loads `localStorage` into `:character`.
  - `:set-character` + `db-char->local-store` (line 1236) — writes localStorage.
  - `::char5e/set-character` (line 2056) — id-match guard here.
  - `update-character-fx` (line 2425) — id-based vs `:character` branching.
  - `::char5e/set-notes`, `::char5e/set-current-hit-points`, etc — all use `update-character-fx`.
  - `:save-character` (line 462) — reads `(:character db)`, posts with its `:db/id`.
  - `:character-save-success` (line 359) — writes both `:character` and `character-map`.
  - `::char5e/level-up` (line 2525) — stale-db capture smell.
- `src/cljs/orcpub/dnd/e5/autosave_fx.cljs` — throttled save queue keyed by id.
- `src/cljs/orcpub/dnd/e5/db.cljs`
  - `character->local-store` (line 167) — swap to `sessionStorage` for fix (1).
  - `:local-store-character` cofx (line 267) — matching read side.
- `src/cljs/orcpub/dnd/e5/subs.cljs`
  - `::char5e/character` (line 507) — reg-sub-raw that fetches on demand.
- `src/cljs/orcpub/character_builder.cljs`
  - Lines 1893, 1908 — the `[character-display nil true 1]` callsites that need a real id for fix (2).
- `src/cljc/orcpub/route_map.cljc`
  - Line 177 — `character-builder` route definition; extend with `["/" :id]` for fix (2).
- `src/clj/orcpub/routes.clj`
  - `update-character` (line 867) — target of fix (3) G1 guard.
  - `entity/db-ids` (used in orphan-id diff).

## Log

- **2026-04-20** — Initial survey. Ruled out modernization as direct cause (not live until Apr 8, reporter noticed Mar 15).
- **2026-04-20** — Audited client save/fetch pipeline and server update-character. No id-routing mechanism found that writes one character's notes to another's entity. Found two staleness smells (level-up, builder vs sheet) — both cause edit loss, not transfer.
- **2026-04-20** — Inventoried existing guards; enumerated missing ones (G1-G4). G1 is the most consequential gap at the server; G4 at the client.
- **2026-04-20** — Multi-tab hypothesis identified as the likely mechanism after re-reading the `::char5e/set-character` guard. The guard protects unsaved edits but leaves `:character` stale across tab/reload boundaries. Localstorage is shared cross-tab but the app doesn't listen for the `storage` event, so contamination lands only on reload.
- **2026-04-20** — Confirmed list-view inline edit already routes correctly by id through `character-map` (`views.cljs:8065`). So the fix isn't "id in every URL" — it's "eliminate the floating `:character` slot."
- **2026-04-20** — Share-link routes already use id in URL (`route_map.cljc:180`, `fork/integrations.cljs:112`). Extending the same convention to the builder is an incremental change, not a new pattern.
- **2026-04-20** — Clarified framing: the real fix isn't "eliminate `:character`" per se, it's "make the builder a by-id view like the list view already is." The id-in-URL is a reload-survival mechanism; runtime routing is already correct anywhere the id is threaded through props. `:character` can stay as a transient buffer for unsaved-new-character flow.
- **2026-04-20** — Tightened further: the list view works without URL ids because the id comes from the data (enumerated characters list). The builder edits one character, so it needs a per-tab identity signal that survives reload. Today that signal is shared localStorage — the contamination root cause. `sessionStorage` is sufficient to fix correctness (per-tab by web spec). URL-in-path is a nice-to-have for shareability and cleanup, not a prerequisite.
- **2026-04-20** — Surveyed the full localStorage surface in `db.cljs:32-49`. 15 builder-draft slots have the same pattern and same bug as `character` (Group A). 3 slots (`user`, `plugins`, `combat`) should stay shared across tabs (Group B). Persistent server data and built-in content don't touch localStorage at all (Group C).
- **2026-04-20** — Replaced the sessionStorage proposal with **UUID-keyed drafts**. Insight: the contamination problem isn't really "shared vs per-tab storage class" — it's "shared vs unique key per draft." Giving every draft a unique id (UUID for unsaved, `:db/id` for saved) and keying storage by id solves multi-tab AND adds crash protection AND supports multiple simultaneous drafts AND matches the model already used by the list view and `character-map`. SessionStorage is a band-aid on a single-slot design; UUID-keyed drafts eliminate the single slot.
