# Java Compatibility

## Run Java 21

The site needs **Java 21** (an LTS release). The Docker images, the devcontainer, the scripts and
CI all use it. On a bare-metal install, install an OpenJDK 21 build such as
[Adoptium Temurin](https://adoptium.net/) and check it with `java -version`.

Java 8 does not work: the web server (Pedestal 0.8 on Jetty 12) needs Java 17 or newer, and
Datomic Pro needs Java 11 or newer.

## Newer Java versions

The site also runs on Java 25 and 26 with Datomic Pro. At startup the JVM prints warnings that
`sun.misc.Unsafe::objectFieldOffset` is terminally deprecated, called from Netty. They are
harmless today. If a future Java release removes that method, the server will fail to start
inside Netty; the fix is a newer Netty version, not a change to the app.

## Datomic Free does not run on Java 21

Datomic Free 0.9.5703 fails on Java 21 with an SSL handshake timeout, because the app cannot
connect to the transactor. Datomic Free is unmaintained, so use Datomic Pro (`com.datomic/peer`).

If you have data in Datomic Free, move it to Pro before you upgrade. See
[migration/datomic-data-migration.md](migration/datomic-data-migration.md) and the migration
section of the [README](../README.md#moving-from-datomic-free).

## Pedestal and Jetty versions

Pedestal 0.8.3 runs on Jetty 12.1.15, which needs Java 17 or newer. Every Jetty artifact is
pinned to that version in `project.clj` (`:managed-dependencies`): Pedestal brings Jetty 12.0
and figwheel's Ring (development only) brings 12.1, and a mix of the two fails at start-up.
Leiningen ignores imported BOMs, so each artifact is listed. Development uses a vendored build
of figwheel's main branch; see [migration/pedestal-0.8.md](migration/pedestal-0.8.md).

## javax.servlet-api

Java 9 and later no longer put `javax.servlet` on the classpath, so the project lists it
explicitly:

```clojure
[javax.servlet/javax.servlet-api "4.0.1"]
```

## CI

`.github/workflows/continuous-integration.yml` uses `setup-java@v4` with the `temurin`
distribution and Java 21.
