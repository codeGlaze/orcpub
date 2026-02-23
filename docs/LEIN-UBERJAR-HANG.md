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

Split ClojureScript compilation out of `lein uberjar` into its own step. Two
Leiningen profiles in `project.clj` make this work:

```clojure
;; CLJS only — no prep-tasks, run cljsbuild directly
:uberjar-cljs {:prep-tasks ^:replace []}

;; Garden CSS + AOT + jar packaging — no cljsbuild
:uberjar-jar  {:prep-tasks ^:replace [["garden" "once"] "compile"]}
```

In `docker/Dockerfile`:

```dockerfile
# Step 1: CLJS only. timeout kills the post-compilation hang.
# "Successfully compiled" = JS file exists = success.
RUN timeout 120 lein with-profile uberjar,uberjar-cljs cljsbuild once prod; \
    test -f resources/public/js/compiled/orcpub.js || exit 1

# Step 2: garden CSS + AOT + jar packaging (JS already in resources/).
RUN lein with-profile uberjar,uberjar-jar uberjar
```

- **Step 1** merges `uberjar` (for compiler config) with `uberjar-cljs` (wipes
  prep-tasks). `timeout` kills the JVM after CLJS finishes, then `test -f`
  confirms the JS file was produced. No wasted AOT.
- **Step 2** merges `uberjar` with `uberjar-jar` (garden + AOT, no cljsbuild).
  The compiled JS from step 1 is already in `resources/public/js/compiled/`.
- Garden CSS is gitignored (build artifact), so it must run during Docker build.

In CI (`.github/workflows/docker-integration.yml`), the build uses
`DOCKER_BUILDKIT=0` and direct `docker build` commands. Docker Compose v2
delegates to BuildKit on GitHub runners, which compounds the hang by also
stalling during image export.

## Affected Components

| File | What | Why |
|------|------|-----|
| `docker/Dockerfile` | Two-step build with `timeout` | Isolates cljsbuild hang |
| `project.clj` | `:uberjar-cljs` + `:uberjar-jar` profiles | Split prep-tasks |
| `.github/workflows/docker-integration.yml` | `DOCKER_BUILDKIT=0` + direct `docker build` | Avoids BuildKit export hang |
| `project.clj` | `lein-cljsbuild 1.1.8` | Abandoned plugin, no thread cleanup |

## Will This Be Fixed Upstream?

Unlikely. `lein-cljsbuild` has had no releases since April 2020. Leiningen
itself is in maintenance mode. The Clojure ecosystem has moved toward
`tools.build` and `deps.edn`, which don't have this problem because they
don't use Leiningen's plugin/hook architecture.

If OrcPub ever migrates from Leiningen to `deps.edn` + `tools.build`, the
timeout workaround can be removed.
