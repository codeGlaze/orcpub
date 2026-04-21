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

---

## Ask reporter (prioritized)

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


- **2026-04-20 (initial)** — T1/T2/T5/T6/T7 closed. T3/T4 flagged open.
- **2026-04-20 (deep dig)** — T3/T4 confirmed: `::se/values` is a component entity, server orphan-id logic is per-character, round-trip preserves sub-entity ids. T8/T9/T10 closed. S1/S2/S3 opened. Conclusion: modernization didn't introduce the bug; data-level shared id is the most plausible root cause.
- **2026-04-20 (timeline correction)** — reporter noticed bug Mar 15; modernization didn't reach production until Apr 8. Modernization code was never running when the symptom appeared. Shifts focus to pre-existing data corruption or a pre-modernization code bug.
- **2026-04-20 (reproduction)** — reporter reports the bug recurred after rebuilding notes on both characters. Active, reproducible state. Shared-values-entity theory still consistent, but the exact "one empty, one populated" steady state needs the reporter's rebuild sequence and a `d/pull` to interpret. Added prioritized asks for reporter.
