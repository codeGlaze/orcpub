# Plan: email that is not lost, and admin alerts that do not depend on the database

**Status:** designed with the owner 2026-10-17, not built. Branch `feature/email-and-alerts` from
`integration` (name proposed; awaiting the owner's yes), after #64 lands. Replaces #65's own
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

### Optional: a Discord webhook as a second channel

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
