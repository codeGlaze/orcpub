# lein uberjar JVM Hang — Agent Knowledge

> Build-blocking issue: `lein uberjar` hangs in Docker/CI because
> lein-cljsbuild's I/O subprocess pump never returns in no-TTY environments,
> AND cljsbuild hooks fire on compile/jar tasks even when removed from prep-tasks.

## What Happens

1. `lein uberjar` triggers cljsbuild via hooks (not just prep-tasks)
2. cljsbuild spawns a subprocess via `eval-in-project` (even with zero builds)
3. The I/O pump for that subprocess hangs — the task never returns
4. lein never reaches jar packaging
5. In Docker: the `RUN` step hangs indefinitely, CI times out

## Root Causes (Three Compounding Issues)

### 1. cljsbuild I/O pump hang (primary)
lein-cljsbuild spawns a subprocess and pumps its I/O. In no-TTY environments
(Docker, CI), the pump hangs after compilation finishes. The prep-task never
returns to lein. This is lein-cljsbuild issue #171.

### 2. cljsbuild hooks always spawn a subprocess (critical discovery)
lein-cljsbuild registers hooks on `compile`, `jar`, and `clean` at plugin load
time via `robert.hooke` (see `activate` function in `leiningen/cljsbuild.clj`).

**The `compile-hook` ALWAYS spawns a subprocess**, regardless of config:
```clojure
;; leiningen/cljsbuild.clj line 281-283
(defn compile-hook [task & args]
  (apply task args)
  (run-compiler (first args) (config/extract-options (first args)) nil false))
```

`run-compiler` → `run-local-project` → `leval/eval-in-project` → spawns JVM.
Even with nil/empty `:cljsbuild` config, `extract-options` normalizes nil into
`{:builds ()}` with defaults, and `run-local-project` is STILL called because
the hook doesn't check whether builds is empty before calling it.

**This means no amount of profile-based `:cljsbuild` config manipulation can
prevent the hang.** The only fix is to prevent the plugin from loading entirely.

### 3. Top-level plugins survive profile overrides (observed, mechanism unclear)
A plugin at the top level of `:plugins` is loaded even when `:plugins ^:replace`
in a profile should remove it (CI run 22301557284). The exact mechanism is unclear:
it could be that lein loads plugins before applying profiles, or that the plugin
jar is already on the classpath from dependency resolution. Either way, the
observed behavior is that `^:replace` on `:plugins` in a profile does NOT prevent
a top-level plugin from being loaded and activated.

### 4. Non-daemon agent threads (secondary)
Clojure's agent thread pool uses non-daemon threads. Libraries that call
`send`, `send-off`, or `pmap` during namespace loading start these threads.
During AOT, all namespaces are loaded. Even if lein finishes, the JVM won't
exit. `shutdown-agents` is a **runtime** call — cannot be added to project.clj.

## Current Approach (Attempt 7 — UNVERIFIED, CI pending)

**Both** the plugin AND the config live in a profile, not at the top level:

```clojure
;; project.clj — NO lein-cljsbuild in top-level :plugins
;; NO :cljsbuild config at top level

:plugins [[lein-garden ...] [lein-environ ...]]  ;; no cljsbuild

:profiles {
  ;; Plugin + config isolated here — only loaded when profile is active (theory)
  :cljsbuild-config {:plugins [[lein-cljsbuild "1.1.8" ...]]
                     :cljsbuild {:builds {:dev {...} :prod {...}}}}

  ;; Dev sees builds via composite
  :dev [:cljsbuild-config :dev-config]

  ;; No cljsbuild plugin or config
  :uberjar {:prep-tasks ["clean" ["garden" "once"] "compile"]
            :env {:production true} :aot :all :omit-source true}
}
```

Docker step 1 (CLJS): explicitly includes `cljsbuild-config`.
Docker step 2 (jar): uses `uberjar,uberjar-package` (NO cljsbuild-config).
Theory: plugin not loaded → `activate()` never runs → no hooks → no subprocess.

```dockerfile
# Step 1: cljsbuild-config provides plugin + config
RUN timeout 120 lein with-profile uberjar,uberjar-cljs,cljsbuild-config cljsbuild once prod; \
    test -f resources/public/js/compiled/orcpub.js || exit 1

# Step 2: explicit profiles (no cljsbuild-config)
RUN timeout 300 lein with-profile uberjar,uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1
```

**If this fails**: the plugin jar may be on the classpath from Docker layer
caching or step 1's `lein deps`. Next approach would be to split into separate
Dockerfile stages (separate Docker images) so step 2's classpath is fully clean.

## Key Facts for Agents

### Verified (source code or CI evidence)
- `lein-cljsbuild` is abandoned (last release: 1.1.8, April 2020)
- The hooks spawn a subprocess even with nil/empty config — there is no "safe" config
  (source: `leiningen/cljsbuild.clj` lines 281-283, `config.clj` lines 179-185)
- `:plugins ^:replace` in a profile does NOT prevent a top-level plugin from loading
  (CI run: 22301557284)
- The jar is **never created** when hooks hang — `test -f` fails (all CI runs)
- `shutdown-agents` does NOT fix this — the hang is in cljsbuild's subprocess
- This is NOT a Docker, BuildKit, or CI-specific problem — it's lein-cljsbuild

### Observed but mechanism unclear
- Top-level plugins survive profile overrides — could be load order or classpath
- BuildKit on GH runners compounds the issue with an additional export hang

---

## Failed Approaches — Chronological Record

### Attempt 1: Remove cljsbuild from `:prep-tasks` only
**What**: Created `:uberjar-package` profile with `:prep-tasks ^:replace [["garden" "once"] "compile"]`
(no cljsbuild step).

**Why it failed**: lein-cljsbuild hooks into `compile` and `jar` at plugin load
time via `robert.hooke/add-hook`. Removing cljsbuild from prep-tasks only prevents
the explicit prep-task — the hooks still fire on every `compile` and `jar` call.

**Lesson**: Hooks are on the task functions themselves, not controlled by `:prep-tasks`.

---

### Attempt 2: `:cljsbuild ^:replace {}` in `:uberjar-package`
**What**: Added `:cljsbuild ^:replace {}` to `:uberjar-package` to wipe the
cljsbuild config so hooks would find nothing to compile.

**Why it failed**: Two reasons:
1. The `uberjar` task internally re-merges profiles from the base project
   (`leiningen/uberjar.clj` line 176). It applies ONLY `[:uberjar :provided]`.
   `:uberjar-package` is NOT in the re-merge set and is stripped. The base
   project's `:cljsbuild` config survives.
2. Even if the config were `{}`, the hooks still call `run-compiler` which
   calls `run-local-project` which calls `eval-in-project` — spawning a
   subprocess regardless.

**Lesson**: `^:replace` metadata is consumed once and doesn't survive re-merges.
And the hooks don't check for empty builds before spawning.

---

### Attempt 3: Move `:prod` build from `:uberjar` to top level
**What**: Moved the `:prod` cljsbuild config out of `:uberjar` profile to
the top level of project.clj, alongside `:dev`. Theory: the re-merge restores
`:uberjar` config, so keeping cljsbuild OUT of `:uberjar` would prevent restoration.

**Why it failed**: The re-merge starts from the BASE project (`:without-profiles`),
which includes top-level config. So top-level `:cljsbuild` survives the re-merge
just like `:uberjar`-level config. And even if config were empty, the subprocess
spawns anyway (Attempt 2 lesson).

**CI run**: `22298816262` — failed. Same AOT-then-hang pattern.

**Lesson**: Top-level config is part of the base project and is always present
after re-merge.

---

### Attempt 4: Move ALL cljsbuild config to `:cljsbuild-config` profile
**What**: Created `:cljsbuild-config` profile with both `:dev` and `:prod` builds.
Made `:dev` a composite profile `[:cljsbuild-config :dev-config]`. No `:cljsbuild`
at top level or in `:uberjar`. Docker step 2 used `+uberjar-package`.

**Why it failed**: The `+` prefix in `lein with-profile +uberjar-package uberjar`
means "add to defaults". Defaults include `:dev`, which is composite and includes
`:cljsbuild-config`. So the plugin is loaded via `:dev` → `:cljsbuild-config`.
And even if config manipulation worked, the subprocess spawns regardless.

**CI run**: `22299537252`, `22299722719` — both failed.

**Lesson**: `+prefix` includes `:dev` which pulls in `:cljsbuild-config` via
composite. Must use explicit profiles (no `+`) to exclude `:dev`.

---

### Attempt 5: Explicit profiles (no `+`) to exclude `:dev`
**What**: Changed Docker step 2 from `+uberjar-package` to `uberjar,uberjar-package`
(explicit list, no `+`). This excludes `:dev` (and therefore `:cljsbuild-config`)
from the active profiles.

**Why it failed**: The lein-cljsbuild plugin is in the top-level `:plugins`. Lein
loads plugins from the raw project BEFORE applying profiles. The plugin is loaded,
`activate()` runs, hooks are registered. Config doesn't matter — the subprocess
spawns regardless.

**CI run**: `22300857430` — failed. Same pattern.

**Lesson**: Plugin loading precedes profile application. Top-level plugins are
always loaded.

---

### Attempt 6: `:plugins ^:replace [...]` in `:uberjar-package`
**What**: Added `:plugins ^:replace [[lein-garden ...] [lein-environ ...]]` to
`:uberjar-package`, excluding lein-cljsbuild from the plugin list.

**Why it failed**: Same as Attempt 5 — `:plugins ^:replace` is a profile override,
applied AFTER lein has already read the raw project's `:plugins` and loaded them.
The plugin is loaded before the profile is applied. `activate()` has already run.

**CI run**: `22301557284` — failed. Same pattern.

**Lesson**: You cannot unload a plugin that was loaded from the raw project. Profile
overrides on `:plugins` only affect which plugins appear in the merged project map,
not which plugins were actually loaded and activated.

---

### Attempt 7 (current): Move plugin to `:cljsbuild-config` profile
**What**: Removed `lein-cljsbuild` from top-level `:plugins`. Added it to
`:cljsbuild-config` profile. Dev tasks get it via `:dev` composite. Docker step 1
gets it via explicit `cljsbuild-config` in the profile list. Docker step 2 uses
`uberjar,uberjar-package` — no `:cljsbuild-config`, so the plugin should not load.

**Theory**: If the plugin jar is not in the effective project's `:plugins` list
after profile merging, lein won't resolve or load it, `activate()` never runs,
and hooks are never registered.

**Status**: UNVERIFIED. Pushed as commit `7e2fdc6e`. CI pending.

**Risk**: It's unclear exactly when lein resolves plugin jars vs when it loads
plugin namespaces. Attempts 5 and 6 showed that `:plugins ^:replace` in a profile
didn't prevent the hooks. This could mean either: (a) plugins are loaded before
profile merging, or (b) the jar was already on the classpath from a prior Docker
layer (`lein deps` or step 1). Attempt 7 removes the plugin from the top level
entirely, which is a stronger intervention than `^:replace` — but whether it
actually prevents loading is unconfirmed until CI passes.

---

## What Is Verified vs Assumed

### VERIFIED (from source code or CI failures)

- `compile-hook` always calls `run-compiler` → `run-local-project` →
  `eval-in-project` — spawns subprocess regardless of config
  (source: `leiningen/cljsbuild.clj` lines 33-44, 80-151, 281-283)
- `extract-options` processes nil `:cljsbuild` into `{:builds ()}` with defaults
  — it does NOT short-circuit on nil
  (source: `leiningen/cljsbuild/config.clj` lines 179-185)
- `^:replace {}` on `:cljsbuild` in `:uberjar-package` does NOT prevent the
  subprocess (CI runs: 22298816262, 22299537252, 22299722719, 22300857430)
- `^:replace` on `:plugins` in `:uberjar-package` does NOT prevent the hooks
  (CI run: 22301557284)
- Removing cljsbuild from `:prep-tasks` does NOT prevent hooks — they fire on
  compile/jar tasks via `robert.hooke`
  (source: `leiningen/cljsbuild.clj` lines 292-298)
- The uberjar task does an internal re-merge
  (source: `leiningen/uberjar.clj` line 176, verified by reading lein JAR)
- Top-level `:cljsbuild` config survives the uberjar re-merge
  (CI runs confirm hang persists after moving config to top level)

### UNVERIFIED (theories / assumptions)

- Whether `merge-profiles` in the uberjar task starts from `:without-profiles`
  or layers on top of the current project — we inferred "starts from base" but
  haven't confirmed via lein source
- Whether lein loads plugins from the raw project or the merged project —
  Attempts 5-6 suggest top-level plugins survive, but the mechanism is unclear
- Whether moving the plugin to a profile (Attempt 7) actually prevents loading —
  CI pending
- Whether Docker layer caching of `lein deps` puts the plugin jar on the
  classpath regardless of `:plugins` — untested

## DO NOT (verified by CI failures or source analysis)

- Remove the `timeout` wrapper — even if hooks are fixed, agent threads prevent exit
- Replace `timeout` with `(shutdown-agents)` — hang is in cljsbuild subprocess, not post-build
- Assume `^:replace {}` on `:cljsbuild` prevents the subprocess — it doesn't;
  hooks call `run-compiler` regardless of config (verified: source lines 281-283)
- Assume `:plugins ^:replace` in a profile prevents plugin loading — didn't work
  (verified: CI run 22301557284)
- Assume removing cljsbuild from `:prep-tasks` is sufficient — hooks fire via
  `robert.hooke` on compile/jar (verified: source lines 292-298)
- Put `:cljsbuild` config in `:uberjar` — the re-merge applies `:uberjar`
  (verified: reading lein uberjar.clj source)

## AVOID (based on CI failures, mechanism not fully understood)

- Put lein-cljsbuild in top-level `:plugins` — correlated with all failures
- Put `:cljsbuild` config at the top level — correlated with failures
- Use `+prefix` in Docker step 2 — includes `:dev` which includes cljsbuild

## Related Files

- `docker/Dockerfile` — two-step build with timeout
- `project.clj` — `:cljsbuild-config`, `:dev` composite, Docker profiles
- `.github/workflows/docker-integration.yml` — `DOCKER_BUILDKIT=0` + direct build
- `docs/LEIN-UBERJAR-HANG.md` — human-facing documentation
- `lein-cljsbuild 1.1.8` source: `~/.m2/repository/lein-cljsbuild/lein-cljsbuild/1.1.8/`
  - `leiningen/cljsbuild.clj` — hooks, compile-hook, run-compiler
  - `leiningen/cljsbuild/config.clj` — extract-options (processes nil config)
  - `leiningen/cljsbuild/subproject.clj` — make-subproject (builds subprocess)
