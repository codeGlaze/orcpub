# Changelog: Error Handling, Import Validation & Content Reconciliation

All changes target `develop` from `feature/error-handling-import-validation`.

---

## New Features

### Import Validation (`import_validation.cljs` -- new file)
- **Unicode normalization**: Converts smart quotes, em-dashes, non-breaking spaces, and 40+ other problematic Unicode characters to ASCII equivalents on import and homebrew save. Prevents copy-paste corruption from Word/Google Docs.
- **Required field detection & auto-fill**: On import, missing required fields (`:name`, `:hit-die`, `:speed`, etc.) are auto-filled with placeholder values like `[Missing Name]`. Content types covered: classes, subclasses, races, subraces, backgrounds, feats, spells, monsters, invocations, languages, encounters.
- **Trait validation**: Nested `:traits` arrays are checked for missing `:name` fields and auto-filled.
- **Option validation**: Empty options (`{}`) created by the UI are detected and auto-filled with unique default names ("Option 1", "Option 2", etc.).
- **Multi-plugin format detection**: Distinguishes single-plugin from multi-source orcbrew files for correct processing.

### Export Validation
- **Pre-export warning modal**: Before exporting homebrew, all content is validated for missing required fields. If issues are found, a modal lists them with an "Export Anyway" option.
- **Specific save error messages**: `reg-save-homebrew` now extracts field names from spec failures and shows targeted messages instead of generic "You must specify a name" errors.

### Content Reconciliation (`content_reconciliation.cljs` -- new file)
- **Missing content detection**: When a character references homebrew content that isn't loaded (e.g., deleted plugin), the system detects missing races, classes, and subclasses.
- **Fuzzy key matching**: Uses prefix matching and base-keyword similarity to suggest available content that resembles missing keys (top 5 matches with similarity scores).
- **Source inference**: Guesses which plugin pack a missing key likely came from based on key structure.

### Missing Content Warning UI (`character_builder.cljs`)
- **Warning banner**: Orange expandable banner appears in character builder when content is missing, showing count and details.
- **Detail panel**: Lists each missing item with its content type, key, inferred source, and suggestions for similar available content.
- **DOM IDs for testability**: `#missing-content-warning`, `#missing-content-details`, `.missing-content-item` with `data-key` and `data-type` attributes.

### Conflict Resolution Modal (`views.cljs`, `events.cljs`)
- **Duplicate key detection**: On import, detects keys that conflict with already-loaded homebrew (both internal duplicates within a file and external conflicts with existing content).
- **Resolution UI**: Modal presents each conflict with rename options. Key renaming updates internal references (subclass -> parent class mappings, etc.).

### OrcBrew CLI Debug Tool (`tools/orcbrew.clj` -- new file)
- `lein prettify-orcbrew <file>` -- Pretty-prints orcbrew EDN for readability.
- `lein prettify-orcbrew <file> --analyze` -- Reports potential issues: nil-nil patterns, problematic Unicode, disabled entries, missing trait names, file structure summary.

---

## Bug Fixes

### nil nil Corruption (`events.cljs`)
- **Root cause fix**: `set-class-path-prop` was calling `assoc-in` with a nil path, producing `{nil nil}` entries in character data. Now guards against nil path before the second `assoc-in`.

### Nil Character ID Crash (`views.cljs`)
- Character list page crashed with "Cannot form URI without a value given for :id parameter" when characters had nil `:db/id`. Added `(when id ...)` guard to skip rendering those entries.

### Subclass Key Preservation (`options.cljc`, `spell_subs.cljs`)
- Subclass processing now uses explicit `:key` field if present (for renamed plugins), falling back to name-generated key. Prevents renamed keys from reverting.
- `plugin-subclasses` subscription preserves map keys and sets `:key` on subclass data correctly.

### Plugin Data Robustness (`spell_subs.cljs`)
- `plugin-vals` subscription wrapped in try-catch to skip malformed plugin data instead of crashing.
- `level-modifier` handles unknown modifier types gracefully (logs warning, returns nil instead of throwing).
- `make-levels` filters out nil modifiers with `keep`.

---

## Error Handling (Backend)

### Database (`datomic.clj`)
- Startup wrapped in try-catch with structured errors: `:missing-db-uri`, `:db-connection-failed`, `:schema-initialization-failed`.

### Email (`email.clj`)
- Email config parsing catches `NumberFormatException` for invalid port (`:invalid-port`).
- `send-verification-email` and `send-reset-email` check postal response and raise on failure (`:verification-email-failed`).

### PDF Generation (`pdf.clj`, `pdf_spec.cljc`)
- Network timeouts (10s connect, 10s read) for image loading. Specific handling for `SocketTimeoutException` and `UnknownHostException`.
- Nil guards throughout `pdf_spec.cljc`: `total-length`, `trait-string`, `resistance-strings`, `profs-paragraph`, `keyword-vec-trait`, `damage-str`, spell name lookup. All use fallback strings like "(unknown)", "(Unknown Spell)", "(Unnamed Trait)".

### Routes (`routes.clj`, `routes/party.clj`)
- All mutation endpoints wrapped with error handling: verification, password reset, entity CRUD, party operations. Each uses structured error codes (`:verification-failed`, `:entity-creation-failed`, `:party-creation-failed`, etc.).

### System (`system.clj`)
- PORT environment variable parsing validates numeric input (`:invalid-port`).

### Error Infrastructure (`errors.cljc` -- expanded)
- New error code constants for auth flows.
- `log-error`, `create-error` utility functions.
- `with-db-error-handling`, `with-email-error-handling`, `with-validation` macros for consistent patterns.

---

## Supporting Changes

### Common Utilities (`common.cljc`)
- `kw-base`: Extracts keyword base before first dash (e.g., `:artificer-kibbles` -> `"artificer"`).
- `traverse-nested`: Higher-order function for recursively walking nested option structures.

### Styles (`styles/core.clj`)
- `.bg-warning`, `.bg-warning-item` CSS classes for warning banner UI.

### App State (`db.cljs`)
- Added `import-log` and `conflict-resolution` state maps to re-frame db.

### Subscriptions (`subs.cljs`)
- Import log, conflict resolution, export warning, missing content report subscriptions.

### Entry Point (`core.cljs`)
- Dev version logging on startup.
- Import log overlay component mounted in main view wrapper.

### Linter Configuration
- `.clj-kondo/config.edn`: Exclusions for `with-db` macro and user namespace functions.
- `.lsp/config.edn` (new): Explicit source-paths to prevent clojure-lsp from scanning compiled CLJS output in `resources/public/js/compiled/out/`.

---

## Files Changed

| Status | File | Category |
|--------|------|----------|
| Modified | `src/clj/orcpub/datomic.clj` | Error handling |
| Modified | `src/clj/orcpub/email.clj` | Error handling |
| Modified | `src/clj/orcpub/pdf.clj` | Error handling |
| Modified | `src/clj/orcpub/routes.clj` | Error handling |
| Modified | `src/clj/orcpub/routes/party.clj` | Error handling |
| Modified | `src/clj/orcpub/styles/core.clj` | UI styles |
| Modified | `src/clj/orcpub/system.clj` | Error handling |
| **New** | `src/clj/orcpub/tools/orcbrew.clj` | CLI tool |
| Modified | `src/cljc/orcpub/common.cljc` | Utilities |
| Modified | `src/cljc/orcpub/dnd/e5/options.cljc` | Bug fix |
| Modified | `src/cljc/orcpub/errors.cljc` | Error infrastructure |
| Modified | `src/cljc/orcpub/pdf_spec.cljc` | Nil guards |
| Modified | `src/cljs/orcpub/character_builder.cljs` | Warning UI |
| **New** | `src/cljs/orcpub/dnd/e5/content_reconciliation.cljs` | Missing content detection |
| Modified | `src/cljs/orcpub/dnd/e5/db.cljs` | App state |
| Modified | `src/cljs/orcpub/dnd/e5/events.cljs` | Import/export events |
| **New** | `src/cljs/orcpub/dnd/e5/import_validation.cljs` | Validation framework |
| Modified | `src/cljs/orcpub/dnd/e5/spell_subs.cljs` | Plugin robustness |
| Modified | `src/cljs/orcpub/dnd/e5/subs.cljs` | Subscriptions |
| Modified | `src/cljs/orcpub/dnd/e5/views.cljs` | Conflict/export modals |
| Modified | `web/cljs/orcpub/core.cljs` | Entry point |
| **New** | `test/clj/orcpub/errors_test.clj` | Unit tests |
| **New** | `test/cljs/orcpub/dnd/e5/import_validation_test.cljs` | Unit tests |
| **New** | `test/duplicate-external-a.orcbrew` | Test fixture |
| **New** | `test/duplicate-external-b.orcbrew` | Test fixture |
| Modified | `.clj-kondo/config.edn` | Linter config |
| **New** | `.lsp/config.edn` | LSP config |
| **New** | `docs/CONFLICT_RESOLUTION.md` | Feature documentation |
| **New** | `docs/CONTENT_RECONCILIATION.md` | Feature documentation |
| **New** | `docs/ERROR_HANDLING.md` | Feature documentation |
| **New** | `docs/HOMEBREW_REQUIRED_FIELDS.md` | Feature documentation |
| **New** | `docs/ORCBREW_FILE_VALIDATION.md` | Feature documentation |
| **New** | `docs/README.md` | Documentation index |

---

## Documentation

Feature documentation is included in `docs/`:

| Document | Covers |
|----------|--------|
| [ERROR_HANDLING.md](docs/ERROR_HANDLING.md) | Backend error macros, error codes, usage patterns |
| [CONFLICT_RESOLUTION.md](docs/CONFLICT_RESOLUTION.md) | Duplicate key detection, resolution modal, reference updates |
| [CONTENT_RECONCILIATION.md](docs/CONTENT_RECONCILIATION.md) | Missing content detection, fuzzy matching strategies |
| [HOMEBREW_REQUIRED_FIELDS.md](docs/HOMEBREW_REQUIRED_FIELDS.md) | Required fields per content type, breaking code locations |
| [ORCBREW_FILE_VALIDATION.md](docs/ORCBREW_FILE_VALIDATION.md) | Import/export validation user and developer guide |

## Design Principles

- **Import = permissive** (auto-fix and continue), **Export = strict** (warn user, let them decide)
- **Placeholder text convention**: `[Missing Name]` format (square brackets indicate auto-filled)
- **Modal pattern**: db state -> re-frame subscription -> event handlers -> component in `import-log-overlay`
