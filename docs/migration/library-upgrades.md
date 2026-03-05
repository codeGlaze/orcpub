# Library Upgrades

All dependency changes in `project.clj`, grouped by category.

## Clojure / ClojureScript Core

| Library | Before | After |
|---------|--------|-------|
| `org.clojure/clojure` | 1.9.0 | 1.12.4 |
| `org.clojure/clojurescript` | 1.9.x | 1.12.134 |
| `org.clojure/core.async` | 0.3.x | 1.8.741 |
| `org.clojure/core.match` | old | 1.1.1 |
| `org.clojure/test.check` | old | 1.1.1 |
| `org.clojure/data.json` | old | 2.5.0 |

## Date/Time: clj-time → java-time

**clj-time** wraps Joda-Time, which is end-of-life. Replaced with **java-time** which wraps `java.time` (built into Java 8+).

```clojure
;; Before
[clj-time "0.x"]

;; After
[clojure.java-time "1.4.2"]
```

The require alias is preserved to minimize churn:

```clojure
;; Same alias, different library
[clj-time.core :as t]  →  [java-time.api :as t]
```

**Files affected**: `src/clj/orcpub/pedestal.clj` (`parse-date` rewritten)

**Frontend**: `com.andrewmcveigh/cljs-time 0.5.2` is unchanged (it wraps goog.date, not Joda).

## PDF Generation

```clojure
;; Before
[org.apache.pdfbox/pdfbox "2.x"]

;; After
[org.apache.pdfbox/pdfbox "3.0.6"]
```

PDFBox 3 has API changes in font handling and document loading. The vendored JAR is in `lib/org/apache/pdfbox/` and resolves via the `file:lib` repository.

## Authentication

```clojure
;; Before
[buddy/buddy-auth "1.x"]
[buddy/buddy-hashers "1.x"]

;; After
[buddy/buddy-auth "3.0.323"]
[buddy/buddy-hashers "2.0.167"]
```

Buddy 3 is compatible with Java 21. The JWS token handling in `orcpub.routes` is unchanged.

## Web Framework

See [pedestal-0.7.md](pedestal-0.7.md) for details.

```clojure
;; Before
[io.pedestal/pedestal.service "0.5.1"]
[io.pedestal/pedestal.route "0.5.1"]
[io.pedestal/pedestal.jetty "0.5.1"]

;; After (pinned — see pedestal-0.7.md for why)
[io.pedestal/pedestal.service "0.7.0"]
[io.pedestal/pedestal.route "0.7.0"]
[io.pedestal/pedestal.jetty "0.7.0"]
[io.pedestal/pedestal.error "0.7.0"]   ;; new
[commons-io/commons-io "2.15.1"]       ;; required by Pedestal 0.7 ring-middlewares
```

## Component / Infrastructure

| Library | Before | After |
|---------|--------|-------|
| `com.stuartsierra/component` | old | 1.2.0 |
| `com.google.guava/guava` | old | 32.1.2-jre |
| `com.fasterxml.jackson.core/*` | old | 2.15.2 |
| `environ` | 1.1.0 | 1.2.0 |

Jackson and Guava are pinned to resolve transitive dependency conflicts and CVEs.

## Other

| Library | Before | After | Notes |
|---------|--------|-------|-------|
| `funcool/cuerdas` | old | 2026.415 | String utilities (Clojars versioning) |
| `clj-http` | old | 3.13.1 | HTTP client (JVM) |
| `cljs-http` | 0.1.45 | 0.1.49 | HTTP client (CLJS) — fixes `no.en.core` shadowing (see below) |
| `hiccup` | 1.x | 2.0.0 | HTML rendering |
| `garden` → `com.lambdaisland/garden` | old | 1.9.606 | CSS-in-Clojure (see below) |
| `bidi` | old | 2.1.6 | Routing |
| `com.draines/postal` | old | 2.0.5 | Email |
| `javax.servlet/javax.servlet-api` | — | 4.0.1 | Required on Java 9+ |

## Garden: noprompt → lambdaisland Fork

```clojure
;; Before
[garden "1.3.10"]

;; After
[com.lambdaisland/garden "1.9.606"]
```

The original `noprompt/garden` is unmaintained. On Clojure 1.12, it causes a `clojure.core/abs` shadowing warning because it only excludes `complement` from core, not `abs` (added in Clojure 1.11).

The [lambdaisland/garden](https://github.com/lambdaisland/garden) fork fixes this by adding `abs` to `:refer-clojure :exclude`. It's a **drop-in replacement** — same namespaces, same API, same `lein-garden` plugin compatibility.

## Test Dependencies

| Library | Before | After | Notes |
|---------|--------|-------|-------|
| `org.clojars.favila/datomock` | — | 0.2.2-favila1 | Replaces `vvvvalvalval/datomock` (Datomic Pro compatible) |

## Dev Dependencies

| Library | Before | After |
|---------|--------|-------|
| `com.bhauman/figwheel-main` | — | 0.2.20 |
| `com.bhauman/rebel-readline-cljs` | — | 0.1.4 |
| `binaryage/devtools` | old | 1.0.7 |
| `cider/piggieback` | old | 0.5.3 |
| `day8.re-frame/re-frame-10x` | old | 1.11.0 |

## Clojure 1.11+ Core Shadowing Pattern

Clojure 1.11 added `abs`, `parse-long`, `parse-double`, and `parse-integer` to `clojure.core`. Libraries written before 1.11 that define their own versions of these functions produce shadowing warnings on modern Clojure/ClojureScript.

This affected two dependencies in this project:

| Library | Shadowed functions | Fix |
|---------|-------------------|-----|
| `garden` (noprompt) | `abs` | Switched to `com.lambdaisland/garden` fork |
| `noencore` (via `cljs-http`) | `parse-long`, `parse-double` | Upgraded `cljs-http` 0.1.45 → 0.1.49 |

**If you see similar warnings from other libraries in the future**, the fix is usually: check for a newer version that adds the functions to `:refer-clojure :exclude`, or switch to a maintained fork.

## Build Plugins

| Plugin | Before | After |
|--------|--------|-------|
| `lein-cljsbuild` | 1.1.7 | 1.1.7 (unchanged) |
| `lein-garden` | 0.3.0 | 0.3.0 (unchanged) |
| `clj-kondo` (via lint profile) | — | 2024.05.22 |
