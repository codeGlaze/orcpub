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

## Adding a setting

1. Read it with `orcpub.env/value`, behind an accessor in `config.clj` when it has a default.
2. Add a row to `settings`, with `:get` pointing at that accessor, a `:note` an operator can act on,
   and `:accepts`/`:expects` when not every string is valid.
3. Document it in `docs/ENVIRONMENT.md`, beside the settings of the same group.

## Gaps known on 2026-10-17

- **Branding settings are a count, not rows.** A bad value is not shown as ignored, and the defaults
  (the generic fallbacks in `fork/branding.clj`) are not shown at all. #64 added checks to every
  branding value (`checked-url`, `checked-email`, `checked-int`), which warn in the log only.
- **`APP_EMAIL_SIGNOFF`** (added on #64) is missing from `branding-vars`, so it is not even counted.

## Planned: "New in this release" ([plan-email-and-alerts.md](plan-email-and-alerts.md))

Each row gets the release it arrived in (`:since`). The report ends with a block listing that
release's new settings, the unset ones highlighted, so an operator upgrading sees what to set in the
last lines of the start-up output. The same rows feed a "New for admins" section in
`docs/ENVIRONMENT.md`. Two tests keep it from being forgotten: every row has `:since`, and the last
lines of the report list the new unset settings.
