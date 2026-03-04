# Docker Compose Config vs Stack Deploy — Compatibility Reference

## Context
- `docker compose config` outputs Compose Specification YAML
- `docker stack deploy` validates against an older v3 schema baked into Docker CLI
- These have diverged, causing errors when piping compose config into stack deploy
- We use `.env` interpolation and multi-file merging (`COMPOSE_FILE=a.yaml:b.yaml`), so we can't pass compose files directly to `docker stack deploy`

## Solution
`docker stack deploy` accepts JSON. `docker compose config --format json` outputs JSON. `jq` handles type conversions cleanly — no YAML quoting fragility.

Pipeline:
```bash
docker compose config --format json | jq '
  del(.name) |
  .services |= with_entries(
    .value.depends_on |= (if type == "object" then keys else . end)
  ) |
  .services |= with_entries(
    .value.ports |= (if . then [.[] | .published |= tonumber] else . end)
  )
' | docker stack deploy -c - orcpub
```

## Known Incompatibilities

FATAL — blocks deployment:

| # | Field | compose config outputs | stack deploy expects | Affects us? | Issue |
|---|-------|----------------------|---------------------|-------------|-------|
| 1 | `ports[].published` | `"443"` (string, supports ranges) | `443` (integer) | YES | compose#9910 |
| 2 | `depends_on` | map with `condition:` keys | simple list | YES | compose#9957 |
| 3 | top-level `name:` | project name injected | not allowed | YES | compose#9424 |
| 4 | `tmpfs.size` | `"512000000"` (string) | integer | no | compose#9425 |
| 5 | `deploy.resources.limits.cpus` | `0.5` (number) | `"0.5"` (string) | no | compose#11721 |
| 6 | `env_file` | map with `path:`/`required:` | simple string | no | cli#4952 (fixed Docker >= 25.0.5) |
| 7 | `profiles` on services | present | not allowed | no | cli#4721 |
| 8 | missing `version:` | omitted (deprecated) | defaults to 1.0 | no | older Docker CLI only |

Silently ignored by Swarm (no error, no effect):
`build`, `container_name`, `restart` (use `deploy.restart_policy`), `links`, `devices`, `cap_add`/`cap_drop`, `network_mode`, `security_opt`, `depends_on` (even list-form is ignored — Swarm has no startup ordering).

## Why not use strict v3 format?

`condition: service_healthy` on `depends_on` was removed in v3.0, restored in v3.9, and is fully supported in the Compose Specification. But `docker stack deploy`'s validator was never updated to accept it — even Docker Engine 29.2.1 with `version: "3.9"` rejects conditions. This isn't a format version problem — stack deploy's validator simply hasn't been updated to match the v3.9 spec it claims to support.

Dropping conditions would break startup ordering for `docker compose up` (app starts before datomic healthcheck passes → crash).

## `docker stack config` — not a solution

Validates against the same old v3 schema on INPUT. Rejects our file with `depends_on must be a list` before producing output.

## Ports: keep quotes in compose.yaml

YAML 1.1 parses unquoted `xx:yy` as base-60 when both values < 60. Always quote per Docker docs. The string→int issue is in compose config's output, not the source file.

## Swarm Runtime Gotchas

Beyond schema incompatibilities, Swarm behaves differently at runtime:

### nginx upstream DNS
Swarm ignores `depends_on` — services start in arbitrary order. If nginx resolves
the app upstream at startup and the app isn't registered yet, it crashes with
`host not found in upstream`. Fix: use runtime DNS resolution.

```nginx
resolver 127.0.0.11 valid=30s;
set $upstream_app http://orcpub:${ORCPUB_PORT};

location / {
    proxy_pass $upstream_app;
}
```

`127.0.0.11` is Docker's embedded DNS. The `set` variable forces nginx to resolve
at request time, not startup. `${ORCPUB_PORT}` is substituted by envsubst at
container start — `$upstream_app` survives because it's not an env var.

### Datomic ALT_HOST
Datomic's `host=` controls both bind address and advertised hostname. In Swarm:
- `host=0.0.0.0` — bind to all interfaces (required; "datomic" resolves to a VIP)
- `alt-host=datomic` — peer fallback via Docker DNS (NOT `127.0.0.1`)

`docker-setup.sh --swarm` sets `ALT_HOST=datomic` in `.env` automatically. Getting
this wrong causes ActiveMQ connection errors in the app container.

### Compose teardown before Swarm init
Stale Compose containers leave behind bridge networks (e.g., `orcpub_default`).
`docker stack deploy` fails with `network with name orcpub_default already exists`.
Run `docker compose down` before `docker swarm init`. `docker-setup.sh --swarm`
detects running Compose containers and offers to stop them.

### Codespaces limitation
Swarm's ingress overlay network is not reachable by Codespaces' port forwarding
proxy. Ports publish correctly for `curl` inside the Codespace but return 504
from the browser. Use Compose for Codespaces development.

## Environment tested on

- Docker Engine 29.2.1
- Docker Compose v2.40.3
- jq 1.6
- mawk 1.3.4 (limited — avoid for YAML processing)
