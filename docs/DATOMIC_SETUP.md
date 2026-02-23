# Datomic Pro Installation & Setup

> Agent guidance for Datomic Pro configuration, transactor setup, and peer JAR management.

---

## Overview

Set `DATOMIC_VERSION` in the environment to control which Datomic Pro distribution
is installed by `.devcontainer/post-create.sh`:

```bash
export DATOMIC_VERSION=1.0.7482
```

## Local Datomic Transactor

The canonical script for starting a local Datomic Pro transactor is `scripts/start.sh`:

```bash
./scripts/start.sh datomic              # Start transactor
./scripts/start.sh datomic --quiet --idempotent  # Idempotent start
```

Responsibilities:
- Prepares transactor properties from template
- Starts transactor in background, writes PID to `logs/`
- Waits for port readiness
- Supports `--quiet`, `--check` (pre-flight), `--idempotent` flags

## Installation

**Installation is handled by `.devcontainer/post-create.sh`** (canonical installer).

The installer:
1. Unzips Datomic distribution into `lib/com/datomic/datomic-pro/<version>/`
2. Flattens nested directories if present
3. Runs vendor `bin/maven-install` for Maven/local layout
4. Leiningen can then resolve dependencies

If `scripts/start.sh --install` is called, it invokes the canonical installer
rather than duplicating logic.

## Transactor vs Peer JAR

- The **transactor** is NOT a JAR. It's a process started via `bin/transactor` with a properties file.
- The **peer library** (`datomic.api`) is a JAR: `peer.<version>.jar` in the distribution's `lib/`.
- **Do NOT rename or move the peer JAR** after unzipping. Keep original name and location.
- The project references the peer JAR via the `file:lib` repository pattern in `project.clj`.

## Unzipping Datomic Pro

- Unzip as-is into `.datomic/datomic-pro-<version>/`
- Do not rename, move, or tamper with any files
- Transactor config: `.datomic/datomic-pro-<version>/config/dev-transactor-template.properties`

## Vendor Dependencies Pattern

This project uses `file:lib` for vendor dependencies:

1. Place JAR in Maven directory structure: `lib/com/group/artifact/version/artifact-version.jar`
2. Repository already configured in `project.clj`: `["local" {:url "file:lib"}]`
3. CI copies `lib/*` to `~/.m2/repository/` automatically

Used for: `org.apache.pdfbox/pdfbox`, `com.datomic/datomic-pro`

## Compatibility Warning

**Datomic Free + Java 21:** Datomic Free 0.9.5703 does NOT work on Java 21.
Peer-to-transactor connections fail with SSL handshake timeout.
See `docs/DATOMIC_JAVA21_TEST_RESULTS.md` for complete test results.

Migration to Datomic Pro is required for JDK 21 support.

## Distribution & Maven

Datomic Pro is **not on Maven Central**. The peer jar is only available inside
the distribution zip hosted on S3 (`datomic-pro-downloads.s3.amazonaws.com`).
The zip is public (no auth required).

The distribution bundles `bin/maven-install` — a shell script that calls
`mvn install:install-file` to place the peer jar into `~/.m2/repository/`.
**`mvn` must be installed** for this to work (Alpine images don't have it).

## Docker Build Architecture

A single `docker/Dockerfile` with a shared download stage and two build targets:

- `datomic-dist` — downloads the zip once (shared layer)
- `transactor` — copies full distribution, configures dev storage, runs transactor
- `app` — copies distribution, runs `maven-install` for peer jar, builds uberjar

`docker-compose-build.yaml` selects targets:
```yaml
orcpub:
  build: { dockerfile: docker/Dockerfile, target: app }
datomic:
  build: { dockerfile: docker/Dockerfile, target: transactor }
```

**Do NOT split into separate Dockerfiles** — that causes the 150MB zip to be
downloaded twice independently.

### BuildKit on GitHub Actions

`docker compose build` delegates to BuildKit (buildx) on GH runners. BuildKit
**hangs for 10+ minutes** during image export after successful compilation.
Force the legacy builder with `DOCKER_BUILDKIT=0` in CI.

## Summary for Agents

- Never treat the transactor as a JAR or attempt to run it as one
- Never rename the peer JAR; always use the original name and location
- Installation is handled by `.devcontainer/post-create.sh`, not custom scripts
- Use the existing `file:lib` pattern for vendor dependencies
- Datomic Pro is NOT on Maven Central — only available via S3 zip download
- `bin/maven-install` requires `mvn` — ensure Maven is installed in build images
- Docker uses ONE Dockerfile with two targets — never split back into two files
- Disable BuildKit in CI (`DOCKER_BUILDKIT=0`) to avoid export hang
