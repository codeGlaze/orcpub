# Upgrade Plan — Dependencies & Tooling 🔧

## Overview

This document describes a proposed, incremental plan to modernize the project's runtime, libraries, and build tooling. The goal is to bring server- and client-side dependencies up to supported modern versions, reduce security risk, and improve developer experience and CI reliability.

---

## 🚨 Major Changes (January 2026)

This section documents the significant architectural and tooling changes made during the upgrade from the legacy stack.

### Summary of Breaking Changes

| Component | Before | After | Impact |
|-----------|--------|-------|--------|
| **Database** | Datomic Free 0.9.5697 | Datomic Pro 1.0.7482 | Different installation, Java 21 support |
| **Web Framework** | Pedestal 0.5.x | Pedestal 0.7.0 | Interceptor wrapping required |
| **Hot Reload** | lein-figwheel (port 3449) | figwheel-main 0.2.20 (port 9500) | Different dev workflow |
| **Clojure** | 1.11.4 | 1.12.4 | Minor API additions |
| **ClojureScript** | 1.11.132 | 1.12.134 | Google Closure Library changes |

---

### 1. Datomic Free → Datomic Pro

**Why:** Short answer: Datomic Pro is basically the new Datomic Free (which is no longer supported).
Longer answer: Datomic Free 0.9.5697 does NOT work on Java 21 due to SSL/TLS incompatibility in the ActiveMQ Artemis layer. Datomic Pro is now free under Apache 2.0 and supports Java 11/17/21.

**What Changed:**
- Dependency changed from `com.datomic/datomic-free` to `com.datomic/peer`
- Transactor uses `dev-transactor-template.properties` instead of `free-transactor-template.properties`
- Datomic Pro requires installation via the post-create script (downloads from an aws link via datomic's official page)

**Developer Impact:**
- The `DATOMIC_URL` environment variable format changed:
  - Before: `datomic:free://localhost:4334/orcpub`
  - After: `datomic:dev://localhost:4334/orcpub`
- Transactor must be started via `scripts/start-datomic-auto.sh` or the dev menu
- First-time setup runs `.devcontainer/post-create.sh` which downloads and installs Datomic Pro

**See:** [docs/DATOMIC_JAVA21_TEST_RESULTS.md](docs/DATOMIC_JAVA21_TEST_RESULTS.md) for compatibility test details.

---

### 2. Pedestal 0.5.x → 0.7.0 (Interceptor Changes)

**Why:** Security updates and Jetty 11 LTS support. Pedestal 0.7.1+ uses Jetty 12 which is incompatible with figwheel-main, so we're pinned to 0.7.0.

**What Changed:**
Pedestal 0.7 has stricter interceptor validation. Plain maps are no longer auto-coerced to interceptors. All custom interceptors must be explicitly wrapped with `interceptor/interceptor`.

**Files Modified:**
- [src/clj/orcpub/routes.clj](src/clj/orcpub/routes.clj) - Added `[io.pedestal.interceptor :as interceptor]` require and wrapped:
  - `check-auth`
  - `parse-id`
  - `check-party-owner`

- [src/clj/orcpub/pedestal.clj](src/clj/orcpub/pedestal.clj) - Wrapped:
  - `db-interceptor`
  - `etag-interceptor`

**How to Create New Interceptors:**
```clojure
;; BEFORE (Pedestal 0.5.x) - Plain maps worked
(def my-interceptor
  {:name ::my-interceptor
   :enter (fn [ctx] ...)
   :leave (fn [ctx] ...)})

;; AFTER (Pedestal 0.7.x) - Must wrap with interceptor/interceptor
(def my-interceptor
  (interceptor/interceptor
    {:name ::my-interceptor
     :enter (fn [ctx] ...)
     :leave (fn [ctx] ...)}))
```

**Error if Not Wrapped:**
```
AssertionError: Assert failed: (every? interceptor/interceptor? interceptors)
```

---

### 3. lein-figwheel → figwheel-main 0.2.20

**Why:** lein-figwheel (figwheel-sidecar) is deprecated. figwheel-main is the actively maintained replacement with better error messages and modern tooling.

**What Changed:**
| Aspect | Before (lein-figwheel) | After (figwheel-main) |
|--------|------------------------|----------------------|
| Port | 3449 | **9500** |
| Start command | `lein figwheel` | `lein fig:dev` (or `lein figwheel` - aliased) |
| Config file | In `project.clj` `:figwheel` | `dev.cljs.edn` + `project.clj` |
| REPL | Via browser | Via browser + improved REPL |

**Developer Workflow:**
```bash
# Start all services (recommended order):
1. Start Datomic:    Run task "Dev: Start Local Datomic" or ./scripts/start-datomic-auto.sh
2. Start Server:     Run task "Dev: Start Server" (port 8890)
3. Start Figwheel:   Run task "Dev: Start Figwheel" (port 9500)

# Access the app:
- Backend API:       http://localhost:8890
- Frontend (dev):    http://localhost:9500 (with hot-reload)
```

**devcontainer.json Port Forwarding:**
Updated from `[8890, 3449, 7888]` to `[8890, 9500, 4334]`

---

### 4. ClojureScript Browser Detection Rewrite

**Why:** ClojureScript 1.11+ updated the Google Closure Library, removing/changing the `goog.labs.userAgent.browser` API.

**File Changed:** [src/cljs/orcpub/user_agent.cljs](src/cljs/orcpub/user_agent.cljs)

**What Changed:**
```clojure
;; BEFORE - Used deprecated Closure Library APIs
(ns orcpub.user-agent
  (:require [goog.labs.userAgent.browser :as browser]
            [goog.labs.userAgent.device :as device]
            [goog.labs.userAgent.platform :as platform]))

(defn chrome? [] (browser/isChrome))
(defn firefox? [] (browser/isFirefox))
;; etc.

;; AFTER - Uses native navigator.userAgent
(ns orcpub.user-agent
  (:require [clojure.string :as str]))

(defn- user-agent-string []
  (when (exists? js/navigator)
    (.-userAgent js/navigator)))

(defn chrome? []
  (when-let [ua (user-agent-string)]
    (and (str/includes? ua "Chrome")
         (not (str/includes? ua "Edg")))))
;; etc.
```

---

### 5. Test Library: datomock Upgrade

**Why:** The original `vvvvalvalval/datomock 0.2.0` doesn't support Datomic Pro 1.0.7482's new `transact` method signature.

**What Changed:**
```clojure
;; BEFORE
[vvvvalvalval/datomock "0.2.0"]

;; AFTER - Fork with Datomic Pro compatibility
[org.clojars.favila/datomock "0.2.2-favila1"]
```

**No code changes required** - the namespace (`datomock.core`) is identical.

---

### 6. Dependency Version Summary (Current State)

| Dependency | Old Version | New Version |
|------------|-------------|-------------|
| org.clojure/clojure | 1.11.4 | **1.12.4** |
| org.clojure/clojurescript | 1.11.132 | **1.12.134** |
| re-frame | 1.3.0 | **1.4.4** |
| clj-http | 3.12.3 | **3.13.1** |
| com.stuartsierra/component | 1.1.0 | **1.2.0** |
| com.cognitect/transit-cljs | 0.8.256 | **0.8.280** |
| Pedestal (all) | 0.7.2 | **0.7.0** (downgraded for Jetty 11) |
| figwheel-main | N/A | **0.2.20** (new) |
| datomock | 0.2.0 | **0.2.2-favila1** (fork) |
| Datomic | Free 0.9.5697 | **Pro 1.0.7482** |

---

### 7. Known Constraints

| Constraint | Reason | Workaround |
|------------|--------|------------|
| **Pedestal pinned to 0.7.0** | Pedestal 0.7.1+ uses Jetty 12, incompatible with figwheel-main's Ring adapter (Jetty 11) | Wait for figwheel-main Jetty 12 support |
| **React pinned to 16.x** | Reagent 1.2.0 requires React 16; upgrading to React 18 requires Reagent 2.0 | Future: Coordinate Reagent 2.0 + React 18 upgrade |
| **cljsjs React packages** | Using cljsjs/react instead of npm | Future: Consider Shadow-CLJS for npm React |

---

### 8. Development Commands Quick Reference

```bash
# Start development environment (in order):
./scripts/start-datomic-auto.sh    # or VS Code task "Dev: Start Local Datomic"
# Then in REPL: (start-server)      # or VS Code task "Dev: Start Server"
lein fig:dev                        # or VS Code task "Dev: Start Figwheel"

# Run tests
lein test                           # All server-side tests

# Compile ClojureScript (without hot-reload)
lein cljsbuild once dev

# Run linter
lein lint

# Access the app
# Backend:  http://localhost:8890
# Frontend: http://localhost:9500 (dev with hot-reload)
```

---

## ✅ Completed Upgrades

### Phase 1: Security-Critical Updates (Complete)
| Dependency | Old Version | New Version | Status |
|------------|-------------|-------------|--------|
| jackson-databind | 2.11.1 | 2.15.2 | ✅ Done |
| jackson-core | 2.11.1 | 2.15.2 | ✅ Done |
| jackson-annotations | 2.11.1 | 2.15.2 | ✅ Done |
| guava | 21.0 | 32.1.2-jre | ✅ Done |

### Phase 2: Core Clojure Upgrade (Complete)
| Dependency | Old Version | New Version | Status |
|------------|-------------|-------------|--------|
| org.clojure/clojure | 1.10.0 | **1.12.4** | ✅ Done |
| org.clojure/clojurescript | 1.10.439 | **1.12.134** | ✅ Done |
| org.clojure/core.async | 0.4.490 | 1.8.741 | ✅ Done |

### Phase 3: Pedestal Stack Upgrade (Complete)
| Dependency | Old Version | New Version | Status |
|------------|-------------|-------------|--------|
| pedestal.service | 0.5.1 | **0.7.0** | ✅ Done |
| pedestal.route | 0.5.1 | **0.7.0** | ✅ Done |
| pedestal.jetty | 0.5.1 | **0.7.0** | ✅ Done (Jetty 9→11 LTS) |
| pedestal.error | N/A | **0.7.0** | ✅ Added |

**Note:** Pedestal pinned to 0.7.0 (not 0.7.2) due to Jetty 12 incompatibility with figwheel-main.

### Phase 4: Frontend Upgrades (Complete)
| Dependency | Old Version | New Version | Status |
|------------|-------------|-------------|--------|
| reagent | 0.7.0 | 1.2.0 | ✅ Done |
| re-frame | 0.10.9 | **1.4.4** | ✅ Done |
| re-frame-10x | 0.3.7 | 1.11.0 | ✅ Done |
| devtools | 0.9.10 | 1.0.7 | ✅ Done |
| figwheel-main | N/A | **0.2.20** | ✅ Added (replaces lein-figwheel) |

### Phase 5: Additional Library Upgrades (Complete)
| Dependency | Old Version | New Version | Status |
|------------|-------------|-------------|--------|
| PDFBox | 2.1.0-SNAPSHOT | 3.0.6 | ✅ Done (API migrated) |
| buddy-auth | 1.x | 3.0.323 | ✅ Done |
| buddy-hashers | 1.x | 2.0.167 | ✅ Done |
| clj-http | 3.9.0 | **3.13.1** | ✅ Done |
| data.json | 0.2.6 | 2.5.0 | ✅ Done |
| hiccup | 1.0.5 | 2.0.0 | ✅ Done |
| postal | 2.0.2 | 2.0.5 | ✅ Done |
| environ | 1.1.0 | 1.2.0 | ✅ Done |
| component | 0.3.2 | **1.2.0** | ✅ Done |
| garden | 1.3.5 | 1.3.10 | ✅ Done |
| bidi | 2.1.3 | 2.1.6 | ✅ Done |
| test.check | 0.9.0 | 1.1.1 | ✅ Done |
| core.match | 0.3.0-alpha5 | 1.1.1 | ✅ Done (stable) |
| cuerdas | 2.0.5 | 2026.415 | ✅ Done |
| clojure.java-time | N/A | 1.4.2 | ✅ Added (replaces clj-time) |
| transit-cljs | 0.8.256 | **0.8.280** | ✅ Done |

### Phase 6: Database Migration (Complete)
| Dependency | Old Version | New Version | Status |
|------------|-------------|-------------|--------|
| Datomic Free | 0.9.5697 | N/A | ❌ Removed (Java 21 incompatible) |
| Datomic Pro (peer) | N/A | **1.0.7482** | ✅ Added |
| datomock | 0.2.0 | **0.2.2-favila1** | ✅ Done (Pro-compatible fork) |

**Note**: Jetty upgraded from 9.x (EOL) to 11.x LTS for security fixes.

---

## Current observations (January 2026)
- `project.clj` now uses **Clojure 1.12.4** and **ClojureScript 1.12.134**.
- Frontend uses **Reagent 1.2.0**, **re-frame 1.4.4** and `cljsjs`-packaged **React 16.6.0**.
- Dev tooling uses **figwheel-main 0.2.20** (replaced lein-figwheel).
- Server libraries: **Pedestal 0.7.0**, **Buddy 3.x**, **PDFBox 3.0.6**, **Datomic Pro 1.0.7482**.
- Jackson 2.15.2 and Guava 32.1.2-jre (secure versions).
- All tests pass (62 tests, 199 assertions).
- Running on **Java 21** with full Datomic Pro compatibility.

---

## Goals (measurable)
- Upgrade to a supported JDK (target: **JDK 17** or **JDK 21** — choose after audit).
- Upgrade Clojure to the latest stable 1.10+/1.11+ line as appropriate.
- Upgrade ClojureScript, Reagent, and re-frame; replace `cljsjs` React with npm-managed React.
- Evaluate and migrate to a modern CLJS build chain (Shadow-CLJS or updated cljsbuild + Figwheel Main).
- Update Pedestal and server libs, and bump Guava / Jackson to secure versions.
- Keep CI green across the upgrade; add matrix testing for JDK and Node versions.

---

## Scope & Constraints
- Work incrementally with small PRs; each PR targets a single library or related set of small breaking changes.
- Maintain backwards compatibility for export formats where possible (see repo docs regarding exports).
- When schema changes are required (e.g., DB migration), provide a migration script & tests.

---

## High-level roadmap & milestones
1. Audit (this week)
   - Run `lein deps :tree`, `lein test`, `lein lint`, `npm outdated`, `npx npm-check-updates --packageFile package.json` and capture failing tests.
   - Produce a precise list of direct deps to upgrade with suggested target versions and risk notes.
2. Branching & PR strategy
   - Create branch: `upgrade/deps-YYYY-MM-DD` and open draft PR.
   - Make small, atomic PRs: `upgrade/clojure-1.11`, `upgrade/pedestal-0.x`, `upgrade/cljs-...` etc.
3. JDK & build tooling
   - Set and test target JDK (17 or 21). Update Dockerfiles and CI configs.
4. Server-side upgrades (incremental)
   - Jackson, Guava, Pedestal, Buddy, Datomic (client or compat check).
5. Client-side upgrades (incremental)
   - ClojureScript, Reagent, re-frame; replace `cljsjs` React with npm React; consider Shadow-CLJS migration.
6. CI and tooling updates
   - Linters, clj-kondo, cljfmt, GH Actions/CI matrix adjustments.
7. Tests and deprecation fixes
   - Make tests pass; fix deprecations; add tests where missing.
8. Release & cleanup
   - Merge mainline PRs, tag release, update README & docs.

---

## Detailed tasks (immediate)
- [x] Create this `UPGRADE_PLAN.md` (this file).
- [x] Run dependency audit: `lein deps :tree` and `npm outdated`. ✅ *Completed — output in `audit/deps-tree.txt`*
- [x] Run test suite: `lein test` and `npm test` (if applicable). ✅ *54 tests, 157 assertions pass — see `audit/test-results.txt`*
- [x] Create branch `upgrade/deps-<date>` and open a draft PR. ✅ *Branch: `upgrade/security-jackson-guava`*
- [x] Prioritize critical security updates (e.g., Jackson, Guava) for immediate PRs. ✅ *Jackson 2.15.2, Guava 32.1.2-jre applied*

## Next phase (in progress)
- [x] Upgrade Clojure 1.10.0 → 1.12.4 ✅
- [x] Upgrade ClojureScript 1.10.439 → 1.12.134 ✅
- [x] Upgrade core.async 0.4.490 → 1.8.741 ✅
- [x] Upgrade Pedestal 0.5.1 → 0.7.0 ✅ (pinned due to Jetty 12 incompatibility)
- [x] Upgrade Buddy libs to 3.x ✅
- [x] Upgrade Reagent 0.7.0 → 1.2.0 and re-frame 0.10.9 → 1.4.4 ✅
- [x] Migrate clj-time 0.15.0 → clojure.java-time 1.4.2 ✅ *Server-side only*
- [x] Upgrade PDFBox 2.1.0-SNAPSHOT → 3.0.6 ✅ *API migrated, warnings fixed*
- [x] Migrate Datomic Free → Datomic Pro 1.0.7482 ✅
- [x] Migrate lein-figwheel → figwheel-main 0.2.20 ✅
- [ ] **React 18 Migration** - Upgrade React 16 → 18 with Reagent 2.0 (breaking changes)
- [ ] Evaluate Shadow-CLJS migration
- [ ] Consider replacing cljsjs React with npm React

---

## ⚠️ Known Build Warnings (Third-Party, Unfixable)

After upgrading to Clojure 1.11.4, some third-party libraries produce warnings that **cannot be fixed from our code**. These are documented here for reference.

### 1. `garden.color/abs` shadows `clojure.core/abs`

**Warning message:**
```
WARNING: abs already refers to: #'clojure.core/abs in namespace: garden.color, being replaced by: #'garden.color/abs
```

**Cause:** The `garden` CSS library (v1.3.10, last updated 2019) defines its own `abs` function internally. Clojure 1.11 added `clojure.core/abs`, causing a var shadowing conflict.

**Impact:** None — the warning is cosmetic. Garden's internal `abs` function works correctly for its own use.

**Why we can't fix it:** The warning comes from inside `garden.color` namespace, not our code. We cannot add `:refer-clojure :exclude [abs]` to a third-party library's source.

**Quick-fix option (if desired):** Fork garden and add one line to `garden/color.cljc`:
```clojure
(ns garden.color
  (:refer-clojure :exclude [abs])  ;; <-- add this line
  ...)
```
Then reference the fork in `project.clj` via a local path or git coordinate.

**Decision:** Live with the warning. Garden is stable and widely used; forking adds maintenance burden.

---

### 2. `datomic.common/requiring-resolve` shadows `clojure.core/requiring-resolve`

**Warning message:**
```
WARNING: requiring-resolve already refers to: #'clojure.core/requiring-resolve in namespace: datomic.common, being replaced by: #'datomic.common/requiring-resolve
```

**Cause:** Datomic Free 0.9.5697 (released ~2018) defined its own `requiring-resolve` before Clojure 1.10 added `clojure.core/requiring-resolve`.

**Impact:** None — Datomic works correctly.

**Why we can't fix it:** Datomic Free is abandoned (no longer maintained by Cognitect). There will be no fix.

**Decision:** Accept the warning. Migration to Datomic Pro is required for JDK 21 support (see test results below).

---

### 3. Datomic Free + Java 21 Compatibility Test Results

**Date:** January 6, 2026  
**Test Environment:** GitHub Codespace, Alpine Linux, OpenJDK 21.0.9

#### Test Summary

| Component | Java 21 Status | Notes |
|-----------|----------------|-------|
| **Transactor startup** | ✅ **Works** | Transactor launches successfully on Java 21 |
| **Peer library loading** | ✅ **Works** | Datomic peer library loads without errors |
| **Unit tests (mocked)** | ✅ **Pass** | All 61 tests pass (uses `datomock`, not real transactor) |
| **Peer → Transactor connection** | ❌ **FAILS** | SSL handshake timeout in ActiveMQ Artemis layer |

#### Detailed Test Results

**Test 1: Transactor Startup**
```bash
# Transactor started successfully
bin/transactor config/samples/free-transactor-template.properties
# Output: "System started datomic:free://localhost:4334/<DB-NAME>"
```

**Test 2: Peer Library Connection**
```clojure
(require '[datomic.api :as d])
(d/create-database "datomic:free://127.0.0.1:4334/test")
```

**Result:** Connection fails with:
```
javax.net.ssl.SSLException: handshake timed out
ActiveMQNotConnectedException: Cannot connect to server(s)
```

#### Root Cause

Java 21 enforces stricter SSL/TLS defaults that are incompatible with Datomic Free's older SSL implementation. The ActiveMQ Artemis messaging layer (used for peer-transactor communication) cannot complete the SSL handshake.

#### Conclusion

**Datomic Free 0.9.5697 does NOT fully work on Java 21.** While the transactor starts and the peer library loads, actual peer-to-transactor connections fail due to SSL/TLS incompatibility.

**Migration to Datomic Pro is required** to use Java 21. Datomic Pro is now free under Apache 2.0 license and supports Java 11, 17, and 21.

**See:** [`docs/DATOMIC_JAVA21_TEST_RESULTS.md`](docs/DATOMIC_JAVA21_TEST_RESULTS.md) for complete test details.

---

### 3. PDFBox font fallback warnings

**Warning message:**
```
WARN org.apache.pdfbox.pdmodel.font.PDType1Font - Using fallback font ...
```

**Cause:** The Docker container/CI environment lacks Helvetica fonts. PDFBox falls back to available system fonts.

**Impact:** PDFs may use a slightly different font if Helvetica is not installed. In production with fonts installed, this won't appear.

**Fix (if needed):** Install `fonts-liberation` or `fonts-freefont-ttf` in the Docker image.

---

## 🔮 Future Work: Unified Date/Time Library

**Current state:**
- Server-side ([`src/clj`](src/clj)): Uses `clojure.java-time` 1.4.2
- Client-side ([`src/cljs`](src/cljs), [`web/cljs`](web/cljs)): Uses `cljs-time` 0.5.2

**Problem:** `cljs-time` is stale (last updated 2019) and won't receive updates.

**Future consideration:** Evaluate migrating both server and client to a unified cross-platform library:
- **`cljc.java-time`** - Thin wrapper, works in `.cljc` files, uses js-joda on CLJS (~40KB bundle increase)
- **`tick` (juxt)** - Higher-level API, also cross-platform, uses cljc.java-time internally

**Benefits of unification:**
- Single API for date/time logic
- Shared date utilities can live in `src/cljc`
- Active maintenance and security updates for both platforms

**Trade-off:** CLJS bundle size increases due to js-joda dependency.

**Decision:** Keep current setup for now. Revisit when `cljs-time` becomes a blocker or when shared date logic is needed in `src/cljc`.

---

## 🧪 Validation Commands

After each upgrade phase, run these commands to validate:

```bash
# Clean previous build artifacts
lein clean

# Run SERVER-SIDE tests only (Clojure JVM code)
# Does NOT test ClojureScript/Reagent/re-frame code!
lein test

# Run linter (checks both CLJ and CLJS syntax)
lein lint

# ⚠️ CRITICAL: Compile ClojureScript to catch frontend issues
# This is REQUIRED after any Reagent/re-frame/CLJS dependency change!
lein cljsbuild once dev

# Full frontend validation with live reload (figwheel-main)
lein fig:dev
# Or the alias:
lein figwheel
```

### What each command validates:

| Command | Scope | Catches |
|---------|-------|--------|
| `lein test` | Server-side Clojure only | Backend logic, routes, DB, PDF |
| `lein lint` | CLJ + CLJS syntax | Typos, unused vars, style |
| `lein cljsbuild once dev` | **ClojureScript compilation** | Reagent/re-frame API changes, missing namespaces, CLJS errors |
| `lein fig:dev` | Full frontend runtime | Runtime errors, React rendering issues |

---

## Branching & PR rules
- One major upgrade per PR where practical.
- Include migration notes and failing test artifacts in PR description.
- Add `:breaking` label and a short compatibility note if public API or exports change.

---

## Testing & CI
- Add a CI matrix for JDK versions (17, 21) and Node versions used for CLJS builds.
- Run unit tests and an integration smoke test (start server, call a handful of endpoints).
- Add vulnerability checks (`mvn dependency:check` or `snyk` / `npm audit` as appropriate).

---

## Risk & Rollback
- Keep PRs small so rollbacks are easy.
- If DB/schema change is required, add a reversible migration script and one-liner to revert.
- Keep release notes for breaking changes.

---

## Notes / Helpful commands
- Dependency tree: `lein deps :tree`
- Run server tests: `lein test`
- Run linter: `lein lint` (alias provided)
- Check CLJS build locally: `lein figwheel` (dev) or `lein prod-build`
- To convert to Shadow-CLJS, consider: `npm init -y; npm i --save-dev shadow-cljs react react-dom` and follow incremental porting guide.

---

## Audit results (static)

**Note:** I attempted to run `lein deps :tree`, `lein test`, and other live commands, but the environment here prevented running those commands. Below are *static audit* findings derived from `project.clj` and `package.json` (direct dependencies) and actionable recommendations. If you'd like me to run the live commands in CI or your dev container, say “allow live audit” and I will run them and append the actual outputs.

| Dependency | Current version | Recommendation | Risk / Notes |
|---|---:|---|---|
| org.clojure/clojure | 1.10.0 | Upgrade to Clojure **1.11.x** (or latest 1.10/1.11 stable) | Minor-to-moderate; run tests and fix any deprecations.
| org.clojure/clojurescript | 1.10.439 | Upgrade to **1.10.x (latest)** or **1.11.x**; test CLJS build | CLJS compiler changes may require small code tweaks.
| reagent | 0.7.0 | Move to **Reagent 1.x** (modern React interop) | Breaking: Reagent 1 uses modern React APIs; replace `cljsjs` React with npm React.
| re-frame | 0.10.9 | Upgrade to latest 1.x line incrementally | Review breaking API changes in re-frame change logs.
| cljsjs/react, cljsjs/react-dom | 16.6.0 | Replace with npm-managed **react/react-dom (React 18)** and use Shadow-CLJS or cljsdeps integration | Will simplify dependency management and enable upgrades.
| io.pedestal/pedestal.* | 0.5.1 | Upgrade to latest 0.5.x (or newer) | API changes possible; run integration tests.
| com.fasterxml.jackson.core/jackson-databind | 2.11.1 | Upgrade to **2.14+ / 2.15+** to address CVEs | Security priority — do early.
| com.google.guava/guava | 21.0 | Upgrade to **30.x / 31.x+** | Security and bug fixes; ensure binary compatibility.
| buddy/buddy-auth, buddy-hashers | 1.x | Upgrade to **2.x** if available; check auth API changes | Test authentication behavior after upgrade.
| clj-time | 0.15.0 | Migrate to `java-time` / `clojure.java-time` or `tick` | Joda Time is legacy; migration will touch date/time code.
| com.datomic/datomic-free | 0.9.5697 | Consider Datomic compatibility with JDK 17/21; plan migration if needed | Datomic Free is old; evaluate maintaining vs migrating to other DBs.
| lein-figwheel, lein-cljsbuild | older versions | Consider moving to **Figwheel Main** or **Shadow-CLJS** for modern workflows | Shadow-CLJS enables easy npm interop and faster dev UX.
| package.json deps (expo, react 16 alpha) | react 16.0.0-alpha.6, expo ^17 | Update RN / React/Expo to current, or remove if not used by CLJS workflow | Verify if package.json is actually used; fix outdated React version.

**Immediate priorities**
1. Upgrade Jackson and Guava (security) — small PRs that prioritize CI runs.
2. Upgrade core Clojure and Pedestal in separate PRs, run full test suite.
3. Plan frontend migration: replace `cljsjs` React with npm React, evaluate Shadow-CLJS migration.
4. Plan `clj-time` migration to `java-time` in a follow-up PR.

---

## Audit results (live — deps tree)

I inspected the `deps-tree.txt` output you provided and prioritized likely security and compatibility issues below (direct and notable transitive versions found):

- **com.fasterxml.jackson.core/jackson-databind 2.11.1** — outdated and known to have multiple CVEs in older lines; **recommend pinning to 2.15.x (or latest stable 2.15/2.16+)** and add an explicit dependency override in `project.clj` to ensure transitive libs use the safe version.

- **com.google.guava/guava 21.0** — old; **recommend upgrading to >= 30.1.x or the latest stable 31/32+** to pick up security and bug fixes. Verify binary compatibility with any code using Guava APIs.

- **org.h2database/h2 1.3.171** (transitive via Datomic) — very old; upgrade to a modern 1.4.x/2.x line if you directly depend on H2, or accept Datomic's internal use if not used directly.

- **org.eclipse.jetty 9.3.8.v20160314** (via pedestal.jetty) — old Jetty versions may lack HTTP/2 and have security fixes in newer 9.4.x or 11.x lines; test Pedestal upgrades carefully.

- **io.pedestal/pedestal.* 0.5.1** — older Pedestal; plan an incremental upgrade and run integration tests since there may be breaking changes in interceptors or HTTP pipeline behavior.

- **reagent 0.7.0**, **re-frame 0.10.9**, **cljsjs/react 16.6.0** — frontend stack is old; recommend moving React to an npm-managed React (React 18+), upgrade Reagent to 1.x and re-frame incrementally, and consider Shadow-CLJS for smoother npm interop.

- **com.datomic/datomic-free 0.9.5697** — legacy Datomic Free; verify compatibility with target JDK (17/21) and consider if migration to a supported DB or Datomic Cloud is needed in the medium term.

- Several transitive deps show dated versions (commons-logging, commons-codec, tomcat, etc.). After bumping Jackson & Guava, run `lein deps :tree` again and a CVE scanner to find remaining risky transitive versions.

---

## Concrete next steps I can take now
1. Create a small branch `upgrade/security-jackson-guava` and open a PR that:
   - Adds an explicit dependency override/pin for `com.fasterxml.jackson.core/jackson-databind` to `2.15.x` (or latest stable), and updates `com.google.guava/guava` to `31.x` or later.
   - Runs CI (the `dependency-audit` workflow will post artifacts) and fixes any immediate failures.
2. Add Dependabot configuration (`.github/dependabot.yml`) and/or a CI CVE scan job to continuously track vulnerabilities.
3. After the PR lands, re-run the dependency audit to gather updated `deps-tree` and repeat for the next-highest-risk libs (Pedestal, Reagent, etc.).

---

Would you like me to open the PR `upgrade/security-jackson-guava` now (pins: `jackson-databind` **2.15.2**, `jackson-core` **2.15.2**, `jackson-annotations` **2.15.2**, `guava` **32.1.2-jre**) and push the change, or would you rather review different target versions first? (Reply with **"open PR"** or **"show versions first"**.)

## Next steps (my plan)
1. Run the live dependency audit and tests (if you say “allow live audit”) and append concrete outputs to this document.
2. Create the upgrade branch and begin with high-priority security upgrades.
3. CI workflow added: `.github/workflows/dependency-audit.yml` was created to run audits on PRs and manually (workflow_dispatch). Artifacts (deps tree, tests, lint, npm outdated) are uploaded for review.
4. Local audit script: `scripts/run-dependency-audit.sh` added to run the audit locally and save outputs to `./audit/`. Add execute permission and run with `./scripts/run-dependency-audit.sh`.

---

If you want to proceed now, reply with:
- **"allow live audit"** — I will run `lein deps :tree`, `lein test`, `lein lint`, and `npm outdated` and append outputs and suggested version pins; or
- **"start upgrades"** — I will open branch `upgrade/deps-$(date +%F)` and prepare the first PR to bump Jackson/Guava.

---

How to run the script locally:

1. Make it executable: `chmod +x scripts/run-dependency-audit.sh`
2. Run: `./scripts/run-dependency-audit.sh`
3. Upload or attach the `audit/` folder to the PR for review (or `git add audit/ && git commit -m "chore(audit): add audit output"` if you want to include temporary outputs for discussion).


