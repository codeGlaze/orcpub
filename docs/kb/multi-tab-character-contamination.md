# One localStorage slot for "the character" — how two tabs corrupt each other's edits

**Live defect.** References are against `integration` at `36766010` (identical `src/` on
`agents/develop` `260aae1f`), checked 2026-09-12.

A user with two builder tabs open on different characters can have edits from one land on the other.
Reported as notes moving between two clones of the same character: the lower-level clone's notes
went blank and the higher-level clone held what the lower one used to have.

## The mechanism

The in-progress character is cached in localStorage under **one fixed key with no character id in
it** — `db.cljs:34`:

```clojure
(def local-storage-character-key "character")
```

Written unconditionally on save, `db.cljs:198-203`:

```clojure
(defn character->local-store [character]
  (when js/window.localStorage
    (set-item local-storage-character-key
              (str (assoc (char5e/to-strict character) :changed (:changed character))))))
```

Read back into `:character` at boot, `events.cljs:316`, via the `:local-store-character` cofx
(`db.cljs:391`):

```clojure
local-store-character (assoc :character local-store-character)
```

localStorage is **per-origin, not per-tab**. Two tabs on different characters write the same slot,
last writer wins. A reload in either tab restores whichever character was saved most recently
**across all tabs** — so the user is looking at tab A's page while `:character` holds tab B's
entity. Keystrokes land on the wrong character; a save persists them there.

The symptom is confusing because the *visible* page does not change — only the floating `:character`
slot behind it does. A user who "rebuilds" the apparently-empty notes clears the real ones, which is
how the reported case ended with content on the wrong clone and nothing on the right one.

## Where the notes actually live

Notes are a string on a per-character component sub-entity, so nothing about the storage schema
mixes two characters — `schema.clj:182-185`:

```clojure
{:db/ident ::se/values
 :db/valueType :db.type/ref
 :db/cardinality :db.cardinality/one
 :db/isComponent true}
```

and `schema.clj:234-237`:

```clojure
{:db/ident ::char5e/notes
 :db/valueType :db.type/string
 :db/cardinality :db.cardinality/one
 :db/noHistory true}
```

Both line references were re-checked against the current tree and still hold. **This matters for
triage: the contamination is a client-side slot collision, not a server-side or schema problem.**
A server-side guard against payloads mixing two characters' sub-entity ids is worth having as
defence in depth, but it is not what causes the reported symptom and would not fix it.

`::char5e/notes` carries `:db/noHistory true`, so overwritten notes are **not recoverable from
Datomic history**. Data lost this way is lost.

## The shape of a fix

Key the draft by character id rather than using a single slot, and tie the restore to the id in the
URL, so a tab only ever rehydrates the character it is actually showing. Not implemented; the
investigation that established the above stopped at the diagnosis.

## Provenance

Established on `claude/fix-character-notes-merge-4YNzf` (`702fffc7`), which carries the
investigation and five tests pinning the event-layer behaviour (`events_test.cljs`, +95 lines) but
no fix. Its 5 new definitions appear on no other branch. Nothing here was compiled or run — the
mechanism is verified by reading the code paths above.

## Related

- [entity-options-architecture.md](entity-options-architecture.md) — entity structure and the
  autosave path.
- [spa-routing-architecture.md](spa-routing-architecture.md) — where a character id in the URL is
  and is not honoured.
