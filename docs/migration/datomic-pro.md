# Datomic Free → Datomic Pro

## Why

Datomic Free 0.9.5703 does not work on Java 21. The peer-to-transactor connection fails with an SSL handshake timeout in ActiveMQ Artemis (Datomic's internal messaging layer). This is not configurable — Datomic Free is unmaintained and will never be fixed.

Datomic Pro is free under the Apache 2.0 license and supports Java 11, 17, and 21.

## What Changed

### Connection URI

```
# Before (Datomic Free)
datomic:free://localhost:4334/orcpub

# After (Datomic Pro dev protocol)
datomic:dev://localhost:4334/orcpub
```

The `dev` protocol uses the Datomic Pro dev transactor (same data model, same API, different transport).

### Maven Dependency

```clojure
;; Before
[com.datomic/datomic-free "0.9.5703"]

;; After
[com.datomic/peer "1.0.7482" :exclusions [org.slf4j/slf4j-nop]]
```

The Datomic Pro peer library is `com.datomic/peer` (not `com.datomic/datomic-pro`). The `slf4j-nop` exclusion avoids duplicate SLF4J binding warnings.

### Installation

Datomic Pro is installed during container creation by `.devcontainer/post-create.sh`:

1. Downloads the Datomic Pro zip from S3
2. Extracts to `lib/com/datomic/datomic-pro/<version>/`
3. Runs `bin/maven-install` to populate the local Maven repository

The peer JAR resolves via the existing `file:lib` repository pattern in `project.clj`.

### Transactor

The transactor is a separate process started by `scripts/start.sh datomic`. It uses a properties file generated from the template at the Datomic install path. The transactor is **not** a JAR — it's the `bin/transactor` script with a config file.

### Configuration

All Datomic config flows through `src/clj/orcpub/config.clj`:

```clojure
(config/get-datomic-uri)
;; Returns DATOMIC_URL env var or default "datomic:dev://localhost:4334/orcpub"
```

Environment variables (see `.env.example`):
- `DATOMIC_URL` — connection URI
- `DATOMIC_VERSION` — version for installer (default: `1.0.7482`)
- `DATOMIC_TYPE` — `pro` (default)
- `DATOMIC_PASSWORD` — transactor password

### Code Changes

| File | Change |
|------|--------|
| `project.clj` | `datomic-free` → `com.datomic/peer`, version `1.0.7482` |
| `src/clj/orcpub/config.clj` | New — `get-datomic-uri`, `datomic-env` (SSOT for URI) |
| `src/clj/orcpub/system.clj` | Uses `config/get-datomic-uri` instead of inline logic |
| `src/clj/orcpub/datomic.clj` | Connection logic unchanged — `datomic.api` is the same |
| `dev/user.clj` | `init-database` uses `config/datomic-env` |
| `.devcontainer/post-create.sh` | Datomic Pro installer (download, extract, maven-install) |
| `.env.example` | Datomic Pro defaults |

### Datomock Compatibility

The test suite uses `datomock` for in-memory database testing. The original `vvvvalvalval/datomock 0.2.0` causes `AbstractMethodError` with Datomic Pro (new `transact` signature). Replaced with `org.clojars.favila/datomock 0.2.2-favila1`.
