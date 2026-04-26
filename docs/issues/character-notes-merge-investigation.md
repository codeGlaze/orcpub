# Character notes merge/transfer investigation

## Status

- **Symptom**: reporter has two clones of the same character (different levels); lower-level's notes appear empty, higher-level's notes hold what lower used to have.
- **Reproducible**: yes — recurred after the user rebuilt notes on both characters.
- **Mechanism**: multi-tab contamination of the floating `:character` slot. Tab reload restores `:character` from `localStorage["character"]`, which holds whichever character was saved most recently across tabs. User in the builder believes they're editing one character but `:character` actually holds another; their keystrokes land on the wrong character. Reporter's "rebuild" cleared the original (which is why it now shows empty) and the rebuilt text was saved onto the wrong entity.
- **Recommended action**: fix the multi-tab confusion at the client (UUID-keyed drafts + URL ids — see Recommended fixes). Server-side G1 guard is worth doing as defense-in-depth against a different class of bug (malformed payloads mixing two characters' sub-entity ids), but it's not what fixes the reported symptom.

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
5. Manual save posts `:character`. The payload's top-level `:db/id` and every sub-entity `:db/id` belong to higher (because `:character` is wholesale higher's map). Server's `update-character` sees a normal, internally-consistent save for higher, runs `owns-entity?`, updates higher with the new notes content. The notes the user typed land on higher.
6. Reporter said they had "rebuilt" notes — i.e. cleared the original and re-typed. Whichever character was cleared shows empty on the server; whichever character was edited under the contaminated slot received the text intended for the other one.

End state: higher carries the text the user intended for lower; lower is empty (because the user cleared it during the rebuild). Matches the symptom exactly. **No foreign sub-entity ids, no Datomic cascade-retract magic, no server-side bug needed.** The save itself is internally consistent — it's just for the wrong character relative to what the user thought they were editing.

### The contamination surface is wider than "two tabs in the builder"

Sheet/list inline-edit of notes uses the same `summary-details` component as the builder, but the parent passes a real id (`views.cljs:8065-8135`). With a real id, `(set-notes-handler id)` dispatches `[::char/set-notes id v]` → `update-character-fx` id-branch → writes `character-map[id]` + queues throttled autosave. **The live-edit path never touches `:character`.**

But `:character-save-success` (`events.cljs:359-367`) dispatches `[:set-character character]` after every successful autosave. `:set-character` writes both `:character` and `localStorage["character"]`. **So every save from any view — sheet, list, builder — primes localStorage with that character's data.**

That means:

| Scenario | Currently susceptible? |
|---|---|
| Two tabs both on sheets, only inline-editing notes | live edits OK (id-keyed), but every save primes localStorage |
| Two tabs on sheets, then user clicks "Edit" | **yes** — `:edit-character` (`events.cljs:2069`) dispatches `[:set-character character][:route ...builder]`; whichever character was last in localStorage influences the next builder load on reload |
| Two tabs reload to builder URL after sheet activity | **yes** — `:initialize-db` reads localStorage and the builder's `:character` is whoever-saved-last |
| Two tabs both in the builder | **yes** — canonical case |

So sheet activity isn't safe-because-it's-never-going-to-bite — it's safe-during-the-session-but-priming-the-trap. The trap springs the next time any tab reads `:character` from localStorage (any reload that lands on the builder, or any `:edit-character` followed by an interruption).

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

1. **UUID-keyed builder drafts + URL ids** *(this is what fixes the reported bug)*. The reported symptom is the multi-tab confusion writing to the wrong character. Removing the floating `:character` slot in favor of per-id storage removes the confusion. Generate a client-side UUID when the user starts a new draft. Treat the draft like a saved character with that id: store at `character-map[:draft/{uuid}]`, persist as `localStorage["character-draft-{uuid}"]`. Saved characters keep using their `:db/id`. Builder route accepts both real ids and `:draft/{uuid}` segments. On first save, copy the slot to the new key, drop the draft slot, redirect the route.

   Multi-tab safe by construction; crash-recoverable; supports multiple simultaneous drafts; matches the existing id-keyed model used by the list view. Removes the `:character` floating slot for saved characters.

   **Underspecified ripples to handle in the implementation PR(s):**
   - `:character-save-success` (`events.cljs:359-367`) currently dispatches both `[:set-character character]` and `[::char5e/set-character id character]`. Needs to atomically replace the draft slot with the real-id slot and trigger the route redirect.
   - `:edit-character` (`events.cljs:2069-2072`) callsites all need to route to `/character-builder/:id` instead of dispatching `:set-character` and routing to a no-id URL.
   - `::char5e/clone-character` (`events.cljs:238-248`) currently writes to `:character`. Must mint a new draft UUID and route to `/character-builder/draft/{uuid}`.
   - `::char5e/level-up` (`events.cljs:2525-2529`) has a separate stale-db smell (`:set-character` argument is computed from pre-add-level db). UUID drafts don't fix this; it needs its own fix (route to `/character-builder/:id` and let the builder re-hydrate from the now-incremented `character-map[id]`).
   - Every event using `character-interceptors` (`events.cljs:146`) writes via `(path :character)`. The interceptor needs to learn which draft id is active (from the route) and write to `[:character-map id]` instead.
   - `:save-character` reads `(:character db)`; needs to read `(get-in db [:character-map active-id])`.
   - **Migration**: existing users will have `localStorage["character"]` from the prior version. On first init under the new code, mint a UUID and migrate it to `localStorage["character-draft-{uuid}"]`, then route to that draft. Don't silently discard.
   - **Autosave race during draft → real-id transition**: if an autosave is in-flight under the draft id when save-success arrives with the real id, the in-flight save can either 404 or duplicate. Drain the autosave queue (`autosave_fx.cljs`) on transition; or have the queue key by draft-id and resolve the real id at dispatch time.

2. **Instrumentation** *(ship alongside)*. Log `{:char-id, :values-id, :selection-ids}` on every save (server) and on every autosave dispatch (client). Future recurrence leaves diagnostic evidence regardless of code path.

3. **Server-side G1 guard** *(defense-in-depth, NOT what fixes this bug)*. In `update-character` (`routes.clj:867-903`), compute `incoming-ids = entity/db-ids incoming-character` and `current-ids = entity/db-ids current-character`. Reject with 400 if any `:db/id` in `incoming-ids` (other than the top-level character id) is not in `current-ids`. One `clojure.set/difference` per save.

   Why it does NOT fix the reported symptom: the multi-tab scenario produces a save payload that's internally consistent — every id in the payload belongs to one character (the wrong one from the user's perspective, but legitimate). G1 has nothing to reject because no foreign ids are present. G1 protects against a different threat: a payload that mixes ids from two characters (which could come from a future client bug, or a malicious request). Worth shipping for hardening, but it's not on the critical path here.

   Placement specifics if implementing:
   - Run *after* `owns-entity?` (line 869) and *before* the `current-valid?` branch (line 872).
   - **Skip** in the `current-valid? = false` retract-and-replace branch (line 880-887) — that branch intentionally throws the stored tree away.
   - Skip on `create-new-character` (line 905); first-time creation has nothing to compare against.

**Rejected options:**

- *sessionStorage swap* — works for multi-tab but doesn't survive browser crash, can't hold multiple drafts of the same type, and doesn't address the underlying single-slot design. UUID-keyed drafts subsume its multi-tab benefit and add crash protection.
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

1. Does the user actually use multiple tabs when managing the party? (If yes → plant-step is confirmed behaviorally; if no → look elsewhere for what produces a payload with foreign sub-entity ids.)
2. Do *other* fields (HP, XP, description) transfer between the two characters, or only notes? If only notes, the `::se/values`-shared theory is wrong and we should look at a notes-specific path.
3. A `d/pull` of both characters would show whether `::se/values :db/id` is shared post-bug. Conclusive in one query.
4. ~~Was the original clone made in-app via the Clone button, or imported from `.orcbrew`?~~ **Closed** — orcbrew is plugin/homebrew content only, not a character import path. There is no way for a character to enter the system bypassing the in-app save route.
5. Did the user explicitly clear lower's notes at any point during the rebuild, or did lower's notes go empty without a user action? The cleanest "lower empty + higher carries lower's text" steady state requires either a user clear or a cascade-retract; the multi-tab plant + server commit alone don't fully explain it.

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
- **2026-04-20 (critical review)** — Independent agent reviewed the doc and surfaced several corrections: (a) the multi-tab story doesn't fully account for "lower notes empty" — needs user clear or two events. (b) Closed open question 4 — no orcbrew character import path exists. (c) **Multi-tab and server-side id-swap (G1) are sequential, not alternative.** Multi-tab plants the foreign sub-id into the client payload; the server's `remove-orphan-ids` (`routes.clj:888`) doesn't strip it because its parent has a `:db/id`; Datomic transacts and physically transfers data between characters' values entities. **G1 is the only step that severs the data transfer** regardless of how the foreign id got planted. (d) UUID drafts have several underspecified ripples: `:character-save-success`, `:edit-character`, `clone-character`, `level-up` (separate stale-db bug), `character-interceptors`, `:save-character`, migration of existing localStorage, autosave-queue handling during draft→real-id transition. (e) G1 placement: skip in retract-and-replace branch; only on update path. **Revised ship order: G1 first, instrumentation with it, UUID drafts third.**
- **2026-04-20 (sheet susceptibility)** — Verified: clicking "Edit" routes to the builder (full navigation, not in-place mode). Sheet/list inline-edit of notes is id-keyed and doesn't touch `:character` directly. **But every successful autosave from any view dispatches `:set-character` via `:character-save-success`, which writes `:character` and `localStorage["character"]`.** So sheet activity isn't truly isolated — it primes localStorage with whichever character saved most recently. The trap springs the next time any tab reads `:character` (reload to builder, or `:edit-character` followed by interruption).
- **2026-04-20 (correction — back to the simpler model)** — Reviewer's "G1 is the only step that physically transfers content between characters" claim was misleading for this case. The multi-tab scenario produces a save payload that's internally consistent (all ids belong to one character — just the wrong one from the user's perspective), so G1 has nothing to reject. The bug is purely client-side identity confusion: keystrokes intended for character A land on character B because `:character` holds B's whole map after a tab/reload mixup. The "lower notes empty" half is explained by the user's own "rebuild" (they cleared what they thought was lower). G1 is still worth doing as defense-in-depth against a different threat model (payloads mixing two characters' ids), but it's not what fixes the reported symptom. **Reverted ship order: UUID-keyed drafts first (the actual fix); G1 demoted to defense-in-depth.**
