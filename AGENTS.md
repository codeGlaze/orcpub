# Agent Guidelines

> **This is a living document.** Update it as conventions evolve or new patterns emerge. When you establish a new pattern or discover a better approach, document it here.

---

## First Steps

**Before doing anything else, read [`docs/CODEBASE.md`](./docs/CODEBASE.md).**

That document contains:
- Tech stack and architecture overview
- Directory structure and key files
- Core concepts (entity/template/modifier system)
- Patterns and conventions
- Known quirks and gotchas
- Accumulated learnings from previous work

**Update `docs/CODEBASE.md`** when you:
- Discover something non-obvious about the codebase
- Find a quirk or gotcha that would trip up future agents
- Learn how a subsystem works that isn't documented
- Complete significant work that changes how things work

---

## Development Setup

### Quick Start (Local)

1. **Java 8** required (not newer)
2. **Leiningen** for build/REPL
3. **Datomic transactor** running:
   ```bash
   # Windows (use bundled version due to path length bug)
   bin\transactor config/samples/free-transactor-template.properties

   # Mac/Linux
   bin/transactor config/samples/free-transactor-template.properties
   ```
4. **Backend REPL**: `lein with-profile +start-server repl`
   ```clojure
   (init-database)  ; only once per fresh DB
   (start-server)
   ```
5. **Frontend**: `lein figwheel` (hot reloads)

### Using Dev Container

The `.devcontainer/` setup handles all dependencies automatically.

### Using start.sh

The `start.sh` script automates the above steps with dependency checking.

---

## Code Conventions

### Error Handling

Use the DRY macros from `src/cljc/orcpub/errors.cljc`:

```clojure
(require '[orcpub.errors :as errors])

;; Database operations
(errors/with-db-error-handling :operation-failed {:user-id id} "Failed to save"
  (d/transact conn tx-data))

;; Email operations
(errors/with-email-error-handling :email-failed {:to email} "Failed to send"
  (postal/send-message msg))

;; Parsing/validation
(errors/with-validation :invalid-input {:field "id"} "Invalid ID format"
  (Long/parseLong id-string))
```

See `docs/ERROR_HANDLING.md` for full documentation.

### File Organization

- **Backend only**: `src/clj/orcpub/`
- **Frontend only**: `src/cljs/orcpub/`
- **Shared code**: `src/cljc/orcpub/` (runs on both JVM and browser)

### Testing

- Mirror source structure in `test/clj/` and `test/cljs/`
- Run backend tests: `lein test`
- Run frontend tests: `lein doo`

---

## Working on This Repo

### Before Making Changes

1. Read `docs/CODEBASE.md` for context
2. Understand the entity/template/modifier system if touching character logic
3. Check existing patterns in similar files

### After Making Changes

1. Run relevant tests
2. Update `docs/CODEBASE.md` if you learned something new
3. Update this file if you established new conventions

### Commit Style

Follow conventional commits:
- `feat:` new features
- `fix:` bug fixes
- `docs:` documentation changes
- `refactor:` code restructuring
- `test:` adding/updating tests

---

## Key Documentation

| Document | Purpose |
|----------|---------|
| [`docs/CODEBASE.md`](./docs/CODEBASE.md) | **Start here.** Architecture, patterns, learnings |
| [`docs/ERROR_HANDLING.md`](./docs/ERROR_HANDLING.md) | Error handling utilities and patterns |
| [`docs/ORCBREW_FILE_VALIDATION.md`](./docs/ORCBREW_FILE_VALIDATION.md) | File import/export validation |
| [`README.md`](./README.md) | Setup, deployment, contributing |

---

## Notes for Future Agents

- The modifier system uses `?symbol` syntax - this is intentional DSL, not a typo
- Datomic Free on Windows requires the bundled version (path length bug)
- Frontend changes hot-reload; backend changes need REPL reload
- `clojure.edn/read-string` is safe; `clojure.core/read-string` is not - we use the safe one
