# lein uberjar Hangs During Docker Build

## The Problem

`lein uberjar` hangs indefinitely in Docker and CI (no-TTY environments).
AOT compilation finishes (all namespaces compiled, .class files written),
but the JVM subprocess never exits. lein waits for the subprocess → the jar
is never created → Docker step hangs until timeout.

## Root Causes

Two compounding issues cause the hang:

### 1. AOT compile subprocess hang (primary)

Leiningen's `compile` task spawns a subprocess via `eval-in-project` to AOT
compile namespaces. After compilation finishes, the subprocess JVM should
exit, but **non-daemon threads** started during namespace loading prevent it:

- Datomic Peer starts background threads for caching/heartbeats
- `core.async` creates thread pools
- Other libraries may use agents, futures, or `pmap` during loading

Lein 2.12.0's compile task sets a 100ms keep-alive on the agent thread pool,
but this only handles Clojure agent threads — not threads from libraries that
create their own pools.

`eval-in-project` waits for the subprocess to exit. It never does. lein
hangs. The jar is never created because lein never gets past compile.

### 2. lein-cljsbuild hooks (eliminated)

`lein-cljsbuild` (abandoned, last release: 1.1.8, April 2020) registered
hooks on `compile`/`jar` that spawned an additional subprocess with the same
I/O pump hang. **This was eliminated by replacing cljsbuild with
figwheel-main** for production CLJS builds (see below). But the AOT
subprocess hang persists because it's in lein's own compile task.

## The Fix: Three-Step Docker Build

### Step 1: CLJS via figwheel-main

```dockerfile
RUN lein run -m figwheel.main -- --build-once prod && \
    test -f resources/public/js/compiled/orcpub.js
```

figwheel-main replaces lein-cljsbuild. `--build-once` compiles CLJS with
`:advanced` optimizations, writes the .js file, and exits cleanly. Config
lives in `prod.cljs.edn`.

### Step 2: AOT compile (with timeout)

```dockerfile
RUN timeout 300 lein with-profile uberjar,uberjar-package compile || true; \
    test -f target/classes/orcpub/server__init.class || exit 1
```

AOT compiles all namespaces (`:aot :all`). The compilation **finishes** —
all .class files are written to `target/classes/`. But the subprocess hangs
due to non-daemon threads. `timeout` kills it. `|| true` allows the step
to continue despite the non-zero exit code. `test -f` verifies the main
class was actually compiled.

### Step 3: jar packaging (compile is no-op)

```dockerfile
RUN timeout 300 lein with-profile uberjar,uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1
```

The key insight: lein's `stale-namespaces` function is **timestamp-based**.
It only compiles namespaces where the source file is newer than the .class
file. Since step 2 just wrote all .class files, they're all newer than the
source → `stale-namespaces` returns empty → **no subprocess is spawned** →
compile returns instantly → lein proceeds to jar creation.

`uberjar-package` provides `^:replace` prep-tasks that skip `"clean"`,
preserving the JS from step 1 and .class files from step 2.

### Why `uberjar-package` survives the re-merge

The `uberjar` task internally re-merges profiles. It includes profiles that
are in `:included-profiles` metadata and not in the default profile set.
Since `:uberjar-package` is explicitly specified via `with-profile`, it's in
`:included-profiles` and is NOT a default profile, so it survives the
re-merge. Its `^:replace` on `:prep-tasks` takes precedence over `:uberjar`'s.

### Local builds

```sh
# CLJS (figwheel-main exits cleanly in a TTY)
lein fig:prod

# Uberjar (in a TTY, you can Ctrl-C the hang after "Created target/orcpub.jar")
lein uberjar
```

## Affected Components

| File | What | Why |
|------|------|-----|
| `prod.cljs.edn` | Production CLJS build config | Replaces cljsbuild :prod build |
| `docker/Dockerfile` | Three-step build | Separates compile from jar creation |
| `project.clj` | No `lein-cljsbuild` in any `:plugins` | Eliminates cljsbuild hooks |
| `project.clj` | `fig:prod` alias | Local production CLJS builds |
| `project.clj` | `:uberjar-package` profile | Skips clean, preserves artifacts |
