# Stack Upgrade Index

The stack runs on Java 21, Datomic Pro, Pedestal 0.7, React 18 and Reagent 2.0. These pages explain
each part, what to install, and what to check if you maintain a fork or an older install.

| Topic | Summary | Details |
|-------|---------|---------|
| Java runtime | Java 21 | [JAVA-COMPATIBILITY.md](JAVA-COMPATIBILITY.md) |
| Database | Datomic Pro | [migration/datomic-pro.md](migration/datomic-pro.md) |
| Database data | Moving a Datomic Free database to Pro | [migration/datomic-data-migration.md](migration/datomic-data-migration.md) |
| Web framework | Pedestal 0.7.0 | [migration/pedestal-0.7.md](migration/pedestal-0.7.md) |
| Frontend | React 18, Reagent 2.0 | [migration/frontend-stack.md](migration/frontend-stack.md) |
| Libraries | java-time, PDFBox 2, Buddy and others | [migration/library-upgrades.md](migration/library-upgrades.md) |
| Dev tooling | CLI and REPL helpers in `user.clj`, profiles, scripts | [migration/dev-tooling.md](migration/dev-tooling.md) |
| Environment | `.env` and every setting | [ENVIRONMENT.md](ENVIRONMENT.md) |
| Stack overview | Architecture, dependencies, build system | [STACK.md](STACK.md) |
| Docker build | Why `lein uberjar` runs in three steps | [LEIN-UBERJAR-HANG.md](LEIN-UBERJAR-HANG.md) |

## Why these versions go together

These upgrades depend on each other, so a partial upgrade leaves the app broken:

1. Java 21 is required by Datomic Pro and Pedestal 0.7.
2. Datomic Free does not run on Java 21 (SSL handshake failure), so Datomic Pro is required.
3. Pedestal 0.7 needs wrapped interceptors, and its default Content Security Policy uses
   `strict-dynamic`, which blocks inline scripts unless they carry a nonce.
4. Pedestal stays at 0.7.0 because 0.7.1 and later use Jetty 12, which breaks figwheel-main's Ring
   adapter.
5. Reagent 2.0 and React 18 use the `createRoot` API.
6. `clj-time` is replaced by `java-time`, because `clj-time` wraps Joda-Time, which is end of life
   on Java 21.

## Checks that must pass

CI runs these on Java 21: `lein test`, `lein lint`, and `lein cljsbuild once dev`. The production
ClojureScript build (`:advanced`) uses a custom `externs.js` for the React 18 APIs.
