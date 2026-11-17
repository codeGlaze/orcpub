# Java Compatibility

## Run Java 21

The site needs **Java 21** (an LTS release). The Docker images, the devcontainer, the scripts and
CI all use it. On a bare-metal install, install an OpenJDK 21 build such as
[Adoptium Temurin](https://adoptium.net/) and check it with `java -version`.

Java 8 does not work: the web server (Pedestal 0.7 on Jetty 11) and Datomic Pro need Java 11 or
newer.

## Datomic Free does not run on Java 21

Datomic Free 0.9.5703 fails on Java 21 with an SSL handshake timeout, because the app cannot
connect to the transactor. Datomic Free is unmaintained, so use Datomic Pro (`com.datomic/peer`).

If you have data in Datomic Free, move it to Pro before you upgrade. See
[migration/datomic-data-migration.md](migration/datomic-data-migration.md) and the migration
section of the [README](../README.md#moving-from-datomic-free).

## Pedestal and Jetty versions

Pedestal is pinned to 0.7.0, which uses Jetty 11. Pedestal 0.7.1 and later use Jetty 12, which
removes `ScopedHandler` and makes figwheel-main (development hot reload) fail with
`NoClassDefFoundError`. Do not raise the Pedestal version without checking figwheel-main.

## javax.servlet-api

Java 9 and later no longer put `javax.servlet` on the classpath, so the project lists it
explicitly:

```clojure
[javax.servlet/javax.servlet-api "4.0.1"]
```

## CI

`.github/workflows/continuous-integration.yml` uses `setup-java@v4` with the `temurin`
distribution and Java 21.
