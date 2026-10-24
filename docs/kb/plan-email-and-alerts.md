# Plan: email that is not lost, and admin alerts that do not depend on the database

**Status:** being built on `feature/email-and-alerts` (from `integration` after #64 landed,
approved by name). Done: stage 1 the settings report (`d8cdfb4a6`), stage 2 admin alerts
(`3c16593e7`), their human docs (`f578c9b31`), the settings lists and `.env` reading (see
[settings-registry-and-boot-report.md](settings-registry-and-boot-report.md),
[env-and-auth.md](env-and-auth.md)), stage 3 the player outbox (`fe7737c48`; `0c5446daa` before
it holds only a test file's rename and does not load on its own). Stage 4 (Docker: log rotation,
crash files and alerts in /var/log on the host, Swarm fixes, the upgrade report) is built and
passes the heavy checks on bare metal, Compose and Swarm (2026-10-24). Stage 5, the Discord
webhook, is **deferred to a later branch** (owner, 2026-10-24): see below. Then the PR. Replaces #65's own
scheduler for artist credit notices, whose two CodeRabbit findings (a pending batch lost; a failed
send never retried) were the starting point.

## The problem

Every email is sent once, directly (`postal/send-message`). If the mail server is busy that moment,
the email is gone and nothing retries. #65 built a second, separate scheduler for artist notices
to work around this. Error reports to the admin use the same once-only path, and they are the
emails most likely to be sent while things are failing.

## Two kinds of email

| | Player email | Admin alert |
|---|---|---|
| What | sign-up verification, password reset, welcome, artist credit notices | error reports, "database unreachable", "mail failing", recoveries |
| Created by | an action that already writes to the database | the server noticing something wrong, often the database itself |
| Kept in | a database outbox | a spool file on disk, plus the server log |
| If the database is down | the action fails anyway (no account, no email); the alert below tells the admin | still sent: never touches the database |
| After sending | deleted (reset and verify links are not kept) | removed from the spool |

### Player email: the transactional outbox

- Written to an outbox entity in the same transaction as the action that causes it.
- One worker sends, one email at a time, in small batches; each email is claimed atomically
  (Datomic compare-and-set) so two servers never send the same one.
- Retries with growing gaps and jitter (about 1 min, 5, 30, a few hours). A temporary refusal (SMTP
  4xx, timeout) retries; a permanent one (5xx: no such address) does not.
- After a ceiling of attempts the email is marked failed, kept for a look, and an admin alert says so.
- Backs off when the database or the mail server is failing; never retries harder during an outage.

### Admin alerts: independent of the database

- **One alert per problem, not per failure:** de-duplicated by kind, with counts ("this error, 340
  times in 15 minutes"); a reminder if it continues; "recovered" when it ends.
- **Hard caps:** about 20 emails an hour in total; past that, logged only. The spool is bounded
  (about the last 200 alerts); full, it drops the oldest and records how many it dropped.
- **Send first, spool second:** sent the moment it happens; the spool holds only what could not be
  sent (mail server down, webhook refusing).
- **Empty on shutdown:** the grace period Docker and Swarm give a stopping container is used to send
  what waits, so planned moves (update, rebalance, scale-down) strand nothing.
- **Always written to the server log** as well, so a record exists even if every channel is down.
- **Circuit breaker:** after repeated mail-server failures, sending pauses for a few minutes.
- **Player-submitted "character won't load" reports** are limited per person and by size, so they
  cannot flood either path.

### One admin address (owner, 2026-10-24)

Admin mail falls back so that one setting is enough: alerts use `EMAIL_ERRORS_TO`, else
`EMAIL_ADMIN_TO`, else `APP_SUPPORT_EMAIL`; artist notices try the first two the other way round.
`config/alert-address` and `config/admin-notice-address` return `{:address :from}`; the startup
report's DEPLOYMENT block prints `Admin email: <address> (from <setting>)`, or says nobody is
told. Why: the owner expected everything to default to the support address, and DMV's branding
file defaults `support-email` to `thDM@dungeonmastersvault.com` while its `.env.example` leaves
`EMAIL_ERRORS_TO` empty, so crash alerts reached nobody. The admin deploys through Portainer
(Swarm, `docker stack update`), not `./run`, so email is the one channel that reaches him unasked.

### Future improvement: a Discord webhook as a second channel

**Deferred, not built** (owner, 2026-10-24: record it for later; was wary of it). How it would work:

- Setting `DISCORD_WEBHOOK_URL`, off unless set. Discord gives the URL under a channel's
  Integrations, Webhooks; a JSON POST (`{"content": ...}`) to it posts the message. No bot, no library:
  Java's `HttpClient`, 5-second timeout.
- A second channel beside email in `orcpub.alerts`, fed the same alerts (crashes, failed email,
  database down) through the same de-duplication, hourly cap and breaker; sent on the alerts
  thread, so a slow Discord holds nothing up. A failure is logged and the alert stays spooled.
- Same text as the alert email; never player data. A row in the settings registry (`:secret? true`)
  and in `docs/BRANDING-AND-INTEGRATIONS.md`. Tests against a local fake HTTP server.
- Size: about 60 lines plus tests. Rules for it:

- The webhook address is a secret (anyone holding it can post to the channel; nobody can read
  through it): a Docker secret, never in client code or logs; regenerated in Discord if it leaks.
- Outgoing only. Alerts carry no personal data: the kind of problem, a count, which server, when.
- A private channel. Discord's rate limit is far above the de-duplicated alert rate; a refused post
  waits in the spool.

## Settings: in the registry, and announced at start-up (owner, 2026-10-17)

- Every new setting (spool location, caps, webhook) is a row in `config.clj settings`, so the
  start-up report shows it ([settings-registry-and-boot-report.md](settings-registry-and-boot-report.md)).
- Each row records the release it arrived in (`:since`). The report ends with "New in this release",
  the unset ones highlighted: the last lines of the start-up output, where an operator looks.
- The same rows feed a "New for admins" section in `docs/ENVIRONMENT.md`, by release.
- The branding settings move from a count to rows, so a bad value shows as ignored, and
  `APP_EMAIL_SIGNOFF` is added.
- Tests: every row has `:since`; the report's last lines list the new unset settings.
- Built by hand on what exists: a Datomic queue entity and worker; retries, backoff, the circuit
  breaker and caps in a few dozen lines each; `postal` for mail, plain HTTP for Discord. No queue
  library (they need Postgres, Redis or RabbitMQ) and no retry library (a new dependency nobody
  tracks for CVEs).

## Docker, Compose and Swarm

- App logs go to the container's output (`docker logs`, `docker service logs`). The compose file sets
  no rotation, so Docker keeps them forever: add `max-size` and `max-file` (worth doing regardless).
- The spool lives on a volume; a container's own files vanish when it is replaced. Compose: a mounted
  folder like `./logs` (the app container has none today).
- Swarm with several copies: each copy keeps its own spool file, named after the copy. On local
  volumes, a copy moved to another machine leaves its unsent backlog behind until a copy runs there
  again, so shared storage (as the portrait store supports) is the documented setting for Swarm, and
  the app warns at start-up when it runs in Swarm on local storage. What remains is a machine dying
  outright, with no grace period, while the mail server is also down; the other copies still alert
  about the same failure. Each alert names its copy.

## Documentation

- This page: the design and why, for agents.
- With the code, for people running the site, **in the existing documents** (owner, 2026-10-17: group
  new settings where they already belong; a new document only when nothing fits):
  - `docs/email-system.md`: how player email and admin alerts work, retries, what is kept and for
    how long; fold in or link `docs/error-email-improvements.md`, which covers error emails today.
  - `docs/ENVIRONMENT.md`: every new setting (spool location, caps, webhook), beside the existing
    email settings.
  - `docs/DOCKER.md`: the spool volume, log rotation, and shared storage for Swarm.
  - `docs/DOCKER-SECURITY.md`: the webhook address as a Docker secret.
  - `docs/BRANDING-AND-INTEGRATIONS.md`: the Discord webhook, with the other integrations.

## Admin alerts as built (stage 2, 2026-10-19)

- `orcpub.alerts`: an alerter map whose state changes all run on one single-thread scheduled
  executor (`on-thread!`); tests pass `:executor nil` to run inline. `raise!` returns at once.
- The request thread no longer sends mail: `email/send-error-email` builds subject, body and
  fingerprint, then calls `alerts/raise-current!`. Its own 5-minute throttle is gone.
- **The log follows the email rule**: one line per problem per 15-minute window, then a count.
  Logging every raise put 300 identical lines in the log for 300 errors; the owner's worry about
  logs filling a disk applies.
- No channel (no `EMAIL_ERRORS_TO` or no mail server) means `:send! nil`: logged only, nothing
  spooled. Otherwise every unsent alert would sit in the spool forever.
- **Shutdown flush is a JVM shutdown hook**, not only `component/stop`: `server/-main` just starts
  the system, and Docker's SIGTERM never calls `stop`. Budget 8 s of Docker's default 10 s.
- The spool is `<ALERT_SPOOL_DIR>/alerts-<HOSTNAME>.edn`, one EDN map per line, rewritten whole on
  each change (at most 200 lines). Every alert email ends with the copy's name.
- `:pedestal` depends on `:alerts`, so alerts start before the site takes traffic.
- Tests: `alerts_test.clj` (10), `email_alert_test.clj` (4). Each claim was broken in the code one at
  a time and failed only its own test; "the request does not wait" was proven by making the
  alerter send inline, since the old code passed it vacuously (it sent nothing without
  `EMAIL_ERRORS_TO`).

## The outbox as built (stage 3, 2026-10-22)

- `orcpub.outbox`; schema `outbox-schema` (`:orcpub.outbox/*`), applied on every start like the rest.
  `:message` (the postal map as EDN), `:to` and `:last-error` are `:db/noHistory`, so a sent and
  retracted email leaves no copy of its links in history.
- Callers add `(outbox/email-tx kind message)` to their transaction and call `(outbox/sent!)`
  after it, which nudges the worker. Code with no transaction uses `enqueue!`; code with no
  connection (artist emails) uses `enqueue-current!` (the component's connection).
- Converted: sign-up and re-verify (`do-verification`), password reset, password changed (now in
  the reset transaction), email change (verification to the new address + notice to the old, one
  transaction), sign-in attempts (in its future), artist emails (`artist-email/send!`). The
  character-load report still sends directly: its page reports the result to the player.
- Worker: one single-thread executor, every 30 s and on every nudge; 10 per pass. Claim is one
  transaction of two CAS ops: `status :pending -> :pending` and `claimed-until old -> now+10min`.
  **The status CAS is load-bearing:** without it a worker with a stale view claims an email another
  worker already sent and deleted (CAS on a retracted attribute with old value nil succeeds) and
  sends an empty message. Found by the two-worker test; pinned by a deterministic stale-db test.
- Retry gaps 1, 5, 30, 120, 360 min (+0-20% jitter); 8 attempts, then `:failed` + admin alert.
  Permanent = an SMTP reply code >= 500 anywhere in the cause chain (read by reflection off
  `getReturnCode`) or `AddressException`. postal 2.0.5 with a config map throws on failure.
- **Reversal: sign-up's rollback is gone** (see [blank-env-values.md](blank-env-values.md)). A
  notice that cannot be built (`routes/notice-tx`) is left out with a log line rather than blocking
  the change; found when a test request without `:scheme` made the password reset fail.
- Order: `:pedestal` and `:artist-accounts` depend on `:outbox`, so it is running before requests
  and before artist welcome emails.
- Tests: `outbox_test.clj` (9), `registration_email_test.clj` (4, replaces the rollback test), and
  queued-email checks in `registration_no_email_test`, `email_change_test`, `routes_test`,
  `artist_email_test`. Each outbox claim broken one at a time failed its test. Browser:
  `scripts/e2e/auth-flows.js` 43 checks green against a real SMTP sink.

## Stage 4 as built (2026-10-22)

- **Logs:** compose `x-logging` anchor, json-file `max-size`/`max-file` from `LOG_MAX_SIZE` /
  `LOG_MAX_FILES` (10m x 5) on all three services. `resources/simplelogger.properties` puts
  `io.pedestal` and `datomic` at warn: Pedestal's per-request INFO lines duplicated nginx's access log
  (nginx:alpine's http-level `access_log` to stdout is on). System properties override the file.
- **Security log:** `security/audit!` writes `security: <event> user=… ip=… fwd=…` for sign-in,
  sign-in-failed (account named only when it exists), registered, password-reset-requested,
  password-reset, email-change-requested/confirmed, account-deleted, token-invalid/withdrawn.
  `security_audit_test.clj`. See [client-ip-behind-proxies.md](client-ip-behind-proxies.md) for why
  `ip=` is the proxy.
- **JAVA_OPTS never reached Java** (`java -jar` ignores it): now passed as `JDK_JAVA_OPTIONS`, after
  `-XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError` and the heap-dump / error-file paths.
  Without it the JVM used 25% of the container's memory, a likely factor in the live OOMs.
- **Crash report:** `orcpub.crash-report` reads the dump paths from the JVM's own input arguments
  and raises one alert per new file, recording reported names in `reported.edn`.
- **Visible folder:** `APP_LOGS_DIR` (default `./logs/app`) bind-mounted at `/var/lib/orcpub/state`.
  The image has no `USER`; `deploy/app-start.sh` creates and chowns the state folder, `alerts/` and
  `crash/` only (never recursive; mode 750), then `su-exec app java -jar`. `docker-user.sh` uses
  `docker exec -u app`. `test/docker/test-app-start.sh` (CI) checks user, ownership, privacy.
- **Open:** Swarm still has no mount for the folder (bind sources must exist on every node, or the
  task will not start), and the app-log copy into that folder is not built.

## Tests the build must have, each seen failing first

- A player email survives a mail-server failure and is sent on retry; a permanent refusal is not
  retried; the ceiling marks it failed and raises an alert.
- Two workers never send the same email.
- Sent emails are deleted.
- An alert is sent with the database down; the same error 300 times makes one alert with a count;
  the hourly cap holds; a full spool drops the oldest and says how many.
- Alerts waiting in the spool are sent on shutdown, and after a restart.
- The webhook carries no personal data; its address never reaches a log.
- Artist credit notices from #65 run on the outbox, and #65's own scheduler is removed.
