# lein uberjar Hangs After Build

## The Problem

`lein uberjar` completes compilation (AOT + ClojureScript) but the JVM never
exits. In a terminal you'd wait a minute and hit Ctrl-C. Inside a Docker build,
the `RUN` step hangs indefinitely, eventually hitting the CI timeout.

This blocks the Docker Integration CI pipeline — the build succeeds but the
step never finishes.

## Root Cause

Clojure's agent thread pool uses **non-daemon threads**. From the
[official docs](https://clojure.org/reference/agents):

> Use of Agents starts a pool of non-daemon background threads that will
> prevent shutdown of the JVM. Use `shutdown-agents` to terminate these
> threads and allow shutdown.

During AOT compilation (`:aot :all`), every namespace is loaded and evaluated.
Any library that calls `send`, `send-off`, or `pmap` at load time starts these
threads. The Google Closure Compiler (used by `lein-cljsbuild` for `:advanced`
optimizations) and various Clojure libraries trigger this.

After the uberjar is fully built, the JVM should exit — but the non-daemon
threads keep it alive. Leiningen does not call `(shutdown-agents)` after
compilation, and `lein-cljsbuild` (abandoned since 2020, latest 1.1.8) does
not clean up either.

## Why Not Just Call `(shutdown-agents)`?

`shutdown-agents` is a runtime function call, not a project.clj config option.
You'd have to modify application code to fix a build-only problem. The uberjar
build doesn't run `-main` — it only compiles.

## The Fix

In `docker/Dockerfile`, the uberjar step uses `timeout`:

```dockerfile
RUN timeout 300 lein uberjar; \
    EXIT=$?; \
    if [ -f target/orcpub.jar ]; then exit 0; fi; \
    exit $EXIT
```

- `timeout 300` — gives lein 5 minutes to build, then sends SIGTERM
- If the jar exists after timeout, the build succeeded — exit 0
- If the jar does NOT exist, a real build failure occurred — propagate the error

In CI (`.github/workflows/docker-integration.yml`), the build uses
`DOCKER_BUILDKIT=0` and direct `docker build` commands. Docker Compose v2
delegates to BuildKit on GitHub runners, which compounds the hang by also
stalling during image export.

## Affected Components

| File | What | Why |
|------|------|-----|
| `docker/Dockerfile` | `timeout 300 lein uberjar` | Kills zombie JVM after build |
| `.github/workflows/docker-integration.yml` | `DOCKER_BUILDKIT=0` + direct `docker build` | Avoids BuildKit export hang |
| `project.clj` | `lein-cljsbuild 1.1.8` | Abandoned plugin, no thread cleanup |

## Will This Be Fixed Upstream?

Unlikely. `lein-cljsbuild` has had no releases since April 2020. Leiningen
itself is in maintenance mode. The Clojure ecosystem has moved toward
`tools.build` and `deps.edn`, which don't have this problem because they
don't use Leiningen's plugin/hook architecture.

If OrcPub ever migrates from Leiningen to `deps.edn` + `tools.build`, the
timeout workaround can be removed.
