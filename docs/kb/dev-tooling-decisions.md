# Dev Tooling Decisions — Agent Knowledge Base

## user.clj Consolidation Pattern

### Current State (as of breaking/2026-stack-modernization)

Three files with overlapping responsibilities:

| File | Purpose | CLI? | Overlap |
|------|---------|------|---------|
| `dev/user.clj` | REPL helpers (start/stop server, init-db, figwheel) | No | `init-database`, `verify-new-user`, `add-test-user` |
| `src/clj/orcpub/dev_init.clj` | DB init from CLI | Yes (`-main`, `:gen-class`) | `init-database` logic duplicated |
| `src/clj/orcpub/dev_tools.clj` | User CRUD from CLI | Yes (`-main`, `:gen-class`) | `create-user!`, `verify-user!` overlap with routes |

### Planned Consolidation

Merge `dev_init.clj` and `dev_tools.clj` into `user.clj` with a `-main` dispatch:

```clojure
;; dev/user.clj (planned)
(defn -main [& args]
  (case (first args)
    "init-db" (init-database)
    "add-test-user" (do (init-database) (add-test-user))
    "create-user" (create-user! ...)
    "verify-user" (verify-user! ...)
    (println "Usage: ...")))
```

**Why**: Single file for all dev utilities. No duplication. CLI and REPL use the same functions.

**Blocker**: Requires walk-through with repo owner (changes CLI invocations in start.sh/menu).

### Leiningen Profile: `:init-db`

The `:init-db` profile exists specifically for fast CLI invocations:
```clojure
:init-db {:source-paths ["src/clj" "src/cljc"]
           :prep-tasks ^:replace []}
```
- No ClojureScript compilation
- No Garden CSS
- Minimal dep loading for fast startup

After consolidation, this profile would invoke `user/-main` instead of `orcpub.dev-init/-main`.

## dev-setup.sh

### Current State
Orchestrates first-time setup: start Datomic, install deps, init DB. Overlaps with `start.sh` + `menu`.

### Decision
**Update to best-practice**, not drop. It should be the canonical "I just cloned this repo, make it work" script:
1. Start Datomic transactor
2. `lein deps`
3. Init DB + create test user
4. Print "ready to go" with next steps

The difference from `start.sh` is that `dev-setup.sh` is a one-shot setup, while `start.sh` manages individual services.

## config.clj as SSOT

### Decision
Keep `config.clj` as a single file. Do not split Datomic config from CSP config.

**Reasoning**: Owner prefers single source of truth for all runtime configuration. The file is small enough that splitting adds organizational overhead without benefit.

### What It Contains
- Datomic URI resolution (`get-datomic-uri`, `datomic-env`)
- CSP policy config (`get-csp-policy`, `strict-csp?`, `dev-mode?`, `get-secure-headers-config`)
- Permissive CSP settings map

## Figwheel Port

The project uses **port 3449** for Figwheel. This has been the port since the project's creation.

figwheel-main's default is 9500, but `project.clj` configures it to use 3449. The UPGRADE_PLAN.md incorrectly stated the port changed to 9500 — this was wrong.

## CI Java Version

The CI workflow (`.github/workflows/continuous-integration.yml`) was still set to Java 8 on the upgrade branch. This was a **bug**, not intentional. Fixed to Java 21.
