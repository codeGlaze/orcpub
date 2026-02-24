# lein uberjar JVM Hang — Agent Knowledge

## Summary

`lein uberjar` hangs in Docker/CI because of TWO compounding issues:
1. **lein's own compile subprocess** spawns non-daemon threads (Datomic Peer, core.async) that prevent JVM exit after AOT compilation finishes
2. **lein-cljsbuild hooks** (eliminated) spawned additional subprocesses with the same hang pattern

Issue #2 was a red herring — 7 attempts to fix it via profile manipulation all failed. Removing cljsbuild (Attempt 8) proved the primary issue was always #1.

## Final Working Solution: Three-Step Docker Build

```dockerfile
# Step 1: CLJS via figwheel-main (exits cleanly)
RUN lein run -m figwheel.main -- --build-once prod && \
    test -f resources/public/js/compiled/orcpub.js

# Step 2: AOT compile (compilation finishes, subprocess hangs, timeout kills)
RUN timeout 300 lein with-profile uberjar,uberjar-package compile || true; \
    test -f target/classes/orcpub/server__init.class || exit 1

# Step 3: garden CSS + jar packaging (no compile — .class files from step 2)
RUN timeout 600 lein with-profile uberjar,uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1
```

### Why step 3 doesn't re-compile

Three mechanisms prevent a second compile subprocess:

1. **stale-namespaces**: timestamp-based. .class files from step 2 are newer than source -> empty list -> no subprocess spawned.
2. **`^:replace` prep-tasks**: Both `:uberjar` and `:uberjar-package` profiles use `^:replace` on `:prep-tasks`. `:uberjar` sets `^:replace [["garden" "once"] "compile"]`, `:uberjar-package` overrides to `^:replace [["garden" "once"]]` (no compile). The `^:replace` survives lein's internal uberjar re-merge because these profiles are in `:included-profiles`.
3. **`:auto-clean false`**: lein's `jar.clj` (line 341-342) calls `clean/clean` BEFORE prep-tasks, controlled by `:auto-clean` (defaults `true`). Without this, target/ and resources/public/js/compiled/ are wiped before jar creation begins. Lives in `:uberjar` so both bare-metal (`lein build`) and Docker inherit it.

### project.clj profiles

```clojure
;; Base uberjar — auto-clean false prevents jar.clj from wiping CLJS output
:uberjar {:auto-clean false
           :prep-tasks ^:replace [["garden" "once"] "compile"]
           :env {:production true} :aot :all :omit-source true}

;; Docker step 3 only — skip compile (AOT done in step 2)
:uberjar-package {:prep-tasks ^:replace [["garden" "once"]]}
```

### Bare-metal build alias

```clojure
"build" ["do" "clean," "fig:prod," ["with-profile" "uberjar" "uberjar"]]
```

Clean runs first (explicit), then CLJS, then uberjar. `:auto-clean false` in `:uberjar` preserves CLJS output during jar packaging.

### resource-paths fix

`"target"` was in `:resource-paths`. This causes recursive jar inclusion (jar output -> target/ -> resource-path -> jar includes itself). Observed: 6.7GB thin jar. Fixed by removing `"target"` from `:resource-paths`. Compiled JS goes to `resources/` (already in resource-paths). .class files handled by `:compile-path`.

## Key Facts for Agents

### VERIFIED (from source code, local testing, or CI evidence)

**lein internals:**
- `eval-in-project` spawns a subprocess for AOT compile; waits for it to exit; non-daemon threads prevent exit (verified locally: hangs at views-aux, last namespace alphabetically)
- `jar.clj` line 341-342: `(when (:auto-clean project true) (clean/clean project))` — runs BEFORE prep-tasks (verified: .class files and JS wiped without `:auto-clean false`)
- `stale-namespaces` is timestamp-based — only compiles when source is newer than .class (verified locally: step 3 produces no compile output when .class files fresh)
- `uberjar` re-merges profiles internally but custom profiles survive if in `:included-profiles` (verified: `^:replace` on prep-tasks works in step 3)
- `:eval-in :leiningen` causes classpath conflict — `Syntax error compiling fn* at (orcpub/common.cljc:1:1)` (verified locally)

**lein-cljsbuild (eliminated, but documented for context):**
- Abandoned: last release 1.1.8, April 2020
- `compile-hook` always calls `run-compiler` -> `run-local-project` -> `eval-in-project` regardless of config (source: `leiningen/cljsbuild.clj` lines 281-283)
- `extract-options` processes nil `:cljsbuild` into `{:builds ()}` with defaults — does NOT short-circuit on nil (source: `config.clj` lines 179-185)
- Hooks registered via `robert.hooke` on `compile`, `jar`, `clean` tasks — not controlled by `:prep-tasks`
- `:plugins ^:replace` in a profile does NOT prevent a top-level plugin from loading (CI run: 22301557284)
- Moving plugin to a non-active profile still loads it somehow (CI run: 22314544526)
- No profile-based approach reliably prevents cljsbuild hooks — only removing the plugin entirely works

**Build output (verified locally):**
- figwheel-main `--build-once prod`: 2.8MB JS, 59s, exits cleanly
- AOT compile: all namespaces compiled, .class files written, then hangs (timeout kills ~30s later)
- Final uberjar: 168MB with server classes, prod JS, and CSS
- Thin jar: 83MB

### UNVERIFIED (theories / assumptions)

- Exact mechanism by which profile-based plugins load when their profile isn't active (all 7 attempts failed)
- Whether lein loads plugins from the raw project.clj or the merged project (Attempts 5-7 suggest raw)
- Whether Docker layer caching of `lein deps` puts plugin jars on classpath permanently

## DO NOT (each verified by failure or local testing)

- **Remove `timeout` from step 2** — non-daemon threads prevent JVM exit
- **Remove `:auto-clean false`** from `:uberjar` — jar.clj wipes target/ and compiled JS
- **Add `"target"` to `:resource-paths`** — recursive jar inclusion (6.7GB)
- **Remove `^:replace` from prep-tasks** — "clean" prep-task wipes compiled JS
- **Try `:eval-in :leiningen`** — classpath conflict with Clojure 1.12.4
- **Try to isolate cljsbuild via profiles** — 7 attempts failed; remove the plugin entirely instead
- **Add lein-cljsbuild back** — use figwheel-main for all CLJS builds
- **Assume views-aux causes the hang** — it's just last alphabetically; it's a pure data namespace with no side effects

## Failed Approaches (Chronological)

### Attempts 1-7: Profile-based cljsbuild isolation (all failed)

| # | Approach | Why Failed | CI Run |
|---|----------|-----------|--------|
| 1 | Remove cljsbuild from prep-tasks | Hooks fire via robert.hooke, not prep-tasks | — |
| 2 | `:cljsbuild ^:replace {}` | uberjar re-merge strips profile; hooks ignore config | — |
| 3 | Move :prod build to top level | Top-level config survives re-merge | 22298816262 |
| 4 | :cljsbuild-config profile | +prefix includes :dev -> :cljsbuild-config | 22299537252 |
| 5 | Explicit profiles (no +) | Top-level plugins load before profiles | 22300857430 |
| 6 | `:plugins ^:replace` | Plugin already loaded from raw project | 22301557284 |
| 7 | Plugin in profile only | Plugin still loads from non-active profile | 22314544526 |

### Attempt 8: Replace cljsbuild with figwheel-main

Removed cljsbuild entirely. CI STILL HUNG — proving the primary issue was always lein's own compile subprocess, not cljsbuild. Led to the 3-step build solution.

### Attempts 9-12: Final fix phase (post-cljsbuild removal)

| # | Approach | Why Failed |
|---|----------|-----------|
| 9 | stale-namespaces alone | uberjar re-merges profiles, triggers second compile round, hangs again |
| 10 | `:eval-in :leiningen` | classpath conflict: lein's Clojure vs project's 1.12.4 |
| 11 | Skip clean, keep `"target"` in resource-paths | Recursive jar inclusion: jar includes itself (6.7GB thin jar) |
| 12 | All above fixed but auto-clean wipes artifacts | `jar.clj` calls `clean/clean` before prep-tasks (`:auto-clean` defaults true) |

Attempt 12 was the final piece — adding `:auto-clean false` completed the fix. (Originally in `:uberjar-package`, now in `:uberjar` directly so bare-metal `lein build` also inherits it.)

## Related Files

- `docker/Dockerfile` — three-step build with timeout
- `project.clj` — `:uberjar`/`:uberjar-package` profiles, `build` alias, no lein-cljsbuild, fig:prod alias
- `prod.cljs.edn` — figwheel-main production CLJS config
- `.github/workflows/docker-integration.yml` — `DOCKER_BUILDKIT=0` + direct docker build
- `docs/LEIN-UBERJAR-HANG.md` — human-facing documentation
