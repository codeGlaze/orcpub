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
profiles starting from the **base project** (`leiningen/uberjar.clj`):

```clojure
project (->> (into [:uberjar] provided-profiles)
             (project/merge-profiles project))
```

`merge-profiles` starts from `:without-profiles` (the raw base project with
no profiles applied), then applies only `[:uberjar :provided]`. **Any other
active profile — including custom ones like `:uberjar-package` — is stripped.**

This means:
- `:cljsbuild ^:replace {}` in `:uberjar-package` works for the initial merge
- But the re-merge strips `:uberjar-package` and rebuilds from base + `:uberjar`
- If `:cljsbuild` exists at the top level of `project.clj` OR in `:uberjar`,
  the re-merge restores it, hooks see builds, and the hang returns

**This is why `:cljsbuild` config must NOT exist at the top level of
`project.clj` or in the `:uberjar` profile.** It must be isolated in a
separate profile (`:cljsbuild-config`) that is only explicitly activated
when needed.

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

Two changes work together to prevent the hang:

### 1. Isolate cljsbuild config in its own profile

All ClojureScript build definitions (`:dev` and `:prod`) live in the
`:cljsbuild-config` profile — **not** at the top level of `project.clj`
and **not** in the `:uberjar` profile:

```clojure
;; project.clj — NO :cljsbuild at top level

:profiles {
  ;; All cljsbuild definitions isolated here
  :cljsbuild-config {:cljsbuild {:builds {:dev {...} :prod {...}}}}

  ;; Dev-only overrides (devtools, compiler flags)
  :dev-config {:dependencies [...] :cljsbuild {:builds {:dev {:compiler {...}}}}}

  ;; Composite — includes cljsbuild-config so builds are visible during dev
  :dev [:cljsbuild-config :dev-config]

  ;; No :cljsbuild here — re-merge sees nothing, hooks are no-ops
  :uberjar {:prep-tasks ["clean" ["garden" "once"] "compile"]
            :env {:production true} :aot :all :omit-source true}
}
```

**Why this works**: the `uberjar` task's internal re-merge starts from the
base project (no `:cljsbuild` at top level) and applies `[:uberjar]` (no
`:cljsbuild` key). The hooks check the re-merged project's `:cljsbuild`
config, find nothing, and skip — no I/O pump, no hang.

**Why the top level doesn't work**: we originally tried putting `:cljsbuild`
at the top level with `^:replace {}` in `:uberjar-package`. The initial
merge wiped it correctly, but the re-merge starts from the raw base project
(which includes top-level config) and doesn't include `:uberjar-package`
in its merge set. The top-level config survived the re-merge.

**Why `:uberjar` doesn't work**: same reason — the re-merge applies
`:uberjar`, so any `:cljsbuild` inside it gets restored.

**What about dev?**: the `:dev` profile is a composite that includes
`:cljsbuild-config`. During `lein fig:build` or REPL use, `:dev` is active,
so builds are visible. During `lein uberjar`, `:dev` is NOT in the re-merge
set `[:uberjar :provided]`, so `:cljsbuild-config` is not pulled in.

### 2. Split CLJS compilation into its own Docker step

Three Leiningen profiles coordinate the Docker build:

```clojure
;; Build definitions — only active when explicitly included
:cljsbuild-config {:cljsbuild {:builds {:dev {...} :prod {...}}}}

;; CLJS only — wipes prep-tasks so cljsbuild runs alone (no AOT)
:uberjar-cljs {:prep-tasks ^:replace []}

;; Skips clean (preserves step 1 JS), runs garden + AOT
:uberjar-package {:prep-tasks ^:replace [["garden" "once"] "compile"]}
```

In `docker/Dockerfile`, both steps use `timeout` + artifact check:

```dockerfile
# Step 1: CLJS only. cljsbuild-config provides build definitions.
# timeout kills the I/O pump hang.
RUN timeout 120 lein with-profile uberjar,uberjar-cljs,cljsbuild-config cljsbuild once prod; \
    test -f resources/public/js/compiled/orcpub.js || exit 1

# Step 2: garden CSS + AOT + jar packaging.
# No cljsbuild config visible after re-merge — hooks are no-ops.
# timeout kills the agent thread hang.
RUN timeout 300 lein with-profile +uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1
```

- **Step 1** explicitly includes `cljsbuild-config` for the `:prod` build.
  `uberjar` provides `:env {:production true}`. `uberjar-cljs` wipes
  prep-tasks. `timeout` kills the JVM after CLJS finishes (I/O pump hang),
  then `test -f` confirms the JS file was produced.
- **Step 2** uses `+uberjar-package` on top of auto-activated `:uberjar`.
  Neither the base project nor `:uberjar` has `:cljsbuild`, so the re-merge
  produces a project with no cljsbuild config. Hooks find nothing and skip.
  `timeout` kills the JVM after packaging (non-daemon agent threads), then
  `test -f` confirms the jar exists.
- Garden CSS is gitignored (build artifact), so it must run during Docker build.
- If a step fails, check whether the artifact exists before assuming a code
  error. Missing artifact after timeout = build too slow (bump timeout).

### Local uberjar builds

For building outside Docker (in a TTY), CLJS must be compiled separately:

```sh
lein with-profile +cljsbuild-config cljsbuild once prod
lein uberjar
```

The first command compiles CLJS (the hang can be Ctrl-C'd in a terminal
after "Successfully compiled" appears). The second runs garden + AOT + jar
without cljsbuild interference.

In CI (`.github/workflows/docker-integration.yml`), the build uses
`DOCKER_BUILDKIT=0` and direct `docker build` commands. Docker Compose v2
delegates to BuildKit on GitHub runners, which compounds the hang by also
stalling during image export.

## Affected Components

| File | What | Why |
|------|------|-----|
| `docker/Dockerfile` | Two-step build with `timeout` | Isolates cljsbuild hang |
| `project.clj` | `:cljsbuild-config` profile | Isolates builds from base project |
| `project.clj` | `:dev` as composite `[:cljsbuild-config :dev-config]` | Dev sees builds, uberjar doesn't |
| `project.clj` | `:uberjar-cljs` + `:uberjar-package` profiles | Split Docker steps |
| `project.clj` | `:uberjar` has NO `:cljsbuild` | Re-merge stays clean |
| `.github/workflows/docker-integration.yml` | `DOCKER_BUILDKIT=0` + direct `docker build` | Avoids BuildKit export hang |
| `project.clj` | `lein-cljsbuild 1.1.8` | Abandoned plugin, no thread cleanup |

## Will This Be Fixed Upstream?

Unlikely. `lein-cljsbuild` has had no releases since April 2020. Leiningen
itself is in maintenance mode. The Clojure ecosystem has moved toward
`tools.build` and `deps.edn`, which don't have this problem because they
don't use Leiningen's plugin/hook architecture.

If OrcPub ever migrates from Leiningen to `deps.edn` + `tools.build`, the
timeout workaround can be removed.
