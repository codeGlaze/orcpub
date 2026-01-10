# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **figwheel-main 0.2.20** - Modern hot-reload development tooling (replaces deprecated lein-figwheel)
- **Datomic Pro 1.0.7482** - Java 21 compatible database (replaces Datomic Free)
- `dev.cljs.edn` - figwheel-main build configuration
- `scripts/start-datomic-auto.sh` - Automated Datomic transactor management

### Changed

#### Dependencies
| Package | Old Version | New Version |
|---------|-------------|-------------|
| org.clojure/clojure | 1.10.0 | 1.12.4 |
| org.clojure/clojurescript | 1.10.439 | 1.12.134 |
| org.clojure/core.async | 0.4.490 | 1.8.741 |
| io.pedestal/* | 0.5.1 | 0.7.0 |
| reagent | 0.7.0 | 1.2.0 |
| re-frame | 0.10.9 | 1.4.4 |
| buddy/buddy-auth | 1.x | 3.0.323 |
| buddy/buddy-hashers | 1.x | 2.0.167 |
| org.apache.pdfbox/pdfbox | 2.1.0-SNAPSHOT | 3.0.6 |
| clj-http | 3.9.0 | 3.13.1 |
| org.clojure/data.json | 0.2.6 | 2.5.0 |
| hiccup | 1.0.5 | 2.0.0 |
| com.stuartsierra/component | 0.3.2 | 1.2.0 |
| garden | 1.3.5 | 1.3.10 |
| bidi | 2.1.3 | 2.1.6 |
| org.clojure/test.check | 0.9.0 | 1.1.1 |
| org.clojure/core.match | 0.3.0-alpha5 | 1.1.1 |
| funcool/cuerdas | 2.0.5 | 2026.415 |
| com.cognitect/transit-cljs | 0.8.256 | 0.8.280 |
| com.fasterxml.jackson.core/* | 2.11.1 | 2.15.2 |
| com.google.guava/guava | 21.0 | 32.1.2-jre |
| datomock | 0.2.0 | 0.2.2-favila1 |

#### Infrastructure
- **Jetty** upgraded from 9.x (EOL) to 11.x LTS via Pedestal 0.7.0
- **Java runtime** now targets Java 21 (was Java 8/11)
- **Development port** changed from 3449 to 9500 (figwheel-main default)
- **Datomic URL scheme** changed from `datomic:free://` to `datomic:dev://`

#### Code Changes
- **Pedestal interceptors** must now be wrapped with `interceptor/interceptor` (Pedestal 0.7 requirement)
  - Updated: `check-auth`, `parse-id`, `check-party-owner` in `routes.clj`
  - Updated: `db-interceptor`, `etag-interceptor` in `pedestal.clj`
- **Browser detection** rewritten in `user_agent.cljs` to use native `navigator.userAgent` instead of deprecated Google Closure Library APIs
- **PDFBox API** migrated from 2.x to 3.x (`PDDocument.load()` → `Loader.loadPDF()`)
- **Date/time** server-side code migrated from `clj-time` to `clojure.java-time`

### Deprecated
- `lein figwheel` still works but now aliases to `lein fig:dev` (figwheel-main)

### Removed
- **Datomic Free 0.9.5697** - Incompatible with Java 21 (SSL handshake failures)
- **lein-figwheel (figwheel-sidecar)** - Replaced by figwheel-main
- **clj-time** - Replaced by clojure.java-time (server-side)

### Fixed
- SSL/TLS compatibility issues with Java 21 (via Datomic Pro migration)
- Google Closure Library API deprecation warnings in ClojureScript
- PDFBox font loading warnings (now uses fallback fonts gracefully)

### Security
- **jackson-databind** upgraded to 2.15.2 (fixes multiple CVEs)
- **Guava** upgraded to 32.1.2-jre (fixes security vulnerabilities)
- **Jetty** upgraded to 11.x LTS (security patches, HTTP/2 support)

---

## [0.1.0] - Previous State

This represents the state of the project before the January 2026 modernization effort.

### Stack
- Clojure 1.10.0 / ClojureScript 1.10.439
- Datomic Free 0.9.5697
- Pedestal 0.5.1 (Jetty 9.x)
- Reagent 0.7.0 / re-frame 0.10.9
- React 16.6.0 (via cljsjs)
- lein-figwheel for hot-reload
- Java 8/11 runtime

---

## Future Planned Changes

### React 18 Migration (Breaking)
- Upgrade cljsjs/react 16.6.0 → 18.x
- Upgrade cljsjs/react-dom 16.6.0 → 18.x  
- Upgrade Reagent 1.2.0 → 2.0.x (required for React 18)
- Migrate from `ReactDOM.render` to `createRoot` API

### Build Tooling Improvements
- Evaluate Shadow-CLJS for npm React integration
- Consider replacing cljsjs packages with npm dependencies

### Pedestal Upgrade (Blocked)
- Pedestal 0.7.1+ uses Jetty 12, incompatible with figwheel-main's Ring adapter
- Upgrade blocked until figwheel-main adds Jetty 12 support
