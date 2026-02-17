# Environment Variables

All configuration is managed via a `.env` file at the repository root. Copy `.env.example` to `.env` and edit as needed.

## Precedence

1. `.env` in repo root (authoritative — sourced by all scripts and read by `environ`)
2. `containerEnv` in `.devcontainer/devcontainer.json` (fallback defaults)
3. System environment variables

## Variables

### Datomic

| Variable | Default | Description |
|----------|---------|-------------|
| `DATOMIC_URL` | `datomic:dev://localhost:4334/orcpub` | Database connection URI |
| `DATOMIC_VERSION` | `1.0.7482` | Datomic Pro version for installer |
| `DATOMIC_TYPE` | `pro` | Datomic distribution type |
| `DATOMIC_PASSWORD` | — | Transactor password |

### Application

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | Production web server port (dev uses 8890) |
| `SIGNATURE` | — | JWT signing secret for authentication |
| `ADMIN_PASSWORD` | — | Admin password |

### Security

| Variable | Default | Description |
|----------|---------|-------------|
| `CSP_POLICY` | `strict` | Content Security Policy mode: `strict`, `permissive`, or `none` |
| `DEV_MODE` | `true` (in :dev profile) | Enables dev-mode CSP (Report-Only instead of enforcing) |

CSP modes:
- **strict** — nonce-based CSP with `strict-dynamic`. Dev mode uses `Report-Only` header (logs violations but doesn't block). Prod uses enforcing header.
- **permissive** — allows `unsafe-inline` and `unsafe-eval`. Legacy fallback.
- **none** — disables CSP entirely. Not recommended for production.

### Email (SMTP)

| Variable | Default | Description |
|----------|---------|-------------|
| `EMAIL_HOST` | — | SMTP server hostname |
| `EMAIL_PORT` | — | SMTP port |
| `EMAIL_USER` | — | SMTP username |
| `EMAIL_PASSWORD` | — | SMTP password |

### Logging

| Variable | Default | Description |
|----------|---------|-------------|
| `LOG_DIR` | `./logs` | Directory for log files |
| `POST_CREATE_VERBOSE` | `1` (in devcontainer) | Enable verbose post-create logging |

### Development

| Variable | Default | Description |
|----------|---------|-------------|
| `ORCPUB_ENV` | — | Set to `dev` to enable `add-test-user` in user.clj |

## Files That Read Environment

| File | Variables Used |
|------|---------------|
| `src/clj/orcpub/config.clj` | `DATOMIC_URL`, `CSP_POLICY`, `DEV_MODE` |
| `src/clj/orcpub/system.clj` | `PORT` (via `System/getenv`) |
| `src/clj/orcpub/routes.clj` | `SIGNATURE`, `EMAIL_*`, `ADMIN_PASSWORD` |
| `.devcontainer/post-create.sh` | `DATOMIC_VERSION`, `DATOMIC_TYPE` |
| `scripts/start.sh` | `DATOMIC_URL`, `LOG_DIR` |
| `dev/user.clj` | `ORCPUB_ENV` (for add-test-user guard) |
