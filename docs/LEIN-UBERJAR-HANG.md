# lein uberjar Hangs During Docker Build

## The Problem

`lein uberjar` hangs indefinitely in Docker and CI (no-TTY environments).
AOT compilation finishes (all namespaces compiled, .class files written),
but the JVM subprocess never exits. lein waits for the subprocess, the jar
is never created, Docker step hangs.

## Root Causes (Two Compounding Issues)

### 1. AOT compile subprocess hang (primary)

Leiningen's `compile` task spawns a subprocess via `eval-in-project` to AOT
compile namespaces. After compilation finishes, the subprocess JVM should
exit, but **non-daemon threads** started during namespace loading prevent it:

- Datomic Peer starts background threads for caching/heartbeats
- `core.async` creates thread pools
- Other libraries may use agents, futures, or `pmap` during loading

Lein 2.12.0's compile task sets a 100ms keep-alive on the agent thread pool,
but this only handles Clojure agent threads, not threads from libraries that
create their own pools.

`eval-in-project` waits for the subprocess to exit. It never does. lein
hangs. The jar is never created because lein never gets past compile.

### 2. lein-cljsbuild hooks (eliminated)

`lein-cljsbuild` (abandoned, last release: 1.1.8, April 2020) registered
hooks on `compile`/`jar` that spawned an additional subprocess with the same
hang. **This was eliminated by replacing cljsbuild with figwheel-main.** But
the AOT subprocess hang persists because it's in lein's own compile task.

We spent 7 attempts trying to isolate cljsbuild via profiles before
discovering the real culprit was lein's own compile subprocess. See "Failed
Approaches" below.

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

AOT compiles all namespaces (`:aot :all`). The compilation **finishes** --
all .class files are written to `target/classes/`. But the subprocess hangs
due to non-daemon threads. `timeout` kills it. `|| true` allows the step
to continue despite the non-zero exit code. `test -f` verifies the main
class was actually compiled.

### Step 3: jar packaging (compile is no-op)

```dockerfile
RUN timeout 600 lein with-profile uberjar,uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1
```

This step must NOT re-compile (that would spawn a new hanging subprocess).
Three mechanisms ensure this:

**a) `stale-namespaces` timestamp check**: lein's compile task only compiles
namespaces where the source file is newer than the .class file. Since step 2
just wrote all .class files, they're newer than source, so
`stale-namespaces` returns empty, no subprocess is spawned.

**b) `^:replace` on prep-tasks**: Both `:uberjar` and `:uberjar-package`
profiles use `^:replace` on `:prep-tasks`. `:uberjar` sets
`^:replace [["garden" "once"] "compile"]`, `:uberjar-package` overrides to
`^:replace [["garden" "once"]]` (no compile). The `^:replace` metadata
survives the uberjar task's internal profile re-merge because these profiles
are explicitly specified via `with-profile` and are in `:included-profiles`.

**c) `:auto-clean false`**: Critical discovery. lein's `jar.clj` (line
341-342) calls `clean/clean` **directly** before prep-tasks even run,
controlled by the `:auto-clean` project key (defaults to `true`). This is
NOT controlled by prep-tasks -- it's a separate, earlier cleanup. Without
`:auto-clean false`, lein would wipe `target/` (destroying step 2's .class
files) and `resources/public/js/compiled/` (destroying step 1's JS) before
jar creation even begins. `:auto-clean false` lives in the `:uberjar`
profile so both bare-metal (`lein build`) and Docker builds inherit it.

### `resource-paths` fix

`"target"` was historically in `:resource-paths`. This causes the jar to
recursively include itself (jar output goes to `target/`, which is a
resource path, so the jar includes `target/` including the jar being
written). Masked when `"clean"` runs first (target/ is empty), but fatal
when skipping clean. The thin jar grew to 6.7GB before this was caught.
Fixed by removing `"target"` from `:resource-paths`. The compiled JS goes
to `resources/public/js/compiled/` (already in resource-paths via
`"resources"`). `.class` files are handled by `:compile-path`, not
resource-paths.

### Local builds

```sh
# Single-command production build: clean → CLJS → uberjar
lein build

# Or manually:
lein fig:prod            # CLJS (figwheel-main exits cleanly in a TTY)
lein uberjar             # Uberjar (Ctrl-C the hang after "Created target/orcpub.jar")
```

The `build` alias runs `["do" "clean," "fig:prod," ["with-profile" "uberjar" "uberjar"]]`.
Clean runs first (explicit), then CLJS, then uberjar. The `:uberjar` profile
has `:auto-clean false` and `^:replace` prep-tasks so the CLJS output from
`fig:prod` is preserved during jar packaging.

## Affected Components

| File | What | Why |
|------|------|-----|
| `prod.cljs.edn` | Production CLJS build config | Replaces cljsbuild :prod build |
| `docker/Dockerfile` | Three-step build | Separates compile from jar creation |
| `project.clj` | No `lein-cljsbuild` in any `:plugins` | Eliminates cljsbuild hooks |
| `project.clj` | `fig:prod` alias | Local production CLJS builds |
| `project.clj` | `:uberjar` profile | `:auto-clean false`, `^:replace` prep-tasks (bare-metal + Docker) |
| `project.clj` | `:uberjar-package` profile | Overrides prep-tasks to skip compile (Docker step 3 only) |
| `project.clj` | `build` alias | Single-command: clean → fig:prod → uberjar |
| `project.clj` | `:resource-paths` | Removed `"target"` to prevent recursive jar inclusion |

## DO NOT (each verified by failure)

- **Remove `timeout` from step 2** -- non-daemon threads from Datomic/core.async prevent JVM exit
- **Remove `:auto-clean false` from `:uberjar`** -- `jar.clj` wipes target/ before prep-tasks run (destroys .class files from step 2, CLJS from step 1)
- **Add `"target"` back to `:resource-paths`** -- causes recursive jar inclusion (6.7GB thin jar)
- **Remove `^:replace` from prep-tasks** -- `"clean"` in prep-tasks wipes resources/public/js/compiled/ (destroys JS from step 1)
- **Try `:eval-in :leiningen`** -- causes classpath conflict between lein's internal Clojure and project's Clojure 1.12.4
- **Try to isolate cljsbuild via profiles** -- 7 attempts failed; hooks fire regardless of config (see Failed Approaches)

## Failed Approaches (Chronological)

### Attempt 1: Remove cljsbuild from prep-tasks

Created `:uberjar-package` with no cljsbuild step. Failed: hooks fire on
compile/jar via `robert.hooke`, not controlled by prep-tasks.

### Attempt 2: `:cljsbuild ^:replace {}` in uberjar-package

Wiped cljsbuild config in the packaging profile. Failed: uberjar re-merge
strips uberjar-package; hooks spawn subprocess regardless of config.

### Attempt 3: Move :prod build to top level

Kept cljsbuild config out of :uberjar by putting :prod at top level. Failed:
top-level config is part of base project and survives re-merge, so the hooks
still fire. CI: 22298816262.

### Attempt 4: Move ALL config to :cljsbuild-config profile

Separated cljsbuild into its own profile. Failed: `+prefix` includes :dev
which pulls in :cljsbuild-config. CI: 22299537252, 22299722719.

### Attempt 5: Explicit profiles (no +)

Used `uberjar,uberjar-package` to exclude :dev. Failed: top-level plugins
load before profiles are applied, so cljsbuild hooks still register.
CI: 22300857430.

### Attempt 6: `:plugins ^:replace`

Excluded cljsbuild from plugins in uberjar-package. Failed: plugin is
already loaded from the raw project map before profile merging applies.
CI: 22301557284.

### Attempt 7: Move plugin to :cljsbuild-config profile

Removed cljsbuild from top-level plugins entirely, put it only in the
:cljsbuild-config profile. Failed: plugin still loads despite not being in
active profiles. CI: 22314544526.

### Attempt 8: Replace cljsbuild with figwheel-main

Removed cljsbuild entirely from the project. Partially fixed: eliminated the
cljsbuild hang, but lein's OWN compile subprocess still hangs (non-daemon
threads from Datomic/core.async). This proved the primary hang was never
cljsbuild -- it was lein's compile task all along. Led to the 3-step build
solution.

### Key Insight from Attempt 8

When cljsbuild was completely removed and the hang PERSISTED at
`orcpub.views-aux` (the last namespace alphabetically), it proved that the
primary issue was always lein's own AOT compile subprocess, not cljsbuild.
The 7 previous attempts were chasing a secondary issue.

### Attempt 9: stale-namespaces alone (no prep-tasks split)

Relied on `stale-namespaces` to skip compile in step 3. Failed: `lein uberjar`
re-merges profiles internally, triggering a second compile round. The subprocess
compiled everything again and hung. This is what led to the `^:replace`
prep-tasks approach.

### Attempt 10: `:eval-in :leiningen`

Ran compile in lein's own JVM instead of a subprocess — no subprocess = no hang.
Failed: classpath conflict between lein's internal Clojure and project's Clojure
1.12.4. Error: `Syntax error compiling fn* at (orcpub/common.cljc:1:1)`.

### Attempt 11: Skip clean, keep `"target"` in resource-paths

Removed `"clean"` from prep-tasks via `^:replace`, but left `"target"` in
`:resource-paths`. Failed: the jar recursively includes itself. Jar output goes
to `target/`, which is a resource-path, so the jar being written is included in
the jar being written. The thin jar grew to 6.7GB. Masked historically because
`"clean"` wiped `target/` first.

### Attempt 12: Auto-clean wipes artifacts

After fixing resource-paths (Attempt 11), step 3 created the jars but they were
empty — no .class files, no compiled JS. Root cause: lein's `jar.clj` (line
341-342) calls `clean/clean` **directly**, controlled by `:auto-clean` (defaults
to `true`). This runs BEFORE prep-tasks, wiping both `target/` and
`resources/public/js/compiled/` (the clean-targets). Fixed with `:auto-clean
false` in the uberjar profile (originally in `:uberjar-package`, now in `:uberjar`
directly so bare-metal `lein build` also inherits it).

This was the final piece — the 3-step build worked after this fix.
