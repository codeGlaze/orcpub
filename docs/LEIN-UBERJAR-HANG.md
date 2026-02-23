# lein uberjar Hangs During Docker Build

## The Problem

`lein uberjar` hangs indefinitely in Docker and CI (no-TTY environments).
The ClojureScript compilation succeeds, but lein never reaches AOT or jar
packaging. In a terminal you'd wait and eventually Ctrl-C. Inside a Docker
build, the `RUN` step hangs until CI times out.

## Root Causes

Three compounding issues cause the hang:

### 1. cljsbuild I/O subprocess pump (primary)

`lein-cljsbuild` spawns a subprocess for ClojureScript compilation and pumps
its I/O streams. In no-TTY environments (Docker, CI), the pump hangs after
compilation finishes — the cljsbuild prep-task never returns control to lein.
The jar is **never created** because lein never gets past the prep-tasks.

This is [lein-cljsbuild issue #171](https://github.com/emezeske/lein-cljsbuild/issues/171).
The plugin is abandoned (last release: 1.1.8, April 2020).

### 2. cljsbuild hooks + uberjar re-merge trap

`lein-cljsbuild` registers hooks on `compile`, `jar`, and `clean` tasks at
plugin load time (via `robert.hooke`). These hooks fire regardless of
`:prep-tasks` — they're attached to the task functions themselves.

The hooks check the project's `:cljsbuild` config for builds to compile.
You might think `^:replace {}` in a profile would wipe this config. It does
— for the initial profile merge. But the `uberjar` task internally re-merges
the `:uberjar` profile (`leiningen/uberjar.clj` line 176):

```clojure
project (->> (into [:uberjar] provided-profiles)
             (project/merge-profiles project))
```

This re-merge **restores** any `:cljsbuild` config from `:uberjar`, overriding
the `^:replace {}` wipe. The hooks then see full build config and fire,
triggering the I/O pump hang all over again.

**This is why `:cljsbuild` config must NOT live inside the `:uberjar` profile.**
It must be at the top level of `project.clj`, where `^:replace {}` in
`uberjar-package` can wipe it without the re-merge bringing it back.

### 3. Non-daemon agent threads (secondary)

Clojure's agent thread pool uses **non-daemon threads**. From the
[official docs](https://clojure.org/reference/agents):

> Use of Agents starts a pool of non-daemon background threads that will
> prevent shutdown of the JVM. Use `shutdown-agents` to terminate these
> threads and allow shutdown.

During AOT compilation (`:aot :all`), every namespace is loaded. Libraries
that call `send`, `send-off`, or `pmap` at load time start these threads.
Even if lein finishes, the JVM won't exit. This compounds with the cljsbuild
hang — even bypassing the pump issue wouldn't fully solve the problem.

## Why Not Just Call `(shutdown-agents)`?

`shutdown-agents` is a runtime function call, not a project.clj config option.
It also doesn't fix the primary issue — the hang is in cljsbuild's I/O pump,
not in post-build thread cleanup. The uberjar build doesn't run `-main`.

## The Fix

Split ClojureScript compilation out of `lein uberjar` into its own step. Two
Leiningen profiles in `project.clj` make this work:

```clojure
;; CLJS only — no prep-tasks, run cljsbuild directly
:uberjar-cljs {:prep-tasks ^:replace []}

;; Garden CSS + AOT + jar packaging — no cljsbuild.
;; ^:replace {} wipes the top-level :cljsbuild config so hooks are no-ops.
;; This works because :uberjar has NO :cljsbuild key — the re-merge
;; inside the uberjar task has nothing to restore.
:uberjar-package {:prep-tasks ^:replace [["garden" "once"] "compile"]
                  :cljsbuild  ^:replace {}}
```

**Critical constraint**: the `:prod` cljsbuild build config lives at the
**top level** of `project.clj` (alongside `:dev`), NOT inside the `:uberjar`
profile. If it were inside `:uberjar`, the task's internal re-merge would
restore it after our `^:replace {}` wipe.

In `docker/Dockerfile`, both steps use `timeout` + artifact check:

```dockerfile
# Step 1: CLJS only. timeout kills the post-compilation I/O pump hang.
RUN timeout 120 lein with-profile uberjar,uberjar-cljs cljsbuild once prod; \
    test -f resources/public/js/compiled/orcpub.js || exit 1

# Step 2: garden CSS + AOT + jar packaging. timeout kills the post-AOT
# agent thread hang. jar check confirms success.
RUN timeout 300 lein with-profile +uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1
```

- **Step 1** merges `uberjar` (for compiler config) with `uberjar-cljs` (wipes
  prep-tasks). `timeout` kills the JVM after CLJS finishes (I/O pump hang),
  then `test -f` confirms the JS file was produced. No wasted AOT.
- **Step 2** uses `+uberjar-package` on top of the auto-activated `:uberjar`
  profile (garden + AOT, no cljsbuild). `timeout` kills the JVM after packaging
  (non-daemon agent thread hang), then `test -f` confirms the jar exists.
- Garden CSS is gitignored (build artifact), so it must run during Docker build.
- If a step fails, check whether the artifact exists before assuming a code
  error. Missing artifact after timeout = build too slow (bump timeout).

In CI (`.github/workflows/docker-integration.yml`), the build uses
`DOCKER_BUILDKIT=0` and direct `docker build` commands. Docker Compose v2
delegates to BuildKit on GitHub runners, which compounds the hang by also
stalling during image export.

## Affected Components

| File | What | Why |
|------|------|-----|
| `docker/Dockerfile` | Two-step build with `timeout` | Isolates cljsbuild hang |
| `project.clj` | `:uberjar-cljs` + `:uberjar-package` profiles | Split prep-tasks |
| `project.clj` | `:prod` build at top level, NOT in `:uberjar` | Prevents re-merge trap |
| `.github/workflows/docker-integration.yml` | `DOCKER_BUILDKIT=0` + direct `docker build` | Avoids BuildKit export hang |
| `project.clj` | `lein-cljsbuild 1.1.8` | Abandoned plugin, no thread cleanup |

## Will This Be Fixed Upstream?

Unlikely. `lein-cljsbuild` has had no releases since April 2020. Leiningen
itself is in maintenance mode. The Clojure ecosystem has moved toward
`tools.build` and `deps.edn`, which don't have this problem because they
don't use Leiningen's plugin/hook architecture.

If OrcPub ever migrates from Leiningen to `deps.edn` + `tools.build`, the
timeout workaround can be removed.
