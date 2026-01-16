# OrcPub Documentation

Welcome to the OrcPub documentation! This directory contains comprehensive guides for developers and power users working with OrcPub's homebrew content system.

## Quick Navigation

### For Users

**Working with Homebrew Content:**
- [📥 Import/Export Validation](ORCBREW_FILE_VALIDATION.md) - How to safely import and export `.orcbrew` files
- [⚔️ Conflict Resolution](CONFLICT_RESOLUTION.md) - Handling duplicate keys during import
- [🔍 Missing Content Detection](CONTENT_RECONCILIATION.md) - Finding and fixing missing content references
- [📋 Required Fields Guide](HOMEBREW_REQUIRED_FIELDS.md) - What fields are needed for each content type

### For Developers

**Development Guides:**
- [🏗️ Codebase Overview](CODEBASE.md) - Architecture, patterns, and key concepts
- [🚨 Error Handling](ERROR_HANDLING.md) - Error handling utilities and best practices
- [📝 Progress Log](progress.md) - Current session state and handoff notes

## Feature Overview

### Import/Export System

The OrcPub import/export system provides comprehensive validation and error handling for `.orcbrew` files:

```
Import File → Validate → Detect Conflicts → Resolve → Import Successfully
                ↓            ↓                ↓
            Fix Issues   Show Modal      Rename/Skip/Replace
```

**Key capabilities:**
- ✅ Progressive import (recovers valid items, skips invalid)
- ✅ Detailed error messages with line numbers
- ✅ Automatic cleaning of common corruption patterns
- ✅ Pre-export validation to catch issues early
- ✅ Console logging for debugging

**Documentation:** [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md)

**Implementation:**
- `src/cljs/orcpub/dnd/e5/import_validation.cljs` - Core validation logic
- `src/cljs/orcpub/dnd/e5/events.cljs` - Import/export events
- `test/cljs/orcpub/dnd/e5/import_validation_test.cljs` - Test suite

### Conflict Resolution

When importing content with duplicate keys, the conflict resolution system provides an interactive modal for resolving conflicts:

```
Detect Duplicates → Show Modal → User Chooses → Update References → Import
     ↓                  ↓             ↓               ↓
External/Internal   Visual UI   Rename/Skip      Auto-update
 Conflicts         Per-item      Replace         Subclasses etc.
```

**Key capabilities:**
- ✅ Detects external conflicts (existing vs importing)
- ✅ Detects internal conflicts (within import file)
- ✅ Interactive resolution modal
- ✅ Automatic reference updates when renaming
- ✅ Bulk operations (rename all, skip all)

**Documentation:** [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md)

**Implementation:**
- `src/cljs/orcpub/dnd/e5/import_validation.cljs` - Duplicate detection
- `src/cljs/orcpub/dnd/e5/views.cljs` - Conflict modal UI
- `src/cljs/orcpub/dnd/e5/events.cljs` - Resolution events

### Content Reconciliation

The content reconciliation system detects when characters reference homebrew content that isn't currently loaded, and suggests alternatives:

```
Load Character → Extract References → Check Availability → Suggest Alternatives
      ↓                 ↓                    ↓                    ↓
  Options tree     All ::entity/keys    Missing items?    Fuzzy matching
                                             ↓
                                    ":artificer (not loaded - try :armorer?)"
```

**Key capabilities:**
- ✅ Detects missing classes, races, backgrounds, etc.
- ✅ Fuzzy matching suggestions (Levenshtein, prefix, name matching)
- ✅ Source name display for disambiguation
- ✅ Built-in content exclusions (PHB, Xanathar's, etc.)
- ✅ Clear UI warnings with actionable suggestions

**Documentation:** [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md)

**Implementation:**
- `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs` - Detection & fuzzy matching
- `src/cljs/orcpub/dnd/e5/subs.cljs` - Missing content subscriptions
- `src/cljs/orcpub/dnd/e5/views.cljs` - Warning UI components

### Error Handling Framework

A DRY error handling system built on `ex-info` with reusable macros for common operations:

```clojure
;; Before (verbose, inconsistent)
(try
  @(d/transact conn [party])
  (catch Exception e
    (println "ERROR:" (.getMessage e))
    (throw (ex-info "Unable to create party" {:error :failed} e))))

;; After (concise, consistent)
(errors/with-db-error-handling :party-creation-failed
  {:party-data party}
  "Unable to create party. Please try again."
  @(d/transact conn [party]))
```

**Key capabilities:**
- ✅ Macros for database, email, and validation operations
- ✅ Structured error data with `ex-info`
- ✅ Automatic logging with context
- ✅ User-friendly error messages
- ✅ Comprehensive test coverage

**Documentation:** [ERROR_HANDLING.md](ERROR_HANDLING.md)

**Implementation:**
- `src/cljc/orcpub/errors.cljc` - Error handling utilities
- `test/clj/orcpub/errors_test.clj` - Test suite
- Used throughout: `email.clj`, `datomic.clj`, `routes/*.clj`, `pdf.clj`

### Required Fields Reference

A comprehensive reference of which fields are required for each homebrew content type:

**Tracks:**
- Spec requirements (validated by `clojure.spec`)
- Functional requirements (will break features if missing)
- Default values (can be auto-filled)
- Optional fields (truly optional)

**Covers:**
- Classes, Subclasses
- Races, Subraces
- Backgrounds
- Spells, Items
- And more...

**Documentation:** [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md)

## Common Workflows

### Creating Homebrew Content

1. Create your content in OrcPub
2. Export to `.orcbrew` file → [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md)
3. Check console for validation warnings
4. Fix any missing required fields → [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md)
5. Re-export to create clean file

### Importing Homebrew Content

1. Import `.orcbrew` file → [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md)
2. Review import results (success/warnings/errors)
3. Resolve any conflicts → [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md)
4. Check for missing content warnings → [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md)
5. Export all content to create backup

### Debugging Import Issues

1. Check browser console (F12) for detailed errors
2. Review validation messages → [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md)
3. Identify missing required fields → [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md)
4. Use progressive import to recover valid items
5. File bug report if needed

### Resolving Character Issues

1. Character shows "(not loaded)" warnings
2. Check which content is missing → [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md)
3. Import required plugin
4. Or switch to suggested alternative
5. Verify character displays correctly

## Implementation Status

### ✅ Completed Features

- [x] Import/export validation
- [x] Progressive import strategy
- [x] Automatic corruption cleaning
- [x] Duplicate key detection
- [x] Conflict resolution modal
- [x] Key renaming with reference updates
- [x] Missing content detection
- [x] Fuzzy matching suggestions
- [x] Error handling framework
- [x] Comprehensive test coverage
- [x] User documentation

### 🚧 In Progress

- [ ] Export-time validation warnings
- [ ] Required field validation enforcement
- [ ] Testing which fields actually break features

### 💡 Future Enhancements

**Import/Export:**
- Batch import (multiple files at once)
- Import preview (show what will be imported)
- Merge wizard (combine multiple versions)
- Version control integration

**Conflict Resolution:**
- Smart suggestions (recommend best resolution)
- Diff view (compare conflicting versions)
- Conflict history (remember past decisions)
- Auto-merge compatible changes

**Content Reconciliation:**
- Auto-fix button (one-click apply suggestion)
- Bulk suggestions (fix all missing at once)
- Plugin recommendations (suggest which to install)
- Central content library (download missing content)

**Error Handling:**
- Retry logic for transient failures
- Circuit breakers for external dependencies
- Error monitoring integration (Sentry, Rollbar)
- Internationalization (multi-language errors)

## File Reference

### Documentation Files

```
docs/
├── README.md                          # This file (documentation index)
├── ORCBREW_FILE_VALIDATION.md        # Import/export validation (417 lines)
├── CONFLICT_RESOLUTION.md            # Duplicate key handling (584 lines)
├── CONTENT_RECONCILIATION.md         # Missing content detection (458 lines)
├── ERROR_HANDLING.md                 # Error handling framework (210 lines)
├── HOMEBREW_REQUIRED_FIELDS.md       # Required fields reference (201 lines)
├── CODEBASE.md                       # Codebase architecture (varies)
└── progress.md                        # Session progress log (varies)
```

### Implementation Files

**Import/Export:**
```
src/cljs/orcpub/dnd/e5/
├── import_validation.cljs            # Validation, conflict detection, key renaming
├── events.cljs                       # Import/export/resolution events
└── views.cljs                        # Import UI, conflict modal
```

**Content Reconciliation:**
```
src/cljs/orcpub/dnd/e5/
├── content_reconciliation.cljs       # Missing content detection, fuzzy matching
├── subs.cljs                         # Missing content subscriptions
├── views.cljs                        # Warning UI components
└── spell_subs.cljs                   # Spell-specific detection
```

**Error Handling:**
```
src/cljc/orcpub/
└── errors.cljc                       # Error handling utilities
```

**Utilities:**
```
src/cljc/orcpub/
└── common.cljc                       # Shared utilities (kw-base, traverse-nested)
```

**Tests:**
```
test/cljs/orcpub/dnd/e5/
├── import_validation_test.cljs       # Import validation tests
└── ...
```

## Getting Help

### For Users

1. **Check the documentation** - Start with the relevant guide above
2. **Check the console** - Press F12 and look for detailed error messages
3. **Try progressive import** - Can recover partial data from corrupted files
4. **File an issue** - GitHub issues with reproduction steps

### For Developers

1. **Read the codebase overview** - [CODEBASE.md](CODEBASE.md)
2. **Check the progress log** - [progress.md](progress.md) for current state
3. **Review the test suite** - `test/cljs/orcpub/dnd/e5/import_validation_test.cljs`
4. **Check browser console** - Detailed logging for all operations
5. **File a bug report** - Include error messages and reproduction steps

## Contributing

When adding new features:

1. **Update relevant documentation** - Don't leave docs stale
2. **Add tests** - Comprehensive test coverage is required
3. **Use error handling utilities** - Don't roll your own error handling
4. **Follow existing patterns** - See [CODEBASE.md](CODEBASE.md)
5. **Update this index** - Add new docs to the navigation above

## Version Information

**Current version:** See individual documentation files for version histories

**Branch:** `claude/add-error-handling-mk82zx2vzck9nv9m-IMm3C`

**Recent updates:**
- 2026-01-15: Conflict resolution modal, key renaming, reference updates
- 2026-01-15: Missing content detection, fuzzy matching, source names
- 2026-01-14: Import validation, progressive import, automatic cleaning
- Earlier: Error handling framework, required fields documentation

**Compatibility:** All features work with existing `.orcbrew` files (backwards compatible)

---

**Last updated:** 2026-01-16

**Maintained by:** Claude Code development sessions

**Questions?** Check the documentation above or file an issue on GitHub.
