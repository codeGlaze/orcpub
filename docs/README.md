# OrcPub Documentation

Guides for developers and power users working with OrcPub's homebrew content system.

## Quick Navigation

**For Users:**
- [📥 Import/Export Validation](ORCBREW_FILE_VALIDATION.md) - Safely import/export `.orcbrew` files
- [⚔️ Conflict Resolution](CONFLICT_RESOLUTION.md) - Handle duplicate keys during import
- [🔍 Missing Content Detection](CONTENT_RECONCILIATION.md) - Find/fix missing content references
- [📋 Required Fields Guide](HOMEBREW_REQUIRED_FIELDS.md) - Required fields per content type

**For Developers:**
- [🏗️ Codebase Overview](CODEBASE.md) - Architecture and patterns
- [🚨 Error Handling](ERROR_HANDLING.md) - Error handling utilities
- [📝 Progress Log](progress.md) - Session state and handoff notes

## Key Design Decisions

### Why Progressive Import?

**Problem:** Users had partially corrupted `.orcbrew` files. Previous all-or-nothing approach: one bad item blocks entire import.

**Decision:** Import valid items, skip invalid, show detailed error report.

**Rationale:** Partial data recovery better than total failure. Users can fix issues incrementally.

→ [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md)

### Why Interactive Conflict Resolution?

**Problem:** Silent overwrites caused data loss. Users wouldn't notice until characters broke.

**Decision:** Detect conflicts pre-import, show modal with resolution options (rename/skip/replace).

**Critical insight:** When renaming parent content (e.g., class), all child references (subclasses) must auto-update or they become orphaned. Early implementation forgot this → orphaned subclasses appeared in UI but were unselectable.

→ [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md)

### Why Fuzzy Matching for Missing Content?

**Problem:** Content keys change between versions (`:blood-hunter` → `:blood-hunter-v2`). Users see "(not loaded)" with no help.

**Decision:** Multiple fuzzy matching strategies (Levenshtein, prefix, name similarity) to catch typos and versioning.

**Gotcha:** Must exclude built-in content (PHB, Xanathar's) or system suggests switching from homebrew Artificer to PHB Artificer (which doesn't exist in 5e).

→ [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md)

**Problem:** Inconsistent error handling across codebase. Some code logged, some didn't. User messages inconsistent.

**Decision:** Centralize in macros (`with-db-error-handling`, `with-email-error-handling`, `with-validation`).

**Rationale:** Consistency in logging, user messages, error data structure. Easier to add monitoring later.

→ [ERROR_HANDLING.md](ERROR_HANDLING.md)

## Common Workflows

**Creating homebrew:** Create in UI → Export → Check console warnings → Fix required fields → Re-export

**Importing content:** Import file → Resolve conflicts (if any) → Check for missing content warnings

**Debugging imports:** Console (F12) → Check validation errors → Use progressive import to recover partial data

**Fixing characters:** Check missing content warnings → Import plugin or use suggested alternative

## Known Limitations

**Export validation:** Currently validates on import only. Export-time validation would catch issues earlier.

**Field requirements:** Not all required fields are enforced. Some will silently break features (see HOMEBREW_REQUIRED_FIELDS.md).

**Batch operations:** Can only import one file at a time. Multi-file import with cross-reference resolution would be valuable.

## Implementation Files

**Import/Export:** `import_validation.cljs`, `events.cljs`, `views.cljs` (import UI, conflict modal)
**Content Reconciliation:** `content_reconciliation.cljs`, `subs.cljs`, `views.cljs` (warning UI)
**Error Handling:** `errors.cljc` (DRY macros)
**Tests:** `import_validation_test.cljs`

All in `src/cljs/orcpub/dnd/e5/` unless noted.

## Debugging Tips

**Import failures:** Check console (F12) → Use progressive import to recover partial data

**Character broken:** Look for "(not loaded)" warnings → Import missing plugin or use suggested alternative

**Conflicts on import:** Modal should appear automatically → Choose rename/skip/replace per item

---

**Branch:** `claude/add-error-handling-mk82zx2vzck9nv9m-IMm3C` | **Last updated:** 2026-01-16
