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
| `:note` | one line an operator can act on; also printed in the NEW IN block |
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

Gaps on 2026-10-19 (`feature/email-and-alerts`):

- **Not in the compose list** (set in `.env`, never reach a Docker app): `ALERT_SPOOL_DIR`,
  `ALERT_MAX_PER_HOUR`, `ORCPUB_HTTP_MAX_THREADS`, `ORCPUB_PDF_CONCURRENCY`,
  `ORCPUB_PDF_QUEUE_TIMEOUT_MS`, `ORCPUB_PDF_MAX_RETRIES`, `ORCPUB_PDF_MAX_CASTER_SECTIONS`,
  `ORCPUB_PDF_MAX_CARDS`, `ORCPUB_PORTRAIT_CONCURRENCY`, `ORCPUB_PORTRAIT_QUEUE_TIMEOUT_MS`,
  `ORCPUB_SHARE_MAX_UPLOAD_KB`, `ORCPUB_SHARE_MAX_TEXT_KB`, `ORCPUB_SHARE_MAX_ACCOUNT_KB`,
  `ORCPUB_SHARE_PRUNE_DAYS`, `ORCPUB_PWNED_CHECK`, and 21 of the 22 `APP_*` branding names (only
  `APP_URL` is passed).
- **Not in the Swarm list either, though compose has them:** `ALLOW_UNVERIFIED_REGISTRATION`,
  `APP_URL`, `DEV_MODE`, `EMAIL_ADMIN_TO`, `LOAD_HOMEBREW_URL`, `PORTRAIT_ARTISTS`,
  `PORTRAIT_STORE_DIR`, `PROFILE_ENCRYPTION_KEYS` (the last can still arrive as a Docker secret).
  An upgraded Swarm file keeps whatever its old env block had.
- **Not in `.env.example`:** the same 15 settings, and `APP_EMAIL_SIGNOFF`.
- Unaffected: values read from `/run/secrets/*` (`signature`, `datomic_password`,
  `profile_encryption_keys`), and branding set by editing `fork/branding.clj` (DMV production's
  route, [branding-setup.md](branding-setup.md)).

The only symptom is the boot report showing `DEFAULT` for something the operator set. The owner
noted (2026-10-19) that the production admin now configures through env and config files, so this
matters. The setup scripts (`./run`, `--upgrade`, `swarm.sh`) were reworked heavily to make
upgrades safe: a fix must keep their upgrade behaviour, i.e. add names to their lists rather than
change how they carry an old file forward. [docker-setup-flow.md](docker-setup-flow.md).

## Adding a setting

1. Read it with `orcpub.env/value`, behind an accessor in `config.clj` when it has a default.
2. Add a row to `settings`, with `:get` pointing at that accessor, a `:note` an operator can act on,
   `:accepts`/`:expects` when not every string is valid, and `:since current-release`.
   `every-setting-says-which-release-added-it` fails a row without `:since`.
3. Document it in `docs/ENVIRONMENT.md`, beside the settings of the same group, and add it to that
   page's "New in <release>" table. Human voice: what it does, its default, what happens.
4. Add it to the four lists above, or it works on bare metal only.

## Releases and the "NEW IN" block (built 2026-10-19, `feature/email-and-alerts`, `d8cdfb4a6`)

- `config/current-release` names the release this build ships as ("Summer Patch").
- Every row has `:since`: `"2.5"` for the 14 settings upstream's last release already read
  (checked by grepping `upstream/develop` for each name), `current-release` for the rest.
- `report-lines` ends with `NEW IN <RELEASE>: n settings not set yet`, listing the `current-release`
  rows still unset, after the `(!!)` blocks: the last lines of the start-up output.
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
