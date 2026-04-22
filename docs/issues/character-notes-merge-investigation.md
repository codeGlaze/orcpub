# Investigation: Character notes transferred between clones at different levels

**Reported**: lower-level clone of a character now shows empty notes; higher-level
clone (the leveled-up copy) shows what used to be the lower-level's notes.
Net effect: notes **moved** from one entity to the other, overwriting the target.

**Hypothesis being tested**: the 2026 stack modernization (PR #649/#661) introduced
a regression responsible for this.

---

## Theories (with status)

| # | Theory | Status | Verdict |
|---|--------|--------|---------|
| T1 | Clone leaves a stale `:db/id` somewhere, so clone and original are the same Datomic entity | Checked | Unlikely — `entity/remove-ids` strips every `:db/id` in the tree before save |
| T2 | `update-character-fx` uses the wrong id (cross-character write) | Checked | No — explicit id is passed through; `character-map[id]` keying is stable |
| T3 | Server `update-character` retracts sub-entities that are shared with another character (orphan-id retraction) | **Partially checked** — need to verify `::se/values` is not stored by ref | Open |
| T4 | `::entity/values` is stored as a component/ref entity with its own `:db/id` that can be shared between clones | **Not yet verified** | Open — highest suspect |
| T5 | Reactive `::char5e/character` sub races with pending autosave → writes stale notes to `character-map[id]`, then autosave persists that | Checked | Explains **lost edits**, not transfer. Not the cause. |
| T6 | `memoize` on `set-notes-handler` captures a wrong id | Checked | No — memoize is keyed by id arg; pre-existing pattern |
| T7 | Modernization changes to `to-strict`/`from-strict` (array-map fix, owner round-trip) affect notes path | Checked | Scope is class-level selection order and owner, not notes |
| T8 | A character-save-success handler dispatches with a stale id, overwriting the wrong entity in `character-map` | **Not yet fully traced** | Open |
| T9 | Browser localStorage or an in-memory atom caches the notes across character switches | Not yet searched | Open |
| T10 | Server-side clone endpoint (if one exists) shares notes sub-entity by ref | Not yet searched | Open |

---

## Chain checks (with refs and findings)

### Client: notes write path
- **UI**: `src/cljs/orcpub/dnd/e5/views.cljs:2663` — `set-notes-handler` is `(memoize (fn [id] #(dispatch [::char/set-notes id %])))`. Each distinct id gets its own closed-over handler. **OK.**
- **Event**: `events.cljs:2531` — `(reg-event-fx ::char5e/set-notes (fn [{:keys [db]} [_ id notes]] (update-character-fx db id #(set-notes % notes))))`. Id pass-through. **OK.**
- **Update**: `events.cljs:2425` — `update-character-fx` updates `[::char5e/character-map (js/parseInt id)]` and emits `{::char5e/save-character-throttled id}`. Keyed by explicit id. **OK.**
- **Autosave queue**: `src/cljs/orcpub/dnd/e5/autosave_fx.cljs:32-36` — iterates `@throttled-save-queue` dispatching `[::char5e/save-character id]` per id. Set, so duplicates collapse. **OK — no cross-id leak.**
- **Save**: `events.cljs:436-458` — reads `character-map[int-id]`, `to-strict`s it, POSTs with `:db/id`. **OK.**

### Client: clone path
- `events.cljs:238` — clone: `to-strict → entity/remove-ids → from-strict → assoc name " (clone)"`, dispatched to `:set-character`.
- `entity/remove-ids` (`src/cljc/orcpub/entity.cljc:76`) calls `remove-orphan-ids-aux` with `remove-ids?=true`, which dissocs `:db/id` from **every** map in the tree. **OK — clone has no retained ids.**
- **But**: `from-strict-selections` reattaches `:db/id` **as metadata** from whatever id survived in the strict form (`entity.cljc:171`). Since `remove-ids` stripped before `from-strict`, the metadata is `{:db/id nil}`. **OK.**
- **Save-success for clone**: `events.cljs:359-367` — response body carries the server-assigned `:db/id`, stored at `character-map[new-id]`. **OK.**

### Server: save path
- `src/clj/orcpub/routes.clj:941-962` — `do-save-character`: if incoming has `:db/id`, `update-character`; else `create-new-character`.
- `routes.clj:867-903` — `update-character`: pulls current, computes `current-ids` (all `:db/id`s in current tree) and `new-ids` (all in incoming), then `retract-ids = current-ids - new-ids`, transacts retractions + the new data.
- **Risk**: if two characters ever share a `:db/id` in their sub-trees, saving one would retract entities still referenced by the other. This is exactly the shape of the reported bug.

### Schema (spot-checked)
- `src/clj/orcpub/db/schema.clj:234-237` — `::char5e/notes` is `:db.type/string`, `:db.cardinality/one`, `:db/noHistory true`. Notes is a scalar attribute, not a nested entity. But the **parent** that holds notes (`::se/values`) may itself be a component/ref entity. **Needs verification — see T4.**

### Modernization deltas touched
- `entity/to-strict` added `::owner` round-trip (no notes impact).
- `entity/from-strict-selections` switched `reduce/assoc` → `array-map` to preserve selection order past 8 keys (class-level ordering, not notes).
- Autosave extracted to `autosave_fx.cljs` with `r/track!` template cache — fixes reactive-context warnings; id keying unchanged.
- Session-token path fix (`4fa1519`), loading-overlay fixes — unrelated to notes.
- `set-notes-handler` `memoize` pattern **pre-existed** the modernization (verified vs `853b6f6`).

---

## Current verdict

I cannot find a modernization-introduced change on the client that writes notes to a
different character than the user is editing. The client flow keys rigorously on the
explicit `id` passed in. The most plausible remaining mechanism is **shared
sub-entity id in Datomic between the two characters** — either the `::se/values`
entity itself or a nested option-entity that carries `::entity/values`. This would
be a data-level issue; the save path's orphan-id retraction would then cause the
observed "transfer" shape.

---

## Deeper dig results

### T4 — `::se/values` is a component entity (CONFIRMED)
- `src/clj/orcpub/db/schema.clj:182-185`: `{:db/ident ::se/values :db/valueType :db.type/ref :db/cardinality :db.cardinality/one :db/isComponent true}`.
- `::char5e/notes` is `:db.cardinality/one`, `:db.type/string`, stored **on** the values sub-entity.
- **So the parent-values-notes chain is: character → values-entity → notes-string.**
- `:db/isComponent true` makes retraction cascade — it does **not** enforce single-parent. Two characters can reference the same component entity in Datomic; when they do, writing notes on one flips the notes seen by the other.

### T3 — Orphan-id retraction scope
- `routes.clj:888-901`: `update-character` computes `current-ids = db-ids(current-char)` and `new-ids = db-ids(incoming)`, retracts the difference.
- Scope is **per-character only** — cannot retract an id that another character still references because another character's tree is never pulled.
- So orphan-id logic doesn't itself corrupt sibling characters. But it **does** write to whatever `:db/id` the incoming strict form names. If incoming `::strict/values` has `:db/id 150` and 150 is also another character's values-entity, the transact updates 150 — changing both characters' notes.

### T8 — `:character-save-success` id tracking
- `events.cljs:359-367`: id is taken from `(:db/id (char5e/from-strict (:body response)))`. The body is the server's `d/pull '[*] saved-id`, so :db/id is authoritative. Writes to `character-map[id]` correctly. **Not a cause.**

### T9 — localStorage / cross-character atoms
- Only `throttled-save-timer` and `throttled-save-queue` atoms in `autosave_fx.cljs:11-12`. Queue is a `#{}` of ids (dedup is desired), timer is a setTimeout handle.
- `db-char->local-store` interceptor writes `:character` to localStorage — single-slot, overwrites on each edit. Does not cross-contaminate distinct characters in `character-map`.

### T10 — No server-side clone endpoint
- `grep -E 'clone|merge-character|copy-character|duplicate'` across server code: no character-clone route. Clone is client-only (`events.cljs:238`).

### Sub-entity id round-trip on the wire (CONFIRMED)
- Server returns `(d/pull db '[*] id)` from `get-character-for-id` (`routes.clj:1127`) — every nested entity's `:db/id` comes back.
- `char5e/from-strict` copies `::strict/values` into `::entity/values` **as-is** (`entity.cljc:220`), keeping `:db/id` as a map key.
- On save, `to-strict` copies `::entity/values` → `::strict/values` as-is (`entity.cljc:140`), keeping `:db/id`.
- Server transact writes to that `:db/id`.
- **Bottom line**: if two characters ever share a values sub-entity id, every save on either will flip both.

### Clone path — verified clean
- `events.cljs:238-248`: `to-strict → remove-ids → from-strict` then rename.
- `remove-orphan-ids-aux` with `remove-ids?=true` (`entity.cljc:48-68`) recursively `dissoc`s `:db/id` from every nested map, including `::strict/values`. Metadata `:db/id` is also effectively dropped (the `(into {} ...)` rebuild does not preserve metadata).
- Save of a clone has no `:db/id` anywhere → server takes `create-new-character` path (`routes.clj:918`) → Datomic assigns fresh ids for the character and every component entity.
- **Current clone code does NOT cause shared ids.**

### Modernization delta in this path — verified minimal
- `entity/to-strict` added `::owner` round-trip (no id impact).
- `entity/from-strict-selections` switched `reduce/assoc` → `array-map` to preserve order past 8 keys. Only affects selection iteration order, not notes.
- Several `if` → `when` and `dead require` cleanups (pure refactors).
- Autosave extracted; id keying unchanged.
- **`remove-ids`, `remove-orphan-ids`, `db-ids` — untouched by the modernization** (diff `853b6f6..origin/develop -- src/cljc/orcpub/entity.cljc` shows no change to those functions).

---

## Conclusion as of 2026-04-20

The reported symptom — lower-level clone's notes disappearing and replacing the
higher-level clone's notes — requires a shared `::se/values` `:db/id` in Datomic
between the two character entities. Everything the app does once that state
exists will make edits on one leak to the other, and a save that retracts orphan
ids on one side can zero-out attributes on the shared entity (explaining the
"disappeared" half of the symptom).

**None of the modernization changes create that shared-id state.** The current
clone path properly strips every `:db/id`. The server save path targets exactly
the ids present in the incoming entity tree. The reactive `::char5e/character`
sub and the autosave queue both key on explicit ids with no observed crossover.

**Best remaining hypothesis**: the two characters already shared a values
sub-entity id **before** the modernization — created by a prior bug (or manual
import) that left the clone's values pointing at the original's values entity.
The modernization merely changed *when* the symptom surfaced (e.g. new autosave
timing made a previously-dormant shared write happen sooner, or a user simply
edited notes post-upgrade for the first time in a while).

To confirm this hypothesis we need data-level inspection:

```clojure
;; In a REPL connected to the live DB
(d/pull db '[:db/id {::se/values [:db/id ::char5e/notes]}] <low-level-char-id>)
(d/pull db '[:db/id {::se/values [:db/id ::char5e/notes]}] <high-level-char-id>)
```

If both pulls show the **same** `::se/values :db/id` — confirmed data-level
sharing, pre-dating modernization. Remediation is a one-shot transaction that
assigns each character a fresh `::se/values` copy, then copy-forward the notes
the user expects on each.

If the values `:db/id` differs between the two — the bug is elsewhere, and we
need to look harder at the save pipeline's round-trip. Next candidate would be
re-frame subscription caching where the SAME character-map value is aliased
under two ids (requires ClojureScript reference equality, unlikely but worth
ruling out).

---

## Open sub-questions

- **S1**: Does the historical codebase (pre-modernization fork / upstream OrcPub) have any commit that wrote a clone without calling `remove-ids`? Would explain pre-existing shared ids.
- **S2**: Is there an `.orcbrew`/character import path that preserves sub-entity ids? If so, re-importing a previously-exported character could land shared ids into a user's DB.
- **S3**: `features-used` has special handling (`update-values-from-strict` dissocs `:db/id` on it) — was this added **during** modernization to paper over a similar shared-id symptom for a different field? If yes, that's a precedent pointing at the same class of bug.

---

## Timeline (critical)

| Date | Event |
|------|-------|
| 2026-02-25 | PR #649 merged to `develop` (first modernization tranche) |
| 2026-03-13 | PR #661 merged to `develop` (final modernization tranche) |
| **2026-03-15** | **User noticed the bug** |
| 2026-04-08 | Modernization actually went **live in production** |
| 2026-04-20 | Investigation |

**The modernization was NOT running in production when the bug was noticed.**
Production on Mar 15 was the pre-modernization code. This eliminates the
modernization as the *cause*. The remaining candidates:

1. Pre-existing data-level shared `::se/values :db/id` from a historical bug or
   import — the symptom would surface whenever the user edited notes on either
   character, independent of code deployed.
2. A bug in the **pre-modernization** code path, unchanged by modernization,
   that we haven't identified yet.
3. User-level action (import/restore/manual DB edit) on or before Mar 15.

Diagnostic question for reporter: **has the user edited these characters'
notes at all since ~Mar 8?** If the first post-symptom edit was on Mar 15, the
shared-id data state may have been created much earlier (any time since the
clone was made) and simply never been visible until one side was edited.

---

## Reproduction (2026-04-20 update from reporter)

User rebuilt notes on both characters, and the transfer happened **again**.
The bug is currently reproducible.

### What reproduction rules in

- **Shared `::se/values :db/id`** is still consistent with this: if both
  characters genuinely reference the same values entity in Datomic, every save
  on either side overwrites the shared entity, and last-write wins. Rebuilding
  both characters would reliably recreate the symptom as long as the shared-id
  state persists in the DB.

### What reproduction rules out (or strains)

- **Pure one-time data corruption, already resolved** — if it was a stale state
  that got cleaned up, rebuilding notes shouldn't reproduce it.
- **Pure session-local race with no persistent effect** — reproducibility
  across sessions points at durable state (DB or serialized client cache).

### Subtle shape of the symptom vs shared-id mechanics

Reporter describes: *lower ends up empty, higher carries what lower had.*
With a literally-shared `::se/values` entity, both characters must display
**the same** notes at any given moment (they read from the same datom).
So the exact "one empty, one full" steady state requires:

- Either the save for one side is writing to the shared entity with empty
  string, and the save for the other side is writing the real text — and the
  display is snapshotting those writes at different times.
- Or the two characters don't actually share the values entity, and the bug
  is in the save path's id routing (writing one character's notes under the
  other's values id via some other mechanism).

This means **the `d/pull` query now distinguishes between two live theories**,
not just "data corrupt or not":

1. Same `::se/values :db/id` → shared entity. The persistent "one empty,
   one full" state means a save sequence is writing empty then non-empty to
   the same entity, and the reporter's perception of "which character had
   which notes" is driven by *when* they looked vs *when* the last write
   happened. Fix = give each character its own values entity.
2. Different `::se/values :db/id` → something is routing one character's
   save payload to the other character's values id on write. Need to look
   harder at the client-to-wire-to-server path for id crossover.

## Deeper code-smell audit (2026-04-20)

Audited every write path that can land data into `::char5e/character-map`,
`:character`, or Datomic via the save route.

### Writes to `character-map[id]`

| Site | Source of `id` | Source of `character`'s `:db/id` | Match? |
|------|----------------|-----------------------------------|--------|
| `events.cljs:2059-2066` — `::char5e/set-character` handler | passed arg (from save-success or sub response) | passed arg | Checked: callers always align these |
| `events.cljs:2425-2432` — `update-character-fx` | passed arg | preserved from existing entry | OK |
| `:character-save-success` (`events.cljs:367`) | `(:db/id character)` from server response | same | OK by construction |
| `::char5e/character` sub-raw fetch response (`subs.cljs:518`) | closure-captured `int-id` | from response body (server `d/pull`) | OK |

No path was found where the `id` key and the `character`'s `:db/id` could
disagree, so a character stored at `character-map[100]` with `{:db/id 200 …}`
is not achievable through the code as written.

### Writes to `:character`

Writes happen via `:set-character` (and `character-interceptors` for in-builder
edits). `:set-character` stores whatever is passed; no id consistency check.

This matters because `:save-character` (the manual save) reads `(:character db)`
and posts with **that** map's `:db/id`. If `:character` is ever stale across a
navigation (e.g. still holds lower's data while the user is on higher's sheet),
a manual save writes lower's payload to lower's entity. That's an edit-loss
pattern, not a cross-character transfer.

### Smell found: stale-db capture in `::char5e/level-up`

`events.cljs:2525-2529`:
```clojure
(reg-event-fx
 ::char5e/level-up
 (fn [{:keys [db]} [_ character-id]]
   {:dispatch-n [[::char5e/add-level character-id]
                 [:set-character (get-in db [::char5e/character-map
                                             (js/parseInt character-id)] {})]
                 [:route routes/dnd-e5-char-builder-route]]}))
```

The `:set-character` dispatch captures `db` **at handler return time**, before
`::char5e/add-level` has run. So `:character` is set from the *pre-increment*
character-map entry, while `character-map[id]` has the *post-increment* entry
(add-level runs first in the dispatch-n queue).

This creates a divergence:
- `:character` = pre-level-up state
- `character-map[id]` = post-level-up state

Until the user edits something that triggers save or a new fetch, these diverge.
If the user then types notes in the builder (which routes through `:character`
because the builder renders `character-display` with `id=nil`), a manual save
posts `:character` — overwriting the server-side post-level-up state with
pre-level-up + the user's new notes. The added level is effectively lost on
save if the user uses manual save before any autosave reconciles.

**This is not cross-character contamination, but it's a real bug** — and the
same class of "stale :character captured in dispatch args" could exist
elsewhere. Searched for more; see below.

### Builder passes `id=nil` to `character-display`

`character_builder.cljs:1893` and `:1908` both call
`[views5e/character-display nil true 1]`. Inside the builder, the notes
textarea's `id` is `nil`, so:

- Its subscription `@(subscribe [::char/notes nil])` falls through to
  `(subscribe [:built-character])` → reads `:character` (`subs.cljs:741-746`).
- `(set-notes-handler nil)` dispatches `[::char/set-notes nil v]`.
- `update-character-fx` with `id=nil` takes the else branch
  (`events.cljs:2432`) — updates `:character` via `:set-character`. **No
  autosave is queued.**

On the character SHEET, `id` is the character's real id, notes dispatch carries
that id, and autosave is queued.

So edits in the builder and edits on the sheet target different slots:
- Builder edit → `:character` → saved only by manual save button.
- Sheet edit → `character-map[id]` → saved by throttled autosave.

If the user moves between builder and sheet while editing the same character,
edits made in one view aren't visible to the other until a save+reload cycle
re-syncs. This is another staleness surface but again not cross-character.

### `input-field` Form-2 component state (`components.cljc:51-72`)

`local-val` and `prev` atoms persist for the life of the component instance.
If React reuses the instance across character switches (same tree position,
no `:key`), the per-instance atoms carry old state. The `(not= value @prev)`
reconciler clears `local-val` whenever the subscription value changes, so
the display recovers on re-render. No missing-clear path found; this is
robust across single-character edit flows but it's load-bearing — any change
that short-circuits the reconciler would cause stale typed text to be
dispatched under whatever `on-change` is closed over at that moment.

### No server-side clone, merge, or mass-edit endpoints

Confirmed: all character mutation goes through `save-character` →
`update-character` / `create-new-character`. No admin migration script that
could cross-assign sub-entity ids.

---

## What the "one empty, one populated" symptom argues

If both characters truly **shared** one `::se/values` entity (the leading
theory), edits on either would be visible on both at all times. The reporter's
steady state — lower empty, higher carrying lower's text — is **inconsistent**
with persistent sharing.

Plausible reconciliations:
1. The values entities are **not** shared, and the symptom is id-routing on
   save (one character's save payload somehow writes to the other's values
   datom). Our code audit didn't find the mechanism.
2. The values entities **are** shared, and the symptom is a transient
   snapshot — at the moment of observation, some cascade retraction (e.g.
   incoming save omitted `::entity/values`, server computed `retract-ids`
   and cascaded-retracted the shared values entity) cleared both, and a
   subsequent save on higher only re-populated higher's view. This is
   fragile and depends on request ordering we can't reproduce.
3. The bug is **upstream of Datomic** — a persistence layer issue (H2
   storage, the Datomic Free→Pro migration artifact) leaked data between
   otherwise-distinct entities. The user's production hasn't been migrated
   yet (pre-modernization), so this is lower probability, but worth naming.

---

## Net verdict

After an exhaustive audit of the client save/fetch/update pipeline and the
server update-character route, **no code path reliably causes notes to be
written from one character to another character's entity**. The level-up
stale-db and builder-vs-sheet slot divergence smells we did find cause edit
**loss**, not edit **transfer**. The reproducible transfer symptom most
likely needs either:

- a data-level check (`d/pull` to see if the two characters' `::se/values`
  share an id), or
- a step-by-step reproduction log from the user (click sequence, browser tab
  count, whether they refreshed between edits) to reconstruct the offending
  save order.

Without either, further static code review is unlikely to surface the cause.

1. **`d/pull` for both characters** — single most valuable data point:
   ```clojure
   (d/pull db '[:db/id ::char5e/notes {::se/values [:db/id ::char5e/notes]}] <low-id>)
   (d/pull db '[:db/id ::char5e/notes {::se/values [:db/id ::char5e/notes]}] <high-id>)
   ```
2. **Rebuild sequence** — when you re-entered notes, was it same session or different sessions? Which character's notes were typed first? Did you save (or navigate away) between them? Did you refresh the browser between edits?
3. **Display state at time of report** — does lower show empty *and higher show the text lower used to have*, or does lower show empty while higher shows higher's original text (untouched)? The difference tells us whether a write happened on the higher entity at all.
4. **Other fields** — if you edit HP, XP, or description on lower, does that also transfer? If yes, the shared object is the whole `::se/values` entity. If no (only notes), the shared thing is narrower and our model is wrong.
5. **Browser console errors** during the rebuild attempt.
6. **Network tab**: for each save request during the rebuild, what `:db/id` is in the request body top-level and inside `::strict/values`?
7. **Clone history** — when was the clone originally made? Was it made via the in-app "Clone" button, or a different route (import, orcbrew, admin action)?

---

## Multi-character workflow (DM with a party): design options

A DM editing a party of 2–6 characters typically has several tabs open on
the same browser. The single-slot `:character` + single-key localStorage
design doesn't hold up to that workflow — the two-window mechanism above is
the generalized form of the bug, not an edge case.

Options, ranked by invasiveness:

### Option A — Draft in `sessionStorage`, not `localStorage` (smallest fix)

**Change**: swap `character->local-store`/`:local-store-character` to use
`sessionStorage` for the character draft key. `sessionStorage` is per-tab by
spec.

**Impact**:
- Tab A's reload never picks up Tab B's draft — the cross-tab contamination
  path documented above is closed.
- Drafts survive in-tab reloads (what users actually want).
- Closing a tab intentionally throws away its draft. Arguably desirable;
  today's "recover from localStorage" behavior is what produces the bug.

**Cost**: effectively one-line change in `db.cljs`. No router or view changes.

**Closes**: the two-window hypothesis path. Does not fix the builder-has-no-id
staleness smells for a single tab.

### Option B — Put the character id in the builder URL

**Change**: `/builder` → `/character/:id/edit` (and `/character/new` for an
unsaved new character). On route match, hydrate `:character` from
`character-map[url-id]` (fetch if missing). Update `character-display` and
`summary-details` in the builder to read by id instead of `nil`.

**Impact**:
- Each tab's URL is the single source of truth for "which character am I
  editing." Reloads always restore the correct character.
- The `level-up` stale-db smell (`events.cljs:2525-2529`) disappears because
  level-up just routes to `/character/:id/edit` and the builder re-hydrates
  on arrival — no pre-computed `:set-character` dispatch needed.
- The builder-vs-sheet slot divergence goes away — both views now key by id.
- Natural place to add a route guard that checks ownership before showing
  the builder.

**Cost**: medium. Router changes, view signature changes, need to preserve
the unsaved-new-character flow (`/character/new` → saves to server → routes
to `/character/:new-id/edit`).

**Closes**: the tab-context-mismatch staleness class entirely. Combined
with A, gives full isolation.

### Option C — Per-id draft store in app-db + storage

**Change**: replace `:character` with `::char5e/in-progress {id1 draft1,
id2 draft2, :new new-draft}` keyed by id. Persist as per-id storage keys
(`character-draft-100`, `character-draft-200`, `character-draft-new`).

**Impact**: each character's unsaved work is isolated even if the same tab
visits multiple characters. Natural when combined with B, since the tab
knows its id from the URL.

**Cost**: medium-high. Touches every site that reads or writes
`:character`. Worth considering if DMs routinely leave multiple drafts open.

### Option D — Save-time id reconciliation guard

**Change**: in `:save-character` (and any other path that reads
`:character` to post), compare `(:db/id (:character db))` against the
URL/context id. On mismatch, refuse with an error and surface a dialog.

**Impact**: detection-only — doesn't *prevent* the user from having been
confused, just prevents the wrong-entity save from going through.

**Cost**: low. Needs the tab to know its context id, which requires B.

### Option E — Server-side G1 guard (orthogonal safety net)

**Change**: in `update-character`, validate that every sub-entity `:db/id`
in the incoming payload is already in the target character's current tree
(`entity/db-ids` of the existing entity). Reject otherwise with 400.

**Impact**: closes a different failure mode — any present or future client
bug that would cross-write sub-entities is rejected at the server.
Independent of the tab problem.

**Cost**: low. One extra `clojure.set/difference` per save plus an
error-response branch.

### Recommended ship order

1. **A now** (sessionStorage swap). Single-PR, closes the multi-window
   contamination path. If the user's symptom really is the two-tab pattern,
   this alone prevents recurrence.
2. **B next** (id in builder URL). Makes the tab ↔ character mapping
   explicit and unlocks C cleanly if ever needed. Also cleans up the two
   staleness smells found during the audit.
3. **E as a permanent server-side safety net**, regardless of the client
   changes. Even a future client bug can't cross-write sub-entities into
   another character once this is in place.

Skip C unless the DM workflow reveals it's needed after A+B land. Skip D
if B lands, since B makes the mismatch architecturally impossible.

---


### Guards that DO exist

| Layer | Guard | File:line | What it catches |
|-------|-------|-----------|-----------------|
| Server | `owns-entity?` | `routes.clj:841-848` | Rejects save if the authed user doesn't own the character's top-level `:db/id`. Throws `:not-user-character` → 401. |
| Server | `spec/explain-data ::se/entity` on incoming | `routes.clj:946` | Rejects malformed strict payloads with 400. |
| Server | Current-entity spec validity check | `routes.clj:872-879` | If the already-stored character is spec-invalid, uses the "retract and replace" path instead of diffing. Recovery path, not a cross-character guard. |
| Server | `clean-up-character` | `routes.clj:930-939` | Normalizes XP string → long. Not a cross-character guard. |
| Client | `check-spec-interceptor` | `events.cljs:101,146,213` | Validates `:character` (or initial db) against `::entity/raw-entity` after the event runs. Only applied to `character-interceptors` events and `:initialize-db`. |
| Client | `::char5e/set-character` id-match + changed check | `events.cljs:2063-2065` | Only replaces in-memory `:character` with an incoming server result when `int-id` matches current `:character`'s `:db/id` **and** there are no unsaved changes. Prevents clobbering in-progress builder edits. |
| Client | `update-character-fx` `(if id …)` | `events.cljs:2426` | Falls back to `:character` edit if no id is provided. (A branch, not a guard.) |
| Routes | `check-auth` interceptor on save route | `routes.clj:1450` | Requires a valid session token. |

### Guards that DO NOT exist (the gaps)

| # | Missing guard | What it would prevent |
|---|---------------|-----------------------|
| G1 | **Server: verify every `:db/id` in sub-entities (`::se/values`, `::se/selections`, `::se/options`, `::char5e/features-used`) belongs to the character being saved** | A save for character A carrying character B's values-entity `:db/id`. Server would transact this, retract A's old values entity via the orphan-id diff, and re-point A's `::se/values` ref at B's values entity. Future saves by either side then mutate the shared entity. **This is the one concrete server-side mechanism that would produce the reported symptom.** |
| G2 | Server: reject saves where sub-entities carry `:db/id`s owned by a different user | Cross-user version of G1. |
| G3 | Server: on `create-new-character`, reject incoming sub-entity `:db/id`s | Catches a client bug where `remove-ids` was skipped on a clone; server currently accepts and preserves shared ids. |
| G4 | Client: verify `(= (:db/id character) (js/parseInt id))` before writing to `character-map[int-id]` in `::char5e/set-character` | Out-of-band mispairings of id and character data. Current audit didn't find a caller that mispairs, but this is a cheap invariant. |
| G5 | Client: verify `(:db/id character) == (js/parseInt id)` in the save handler before POST | Catches the case where `character-map[100]` somehow holds a character whose `:db/id` is 200. |
| G6 | Client: clear `set-notes-handler` memoize cache on logout/route, or key by `[id, character :db/id]` | Defense-in-depth against stale closures. Theoretical given the audit. |

### What the `::char5e/set-character` id-match guard does and doesn't do

Re-reading the guard carefully (`events.cljs:2063-2065`):

```clojure
(if (and (= int-id (get-in db [:character :db/id]))
         (not (get-in db [:character :changed])))
  (assoc updated :character character)
  updated)
```

**What it does**: when a background fetch or save-success response arrives
for id X, it only overwrites the in-memory `:character` slot if
(a) `:character` is already at id X, and (b) `:character` has no unsaved
builder edits. This prevents a background fetch from clobbering work the user
is doing in the builder for a different character.

**What it does NOT do**: it does not prevent `:character` from being stale.
If the guard's condition fails, `character-map[int-id]` still gets updated
with the fresh data, but **`:character` is left pointing at whatever
character it was pointing at before** — possibly a completely different
character than the one the user now believes they are viewing.

**Staleness is intentional** (the guard's purpose is precisely to not
overwrite the user's current edit), but the staleness has a downstream
consequence that is NOT guarded: any code path that reads `(:character db)`
and writes based on it will act on the stale character. In particular:

- `:save-character` (manual save) reads `(:character db)` and posts with its
  `:db/id`. If `:character` is stale at id=100 while the user believes they
  are editing/viewing the character at id=200, a manual save writes to
  entity 100, not 200.
- `::char5e/clone-character` reads `(:character db)` and clones from it. A
  stale `:character` means you clone a different character than the one you
  thought.
- Any interceptor in `character-interceptors` writes to `:character` — any
  builder edit touches whatever character is in that slot.

### Cross-tab / multiple-window scenario (responding to the question)

Same browser, two tabs/windows, same origin, same user. Each tab is an
independent JS runtime — its own re-frame `app-db`, React tree, subscription
caches, and `set-notes-handler` memoize cache. They do not share in-memory
state. They **do** share:

- `localStorage` (the "character" key, written by every `:set-character`
  dispatch via the `db-char->local-store` interceptor in `events.cljs:105`).
- Cookies / auth token.
- Server-side DB.

The orcpub code does **not** listen for the DOM `storage` event (grep
confirms no `addEventListener("storage"…)` anywhere). So writes from one tab
do NOT push into the other tab's running `app-db`. Cross-tab `:character`
contamination can only land via **page reload**, where `:initialize-db`
reads whichever tab wrote `localStorage` most recently.

#### Concrete cross-tab sequence that produces a wrong-character write

1. Tab A: open lower character's builder. `:character` = lower, id=100.
   localStorage = lower.
2. Tab B: open higher character's builder. `:character` = higher, id=200.
   localStorage = higher.
3. User clicks save in Tab A → autosave/manual save of lower succeeds →
   `:character-save-success` dispatches `[:set-character response]` →
   Tab A's `:character` = lower, localStorage = **lower**.
4. User clicks save in Tab B → same flow → Tab B's `:character` = higher,
   localStorage = **higher**.
5. Browser crash / accidental reload / device sleep on Tab A. Tab A reloads
   the builder URL. `:initialize-db` reads localStorage — which is now
   **higher** (from step 4). Tab A's `:character` = higher, id=200.
6. The builder URL has no id in the path — `character_builder.cljs:1893`
   and `:1908` render `[views5e/character-display nil true 1]`. The builder
   renders whatever `:character` happens to be.
7. **Tab A's user sees higher's data in what they believe is lower's tab.**
   They may or may not notice — if the character names are similar, or
   they're focused on a specific field (notes), they may just continue
   editing.
8. User types "L notes" into the notes field in Tab A, thinking they are
   editing lower. The in-builder notes handler uses `id=nil`
   (`views.cljs:2790` → `(set-notes-handler nil)`), so it dispatches
   `[::char/set-notes nil "L notes"]`, which takes the else branch in
   `update-character-fx` (`events.cljs:2432`) and writes to `:character`.
   `:character` is higher. So higher's in-memory notes become "L notes".
9. User clicks save in Tab A. `:save-character` reads `(:character db)` —
   higher with notes "L notes". Posts with `:db/id 200`. Server writes.
   Higher on server now has "L notes".
10. Meanwhile, lower on server still has whatever it had before. If the
    user also cleared lower's notes at some earlier point thinking they
    were re-doing them, lower is empty.
11. End state: **higher shows "L notes" (what the user meant for lower),
    lower shows empty**. Matches the reported symptom exactly.

This scenario does not require any shared Datomic sub-entity id. It does
not require a bug in the save path. It only requires:

- The user has two tabs / windows open on different characters.
- The user or browser triggers a reload at the wrong moment.
- The localStorage-restored `:character` doesn't match the tab's context.
- The id-match guard in `::char5e/set-character` correctly refuses to
  overwrite `:character`, so the staleness persists.
- The user edits in the builder (or via a path that routes through
  `:character`) without noticing the discrepancy.

The guard is working exactly as designed — it's protecting unsaved edits.
The design itself assumes a single-tab workflow and doesn't reconcile
`:character` against "which character should I be editing right now".

### Why G1 is the most consequential gap

1. Client sends a save for character A with `::strict/values {:db/id V-B …}`, where `V-B` is actually character B's values-entity id.
2. Server `update-character`:
   - `owns-entity?` passes (user owns A).
   - `current-ids` = db-ids of A's current tree — includes A's original values entity `V-A`.
   - `new-ids` = db-ids of incoming — includes `V-B`, not `V-A`.
   - `retract-ids = {V-A}` → cascade-retracts A's old values entity (emptying A's notes attribute because the entity holding it is gone).
   - Transact writes the incoming data to `V-B`, and reassigns A's `::se/values` ref to `V-B`.
3. Post-commit: `V-A` gone; `V-B` holds the just-saved notes; A references `V-B`; B still references `V-B`. Both A and B now share `V-B`.
4. Every subsequent save on either A or B writes to `V-B`. Every read of either returns `V-B`'s content.

This matches the observed symptom at the moment of observation. The "lower
empty, higher full" display state is explainable as stale UI caching or a
specific observation order; what's persistent in the DB is the shared-values
state.

### What produces a client payload with B's sub-entity id on an A save?

The code audit didn't find a path in current code. Possibilities still open:

- **Historical client bug** (pre-modernization, long-gone code): a version of
  clone or import logic that didn't fully strip ids. The shared-id state
  would persist to today in the user's DB.
- **Reactive fetch race**: re-frame sub caching could transiently mix two
  characters' data into one `character-map` entry before a save fires. Audit
  didn't find a concrete write path but these caches can surprise.
- **Orcbrew import / character export** that preserves ids across
  round-trip. Haven't deeply reviewed the import path.

### Proposed instrumentation (turn the next recurrence into evidence)

Without a repro or `d/pull`, add logging so the next occurrence leaves
diagnostic breadcrumbs:

1. **Server** (`do-save-character`, pre-transact): log `{:char-id (:db/id character), :values-id (-> character ::se/values :db/id), :selection-ids (map :db/id (::se/selections character)), :user username}`. If two distinct characters ever log the same `:values-id`, causation is proven.
2. **Server** (`update-character`, post-diff): log `retract-ids` and datom count. Unexpected `::se/values` retractions stand out.
3. **Client** (`::char5e/save-character` handler, pre-POST): log id being saved alongside top-level `:db/id` and `::entity/values :db/id`.
4. **Client** (`:character-save-success`): log returned `:db/id` and values-id to confirm round-trip.

### Proposed defensive fix (independent of root cause)

Adding G1 as a server-side rejection — not just logging — closes the most
dangerous gap regardless of which client bug produced the bad payload.
Implementation sketch:

```clojure
(defn validate-sub-entity-ownership
  "Every sub-entity :db/id in the incoming payload must either (a) not exist
   yet, or (b) already belong to the character being saved."
  [db incoming-char]
  (let [top-id    (:db/id incoming-char)
        current   (when top-id (d/pull db '[*] top-id))
        valid-ids (when current (entity/db-ids current))
        incoming  (entity/db-ids incoming-char)
        foreign   (reduce disj
                          (disj incoming top-id)   ; top id is already owned-checked
                          valid-ids)]
    (when (seq foreign)
      (throw (ex-info "Sub-entity :db/id not owned by this character"
                      {:error   :foreign-sub-entity-ids
                       :foreign foreign
                       :char-id top-id})))))
```

Wire it into `update-character` just after `owns-entity?` and return 400
(or a specific error code) if it throws. Cost: one extra set-difference per
save. Benefit: the reported symptom becomes impossible to produce via the
save route.

---



- **2026-04-20 (initial)** — T1/T2/T5/T6/T7 closed. T3/T4 flagged open.
- **2026-04-20 (deep dig)** — T3/T4 confirmed: `::se/values` is a component entity, server orphan-id logic is per-character, round-trip preserves sub-entity ids. T8/T9/T10 closed. S1/S2/S3 opened. Conclusion: modernization didn't introduce the bug; data-level shared id is the most plausible root cause.
- **2026-04-20 (timeline correction)** — reporter noticed bug Mar 15; modernization didn't reach production until Apr 8. Modernization code was never running when the symptom appeared. Shifts focus to pre-existing data corruption or a pre-modernization code bug.
- **2026-04-20 (reproduction)** — reporter reports the bug recurred after rebuilding notes on both characters. Active, reproducible state. Shared-values-entity theory still consistent, but the exact "one empty, one populated" steady state needs the reporter's rebuild sequence and a `d/pull` to interpret. Added prioritized asks for reporter.
- **2026-04-20 (deep code-smell audit)** — audited every write path into `character-map` and `:character` and the server save route. Found two real smells (level-up stale-db capture, builder-vs-sheet slot divergence) that cause edit **loss** but not edit **transfer**. No id-routing mechanism found that would write one character's notes onto another's values entity. The "one empty, one populated" steady state is inconsistent with persistent shared-values sharing, leaving either transient cascade-retraction or a persistence-layer anomaly as open possibilities. Static review has hit diminishing returns without either a `d/pull` or a repro log.
- **2026-04-20 (guards audit)** — inventoried existing guards (server `owns-entity?`, spec validation, client id-match/changed check in `::char5e/set-character`) and enumerated six missing guards. The most consequential gap is **G1**: the server trusts every sub-entity `:db/id` in the incoming payload. A save carrying another character's `::se/values :db/id` would cause orphan-id logic to retract the current character's values entity and re-point it at the foreign one, producing persistent shared state that matches the reported symptom. Sketched a defensive fix (reject payloads whose sub-entity ids aren't in the target character's current tree) and instrumentation to catch the next recurrence regardless of root cause.
- **2026-04-20 (two-window hypothesis)** — re-read the `::char5e/set-character` id-match guard closely. It protects unsaved builder edits from being clobbered by background fetches, but leaves `:character` **stale** when the id doesn't match. Combined with (a) localStorage being a single "character" slot shared across tabs but not listened to for cross-tab sync, and (b) the builder URL carrying no id (it always edits `:character`), this produces a concrete cross-tab scenario in which a reload in one tab can load the other tab's `:character` from localStorage, and subsequent builder edits + manual save post to the wrong entity. End state: the "wrong" character carries the text meant for the "right" one. Matches the reported symptom **without requiring any shared sub-entity ids in Datomic**. This is likely the real mechanism.
- **2026-04-20 (DM-workflow design options)** — a DM handling a 2–6 character party across tabs is the generalized form of the two-window case. Documented five design options (A: sessionStorage-per-tab draft; B: id in builder URL; C: per-id draft map; D: save-time id reconciliation; E: server-side G1 sub-entity-id guard). Recommended ship order: A (tiny, closes the contamination path), B (right architectural fix, also cleans up level-up stale-db and builder-vs-sheet divergence smells), E (orthogonal server-side safety net against cross-writes).
