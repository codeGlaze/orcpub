# Error Handling & Import Validation Feature

## Context

Branch: `feature/error-handling-import-validation` (off `develop`)
Scope: OrcBrew import/export validation, error handling, conflict resolution UI, views decomposition, and a full code review pass.

## What Was Built

### Core Features
- **`orcpub.errors`** (`src/cljc/orcpub/errors.cljc`) — centralized error macros (`with-db-error-handling`, `with-validation`, `safe-parse`)
- **`import_validation.cljs`** — pre-import validation with progressive recovery (valid items import, invalid skip with log)
- **`content_reconciliation.cljs`** — detects key conflicts, missing fields, data corruption in orcbrew files
- **Conflict resolution modal** — extracted to `views/conflict_resolution.cljs`
- **Import log panel** — extracted to `views/import_log.cljs` (slide-out with grouped sections)
- **Export pre-validation** — warns before exporting files with known issues

### Architecture: handle-api-response HOF

Replaced 7 bare `case` blocks on HTTP status with a single higher-order function:

```clojure
;; events.cljs
(defn handle-api-response
  "HOF for consistent API response handling.
   Accepts a map of {status handler-fn} with sensible defaults."
  [handlers context-string]
  (fn [response]
    (case (:status response)
      200 ((get handlers 200 identity) response)
      401 (dispatch [:route-to-login])
      500 (dispatch [::show-generic-error])
      (js/console.warn context-string "Unexpected status:" (:status response)))))
```

**Why**: Every API call had copy-pasted `case` blocks with inconsistent error handling. Some missed 401, some crashed on 502. The HOF provides defaults (401->login, 500->error, catch-all->console.warn) while letting callers override specific statuses.

**Call sites**: All 7 API event handlers in `subs.cljs` + `equipment_subs.cljs`.

### Architecture: Views Decomposition

`views.cljs` was 8758 lines. Extracted two self-contained subviews:

| File | Purpose | Size |
|------|---------|------|
| `views/import_log.cljs` | Slide-out panel, FAB button, grouped collapsible sections | ~200 lines |
| `views/conflict_resolution.cljs` | Conflict modal, export warning, overlay composite | ~250 lines |

**Pattern**: Subviews only need `re-frame.core` + `reagent.core` — no imports from the parent `views.cljs`. They communicate purely through re-frame subscriptions and dispatch. The composite overlay mounts in `core.cljs`, not `views.cljs`.

**Why not more extraction?** Most of `views.cljs` has deep internal coupling (shared atoms, local state, helper fns). These two were natural boundaries because they only need re-frame state.

### Architecture: Garden CSS Migration

Moved inline styles to Garden CSS classes for `conflict_resolution.cljs`:
- `.conflict-*` classes (modal, header, body, radio options)
- `.export-issue-*` classes (warning panel, severity indicators)
- Defined in `styles/core.clj`

**Gotcha**: Garden CSS compiles via Leiningen `:prep-tasks` on `lein run` — NOT hot-reloaded by Figwheel. Must restart backend to see CSS changes. In devcontainer, Figwheel's inotify watcher is unreliable; `touch` the file to force recompile.

## Review Findings Summary

31 code findings + 16 style findings across 6 batches. Common patterns:

### Redundant `(str "literal")` — 7 instances
Clojure's `str` on a single string literal is a no-op. Found in email templates, CSS strings, filenames. Pure cleanup.

### Bare destructuring outside `let` — 1 instance (bug)
```clojure
;; BROKEN: destructuring syntax is valid but produces nil
(defn character-summary-for-id [db id]
  {:keys [::se/summary]} (d/pull db '[...] id))

;; FIXED: proper let binding
(defn character-summary-for-id [db id]
  ;; Fixed: bare destructuring outside let silently returned nil
  (let [{:keys [::se/summary]} (d/pull db '[...] id)]
    summary))
```
**Lesson**: Clojure's destructuring syntax in a `defn` body without `let` parses as two expressions — a map literal and a function call — with the map discarded. No compiler warning.

### Test assertion argument order — 1 instance (bug)
```clojure
;; BROKEN: calls 12 as a function with first-expanded as default
(is (= (:base-ac 12 first-expanded)))

;; FIXED: expected-actual order
(is (= 12 (:base-ac first-expanded)))
```
**Lesson**: `(:keyword default-value map)` is valid Clojure — keywords are functions that accept a map and optional default. Wrong argument order silently succeeds when the default matches.

### Silently non-testing test — 1 instance (upstream bug)
```clojure
;; BROKEN: = without `is` wrapper never asserts
(deftest strict-round-trip-2
  (let [strict {...}]
    (= strict round-trip)))  ;; always returns bool, never fails test

;; Disabled with explanation
;; TODO: Pre-existing upstream bug — round-trip loses :orcpub.entity.strict/owner
#_(is (= strict round-trip))
```

### Deferred Items
- W7, I6: Behavioral changes (server-side validation) — separate PRs
- W8-W10, I12: Changes to `develop`-branch code, not feature-branch code — separate PRs

## Test Coverage

- 124 CLJ/CLJC tests, 708 assertions (all pass)
- 42+ CLJS tests, 147+ assertions (runner on `ci/fix-workflow`, not yet in CI on develop)
- Key test files: `import_validation_test.cljs`, `content_reconciliation_test.cljs`, `pdf_spec_test.clj`
- All tests are pure functions — no re-frame test infrastructure needed

## Kondo Config

Consolidated `.clj-kondo/config.edn` from duplicate blocks into single `:exclude` list with comments explaining each suppression (LSP cross-file refs, re-frame keyword dispatch, macro conditional resolution).

**Reminder**: `.clj-kondo/` is gitignored — must use `git add -f` to stage changes.
