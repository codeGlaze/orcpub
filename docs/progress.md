# Progress Log

> Keep updated and concise. Agent runs `git branch` to get current branch.

---

## Current State

### Completed: Duplicate Key Resolution
**Goal**: Let users fix key conflicts during import (e.g., two `:artificer` classes)

**All Complete:**
- Duplicate key detection at import time (internal + external conflicts)
- Warning messages in import result
- Fuzzy matching for "(not loaded)" suggestions
- **NEW**: Conflict resolution modal UI (user chooses which key to rename)
- **NEW**: Key renaming with internal reference updates (subclasses→parent, etc.)
- **NEW**: Tests for key detection and renaming (`import_validation_test.cljs`)

**Key files**: `import_validation.cljs` (v0.06), `events.cljs` (v0.05), `views.cljs` (v0.06)

### In Progress: Export Validation / Required Fields
**Goal**: Prevent bad exports that cause downstream issues (PDF crash, etc.)

**Completed:**
- `nil nil` fix (string + data level cleaning) - was causing PDF black screen
- Created `docs/HOMEBREW_REQUIRED_FIELDS.md` documenting spec vs functional requirements

**Pending:**
- Test which fields actually break things when empty (marked "?" in doc)
- Add export-time validation to warn/prevent incomplete content
- Find source of `nil nil` entries (likely unfilled required fields)

**Key files**: `import_validation.cljs`, `docs/HOMEBREW_REQUIRED_FIELDS.md`

---

## Ready for Testing

1. **Conflict Resolution Modal**: Import file with duplicate keys, modal should appear allowing user to rename/skip/keep
2. **Key Renaming**: Choose to rename a key, verify subclasses update their parent references
3. **PDF Export**: Import serakat library (dev 0.0.12), create Owlbear + Drunken Master build, export PDF
4. **Fuzzy matching**: Delete a plugin, verify "(not loaded - try X?)" suggestions appear

### Running Tests
```bash
# ClojureScript tests (includes import_validation_test.cljs)
lein doo phantom test once

# Or with figwheel running
lein figwheel test
```

---

## Done

### 2026-01-15 (Session 2)
- Conflict resolution modal UI
- Key renaming logic with internal reference updates
- Events: `:start-conflict-resolution`, `:set-conflict-decision`, `:apply-conflict-resolutions`
- Tests for duplicate detection, key renaming, reference updates

### 2026-01-15 (Session 1)
- Duplicate key detection (internal + external conflicts)
- Fuzzy key matching (Levenshtein, prefix, display name)
- `nil nil` cleanup (string-level + data-level)
- Raw entity fallback for class/subclass display
- Homebrew required fields documentation

### 2026-01-14
- Import changelog panel, two-phase cleaning
- Field-specific nil handling
- Delete-all-plugins instant (no reload)

---

## Version Summary

| File | Version | Description |
|------|---------|-------------|
| `core.cljs` | 0.0.12 | Dev version for build verification |
| `import_validation.cljs` | 0.06 | Key renaming for conflict resolution |
| `events.cljs` | 0.05 | Conflict resolution events |
| `views.cljs` | 0.06 | Conflict resolution modal |

---

## Handoff Notes

If picking this up:
1. Run `/onboard` for quick context
2. **Duplicate keys**: Feature complete! Test the modal UI
3. **Export validation**: Need to test which fields break what, then add validation
4. Key files: `import_validation.cljs`, `events.cljs`, `views.cljs`
5. Debug files: `debug-examples/serakat*.orcbrew` (PDF crash test case)
6. Tests: `test/cljs/orcpub/dnd/e5/import_validation_test.cljs`
