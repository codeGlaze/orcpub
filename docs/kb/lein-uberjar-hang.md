# lein uberjar JVM Hang — Agent Knowledge

> Build-blocking issue: `lein uberjar` hangs in Docker/CI because
> lein-cljsbuild's I/O subprocess pump never returns in no-TTY environments,
> AND cljsbuild hooks fire on compile/jar tasks even when removed from prep-tasks.

## What Happens

1. `lein uberjar` triggers cljsbuild via hooks (not just prep-tasks)
2. cljsbuild compiles CLJS (`:advanced` optimizations) successfully
3. cljsbuild's I/O subprocess pump hangs — the task never returns
4. lein never reaches AOT compilation or jar packaging
5. In Docker: the `RUN` step hangs indefinitely, CI times out

## Root Causes (Three Compounding Issues)

### 1. cljsbuild I/O pump hang (primary)
lein-cljsbuild spawns a subprocess and pumps its I/O. In no-TTY environments
(Docker, CI), the pump hangs after compilation finishes. The prep-task never
returns to lein. This is lein-cljsbuild issue #171.

### 2. cljsbuild hooks + uberjar re-merge trap (critical)
lein-cljsbuild hooks into `compile`, `jar`, and `clean` at plugin load time
via `robert.hooke`. These hooks fire regardless of `:prep-tasks` — removing
cljsbuild from prep-tasks does NOT prevent the hooks from running.

The hooks check the project's `:cljsbuild` config for builds. The `uberjar`
task internally re-merges profiles starting from the **base project**
(`:without-profiles`) — not from the current merged state:
```clojure
project (->> (into [:uberjar] provided-profiles)
             (project/merge-profiles project))
```

`merge-profiles` strips ALL active profiles, returns to the raw base project,
then applies ONLY `[:uberjar :provided]`. **Any other profile (including custom
ones like `:uberjar-package`) is not in the re-merge set and is lost.**

This means:
- `^:replace {}` in `:uberjar-package` works for the initial merge
- But the re-merge rebuilds from base + `:uberjar` only
- If `:cljsbuild` exists at the **top level** or in **`:uberjar`**, it survives

**CONSTRAINT**: `:cljsbuild` config must NOT exist at the top level of
project.clj OR in the `:uberjar` profile. It must be isolated in a separate
profile (`:cljsbuild-config`) that is only pulled in when explicitly activated.

### 3. Non-daemon agent threads (secondary)
Clojure's agent thread pool uses non-daemon threads. Libraries that call
`send`, `send-off`, or `pmap` during namespace loading start these threads.
During AOT, all namespaces are loaded. Even if lein finishes, the JVM won't
exit. `shutdown-agents` is a **runtime** call — cannot be added to project.clj.

## Key Facts for Agents

- `lein-cljsbuild` is abandoned (last release: 1.1.8, April 2020)
- The I/O pump hang means the **jar is never created** — not that it's created
  and the JVM stays alive afterward
- `shutdown-agents` does NOT fix this — the hang is in cljsbuild, not post-build
- Removing cljsbuild from `:prep-tasks` is NOT ENOUGH — hooks still fire
- `^:replace {}` on `:cljsbuild` in `:uberjar-package` does NOT survive the
  re-merge — `:uberjar-package` is stripped from the merge set
- Top-level `:cljsbuild` is NOT safe — it's part of the base project and
  survives the re-merge
- `:cljsbuild` in `:uberjar` is NOT safe — the re-merge applies `:uberjar`
- This is NOT a Docker, BuildKit, or CI-specific problem — it's lein-cljsbuild
- BuildKit on GH runners compounds it with an additional export hang

## Current Fix

All cljsbuild config lives in the `:cljsbuild-config` profile. Neither the
top level of project.clj nor the `:uberjar` profile has any `:cljsbuild` key.
The `:dev` profile is a composite `[:cljsbuild-config :dev-config]` so dev
tasks see the builds, but `:dev` is not in the uberjar re-merge set.

```clojure
;; project.clj — NO :cljsbuild at top level

:profiles {
  ;; Isolated build definitions
  :cljsbuild-config {:cljsbuild {:builds {:dev {...} :prod {...}}}}

  ;; Dev overrides (devtools, compiler flags)
  :dev-config {:dependencies [...] :cljsbuild {:builds {:dev {:compiler {...}}}}}

  ;; Composite — dev tasks see builds via cljsbuild-config
  :dev [:cljsbuild-config :dev-config]

  ;; NO :cljsbuild — re-merge produces clean project, hooks skip
  :uberjar {:prep-tasks ["clean" ["garden" "once"] "compile"]
            :env {:production true} :aot :all :omit-source true}

  ;; Docker step 1: wipe prep-tasks, run cljsbuild directly
  :uberjar-cljs {:prep-tasks ^:replace []}

  ;; Docker step 2: skip clean (preserve JS), run garden + AOT
  :uberjar-package {:prep-tasks ^:replace [["garden" "once"] "compile"]}
}
```

In `docker/Dockerfile` (both steps use timeout + artifact check):
```dockerfile
# Step 1: CLJS only. cljsbuild-config provides build definitions.
# timeout kills I/O pump hang.
RUN timeout 120 lein with-profile uberjar,uberjar-cljs,cljsbuild-config cljsbuild once prod; \
    test -f resources/public/js/compiled/orcpub.js || exit 1

# Step 2: garden + AOT + jar. No cljsbuild visible after re-merge.
# timeout kills agent thread hang.
RUN timeout 300 lein with-profile +uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1
```

- Step 1: `cljsbuild-config` provides the `:prod` build. `uberjar` provides
  env vars. `uberjar-cljs` wipes prep-tasks. `timeout 120` kills I/O pump.
- Step 2: `+uberjar-package` layers on auto-activated `:uberjar`. Re-merge
  uses base (no cljsbuild) + `:uberjar` (no cljsbuild) → hooks find nothing.
  `timeout 300` kills agent threads. `test -f` confirms jar exists.

## DO NOT

- Remove the `timeout` wrapper — the hang will return
- Run `lein uberjar` with the default `:uberjar` profile in Docker — it will hang
- Replace `timeout` with `(shutdown-agents)` — the hang is in cljsbuild, not post-build
- Put `:cljsbuild` config at the **top level** of project.clj — the re-merge
  starts from the base project and restores it
- Put `:cljsbuild` config in the **`:uberjar` profile** — the re-merge
  explicitly applies `:uberjar` and restores it
- Assume `^:replace {}` in `:uberjar-package` survives the re-merge — it doesn't,
  because `:uberjar-package` is not in the re-merge set `[:uberjar :provided]`
- Assume removing cljsbuild from `:prep-tasks` is sufficient — hooks still fire
- Assume BuildKit is the cause — the underlying issue is lein-cljsbuild
- Use `docker compose build` in CI — it delegates to BuildKit on GH runners
- Increase timeout beyond 300s thinking "more time = success" — if the jar
  doesn't exist after timeout, the hooks are hanging, not the build being slow
- Make `:dev` a non-composite profile with inline cljsbuild — it must stay as
  `[:cljsbuild-config :dev-config]` so cljsbuild-config can be excluded

## Failed Approaches (for reference)

1. **`:prod` at top level + `^:replace {}` in `:uberjar-package`**: The re-merge
   rebuilds from base (which includes top-level config), stripping `:uberjar-package`.
2. **`:prod` in `:uberjar` + `^:replace {}` in `:uberjar-package`**: The re-merge
   applies `:uberjar` (which has the config), stripping `:uberjar-package`.
3. **Removing cljsbuild from prep-tasks only**: Hooks still fire on compile/jar.

## Related Files

- `docker/Dockerfile` — two-step build with timeout
- `project.clj` — `:cljsbuild-config`, `:dev` composite, Docker profiles
- `.github/workflows/docker-integration.yml` — `DOCKER_BUILDKIT=0` + direct build
- `docs/LEIN-UBERJAR-HANG.md` — human-facing documentation
