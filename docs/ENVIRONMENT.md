# OrcPub/Dungeon Master's Vault Environment Configuration

This file documents the canonical environment variable pattern for all environments (bare metal, Docker, devcontainer, CI).

- Copy `.env.example` to `.env` and edit as needed for your environment.
- All shell scripts, Docker Compose, devcontainer, and Clojure code will source/read `.env` if present.
- Never commit `.env` (contains secrets). `.env.example` is safe for sharing.

## Example .env

    DATOMIC_VERSION=1.0.7482
    DATOMIC_TYPE=pro
    DATOMIC_URL=datomic:dev://localhost:4334/orcpub
    DATOMIC_PASSWORD=changeme
    SIGNATURE=changeme
    ADMIN_PASSWORD=changeme
    PORT=8080
    # Logs directory (defaults to repo ./logs if unset)
    # Example: LOG_DIR=/var/log/orcpub
    LOG_DIR=
    EMAIL_HOST=smtp.example.com
    EMAIL_PORT=587
    EMAIL_USER=your@email.com
    EMAIL_PASSWORD=changeme

## Precedence

1. `.env` in repo root (authoritative, always sourced if present)
2. Docker Compose/devcontainer: use `env_file: .env` or `containerEnv` as fallback
3. Shell scripts: always source `.env` if present
4. Clojure: use `environ` or `dotenv` to read `.env`/ENV

## Updating

- To add a new variable, update `.env.example` and this README section.
- Document all required/optional variables here.

---

For more details, see AGENTS.md and UPGRADE_PLAN.md.
