# Environment Variables

All settings live in one file, `.env`, at the root of the repository. This page lists every
setting the site reads: what it does, its default, and when you would change it.

To start, copy the example file and edit it:

```bash
cp .env.example .env
```

On a Docker install, `./run` writes `.env` for you, with generated passwords and a generated
`SIGNATURE`. See [DOCKER.md](DOCKER.md).

## How `.env` is read

- Each line is `NAME=value`. The value is taken exactly as written. Spaces and apostrophes need no
  quotes (`APP_NAME=Dungeon Master's Vault`). Quotes are optional and are removed.
- Blank lines and lines that start with `#` are ignored. So is ` # comment` after an unquoted value.
- A `$` starts a reference to another setting (`${PORT}`). Write `$$` for a literal `$`, or put the
  value in single quotes.
- A setting left blank counts as not set, and the default is used.
- Docker, Portainer and the setup scripts all read the file this way.

### Where `.env` takes effect

| You run | What reads `.env` |
|---------|-------------------|
| `./run`, `docker compose` | Compose reads it and passes the app container only the settings it names. Every setting on this page is named there, so setting it in `.env` is enough. A variable of your own that is not on this page does not reach the app. |
| `docker stack deploy` (Swarm) | Not read. Export it first (`set -a; source .env; set +a`), or paste `.env.portainer` into Portainer. See [DOCKER.md](DOCKER.md#docker-swarm-deployment). |
| `./menu`, `./scripts/start.sh` | Sourced automatically. |
| `lein repl`, `lein run` | Not read. They use development defaults, including a development `SIGNATURE`. To use your values, run `source .env && lein repl`. |

A variable already set in your shell beats the `.env` value. Compose behaves the same way (see
[DOCKER.md](DOCKER.md#connection-refused-localhost4335)). For the settings that support Docker
secrets, a secret file beats both.

## Required settings

| Variable | Why |
|----------|-----|
| `SIGNATURE` | Signs login tokens. Without it, every login and API call fails. |
| `DATOMIC_PASSWORD` | The app and the transactor use it to talk to each other. |
| `ADMIN_PASSWORD` | The transactor's admin password. |
| `EMAIL_SERVER_URL`, or one of the opt-outs | A deployed site will not start without a mail server unless you opt out. See [Email (SMTP)](#email-smtp). |

`./run` generates the first three.

## What the server prints at boot

Every start prints the settings it resolved, so you can check that a change was picked up. Each
setting shows its value and where the value came from:

```
  SETTING                          VALUE                  SOURCE
  [RUNTIME]
  PORT                             8080                   SET
  [SECURITY]
  SIGNATURE                        set                    JWT signing key; every login and API call fails without it
  [CAPACITY]
  ORCPUB_PDF_CONCURRENCY           24                     SET       sheets generated at once
  ORCPUB_PDF_MAX_CARDS             198                    DEFAULT   cards of each kind per export
  [BRANDING]
                                   3 of 22 set
  (!)  ORCPUB_PDF_MAX_CARDS=oops was ignored: not a positive integer. The default above is in use.
  (!!) SIGNATURE is not set, so every login, signup and authenticated API call fails.
```

| Marker | Meaning |
|--------|---------|
| `SET` | The value came from the environment. |
| `DEFAULT` | Nothing usable was set. This default is what is running. |
| `set` / `NOT SET` | A secret. Only whether it is present is shown, never the value. |
| `(!)` line | A value was given and rejected. The row reads `DEFAULT` because the default is in force. The line names the value that was thrown away. |
| `(!!)` line | A setting whose absence breaks the site. The line says how to fix it. |
| `NEW IN` block | Settings added in the current release that you have not set. They run on their defaults. Once you set each one, the block disappears. |

The report groups settings as runtime, database, security, email, content and capacity. The 22
`APP_*` branding settings are shown as a count.

The report is saved as `startup-report.txt`. In Docker it is `/var/log/orcpub/startup-report.txt`
on the host, and `./run` prints it last, after the containers are up. With Portainer, open the
service's Logs and search for "orcpub started". The admin address also gets a startup email when
the release changes (see [Admin alerts](#admin-alerts)).

A bad value never takes the site down by itself. It is reported and the default is used.

## Settings

### Application

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8890` | Port the app listens on. Nginx and the health check follow it. |
| `SIGNATURE` | none | **Required.** Secret that signs login tokens. Use 20 or more random characters (`openssl rand -hex 16`). Changing it signs everyone out and breaks unsubscribe links, so keep it stable. Docker secret: `signature`. |
| `TZ` | `America/Chicago` | Time zone the Docker images are built with, for log timestamps. It is a build argument, so rebuild after changing it. |
| `APP_URL` | none | The site's public address. Used for links in every account email (verification, password reset, notices) and in artist emails. Set it in production. Unset, those links follow the request's `Host` header, which a sender can forge, and the boot report warns. |
| `ORCPUB_HTTP_HOST` | `localhost` | Address the development server binds to. Set `0.0.0.0` when a browser on another machine, such as Windows reaching WSL, must connect. Docker installs always bind to all interfaces. `lein fig:*` does not read `.env`: use `lein with-profile +wsl fig:dev` there. |
| `DEV_MODE` | unset | Set to the exact string `true` (any case) for development. Then no Content Security Policy header is sent, which lets Figwheel's `ws://localhost:3449` connect, and the site may start without a mail server. Any other value, including `1`, `yes` or empty, means off. Leave it off in production. |

### Database (Datomic)

| Variable | Default | Description |
|----------|---------|-------------|
| `DATOMIC_URL` | `datomic:dev://localhost:4334/orcpub`. Docker Compose: `datomic:dev://datomic:4334/orcpub` | Database connection URI. Leave it out of `.env` unless the database is elsewhere (a remote transactor, another port, or `datomic:sql` storage). Do not put `?password=` in it: the app adds the password from `DATOMIC_PASSWORD`. A URL that already carries a password still works, and its password wins. A password in a `datomic:sql` URI is hidden wherever the URI is logged. |
| `DATOMIC_PASSWORD` | none | Password the app and the transactor use. Docker secret: `datomic_password`. |
| `ADMIN_PASSWORD` | none | Transactor admin and monitoring password. Docker secret: `admin_password`. |
| `DATOMIC_VERSION` | `1.0.7482` | Datomic Pro version the install scripts fetch and look for under `lib/`. |
| `DATOMIC_TYPE` | `pro` | Datomic distribution the scripts look for. |

### Docker and transactor

| Variable | Default | Description |
|----------|---------|-------------|
| `ORCPUB_IMAGE` | `orcpub-app` | App image name. Set it when you tag your own builds (`dmv:2.6.0.0`) or pull from a registry. |
| `DATOMIC_IMAGE` | `orcpub-datomic` | Transactor image name. |
| `TRANSACTOR_HOST` | Compose: `datomic`. Swarm: `0.0.0.0` | Address the transactor serves on and gives the app. Each setup has the right value. Set it only to override. |
| `ALT_HOST` | Compose: `127.0.0.1`. Swarm: `datomic` | The second address the app tries. Leave it unset unless you mean to override it: a Compose value in `.env` breaks Swarm. |
| `ENCRYPT_CHANNEL` | `true` | Encrypts app-to-transactor traffic. Turn it off only to debug. |
| `ADMIN_PASSWORD_OLD`, `DATOMIC_PASSWORD_OLD` | none | The previous passwords, during a rotation. Remove them once the app has reconnected. See [DOCKER.md](DOCKER.md#password-rotation). |
| `COMPOSE_FILE` | none | `./run --secrets` adds this so Compose merges `docker-compose.secrets.yaml`. Do not set it by hand. |
| `JAVA_OPTS` | empty | Java options for the app, for example `-XX:MaxRAMPercentage=75.0`. Left empty, Java uses 25% of the container's memory. See [DOCKER.md](DOCKER.md#logs-crashes-and-the-apps-memory). |
| `XMS`, `XMX` | `-Xms1g`, `-Xmx1g` | Heap size of the Swarm transactor, as the full option (`-Xmx2g`). They do not affect the app. |
| `APP_MEMORY_LIMIT`, `APP_MEMORY_RESERVATION` | `2G`, `1G` | Swarm only: the app container's hard memory limit and its scheduling minimum. |
| `APP_LOGS_NAME` | `orcpub` | Name of the folder in the host's `/var/log` where the app keeps crash files and unsent admin alerts. A plain name only. |
| `LOG_MAX_SIZE`, `LOG_MAX_FILES` | `10m`, `5` | How big each container log file may grow, and how many are kept. |
| `INIT_ADMIN_USER`, `INIT_ADMIN_EMAIL`, `INIT_ADMIN_PASSWORD` | none | The first account, created by `./docker-user.sh init`. Running it again skips accounts that exist. |

### Security

| Variable | Default | Description |
|----------|---------|-------------|
| `CSP_POLICY` | `strict` | Content Security Policy mode: `strict`, `permissive` or `none` (below). |
| `ORCPUB_PWNED_CHECK` | `on` | Refuse new passwords that appear in known breaches. Accepts `on`/`off`, `true`/`false`, `1`/`0`, `yes`/`no`. |
| `TURNSTILE_SITE_KEY`, `TURNSTILE_SECRET_KEY` | unset | Cloudflare Turnstile bot check. It shows on sign-up, password reset, resend verification and sign-in after a failed attempt, and only when **both** keys are set. The site key is public. The secret is never logged. If Cloudflare cannot be reached, the request goes through. A refusal from Cloudflare, or a missing token, does not. Cloudflare's test keys always pass: site key `1x00000000000000000000AA`, secret `1x0000000000000000000000000000000AA`. |
| `REDIS_URL` | unset | Where sign-in, sign-up and reset limits are counted when more than one app container serves the site (`redis://[:password@]host:6379`). Unset, each container counts in its own memory, which is right for one container. If Redis cannot be reached within 200 ms, that call counts in memory and a warning is logged at most once a minute. The Compose files do not add a Redis service. |
| `TRUST_CLOUDFLARE` | unset | Set to `true` only when the site is served through Cloudflare. Limits and the security log then see each player's real address. Leave it unset otherwise: anyone can send requests from Cloudflare's addresses. |
| `TRUSTED_PROXIES` | unset | Other proxies in front of the app, as comma-separated addresses or ranges (`203.0.113.10,198.51.100.0/24`). Private networks, where Docker, Swarm, Traefik and nginx sit, are always trusted. |
| `PROFILE_ENCRYPTION_KEYS` | none | Encrypts the preferred names players give on My Account. Comma-separated `id:base64` pairs, each key 32 random bytes. The first encrypts and all decrypt, so put a new key first to rotate. Generate one with `echo "k1:$(openssl rand -base64 32)"`. Without it, preferred names cannot be saved. If you lose it, stored names cannot be read. `./run` generates one. Docker secret: `profile_encryption_keys`. |

**CSP modes**

- `strict`: a nonce-based policy with `strict-dynamic`, always enforced. There is no report-only mode.
- `permissive`: allows `unsafe-inline` and `unsafe-eval`. Use it only if `strict` blocks something you need.
- `none`: no policy at all. Not for production.

**How the player's address is found.** Behind a proxy, every request reaches the app from the
proxy's address. The app reads the player's address from the `X-Forwarded-For` header, but only the
part your own proxies added. It reads the header from the right, skips the proxies it trusts, and
takes the first address it does not trust. Anything a visitor writes into the header sits further
left and is ignored.

**The breach check.** Sign-up and password reset send the first five characters of the password's
SHA-1 to `api.pwnedpasswords.com` and score the reply locally, so the password never leaves the
server. A password is refused only when it appears 1000 or more times. If the service is slow or
down, the check lets the password through. Set `ORCPUB_PWNED_CHECK=off` if the service is slow
enough to be felt on every sign-up. Nothing else changes.

### Email (SMTP)

| Variable | Default | Description |
|----------|---------|-------------|
| `EMAIL_SERVER_URL` | none | Your SMTP server. Empty means no mail can be sent, so sign-up is turned off rather than letting people in unchecked, and a deployed site will not start unless you opt out (see `EMAIL_REQUIRED`). |
| `EMAIL_SERVER_PORT` | `587` | SMTP port. |
| `EMAIL_ACCESS_KEY` | none | SMTP username. |
| `EMAIL_SECRET_KEY` | none | SMTP password. |
| `EMAIL_SSL` | `FALSE` | Use SSL for SMTP. |
| `EMAIL_TLS` | `FALSE` | Use TLS for SMTP. |
| `EMAIL_FROM_ADDRESS` | `no-reply@example.com` | Sender address. It must be an address your mail provider may send as (SPF/DKIM). The default is a placeholder that no provider delivers, so set it. |
| `EMAIL_ERRORS_TO` | `EMAIL_ADMIN_TO`, then `APP_SUPPORT_EMAIL` | Where admin alerts are emailed. With none of the three set, alerts go to the server log only. |
| `EMAIL_ADMIN_TO` | `EMAIL_ERRORS_TO`, then `APP_SUPPORT_EMAIL` | Who hears about artist account and credit changes. |
| `EMAIL_REQUIRED` | unset | A deployed server (`DEV_MODE` not `true`) will not start without `EMAIL_SERVER_URL`. Set this to `false` to run without email on purpose. Sign-up then stays off. `./run` writes it when setup leaves email empty. |
| `ALLOW_UNVERIFIED_REGISTRATION` | `false` | Set to `true`, with `EMAIL_SERVER_URL` empty, to let people sign up without confirming their address. Accounts work at once and no mail is sent. Use it only on a private site: anyone can sign up with an address that is not theirs. It does nothing when SMTP is set up. You must ask for it on purpose, so that losing your SMTP settings cannot quietly stop the site checking addresses. |

What the site sends and when: [email-system.md](email-system.md).

### Admin alerts

When something goes wrong on the server, an alert is written to the log and emailed to the admin
address (`EMAIL_ERRORS_TO`, else `EMAIL_ADMIN_TO`, else `APP_SUPPORT_EMAIL`).

The same address gets a **startup email** when the server first starts on a new release: what
needs fixing, the release's new settings, and the settings worth setting. It is sent again only when
that list changes, plus one reminder after a week while something still needs fixing. The server
remembers what it sent in `startup-email.edn`, beside the startup report, by setting name only.

Alerts keep working when the rest of the site is struggling: they never use the database, and
sending one never slows the request that hit the error.

- **One email per problem.** The first time an error happens it is emailed at once. If it keeps
  happening, you get one follow-up 15 minutes later with the count. The log works the same way.
- **A ceiling.** At most `ALERT_MAX_PER_HOUR` alert emails go out in an hour. Past that, alerts
  are only logged, and the next email says how many were held back.
- **When the mail server is down.** After three failed sends in a row, the server stops trying for
  five minutes. Alerts wait in a file in `ALERT_SPOOL_DIR` and are sent once mail works. The file
  holds the 200 most recent. When it is full the oldest are dropped, and the next email says how
  many.
- **Restarts.** On shutdown (including `docker stop` and a Swarm update) the server tries to send
  what is waiting. Anything left is sent by the next server that starts with the same
  `ALERT_SPOOL_DIR`.
- **Several copies.** Each copy keeps its own file, named after its host name, and every alert
  email ends with that name, so you can tell which copy raised it.

| Variable | Default | Description |
|----------|---------|-------------|
| `ALERT_SPOOL_DIR` | `orcpub-alerts` in the system temp directory. In Docker, `/var/lib/orcpub/state/alerts`, which is `/var/log/$APP_LOGS_NAME/alerts` on the host | Where alerts wait when they cannot be emailed yet. Without the host mount, the folder is inside the container and is lost, with its alerts, when the container is replaced. |
| `ALERT_MAX_PER_HOUR` | `20` | Most alert emails sent in an hour. Must be a whole number above zero. |

### Capacity

How these settings trade memory for speed, and how to size them:
[PDF-EXPORT-CAPACITY.md](PDF-EXPORT-CAPACITY.md). A value that is present but not a positive
integer is reported at boot, and the default is used.

| Variable | Default | Description |
|----------|---------|-------------|
| `ORCPUB_HTTP_MAX_THREADS` | Pedestal's own (50 until roughly 16 cores) | Web server worker pool: how many requests of any kind are in flight. The rest queue. The boot report shows the size of the running pool. |
| `ORCPUB_PDF_CONCURRENCY` | `max(8, 2 x cores)` | How many character sheets are generated at once. Bounded separately so a rush of exports cannot starve logins and saves. Each one holds about 11 MB of heap. |
| `ORCPUB_PDF_QUEUE_TIMEOUT_MS` | `30000` | How long an export waits for a slot before the server answers 503 with `Retry-After`. |
| `ORCPUB_PDF_MAX_RETRIES` | `3` | How many times the busy page retries itself before it waits for the player to click. |
| `ORCPUB_PDF_MAX_CASTER_SECTIONS` | `13` | Most spellcasting sections one sheet may grow to. Thirteen covers every class. |
| `ORCPUB_PDF_MAX_CARDS` | `198` | Most cards of each kind (spells, items, features) one export prints, nine to a sheet, so 22 sheets. A level 20 wizard's spellbook is about 44 cards. |
| `ORCPUB_PORTRAIT_CONCURRENCY` | `max(2, cores / 2)` | How many shared-link portrait images are drawn at once. Each draw takes about half a second of one core. Repeat requests come from a cache. See [PORTRAIT-SERVING.md](PORTRAIT-SERVING.md). |
| `ORCPUB_PORTRAIT_QUEUE_TIMEOUT_MS` | `5000` | How long a portrait image waits to be drawn before the server answers 503 with `Retry-After`. Short, because link-preview fetchers give up quickly. |

### Share links

A character's owner can share it with a short link. The server keeps one copy of the character's
share data, checked when it is uploaded. The size limits reach the browser in response headers, so
changing one needs no rebuild.

| Variable | Default | Description |
|----------|---------|-------------|
| `ORCPUB_SHARE_MAX_UPLOAD_KB` | `64` | Largest compressed share data one character's link keeps. Over it, the owner's link carries the data itself. A packed level 20 character is 12 to 25 KB. |
| `ORCPUB_SHARE_MAX_TEXT_KB` | `256` | Largest the share data may be once unpacked. This also bounds what a viewer's browser unpacks. |
| `ORCPUB_SHARE_MAX_ACCOUNT_KB` | `1024` | Share data one account keeps across all its characters. |
| `ORCPUB_SHARE_PRUNE_DAYS` | `180` | Days a share link may go unused before the link and its share data are deleted. Only time the server is running counts. The character is never deleted, and its owner's page says the link expired. `0` keeps links forever. `1` to `29` count as `30`, so a typo cannot delete links a day old. Pruning runs hourly. |

### Homebrew and portraits

| Variable | Default | Description |
|----------|---------|-------------|
| `LOAD_HOMEBREW_URL` | none | URL of a `.orcbrew` file the site loads for players on first page load (for example `/homebrew.orcbrew`). Unset means no fetch. On Docker, put the file at `deploy/homebrew/homebrew.orcbrew` and set this to `/homebrew.orcbrew`. |
| `PORTRAIT_ARTISTS` | none | JSON of artist credits and accounts, described in [ARTISTS.md](../ARTISTS.md). An `account` email gets an artist account and a welcome email at startup, so leave real addresses out of a test server. Welcome links use `APP_URL`. |
| `PORTRAIT_STORE_DIR` | `orcpub-portraits` in the system temp directory | Where the server keeps its drawings of shared-link portraits, one file per character. With a shared volume, several containers draw each portrait once. Without one, each container draws on first request and keeps its own file. In Docker, mount a volume at `/var/lib/orcpub/portraits` (the image creates it for the app's user) and set this to that path. A failed write is logged and the drawing is still served. The files are derived: losing them only means drawing again. |

### Logging

| Variable | Default | Description |
|----------|---------|-------------|
| `LOG_DIR` | `./logs` | Directory for the log files written by `./menu` and `./scripts/start.sh`. |

On Docker, see [Logs, crashes and the app's memory](DOCKER.md#logs-crashes-and-the-apps-memory).

### Branding and links

The site's name, logos, footer, contact details, social links and field limits are set with `APP_*`
and `APP_SOCIAL_*` variables. The full list, with defaults, is in
[BRANDING-AND-INTEGRATIONS.md](BRANDING-AND-INTEGRATIONS.md).

### Development

| Variable | Default | Description |
|----------|---------|-------------|
| `ORCPUB_ENV` | none | Set to `dev` to allow the REPL helper `add-test-user`. |
| `FIGWHEEL_PORT` | `3449` | Figwheel WebSocket port for frontend hot reload. |
| `FIGWHEEL_CONNECT_URL` | auto-detected | Figwheel WebSocket URL for remote setups (Gitpod, tunnels), for example `wss://my-remote-host:3449/figwheel-connect`. GitHub Codespaces is detected automatically. |
| `E2E_MAIL_PORT` | `2525` | Port of the throwaway SMTP server the browser test suites start. |

## Docker secrets

`SIGNATURE`, `DATOMIC_PASSWORD`, `ADMIN_PASSWORD` and `PROFILE_ENCRYPTION_KEYS` can come from files
at `/run/secrets/<lowercase name>` instead of `.env`. A secret file wins over the environment. See
[DOCKER.md](DOCKER.md#docker-secrets).
