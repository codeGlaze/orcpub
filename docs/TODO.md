# TODO — Tracked Issues

## Datomic transactor crashes — investigate Postgres migration

**Status:** Open  
**Severity:** Critical — transactor crashing 3–5× per day, 2–3 min downtime each  
**Reported:** 2026-02-26  
**KB doc:** [docs/kb/datomic-crash-analysis.md](kb/datomic-crash-analysis.md)

### Summary

The Datomic transactor is self-terminating multiple times daily with
`"Critical failure, cannot continue: Heartbeat failed"`. Root cause is H2
write-lock contention during memoryIndex flushes starving the heartbeat thread.
`writeConcurrency=4` amplifies the problem — H2 cannot parallelize writes.

### Immediate mitigation (low risk, config only)

Set `datomic.writeConcurrency=1` in the transactor properties file. See KB doc
for caveats.

### Permanent fix

Migrate from Datomic Free + H2 to Datomic Pro + PostgreSQL. Datomic Pro is
free under Apache 2.0 (see `docs/migration/datomic-pro.md` — peer migration
already done). What remains is the **storage backend migration**:

1. Provision PostgreSQL (Docker service or managed)
2. Run Datomic's SQL init scripts (`bin/sql/postgres-*.sql`)
3. Export data from H2 transactor with `bin/datomic backup-db`
4. Restore into Postgres transactor with `bin/datomic restore-db`
5. Update transactor properties: `storage-class=sql`, JDBC params
6. Update Docker Compose to add Postgres service and remove H2 volume

### Related

- `docker/datomic/` — transactor container and config templates
- `docs/migration/datomic-pro.md` — peer library already migrated to Pro
- `docs/kb/datomic-crash-analysis.md` — full root cause analysis with log evidence

---

## localStorage corrupt data persistence

**Status:** Open
**Severity:** Medium
**Reported:** 2026-02-21

### Problem

When `reg-local-store-cofx` reads localStorage data that fails spec validation,
it logs a warning and ignores the data — but never removes it. The corrupt data
persists across reloads, producing `INVALID ITEM FOUND, IGNORING` on every page
load. If the user never interacts with the affected feature (to trigger an
overwrite), the corrupt data stays indefinitely.

Known corruption vector: `assoc-in` on `nil` builds maps with integer keys
instead of vectors. Example from combat tracker:

```clojure
(assoc-in nil [:monsters 0 :monster] :adult-gold-dragon)
;; => {:monsters {0 {:monster :adult-gold-dragon}}}  — MAP, not vector
```

This was partially fixed by guarding `set-combat-path-prop` with
`(or combat default-combat)`, but other handlers using `assoc-in` through
`path` interceptors may have the same vulnerability.

### Proposed fix

Scope cleanup by data criticality:

| Category | Examples | Action on invalid |
|----------|----------|-------------------|
| Ephemeral | combat, builder state | `.removeItem` — safe to lose |
| Rebuildable | spells, monsters | `.removeItem` — regenerated from source |
| Critical | plugins, characters, user | Quarantine: rename key to `<key>_corrupt_<timestamp>` |

This preserves recovery options for irreplaceable user data (homebrew plugins
can be 2-5MB of daily imports) while cleaning up transient state that would
otherwise stubbornly persist.

### Related

- `src/cljs/orcpub/dnd/e5/db.cljs` — `reg-local-store-cofx` (line ~252)
- `src/cljs/orcpub/dnd/e5/events.cljs` — `set-combat-path-prop` nil guard
- All `*->local-store` serializers use `(str data)` / `reader/read-string`

---

## Content-library management — remaining work

**Status:** Open
**Severity:** Low — enhancements; the shipped resolution already removes the data bug
**KB doc:** [docs/kb/library-management-and-conflicts.md](kb/library-management-and-conflicts.md)

The My Content library, duplicate-key resolution, disabled-reason badges, and the
mutual-exclusion UX are built and on `feature/content-library-management` (see the
KB doc for how they work). These are the not-yet-built follow-ups, roughly in
dependency order.

### Opinionated / UX-first import (unblocked)

The conflict modal is powerful but can overwhelm non-technical users. Make it
**opt-in, not a mandatory gate**: on import, auto-resolve with a severity-driven
safe default (risky clash → import the newcomer disabled, existing content
untouched and deterministic; harmless clash → keep both), then show a one-line
plain-language summary + a "Review / change" link that opens the full modal. This
mirrors the existing export-warning modal ("Export & Auto-Fix" primary + a hidden
"export raw" hatch), so it's a consistent pattern, not a new one. The full modal
becomes the advanced / Review view; power users and mods keep total control. The
mutual-exclusion UX it depended on is now shipped, so this is ready to build.

### Disable hierarchy — FORMAT-SAFE

Four disable levels: **global** (overlay) / **source** (`:disabled?` in plugin
data — exists) / **section** (new) / **item** (`:disabled?` — exists). Global +
section live in a **local overlay store** (db/localStorage), NOT in the plugin /
`.orcbrew` data, so there is zero format/spec change and existing libraries are
untouched; `plugin-vals` ORs all four when filtering, and descendants of a
disabled ancestor render dimmed (effective/inherited state).

- Trade-off: section/global disable is a local "view" preference and does NOT
  travel with exported packs (source/item disable still do).
- Constraint: section-disable must NOT be stored inside the content-type map — a
  `:disabled?` there fails the `::plugin` spec (value must be an item) and would
  quarantine the whole source.

### Move / copy content between sources

Let a user move or copy an item from one source to another. Reuses
`detect-duplicate-keys` / `apply-key-renames` to handle any key collision the
move creates.

### Example / demo content tier

A read-only example/demo tier of content, with a per-account version marker and
copy-on-edit graduation (editing an example copies it into the user's own library
so upstream updates never clobber their edits).

### Also parked (with reasons — do not lose)

- **Account backup/restore** of libraries + prefs: blocked NOT by code (the
  `share_url` codec already does gzip + fail-closed decode) but by **legal**
  (hosting user-uploaded, often copyrighted content), **database/scale** (3–5 MB ×
  every user), and standing **admin resistance**. If ever entertained: server-side
  at-rest/transport encryption, NOT end-to-end (lost key → lost backup); and
  backup-restore (last-write-wins) *before* multi-device sync (sync needs conflict
  resolution).
- **Compress localStorage plugins** with the existing gzip codec to fit more under
  the ~5 MB browser ceiling — no cloud, no legal exposure. Caveats: makes stored
  content opaque to inspection, and a hard cap is still needed (compression moves
  the ceiling, doesn't remove it).
- **Native `<select>` → custom popover**: the add-content menu uses a native
  select; adopt `port/redesign-on-refactor`'s Phase 7 custom-select popover when
  branches converge (NOT a cheap early crib — it's coupled to that branch's
  theme-token infrastructure).
