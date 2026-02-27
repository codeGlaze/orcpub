# Remote Development — Figwheel WebSocket in Codespaces & Tunnels

How to get Figwheel hot-reload working in remote development environments
(GitHub Codespaces, Gitpod, SSH tunnels, etc.).

## The Problem

Figwheel's default WebSocket URL is `ws://localhost:3449/figwheel-connect`.
This works when the browser is on the same machine as the dev server. In
remote environments the browser connects through a forwarded hostname
(e.g., `codespace-name-3449.app.github.dev`), so `localhost` doesn't
resolve to the dev server. The WebSocket upgrade fails silently and
hot-reload never fires.

## The Discovery: `--fw-opts`

figwheel-main's CLI accepts a `--fw-opts` flag that takes an EDN map and
**merges** it with the build config in `dev.cljs.edn`. This is an
undocumented (or under-documented) feature that lets you override any
figwheel config key at launch time without generating config files.

```bash
lein run -m figwheel.main -- \
  --fw-opts '{:connect-url "wss://host-3449.app.github.dev/figwheel-connect" :open-url false}' \
  --build dev
```

This is the right approach because:
1. No generated files to `.gitignore`
2. Overrides merge cleanly with `dev.cljs.edn` metadata
3. Works with any launch method (lein alias, nohup, direct invocation)

### What Didn't Work: CLOSURE_UNCOMPILED_DEFINES

`goog.define` values can be overridden at runtime using the
`CLOSURE_UNCOMPILED_DEFINES` JavaScript global, but **only when
`:optimizations` is `:none`** (development mode). This is a Closure
Compiler feature, not a figwheel feature. It was investigated as an
alternative to `--fw-opts` but rejected because:

- It requires injecting a `<script>` tag before the CLJS bootstrap
- It only works with `goog.define`'d values, not figwheel config keys
  like `:connect-url` or `:ring-server-options`
- figwheel reads `:connect-url` from its own config, not from a
  `goog.define`

Useful to know for other runtime-configurable values though.

## Codespaces Auto-Detection

`scripts/start.sh` auto-detects Codespaces and constructs the correct
WebSocket URL:

```bash
# Detection: CODESPACES env var set to "true" by GitHub
if [[ "${CODESPACES:-}" == "true" ]]; then
    cs_domain="${GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN:-app.github.dev}"
    cs_name="${CODESPACE_NAME}"
    connect_url="wss://${cs_name}-${FIGWHEEL_PORT}.${cs_domain}/figwheel-connect"
fi
```

Environment variables available in Codespaces:
| Variable | Example Value |
|----------|---------------|
| `CODESPACES` | `"true"` |
| `CODESPACE_NAME` | `"urban-space-xyzzy-abc123"` |
| `GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN` | `"app.github.dev"` |

The constructed URL format:
```
wss://<CODESPACE_NAME>-<PORT>.<DOMAIN>/figwheel-connect
```

## Port Visibility (Critical)

Port 3449 **must be set to `public`** in `.devcontainer/devcontainer.json`:

```json
"3449": {
    "label": "Figwheel",
    "onAutoForward": "silent",
    "visibility": "public"
}
```

**Why:** Codespaces private ports require a GitHub authentication token in
the `Cookie` header for HTTP requests. The browser includes this
automatically for page loads, but the WebSocket upgrade request does NOT
include the cookie. The handshake gets a 401 and the connection fails.

Setting the port to `public` removes the auth requirement. Since the
Figwheel dev server only serves development assets (no credentials, no
user data), the security trade-off is acceptable.

## Manual Override: FIGWHEEL_CONNECT_URL

For environments that aren't auto-detected (Gitpod, ngrok tunnels, SSH
port forwarding), set the `FIGWHEEL_CONNECT_URL` environment variable
in `.env` or the shell:

```bash
# .env
FIGWHEEL_CONNECT_URL=wss://my-tunnel-host:3449/figwheel-connect
```

`start.sh` reads this from `common.sh` and passes it to `--fw-opts`
automatically.

## Files Involved

| File | Role |
|------|------|
| `scripts/start.sh` | Codespaces detection, `--fw-opts` construction, `start_figwheel()` |
| `scripts/common.sh` | `FIGWHEEL_CONNECT_URL` env var declaration |
| `.devcontainer/devcontainer.json` | Port 3449 visibility = public |
| `.env.example` | Documents `FIGWHEEL_CONNECT_URL` |

## Gotchas

1. **`fig:watch` vs direct invocation** — The `fig:watch` lein alias
   doesn't support passing `--fw-opts`. When remote dev is detected,
   `start.sh` falls back to `lein run -m figwheel.main -- --fw-opts ... --build dev`.
2. **`wss://` not `ws://`** — Codespaces port forwarding uses HTTPS.
   WebSocket must use `wss://` to match.
3. **`/figwheel-connect` path** — This is figwheel-main's default
   WebSocket endpoint path. It's distinct from the older figwheel-sidecar
   path (`/figwheel-ws/dev`).
4. **`:open-url false`** — Must be set in `--fw-opts` for remote envs.
   Otherwise figwheel tries to open a browser on the server, which fails.
5. **`:ring-server-options {:host "0.0.0.0"}`** — Required for remote
   connections. Default `127.0.0.1` only accepts local connections.
