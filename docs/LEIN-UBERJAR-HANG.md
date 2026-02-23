# lein uberjar Hangs During Docker Build

## The Problem

`lein uberjar` hangs indefinitely in Docker and CI (no-TTY environments)
when `lein-cljsbuild` is loaded as a plugin. The ClojureScript compilation
succeeds, but lein never reaches jar packaging. Inside Docker, the `RUN`
step hangs until CI times out.

## Root Causes

### 1. cljsbuild I/O subprocess pump (primary)

`lein-cljsbuild` spawns a subprocess for ClojureScript compilation and pumps
its I/O streams. In no-TTY environments (Docker, CI), the pump hangs after
compilation finishes — the task never returns control to lein. The jar is
**never created** because lein never gets past the compile hooks.

This is [lein-cljsbuild issue #171](https://github.com/emezeske/lein-cljsbuild/issues/171).
The plugin is abandoned (last release: 1.1.8, April 2020).

### 2. cljsbuild hooks always fire

`lein-cljsbuild` registers hooks on `compile`, `jar`, and `clean` tasks at
plugin load time (via `robert.hooke`). The `compile-hook` **always** calls
`run-compiler`, which spawns a subprocess via `eval-in-project` — even with
nil/empty `:cljsbuild` config. No config manipulation can prevent the hang;
the only fix is to not load the plugin at all.

### 3. Non-daemon agent threads (secondary)

Clojure's agent thread pool uses **non-daemon threads**. During AOT
compilation (`:aot :all`), every namespace is loaded. Libraries that call
`send`, `send-off`, or `pmap` at load time start these threads. Even if lein
finishes packaging the jar, the JVM won't exit.

## The Fix: Replace lein-cljsbuild with figwheel-main

`lein-cljsbuild` has been **completely removed** from the project. Production
CLJS builds now use `figwheel-main` (already a dependency for dev builds):

```sh
# Local
lein fig:prod

# Docker (see Dockerfile)
lein run -m figwheel.main -- --build-once prod
```

The production build config lives in `prod.cljs.edn` (same format as
`dev.cljs.edn`). figwheel-main's `--build-once` mode invokes the
ClojureScript compiler directly, writes the .js file, and exits cleanly.
No hooks, no subprocess pump, no hang.

### Docker build structure

```dockerfile
# Step 1: CLJS via figwheel-main (no cljsbuild, no hang)
RUN lein run -m figwheel.main -- --build-once prod && \
    test -f resources/public/js/compiled/orcpub.js

# Step 2: garden CSS + AOT + jar
# timeout kills non-daemon agent threads that prevent JVM exit after AOT.
# The jar IS created before the hang — timeout just lets the container move on.
RUN timeout 300 lein with-profile uberjar,uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1
```

- **Step 1** compiles CLJS with `:advanced` optimizations. No timeout needed
  because figwheel-main exits cleanly after compilation.
- **Step 2** uses `uberjar-package` to skip the `clean` prep-task (which
  would delete the JS from step 1). `timeout` handles the agent thread issue.

### Why not keep cljsbuild in a profile?

We tried 7 different approaches to isolate cljsbuild via Leiningen profiles.
All failed because:

1. cljsbuild hooks fire on `compile`/`jar` tasks regardless of config
2. The `uberjar` task internally re-merges profiles, stripping custom profiles
3. `^:replace` on `:plugins` doesn't prevent a plugin already loaded
4. Even moving the plugin to a profile didn't reliably prevent loading

See the agent KB doc (`docs/kb/lein-uberjar-hang.md`) for the full record
of all 7 failed attempts.

### Local uberjar builds

```sh
# Compile CLJS (figwheel-main exits cleanly in a TTY)
lein fig:prod

# Build the uberjar (garden CSS + AOT + jar)
lein uberjar
```

## Affected Components

| File | What | Why |
|------|------|-----|
| `prod.cljs.edn` | Production CLJS build config | Replaces cljsbuild :prod build |
| `docker/Dockerfile` | Two-step build | Step 1: figwheel-main, Step 2: uberjar |
| `project.clj` | No `lein-cljsbuild` in any `:plugins` | Eliminates hooks entirely |
| `project.clj` | `fig:prod` alias | Local production CLJS builds |
| `project.clj` | `:uberjar-package` profile | Skips clean, preserves step 1 JS |
| `.github/workflows/docker-integration.yml` | `DOCKER_BUILDKIT=0` | Avoids BuildKit export hang |
