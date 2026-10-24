# The settings registry and the boot report

**What this is for:** where the server's settings are listed, how the start-up report is built from
that list, and what to do when you add a setting. Found by searching the code on 2026-10-17; no doc
pointed here. References are against `integration` at `427d16aad`.

## Where things are

| Piece | Where | What it does |
| --- | --- | --- |
| Reading a setting | `orcpub.env/value` | every setting is read from the environment through this; a blank value counts as unset ([blank-env-values.md](blank-env-values.md)) |
| The registry | `config.clj` `settings` (line ~271) | every setting the report shows, in print order: `:var`, `:group`, `:note`, `:get` (the resolved value), `:secret?` (never printed, only set or not), `:critical?` with its `:fix`, `:accepts` and `:expects` (a value outside them is reported as ignored) |
| Branding names | `config.clj` `branding-vars` (line ~217) | the 21 `APP_*` names, reported as a count only ("7 of 21 set") |
| The report | `config.clj` `report`, `report-lines`, `print-report!` (line ~328-430) | each row's value, SET or DEFAULT, and whether a bad value was ignored; ASCII only, so it reads the same in log files |
| When it prints | `pedestal.clj` `arm-boot-report!` (line ~111) | fires when Jetty reports itself started, so the report is the **last thing in the start-up output**, where an operator actually looks |
| Secrets | [secrets-in-boot-output.md](secrets-in-boot-output.md) | why secrets are dropped in `report`, not at print time |

Not in the report: the "registration is DISABLED" warning (`routes.clj` ~line 111) prints on its
own; [blank-env-values.md](blank-env-values.md) has it as an open item to fold in.

## Row keys

| Key | Meaning |
| --- | --- |
| `:var` | the environment name |
| `:group` | the bracketed heading it prints under |
| `:note` | what the setting does, at most 60 characters (`notes-are-short` checks it) |
| `:get` | accessor for the value in force; without it the raw env value is shown |
| `:since` | release that added it: `"2.5"` or `current-release` |
| `:secret?` | never printed, only `set` / `NOT SET` |
| `:redact?` | printed with passwords masked (`redact-secrets`), e.g. `DATOMIC_URL` |
| `:critical?` + `:fix` | absence breaks the site; `:fix` is the `(!!)` text |
| `:accepts` + `:expects` | a predicate on the raw text, and what to call a value it rejects; a rejected value is a `(!)` line and the row reads DEFAULT. Capacity rows get `positive-int-text?` without saying so |

## Four lists, and where a setting is lost (found 2026-10-19)

The registry is what the server reads. Four hand-kept lists decide what an operator can set, and
nothing checks them against the registry:

| List | Used by | What it decides |
| --- | --- | --- |
| `.env.example` | bare metal (`scripts/common.sh` offers to copy it), and operators reading what exists | what anyone knows they can set |
| `docker-compose.yaml`, `orcpub` service `environment:` | Docker Compose | **what reaches the app container at all.** No `env_file`: `.env` only fills `${VAR:-default}` in this list |
| `scripts/swarm.sh` `_a_env_defaults` | `./run --swarm` (fresh), and `--upgrade-swarm`, which keeps the existing env block and appends any of these it lacks | what reaches the app under Swarm |
| `./run` `write_env_file` | a fresh `./run` install writes `.env` from this template; `--upgrade` fixes old formats and fills gaps | what a new install's `.env` contains |

**Fixed 2026-10-19 on `feature/email-and-alerts` (`aaa4a9801`).** Compose, the Swarm list and
`.env.example` now name every registry row and branding name; the empty default (`${X:-}`) means
the default, because `orcpub.env/value` and `env-raw` treat blank as unset. `./run`'s own `.env`
template was left alone: it writes the values a fresh install needs, and every other setting is
optional.

`settings_lists_test.clj` keeps them in step:
- every name the server reads (`env/value`, `env/flag?`, `env-raw`, `positive-int-env` in
  `src/clj`) is a registry row or branding name, except a short dev-only set (`ORCPUB_HTTP_HOST`,
  `PORTRAIT_DEV_SOLID`, `HOSTNAME`);
- every registry row and branding name is in the compose `orcpub` environment, in
  `swarm.sh` `_a_env_defaults`, and in `.env.example` (as `NAME=` or `# NAME=`).
Each check was seen failing by deleting one name from each list in turn.

Before the fix (for the record): 15 settings and 21 of 22 branding names missing from compose;
Swarm missing those plus 8 compose had; `.env.example` missing the 15 and `APP_EMAIL_SIGNOFF`.
Upstream `develop` (what the live site runs) had 15 names in both lists, so the branding gap is
a pre-existing bug there; the changelog has it under Fixed.

**Upgrade safety, checked by driving `swarm.sh` directly** (a harness sourcing it with stub
loggers): a fresh stack file differs from the old one only in its env lines; upgrading an
old-style file with an admin's changed value and an extra custom line kept both and appended the
44 new names; the result passes `docker compose config`. `test/docker/test-upgrade.sh` (60 `.env`
upgrade checks, no Docker needed) passes before and after.

**What it means for an admin upgrading**: lines already in their `.env` / Portainer variables that
were silently ignored start working, `APP_*` ones overriding the branding file. Portainer keeps
its own copy of the stack file, which must be regenerated (`./run --upgrade-swarm`) and pasted;
`--upgrade-swarm` reads the file on disk, not Portainer's copy. Written up for operators in
`docs/DOCKER.md` ("Upgrading an Existing Install" and "Upgrading a Portainer stack").

## Adding a setting

1. Read it with `orcpub.env/value`, behind an accessor in `config.clj` when it has a default.
2. Add a row to `settings`, with `:get` pointing at that accessor, a `:note` an operator can act on,
   `:accepts`/`:expects` when not every string is valid, and `:since current-release`.
   `every-setting-says-which-release-added-it` fails a row without `:since`.
3. Document it in `docs/ENVIRONMENT.md`, beside the settings of the same group, and add it to that
   page's "New in <release>" table. Human voice: what it does, its default, what happens.
4. Add it to `docker-compose.yaml` (the `orcpub` environment), `scripts/swarm.sh`
   `_a_env_defaults` and `.env.example` with a one-to-two-line description. `settings_lists_test`
   fails until all three have it.

## Releases and the "NEW IN" block (built 2026-10-19, `feature/email-and-alerts`, `d8cdfb4a6`)

- `config/current-release` names the release this build ships as ("Summer Patch").
- Every row has `:since`: `"2.5"` for the 14 settings upstream's last release already read
  (checked by grepping `upstream/develop` for each name), `current-release` for the rest.
- `report-lines` ends with `NEW IN <RELEASE>: n settings not set yet`, then the `current-release`
  rows still unset as names only, comma-separated and wrapped at 100 characters (their notes are in
  the table above), after the `(!!)` blocks: the last lines of the start-up output. Before it, a
  DEPLOYMENT block (`deployment-lines`): where crash files and alerts land, Java's memory limit,
  and `deployment-notes`. Groups print once each, in order of first appearance (2026-10-24;
  before, a group split by row order printed twice). Tidied from 88 to 71 lines that day.
- **At the next release:** change `current-release`. Rows keep their `:since`, so the block then lists
  only what that release adds. Re-label the old rows' `:since` only if the block should say which
  release added them; nothing else reads it.

## Branding in the report (built 2026-10-19)

- Still a count ("n of 22 set"), not rows. `APP_EMAIL_SIGNOFF` was missing from `branding-vars`;
  `every-branding-setting-is-counted` reads `branding.clj` for every `:app-*` keyword and the social
  list, so a new one cannot be forgotten again.
- A branding value that fails its check (`checked-url` and friends) is recorded in
  `branding/ignored` and printed as a `(!)` line, beside the other rejected settings.
  `config` now requires `orcpub.fork.branding`; branding requires nothing of `config`.

## Gaps known on 2026-10-17

- **Branding defaults are not shown.** The generic fallbacks in `fork/branding.clj` appear nowhere in
  the report. (A rejected value and the missing `APP_EMAIL_SIGNOFF` were fixed 2026-10-19, above.)

## "New in this release": built, see above

The owner's ask (2026-10-17): new settings highlighted at the end of the start-up output while
empty, and listed for admins in the human docs. The human list is the "New in the Summer Patch"
table at the top of `docs/ENVIRONMENT.md`.
