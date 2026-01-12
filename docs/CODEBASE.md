# OrcPub Codebase Knowledge

> **This is a living document.** It is meant to be updated by agents (and humans) as understanding of the codebase evolves. When you learn something new or discover a quirk, add it here. See also: [AGENTS.md](../AGENTS.md) for contribution guidelines.

---

## Quick Overview

**OrcPub / Dungeon Master's Vault** is a D&D 5e character sheet generator. It's a full-stack Clojure/ClojureScript application forked from the original OrcPub2.

### Tech Stack

| Layer | Technology |
|-------|------------|
| **Backend** | Clojure (JVM) |
| **Frontend** | ClojureScript (browser) |
| **HTTP Server** | Pedestal + Ring |
| **Database** | Datomic (entity-attribute-value store) |
| **Auth** | Buddy (JWT, hashing) |
| **Email** | Postal (SMTP) |
| **PDF** | Apache PDFBox |
| **UI Framework** | Reagent (React wrapper) |
| **State Management** | re-frame (Redux-like) |
| **CSS** | Garden (CSS-in-Clojure) |
| **Build** | Leiningen |
| **Live Reload** | Figwheel |

### Directory Structure

```
orcpub/
├── src/
│   ├── clj/                    # Backend (JVM) code
│   │   └── orcpub/
│   │       ├── datomic.clj     # Database component & queries
│   │       ├── email.clj       # Email operations (verification, reset)
│   │       ├── pdf.clj         # PDF character sheet generation
│   │       ├── routes.clj      # HTTP route handlers
│   │       ├── routes/         # Route handler modules
│   │       │   └── party.clj   # Party management
│   │       └── system.clj      # Component system setup
│   │
│   ├── cljs/                   # Frontend (browser) code
│   │   └── orcpub/
│   │       └── dnd/e5/
│   │           ├── events.cljs          # re-frame event handlers
│   │           ├── views.cljs           # Reagent UI components
│   │           └── import_validation.cljs # Orcbrew file validation
│   │
│   └── cljc/                   # Shared code (JVM + browser)
│       └── orcpub/
│           ├── common.cljc     # Shared utilities
│           ├── errors.cljc     # Error handling utilities
│           └── entity.cljc     # Core entity/modifier system
│
├── test/
│   ├── clj/                    # Backend tests
│   └── cljs/                   # Frontend tests
│
├── docs/                       # Documentation
├── resources/                  # Static resources, templates
├── deploy/                     # Deployment configs (nginx, SSL)
├── docker/                     # Docker configs
└── project.clj                 # Leiningen project config
```

---

## Core Concepts

### The Entity/Template/Modifier System

This is the heart of OrcPub. Understanding this is critical.

**The Problem**: D&D characters have hundreds of interacting options (race, class, feats, items) that modify stats in complex ways. A naive approach of hardcoding every interaction doesn't scale.

**The Solution**: A declarative system where:

1. **Entity** = A record of hierarchical choices made (e.g., "I chose Elf > High Elf")
2. **Template** = Defines available options and their modifiers
3. **Modifier** = A transformation applied to character attributes
4. **Built Entity** = The final computed character after applying all modifiers

```clojure
;; Entity: just stores choices
{:options {:race {:key :elf
                  :options {:subrace {:key :high-elf}}}}}

;; Template: defines what options exist and their effects
{:selections [{:key :race
               :options [{:name "Elf"
                          :key :elf
                          :modifiers [(modifier ?dex-bonus (+ ?dex-bonus 2))]
                          :selections [{:key :subrace ...}]}]}]}

;; Built entity: computed result
{:race "Elf", :subrace "High Elf", :dex-bonus 2, :int-bonus 1}
```

**Key insight**: Modifiers use `?attribute` syntax to reference and update values. The system builds a dependency graph and applies modifiers in topologically sorted order.

### Data Flow

```
[Browser UI] <--re-frame events--> [ClojureScript State]
                                          |
                                    HTTP/Transit
                                          |
                                   [Pedestal Routes]
                                          |
                                   [Datomic Database]
```

- **Frontend state**: Managed by re-frame (subscriptions + events)
- **API format**: Transit (Clojure's efficient serialization)
- **Database**: Datomic stores entities as attribute-value facts

---

## Key Files

| File | Responsibility |
|------|----------------|
| `src/clj/orcpub/routes.clj` | Main HTTP route handlers (character CRUD, auth) |
| `src/clj/orcpub/datomic.clj` | Database connection, schema, queries |
| `src/clj/orcpub/pdf.clj` | PDF character sheet generation |
| `src/clj/orcpub/email.clj` | Email sending (verification, password reset) |
| `src/clj/orcpub/system.clj` | Component system initialization |
| `src/cljc/orcpub/entity.cljc` | Core entity building logic |
| `src/cljc/orcpub/errors.cljc` | Error handling utilities (macros) |
| `src/cljs/orcpub/dnd/e5/events.cljs` | re-frame event handlers |
| `src/cljs/orcpub/dnd/e5/views.cljs` | Main UI components |
| `project.clj` | Dependencies, build profiles, aliases |

---

## Patterns & Conventions

### Error Handling

The codebase uses DRY error handling macros defined in `src/cljc/orcpub/errors.cljc`:

```clojure
;; For database operations
(with-db-error-handling :error-code {:context "data"} "User message"
  (db-operation))

;; For email operations
(with-email-error-handling :error-code {:context "data"} "User message"
  (send-email))

;; For parsing/validation
(with-validation :error-code {:context "data"} "User message"
  (parse-input))
```

All errors follow a consistent structure:
```clojure
{:error :error-code-keyword
 :context-key "context-value"
 :message "underlying exception message"}
```

See `docs/ERROR_HANDLING.md` for full details.

### State Management (Frontend)

Uses re-frame pattern:
- **Events**: `(rf/dispatch [:event-name data])` - trigger state changes
- **Subscriptions**: `(rf/subscribe [:sub-name])` - reactive data access
- **Effects**: Side effects (HTTP, local storage) via effect handlers

### Testing

- Backend: `clojure.test` - run with `lein test`
- Frontend: `cljs.test` - run with `lein doo`
- Test files mirror source structure in `test/` directory

---

## Known Quirks & Gotchas

### Datomic on Windows
- Must use the bundled Datomic version in `lib/datomic-free-0.9.5703.tar.gz`
- Newer versions have Windows path length issues
- Known upstream bug that won't be fixed

### Java Version
- Requires Java 8 specifically
- Later versions may have compatibility issues with Datomic Free

### REPL Workflow
- Frontend changes hot-reload via Figwheel
- Backend changes require REPL reload or server restart
- `(init-database)` only needs to run once per fresh DB

### PDF Generation
- Image loading has a 10-second timeout
- External image URLs can fail silently if unreachable
- Uses PDFBox which has specific JPEG handling quirks

### Orcbrew Files
- EDN format for homebrew content
- Can become corrupted (nil values, missing fields)
- Import validation now handles gracefully (see `docs/ORCBREW_FILE_VALIDATION.md`)

---

## Learnings & Discoveries

> Add discoveries here as you work on the codebase. Format: `- [Date] [Agent/Person]: Discovery`

- [2026-01-10] Claude: Comprehensive error handling added across all critical operations (database, email, PDF, routes). Uses DRY macros to reduce boilerplate by ~30%.

- [2026-01-10] Claude: Orcbrew import validation now uses progressive strategy - imports valid items even if some are corrupted, rather than failing entirely.

- [2026-01-10] Claude: The `clojure.edn/read-string` is used (safe) rather than `clojure.core/read-string` (unsafe, allows code execution).

- [2026-01-12] Claude: Orcbrew auto-cleaning is split into two phases: (1) string-level for syntax fixes only (trailing commas, `disabled? nil`), (2) data-level for semantic fixes after parsing. Nil handling is field-specific: preserve for semantic fields (`:spell-list-kw`), remove for numeric fields (`:str`, `:dex`), replace for others (`:option-pack`). String-level regex manipulation of top-level keys caused duplicate key errors.

- [2026-01-12] Claude: Import changelog panel added - tracks all cleaning operations during import with a slide-in UI panel. Auto-expands after import if changes were made. Components: `import-log-button` (fixed bottom-right), `import-log-panel` (slides from right). State stored in `:import-log` with subscriptions for reactivity. Change types: `:string-fix`, `:renamed-plugin-key`, `:fixed-option-pack`, `:removed-nil`, `:replaced-nil`, `:preserved-nil`.

---

## Related Documentation

- [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Error handling patterns and utilities
- [ORCBREW_FILE_VALIDATION.md](./ORCBREW_FILE_VALIDATION.md) - File import/export validation
- [AGENTS.md](../AGENTS.md) - Guidelines for AI agents working on this repo
- [README.md](../README.md) - Setup, deployment, and contributing guide
