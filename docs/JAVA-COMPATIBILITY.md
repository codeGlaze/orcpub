# Java Compatibility

## Requirement: Java 21

This project requires **Java 21** (LTS). The CI workflow, devcontainer, and all scripts are configured for Java 21.

## Why Not Java 8

The original project ran on Java 8. The upgrade to Java 21 was driven by:

1. **Datomic Pro** — the actively maintained Datomic distribution targets Java 11+
2. **Pedestal 0.8** — uses Jetty 12, which requires Java 17+
3. **Security** — Java 8 is past end of public updates
4. **Ecosystem** — modern Clojure libraries increasingly require Java 11+

## Datomic Free + Java 21: Incompatible

Datomic Free 0.9.5703 fails on Java 21 with an SSL handshake timeout. The peer cannot connect to the transactor. This is caused by changes in Java's TLS implementation that break ActiveMQ Artemis (Datomic's internal messaging). Datomic Free is unmaintained — no fix is available.

**Solution**: Migrate to Datomic Pro (`com.datomic/peer`), which supports Java 21.

## Pedestal + Jetty Constraint

Pedestal 0.8.3 runs on **Jetty 12.1.15**, which requires Java 17+. Every Jetty artifact is pinned to that version in `:managed-dependencies` in `project.clj`, because Pedestal brings Jetty 12.0.x and figwheel's Ring (dev) brings 12.1.x, and a mix of the two fails at start-up. Leiningen ignores imported BOMs, so each artifact is listed.

Dev uses a vendored build of figwheel's main branch (Jetty 12 and SSE reload). See [migration/pedestal-0.8.md](migration/pedestal-0.8.md).

## javax.servlet-api

Java 9+ removed `javax.servlet` from the default classpath. Added explicitly:

```clojure
[javax.servlet/javax.servlet-api "4.0.1"]
```

## CI Configuration

`.github/workflows/continuous-integration.yml` uses `setup-java@v4` with `temurin` distribution, Java 21.
