# Migration Index

This branch (`breaking/2026-stack-modernization`) upgrades the full OrcPub stack from its original 2017-era dependencies to a modern 2026 baseline. Every change ships together because the upgrades are interdependent.

## What Changed

| Topic | Summary | Details |
|-------|---------|---------|
| Java runtime | 8 → 21 | [JAVA-COMPATIBILITY.md](JAVA-COMPATIBILITY.md) |
| Database | Datomic Free → Datomic Pro | [migration/datomic-pro.md](migration/datomic-pro.md) |
| Database data | Migrating existing Free databases | [migration/datomic-data-migration.md](migration/datomic-data-migration.md) |
| Web framework | Pedestal 0.5.1 → 0.7.0 | [migration/pedestal-0.7.md](migration/pedestal-0.7.md) |
| Frontend | React 15 / Reagent 0.6 → React 18 / Reagent 2.0 | [migration/frontend-stack.md](migration/frontend-stack.md) |
| Libraries | clj-time, PDFBox 2, Buddy 1, etc. | [migration/library-upgrades.md](migration/library-upgrades.md) |
| Dev tooling | Consolidated CLI + REPL into user.clj, profiles, scripts | [migration/dev-tooling.md](migration/dev-tooling.md) |
| Environment | `.env` pattern, new variables | [ENVIRONMENT.md](ENVIRONMENT.md) |
| Stack overview | Architecture, dependencies, build system | [STACK.md](STACK.md) |
| Docker build hang | `lein uberjar` JVM hang + BuildKit workarounds | [LEIN-UBERJAR-HANG.md](LEIN-UBERJAR-HANG.md) |

## Why One Branch

These upgrades form a dependency chain:

1. **Java 21** is required by modern Datomic Pro and Pedestal 0.7
2. **Datomic Free** doesn't work on Java 21 (SSL handshake failure), so **Datomic Pro** is required
3. **Pedestal 0.7** requires interceptor wrapping and adds default CSP with `strict-dynamic`, which breaks inline scripts unless nonces are added
4. **Pedestal 0.7.0** specifically — 0.7.1+ uses Jetty 12, which breaks figwheel-main's Ring adapter
5. **Reagent 2.0** / **React 18** use the `createRoot` API (React 17 deprecated `render`)
6. **clj-time → java-time** because clj-time wraps Joda-Time, which is EOL on Java 21

Upgrading any one of these in isolation would leave the application broken.

## Test Status

- Backend: 206 tests, 945 assertions, 0 failures, 0 errors
- Lint: 0 errors, 0 warnings
- Dev CLJS build: 0 errors, 0 warnings
- Production CLJS build (`:advanced`): succeeds with custom `externs.js` for React 18 APIs
- Garden CSS: 0 warnings (after lambdaisland/garden upgrade)
- CI: Java 21, `lein test` + `lein lint` + `lein cljsbuild once dev`
