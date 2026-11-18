# Publishing Docker images

How the public images on Docker Hub get built, and where that stood on 2026-11-18.

## How it works

`.github/workflows/docker.yaml` ("Docker Releases") runs on a tag pushed to `Orcpub/orcpub`, or by
hand (workflow_dispatch with a tag name). It builds `docker/Dockerfile` twice and pushes:

- `orcpub/orcpub:latest` and `orcpub/orcpub:release-<tag>` (target `app`)
- `orcpub/datomic:latest` and `orcpub/datomic:release-<tag>` (target `transactor`)

It logs in with the upstream repo's `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` secrets, then commits
the version bump in `src/cljs/orcpub/ver.cljc` straight to upstream `develop`. Users pick a release
with `ORCPUB_IMAGE` / `DATOMIC_IMAGE` (docs/DOCKER.md, "Compose File").

The base images are floating Temurin 21 tags, so each run takes the newest Java 21 then: a JDK
security fix (CVE-2026-114938) reaches users only through a new run.

## State on 2026-11-18

- Newest image on Docker Hub: `release-v2.5.0.27` (2025-09-11). The Summer Patch (upstream #674,
  merged 2026-11-07) was never published.
- Upstream's last four runs (workflow_dispatch, 2026-02-19) failed after 24 to 43 seconds; the logs
  have expired (HTTP 410). That early, the Docker Hub login is the likely cause: check the secrets
  before the next run.
- Upstream's workflow is the old one. `integration`'s copy is hardened (read-only default
  permissions, the tag validated before use, current action versions, publishing only from
  `Orcpub/orcpub`); it reaches upstream with the next upstream PR.
- Publishing is an upstream action: the owner's call each time.
- The CVE-2026-114938 stopgap in the images is `JAVA2SHELL_STOPGAP` (true or false, default true), read by `deploy/app-start.sh` and `deploy/start.sh`.
