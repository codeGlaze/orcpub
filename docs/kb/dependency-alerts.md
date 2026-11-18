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
| H2 2.1.214, commons-beanutils 1.9.4 | `com.datomic/peer` | 2 high | Low: H2 console and bean access are not used. |
| plexus-utils 4.0.2 | `garden` (CSS build) | 1 high | Low: archive extraction, build-time only. |
| aircompressor 0.27 | `carmine` (Redis store) | 1 high | Low: only with Redis, decompressing its own data. |

Suggested order, one upgrade per PR: Jetty, Jackson, remove `pdfkit-clj`, Bouncy Castle, Guava,
then Netty (Datomic peer or a managed version), PostgreSQL. Pedestal 0.7.0 pins Jetty 11, and
figwheel-main blocks Jetty 12 (`project.clj` comment near line 46), so Jetty moves within 11.0.x.
