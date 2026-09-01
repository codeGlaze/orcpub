# TODO — Tracked Issues

## localStorage corrupt data persistence

**Status:** Open
**Severity:** Medium
**Reported:** 2026-02-21

### Problem

**Narrowed (2026-09).** The *unreadable / parse-failure* case is now handled:
`get-local-storage-item` self-heals a bare-colon empty keyword in place and
re-saves, and `handle-unreadable` copies homebrew to a `:corrupt` companion slot
(recoverable) before clearing the active slot, hard-deleting only throwaway keys.

What remains is the **parses-fine-but-fails-spec** case: when `reg-local-store-cofx`
reads data that reads back OK but doesn't match its spec, it logs "Invalid stored
item, ignoring" and drops it for the session — but never removes it, so the warning
fires on every reload and the value can't self-correct until something overwrites
that key.

**Constraint — do not just `removeItem`.** The affected keys hold real user data:
an in-progress character, user prefs, and the class / subclass / invocation / boon /
magic-item **builder drafts**. Given the homebrew-consistency work on localStorage, a
fix must follow the parse path's model — **quarantine homebrew to a `:corrupt` slot,
and delete only genuinely throwaway keys.** A blind delete would lose a homebrew
draft, which is exactly what that work protects.

(The old combat-tracker `assoc-in`-on-nil corruption vector below is a separate,
already-partially-fixed thread — kept for reference.)

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

- `src/cljs/orcpub/dnd/e5/db.cljs` — `get-local-storage-item` / `handle-unreadable` /
  `reg-local-store-cofx` (line ~303–360)
- `src/cljs/orcpub/dnd/e5/events.cljs` — `set-combat-path-prop` nil guard
- All `*->local-store` serializers use `(str data)` / `reader/read-string`

---

## Content-library management — remaining work

**Status:** Open
**Severity:** Low — enhancements; the shipped resolution already removes the data bug
**KB doc:** [docs/kb/library-management-and-conflicts.md](kb/library-management-and-conflicts.md)

The My Content library, duplicate-key resolution, disabled-reason badges, the
mutual-exclusion UX, the opinionated (summary-first) import, the four-level
disable hierarchy, and move/copy content between sources are built and on
`feature/content-library-management` (see the KB doc for how they work). These are
the not-yet-built follow-ups, roughly in dependency order.

### Example / demo content tier

A read-only example/demo tier of content, with a per-account version marker and
copy-on-edit graduation (editing an example copies it into the user's own library
so upstream updates never clobber their edits). **Design notes + decided approach
(copy-on-edit + provenance, not a diff) and the separate "variant rules" idea:**
[docs/kb/demo-content-tier.md](kb/demo-content-tier.md).

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
