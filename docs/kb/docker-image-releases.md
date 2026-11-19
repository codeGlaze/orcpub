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

## DMV: pushing past Cloudflare (2026-11-18)

DMV's `docker-builds.yaml` (Gitea, tag-triggered) got `413 Payload Too Large` pushing the app image: Cloudflare's proxy refuses request bodies over 100 MB (Free/Pro), and a layer goes up as one request. The admin's fix: `docker/setup-buildx-action` with `driver: docker`, so the push runs in the host dockerd (socket mounted into the runner), and a host `/etc/hosts` entry sends `code.dungeonmastersvault.com` straight to the server. The hosts entry is what bypasses Cloudflare; dockerd still sends each layer as one streamed request. Moving the runner or dropping the entry brings the 413 back. With the docker driver the host's cached base image is reused, so build steps need `pull: true` to pick up a patched Java.
