# lein uberjar JVM Hang — Agent Knowledge

> Build-blocking issue: `lein uberjar` completes compilation but the JVM
> never exits. Causes Docker builds to hang indefinitely.

## What Happens

1. `lein uberjar` runs AOT compilation (`:aot :all`)
2. ClojureScript compiles via `lein-cljsbuild` (`:advanced` optimizations)
3. The uberjar JAR file is created successfully
4. The JVM does NOT exit — non-daemon agent threads keep it alive
5. In Docker: the `RUN` step hangs, CI eventually times out

## Root Cause

Clojure's agent thread pool uses non-daemon threads. Per official docs:
> "Use of Agents starts a pool of non-daemon background threads that will
> prevent shutdown of the JVM."

Libraries that call `send`, `send-off`, or `pmap` during namespace loading
start these threads. During AOT, all namespaces are loaded. The threads
persist after compilation finishes.

## Key Facts for Agents

- `shutdown-agents` is a **runtime** call — cannot be added to project.clj
- `lein-cljsbuild` is abandoned (last release: 1.1.8, April 2020)
- Leiningen does NOT call `shutdown-agents` after task completion
- This is NOT a Docker, BuildKit, or CI-specific problem — it's the JVM
- The issue also compounds with BuildKit on GH runners (export hang)

## Current Workaround

In `docker/Dockerfile`:
```dockerfile
RUN timeout 300 lein uberjar; \
    EXIT=$?; \
    if [ -f target/orcpub.jar ]; then exit 0; fi; \
    exit $EXIT
```

- `timeout` kills the JVM after build completes
- Jar existence check distinguishes timeout-after-success from real failure
- 300s (5 min) is generous — normal build is ~80s

## DO NOT

- Remove the `timeout` wrapper — the hang will return
- Replace `timeout` with `(shutdown-agents)` in app code — that only helps
  at runtime, not during AOT compilation
- Assume BuildKit is the cause — the underlying issue is the JVM, not Docker
- Use `docker compose build` in CI — it delegates to BuildKit on GH runners

## Related Files

- `docker/Dockerfile` — timeout workaround lives here
- `.github/workflows/docker-integration.yml` — `DOCKER_BUILDKIT=0` + direct build
- `project.clj` — `:uberjar` profile with `:prep-tasks` and cljsbuild hooks
- `docs/LEIN-UBERJAR-HANG.md` — human-facing documentation
