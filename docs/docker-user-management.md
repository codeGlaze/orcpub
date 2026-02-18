# Docker User Management

Dungeon Master's Vault uses email verification for new accounts by default. When self-hosting with Docker, you likely don't have (or want) an SMTP server configured. These scripts let you create pre-verified user accounts directly in the Datomic database, bypassing the email verification step entirely.

## Quick Start (Without Cloning)

You don't need to clone the whole repo. Grab the three files you need and go:

```bash
# 1. Get the compose file and user scripts
mkdir orcpub && cd orcpub
curl -fLO https://raw.githubusercontent.com/Orcpub/orcpub/develop/docker-compose.yaml
curl -fLO https://raw.githubusercontent.com/Orcpub/orcpub/develop/docker-user.sh
mkdir -p docker/scripts
curl -fL https://raw.githubusercontent.com/Orcpub/orcpub/develop/docker/scripts/manage-user.clj \
     -o docker/scripts/manage-user.clj
chmod +x docker-user.sh

# 2. Start the stack
docker compose up -d

# 3. Create your account (script waits for healthcheck automatically)
./docker-user.sh create myname me@email.com MyPassword123
```

Open https://localhost and log in. That's it.

### Day-to-day

```bash
docker compose up -d          # start (if not already running)
docker compose down           # stop
docker compose down -v        # stop and wipe data
```

User management works any time the stack is up:

```bash
./docker-user.sh list                                         # who's in there?
./docker-user.sh create buddy friend@email.com TheirPass456   # add someone
./docker-user.sh batch players.txt                            # add many at once
./docker-user.sh check buddy                                  # look up a user
```

### The script handles the waiting

You don't need to know when Datomic is "ready." If you run `docker compose up -d` and immediately run `./docker-user.sh create ...`, it will:

1. Find the orcpub container automatically
2. Poll the Docker healthcheck (dots printed while waiting)
3. Run the command once healthy

No "wait 30 seconds then..." — just run it.

If you forget the commands: `./docker-user.sh --help`

## Full Setup (With Cloned Repo)

If you've cloned the repo, the setup script generates secure passwords, SSL certs, and required directories in one step:

```bash
./docker-setup.sh              # Interactive — prompts for optional values
./docker-setup.sh --auto       # Non-interactive — accepts all defaults
./docker-setup.sh --auto --force  # Regenerate everything from scratch
```

In interactive mode, the setup script will prompt for an initial admin account. Then start the stack and initialize:

```bash
docker compose up -d
./docker-user.sh init       # creates the admin user configured in .env
```

Or skip the prompt and create users directly:

```bash
docker compose up -d
./docker-user.sh create admin admin@example.com MySecurePass123
```

The setup script creates a `.env` file used by both `docker-compose.yaml` and `docker-compose-build.yaml`. You can also copy and edit `.env.example` manually if you prefer.

### Environment Variables

| Variable | Description | Default |
|---|---|---|
| `PORT` | Application port | `8890` |
| `ADMIN_PASSWORD` | Datomic admin interface password | generated |
| `DATOMIC_PASSWORD` | Datomic application password | generated |
| `SIGNATURE` | JWT signing secret (20+ chars) | generated |
| `EMAIL_SERVER_URL` | SMTP server (leave empty to skip email) | empty |
| `EMAIL_ACCESS_KEY` | SMTP username | empty |
| `EMAIL_SECRET_KEY` | SMTP password | empty |
| `EMAIL_SERVER_PORT` | SMTP port | `587` |
| `EMAIL_FROM_ADDRESS` | Sender address | empty |
| `EMAIL_SSL` / `EMAIL_TLS` | SMTP encryption | `FALSE` |
| `INIT_ADMIN_USER` | Initial admin username (for `./docker-user.sh init`) | empty |
| `INIT_ADMIN_EMAIL` | Initial admin email | empty |
| `INIT_ADMIN_PASSWORD` | Initial admin password | empty |

## User Management Commands

### Initialize Admin from .env

```bash
./docker-user.sh init
```

Reads `INIT_ADMIN_USER`, `INIT_ADMIN_EMAIL`, and `INIT_ADMIN_PASSWORD` from `.env` and creates the account. Safe to run multiple times — if the user already exists, it's skipped.

This is the easiest path after running `docker-setup.sh` in interactive mode, which prompts for these values.

### Create a Single User

```bash
./docker-user.sh create <username> <email> <password>
```

Creates a new user account that is **automatically verified** — no email confirmation needed. The password is hashed with bcrypt before storage.

### Create Multiple Users (Batch)

```bash
cp docker/scripts/users.example.txt users.txt
# edit users.txt with your actual users
./docker-user.sh batch users.txt
```

Creates multiple users from a local file in a single JVM session (much faster than calling `create` repeatedly). The script copies the file into the container automatically — you don't need to interact with Docker directly. A template is included at [`docker/scripts/users.example.txt`](../docker/scripts/users.example.txt). File format:

```
# Comments and blank lines are ignored
admin    admin@example.com      SecurePass123
player1  player1@example.com    AnotherPass456
player2  player2@example.com    YetAnotherPass789
```

Duplicates are logged and skipped (not treated as errors).

### Verify an Existing User

```bash
./docker-user.sh verify <username-or-email>
```

If a user registered through the web UI but never received/clicked the verification email, this marks them as verified.

### Check a User

```bash
./docker-user.sh check <username-or-email>
```

Displays the user's username, email, verification status, and creation date.

### List All Users

```bash
./docker-user.sh list
```

Prints a table of all users in the database with their verification status.

### Options

| Flag | Description |
|---|---|
| `--container <name>` | Override automatic container detection |
| `--help` | Show usage information |

## Docker Compose Changes

Both `docker-compose.yaml` and `docker-compose-build.yaml` have been updated:

- **Environment variables use `.env` interpolation** — no more editing passwords directly in the YAML. All config flows from the `.env` file generated by `docker-setup.sh`.
- **Native healthchecks** — Datomic and the application containers declare healthchecks so that dependent services wait for readiness automatically. This replaces fragile startup-order workarounds.
- **Service dependencies use `condition: service_healthy`** — nginx won't start until the app is actually serving, and the app won't start until Datomic is accepting connections.

## How It Works

The user management script ([docker/scripts/manage-user.clj](../docker/scripts/manage-user.clj)) runs as a Clojure program inside the orcpub container, reusing the application's own uberjar classpath. This gives it direct access to `datomic.api` and `buddy.hashers` without installing anything extra.

The shell wrapper (`docker-user.sh`) handles:
1. Auto-detecting the running orcpub container
2. Waiting for Docker's native healthcheck to report healthy
3. Copying the Clojure script into the container
4. Executing it via `java -cp /orcpub.jar clojure.main`

## Troubleshooting

**"Cannot find the orcpub container"**
- Ensure containers are running: `docker compose ps`
- If using a non-standard project name, pass `--container <name>` explicitly

**"Cannot connect to Datomic"**
- The datomic container may still be starting. The script waits up to 120 seconds, but on slow systems it may need longer.
- Check datomic logs: `docker compose logs datomic`

**"Container reported unhealthy"**
- Check the application logs: `docker compose logs orcpub`
- Verify `.env` values match between `DATOMIC_PASSWORD` and the password in `DATOMIC_URL`

**User created but can't log in**
- Run `./docker-user.sh check <username>` to confirm the account exists and is verified
- Make sure `SIGNATURE` in `.env` hasn't changed since the container started (restart containers after `.env` changes)
