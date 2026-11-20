# Dependency security alerts

Where the GitHub Dependabot alerts come from, and the triage of 2026-11-18.

**Never filter GitHub's "found N vulnerabilities" push message out of output.** It printed on every
push for weeks and went unreported to the owner, because an agent dropped it as noise (2026-11-18).
A new critical or high alert goes to the owner in that session's summary.

## Where the alerts come from

The `Submit Clojure dependencies` job (`continuous-integration.yml`) runs `lein pom` and submits the
resolved tree on pushes to `develop` and `main` (not on PRs, not on `integration`). Dependabot reads
that snapshot, so the alerts describe `develop` as of its last push. The manifest shows as `pom.xml`,
which exists only inside that job. `lein deps :tree` gives the same tree locally.
The pom includes the `:dev` profile's dependencies at test scope, so dev-only libraries (rebel-readline's
jline) raise alerts too, and a pin in that profile is what clears them.
Alerts: `gh api "repos/codeGlaze/orcpub/dependabot/alerts?state=open&per_page=100" --paginate`.

## Triage, 2026-11-18

83 open: 3 critical, 33 high, 44 medium, 3 low. Resolved versions are from `lein deps :tree` on
`feature/inventory-kits` (`integration` plus #89). Exposure is a reading of how the app uses each
library, not a test.

| Library (version) | Comes in through | Alerts | Exposure |
|---|---|---|---|
| Jetty 11.0.20 (`jetty-server`, `jetty-http`, `http2-common`, `jetty-security`) | `pedestal.jetty` 0.7.0 | 4 high | **Highest.** The web server behind nginx: request smuggling and a 100-Continue memory DoS. HTTP/2 and Digest auth are not used (nginx speaks HTTP/1.1 to Jetty). Fix 11.0.31. |
| Jackson 2.15.2 (`jackson-core`, `-databind`) | direct, `project.clj:63-65` | 7 high | **High.** Parses request JSON: parser DoS. Polymorphic typing bypasses need default typing, which the app does not turn on. Fix 2.18.11. |
| Bouncy Castle 1.75 (`bcprov-jdk18on`) | `buddy-hashers` | 2 critical, 2 high | Low. Password hashing uses its digests; the flaws are in GOST, certificate name constraints, ASN.1 and timing. Fix 1.85. |
| Netty 4.1.100 (`netty-handler`, `-codec`, `-codec-http`) | `com.datomic/peer` | 1 critical, 10 high | Low. Peer-to-transactor link on the private Compose network; no internet-facing Netty. Fix 4.1.137. |
| Guava 32.1.2 | direct | 1 high | Low: Java deserialization of untrusted data, which the app does not do. Fix 33.7.2. |
| jsoup 1.7.2 | `pdfkit-clj` 0.1.7 | 2 high | None found: nothing in `src/` uses `pdfkit-clj`. Removing it removes jsoup. |
| PostgreSQL JDBC 42.7.3 | direct | 1 high | Only with Datomic SQL storage; Docker uses `dev` storage. Fix 42.7.11. |
| H2 2.1.214, commons-beanutils 1.9.4 | `com.datomic/peer` | 2 high | Low: H2 console and bean access are not used. H2 **is** in production: Datomic's `dev` storage (the Docker default, `protocol=dev`) is H2, the transactor image ships its own `h2-2.1.214.jar`, and the app's copy is the client. Pinning 2.2.220 changes only the client, mixing versions on the data path. Alert #8 dismissed `not_used` (2026-11-20). |
| plexus-utils 4.0.2 | `garden` (CSS build) | 1 high | Low: archive extraction, build-time only. |
| aircompressor 0.27 | `carmine` (Redis store) | 1 high | Low: only with Redis, decompressing its own data. |

Done on #89 (`feature/inventory-kits`), one commit per library on the open branch, never a branch per
bump (owner, 2026-11-18). Netty moved with Datomic 1.0.7745 plus pins. Pedestal 0.7.0 pins Jetty 11, and
figwheel-main blocks Jetty 12 (`project.clj` comment near line 46), so Jetty moves within 11.0.x.

## The PR scan: `scripts/security-scan.sh` (2026-11-18, #89)

CI's `Library security scan` job (`continuous-integration.yml`) runs on every PR and push, and from
"Run workflow" on any branch. It fails on any critical or high (CVSS 7.0 or more, or unscored)
alert in the libraries the app ships.

- `lein with-profile uberjar classpath` resolves the production libraries; `scripts/flat-pom.py`
  lists each in a plain pom; OSV-Scanner (`ghcr.io/google/osv-scanner`, pinned) scans it with
  `--no-resolve`. Its own resolver reads one Maven registry, so it cannot resolve Clojars libraries
  (`carmine`) and falls back to 52 direct entries, which misses everything transitive.
- Accepted alerts live in `osv-scanner.toml`, each with a reason and an `ignoreUntil` date.
- Seen to fail: with an empty `osv-scanner.toml` it listed the four accepted alerts and exited 1.
- It found `commons-fileupload2-core` 2.0.0-M1 (under Pedestal), which Dependabot never showed
  because Dependabot reads `develop` only.

## Pinning Netty: include the native jars

`netty-transport-native-epoll` (`:classifier "linux-x86_64"`) and `-kqueue` (`"osx-x86_64"`) must
match the other Netty jars. Left at 4.1.131 under 4.1.141 classes, the Datomic peer failed to
connect with `UnsatisfiedLinkError: ...errnoENAMETOOLONG`, and the app container never became
healthy. `lein test` and the browser suites use `datomic:mem` and open no Netty connection, so only
a real stack (`./run --auto`, CI's Docker jobs) shows it. A `lein deps :tree` check must include
entries with `:classifier`.

## Status 2026-11-20

#91 (Long Rest Update) took #89's fixes to `develop`; open alerts went from 83 to 7, and the H2
dismissal leaves 6: five Jetty 11 (cleared by `upgrade/pedestal-jetty12`, Jetty 12.1.15) and
jline-reader 3.5.1, which rebel-readline brings into the dev REPL only. The API's dismissal reasons
are `fix_started`, `inaccurate`, `no_bandwidth`, `not_used`, `tolerable_risk`.

**jline (dev REPL):** fixed on #92 by rebel-readline 0.1.11 plus jline 3.30.15 pinned in `:dev-config`.
Trap: rebel-readline 0.1.11 brings cljfmt 0.13.0, which needs rewrite-clj 1.x, and the dev profile
pinned rewrite-clj 0.5.2 (for `comment_discipline_test`), so the REPL died on `No such var: z/of-node`.
rewrite-clj 1.1.48 serves both. Only a REPL started in a terminal shows it; `lein test` passes either way.

**Vendored jars and the personal-data scan:** a `.pom` in `lib/` carries an
`scm:git:ssh://` link whose user@host part (GitHub's shared SSH login) the pre-push scan reads as
an email address. `.personal-data-allow` lists that one address, with its reason (on
`upgrade/pedestal-jetty12` until it merges). Quoting the address in a doc trips the scan the same way.
