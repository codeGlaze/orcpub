# Stack Overview

A guide to the libraries, frameworks, and architecture for developers getting oriented with the codebase.

## Architecture at a Glance

```
Browser (ClojureScript)          Server (Clojure/JVM)
┌─────────────────────┐          ┌─────────────────────┐
│  Reagent + React 18 │  HTTP    │  Pedestal 0.7       │
│  re-frame           │ ◄──────► │  Buddy (auth)       │
│  Garden (CSS)       │  Transit │  PDFBox 3 (PDF gen) │
│  Figwheel (dev)     │          │  Datomic Pro (db)   │
└─────────────────────┘          └─────────────────────┘
```

This is a **Clojure/ClojureScript monorepo**. Code lives in three source trees:

| Path | Language | Runs on |
|------|----------|---------|
| `src/clj/` | Clojure | JVM only (server, routes, PDF) |
| `src/cljs/` | ClojureScript | Browser only (UI, re-frame events/subs) |
| `src/cljc/` | Clojure(Script) | Both — shared logic (character model, templates, specs) |

## Frontend

### React 18 + Reagent 2.0

[Reagent](https://reagent-project.github.io/) wraps React with ClojureScript idioms. Components are plain functions returning hiccup vectors:

```clojure
(defn greeting [name]
  [:div.hello "Welcome, " name])
```

Reagent 2.0 uses React 18's `createRoot` API. The app mounts via `reagent.dom.client/render`.

**Key gotcha**: Use `:class` (not `:class-name`) when the hiccup tag has inline classes like `[:div.foo ...]`. Reagent 2.x changed `:class-name` to overwrite rather than merge.

### re-frame (State Management)

[re-frame](https://day8.github.io/re-frame/) is a data-flow framework built on Reagent. It follows a unidirectional cycle:

```
Event → Handler → Effects → App-DB → Subscriptions → Components → Event
```

- **Events** (`reg-event-fx`, `reg-event-db`): Handle user actions and side effects — `src/cljs/.../events.cljs`
- **Subscriptions** (`reg-sub`): Derived views of app-db — `src/cljs/.../subs.cljs`
- **Effects** (`reg-fx`): Side effects like HTTP requests — `:http` fx in events.cljs
- **App-DB**: Single atom holding all application state

### Garden (CSS-in-Clojure)

[Garden](https://github.com/lambdaisland/garden) compiles Clojure data structures to CSS. Styles live in `src/clj/orcpub/styles/core.clj` and compile to `resources/public/css/compiled/styles.css`.

```clojure
[:.builder-option-dropdown
 {:background-color :transparent
  :cursor :pointer}]
```

Compile: `lein garden once`

We use the **lambdaisland/garden** fork (drop-in replacement) because the original noprompt/garden is unmaintained and causes `clojure.core/abs` shadowing warnings on Clojure 1.11+.

### Figwheel (Hot Reload)

[figwheel-main](https://figwheel.org/) provides hot code reloading during development:

| Command | Mode | Use |
|---------|------|-----|
| `lein fig:dev` | REPL + watch | Interactive development |
| `lein fig:watch` | Watch (headless) | Background/scripted |
| `lein fig:build` | Build once | CI / compilation check |

Config: `dev.cljs.edn` (preloads, compiler options).

## Backend

### Pedestal 0.7 (Web Framework)

[Pedestal](http://pedestal.io/) is an interceptor-based web framework. Routes map URL patterns to interceptor chains:

```clojure
["/api/folders" ^:interceptors [check-auth]
 {:post `create-folder
  :get  `list-folders}]
```

Interceptors are composable middleware units (auth, parsing, ownership checks). Always wrap with `interceptor/interceptor` for proper Pedestal records.

**Pinned at 0.7.0**: Pedestal 0.7.1+ uses Jetty 12, which breaks figwheel-main's Ring adapter. See `project.clj` comments.

### Datomic Pro (Database)

[Datomic](https://docs.datomic.com/) is an immutable, time-aware database. Key concepts:

- **Entities**: Maps with `:db/id` — similar to documents
- **Transactions**: Immutable facts added over time — never UPDATE, always assert new facts
- **Pull**: Declarative data retrieval (like GraphQL)
- **Queries**: Datalog (logic programming)

```clojure
;; Pull an entity
(d/pull db [:db/id ::folder/name] folder-id)

;; Query
(d/q '[:find ?e :where [?e ::folder/owner "alice"]] db)
```

Connection uses `datomic:dev://` protocol (required for Java 21). Schema lives in `src/clj/orcpub/db/schema.clj`.

### Buddy (Authentication)

[Buddy](https://funcool.github.io/buddy-auth/latest/) handles JWT token signing and bcrypt password hashing:

- `buddy-auth` — Token-based authentication middleware
- `buddy-hashers` — Password hashing (bcrypt)

Tokens are signed with the `SIGNATURE` env var. See `src/clj/orcpub/routes.clj` for the `check-auth` interceptor.

### PDFBox 3 (PDF Generation)

[Apache PDFBox](https://pdfbox.apache.org/) fills PDF character sheet templates. The app loads a blank D&D character sheet PDF, fills in field values from the built character, and returns the result.

```clojure
(with-open [doc (Loader/loadPDF input)]
  ;; fill fields...
  (.save doc output))
```

**PDFBox 3.x API change**: Use `Loader/loadPDF` instead of `PDDocument/load`.

## Shared Code (.cljc)

The character model, D&D rules, templates, and entity system are all `.cljc` — they run on both JVM (for testing and PDF generation) and browser (for the UI). Key namespaces:

| Namespace | Purpose |
|-----------|---------|
| `orcpub.entity` | Entity/template system (build, from-strict, to-strict) |
| `orcpub.dnd.e5.character` | Character accessors (abilities, HP, AC, etc.) |
| `orcpub.dnd.e5.options` | D&D class/race/feat option builders |
| `orcpub.dnd.e5.template` | Full character template (all classes, races, backgrounds) |
| `orcpub.pdf-spec` | PDF field mapping (pure — no re-frame dependency) |
| `orcpub.dnd.e5.magic-items` | Magic item/weapon computation (homebrew-aware) |

## Build System

### Leiningen

The project uses [Leiningen](https://leiningen.org/) for builds, dependency management, and task running.

Key aliases:

| Command | What it does |
|---------|-------------|
| `lein test` | Run JVM tests (206 tests) |
| `lein fig:build` | One-shot CLJS compilation |
| `lein fig:dev` | CLJS dev with REPL + hot reload |
| `lein fig:test` | Compile + run CLJS tests in browser |
| `lein garden once` | Compile CSS |
| `lein lint` | Run clj-kondo linter |
| `lein uberjar` | Production build (AOT + advanced CLJS + CSS) |

**Known issue**: `lein uberjar` hangs after completing the build due to
Clojure's non-daemon agent threads. Docker builds use `timeout` to work
around this. See [LEIN-UBERJAR-HANG.md](LEIN-UBERJAR-HANG.md) for details.

### Profiles

| Profile | Purpose |
|---------|---------|
| `:dev` | Development (devtools, piggieback, re-frame-10x) |
| `:uberjar` | Production build (AOT, advanced optimizations) |
| `:lint` | clj-kondo linting |
| `:init-db` | Minimal profile for DB init scripts (no CLJS/Garden) |

### Local JARs

Some dependencies (Datomic Pro, PDFBox) are loaded from `lib/` via a `file:lib` local Maven repository. This avoids requiring Datomic credentials or dealing with non-standard Maven repos.

## Data Serialization

Client-server communication uses [Transit](https://github.com/cognitect/transit-format) (JSON-based, preserves Clojure types like keywords and sets). The `cljs-http` library handles Transit encoding/decoding automatically.

Homebrew content (`.orcbrew` files) uses Transit for import/export. Users regularly import 2-5MB plugin files.

## Docker

Three services in `docker-compose.yaml`:

| Service | Image | Purpose |
|---------|-------|---------|
| `orcpub` | Application | Clojure app server (port 8890) |
| `datomic` | Database | Datomic Pro transactor |
| `web` | nginx:alpine | Reverse proxy (ports 80/443) |

Config via `.env` file — see `.env.example` for all variables.

## Dependency Pinning

Several dependencies are pinned to specific versions for compatibility:

| Dependency | Pin | Reason |
|-----------|-----|--------|
| Pedestal | 0.7.0 | 0.7.1+ uses Jetty 12, breaks figwheel |
| Jackson | 2.15.2 | Resolves transitive conflicts and CVEs |
| Guava | 32.1.2-jre | Same |
| commons-io | 2.15.1 | Required by Pedestal 0.7 ring-middlewares |
| javax.servlet-api | 4.0.1 | Required on Java 9+ |

See [migration/library-upgrades.md](migration/library-upgrades.md) for the full upgrade history.
