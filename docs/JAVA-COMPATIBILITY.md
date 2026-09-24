# Java Compatibility

## Requirement: Java 21

This project requires **Java 21** (LTS). The CI workflow, devcontainer, and all scripts are configured for Java 21.

## Why Not Java 8

The original project ran on Java 8. The upgrade to Java 21 was driven by:

1. **Datomic Pro** — the actively maintained Datomic distribution targets Java 11+
2. **Pedestal 0.7** — uses Jetty 11, which requires Java 11+
3. **Security** — Java 8 is past end of public updates
4. **Ecosystem** — modern Clojure libraries increasingly require Java 11+

## Datomic Free + Java 21: Incompatible

Datomic Free 0.9.5703 fails on Java 21 with an SSL handshake timeout. The peer cannot connect to the transactor. This is caused by changes in Java's TLS implementation that break ActiveMQ Artemis (Datomic's internal messaging). Datomic Free is unmaintained — no fix is available.

**Solution**: Migrate to Datomic Pro (`com.datomic/peer`), which supports Java 21.

## Pedestal + Jetty Constraint

Pedestal 0.7.0 uses **Jetty 11**. Pedestal 0.7.1+ uses **Jetty 12**.

Jetty 12 removes `ScopedHandler`, causing `NoClassDefFoundError` when figwheel-main is running (figwheel-main's Ring adapter depends on Jetty 11 classes).

**Current pin**: Pedestal 0.7.0 with Jetty 11. This constraint is about Pedestal/Jetty compatibility, not Java version.

## javax.servlet-api

Java 9+ removed `javax.servlet` from the default classpath. Added explicitly:

```clojure
[javax.servlet/javax.servlet-api "4.0.1"]
```

## CI Configuration

`.github/workflows/continuous-integration.yml` uses `setup-java@v4` with `temurin` distribution, Java 21.

## Observed on Java 25 (field report, 2026-09)

A self-hoster ran the full stack on **Java 25** (and another on Java 26) with
Datomic Pro 1.0.7482. It works — server boots, Datomic connects, schema
initialises — but the JVM now prints:

```
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by
         io.netty.util.internal.PlatformDependent0$4 (netty-common-4.1.100.Final.jar)
WARNING: sun.misc.Unsafe::objectFieldOffset will be removed in a future release
```

Netty 4.1.100 reaches for `sun.misc.Unsafe`, which is **terminally deprecated**
— it still works on 25, and will stop working on some later JDK. Nothing to do
today; when it does break it will present as a startup failure inside netty,
and the fix is a netty bump, not a code change on our side.

Worth noting this page says Java 21 while users are already several releases
past it without trouble.
