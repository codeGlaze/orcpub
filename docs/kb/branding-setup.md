# Branding: how the branding file works, and how to change or add a value

**What this is for:** where a site's name, logos, links, email identity and form limits come from,
the two ways a deployment sets them, how the browser gets them, and the checklist for adding one.
Checked against `integration` plus `feature/email-and-alerts` on 2026-10-19. Human reference for
operators: `docs/BRANDING-AND-INTEGRATIONS.md`. The wider override-file pattern (integrations, user
tier): [fork-customization.md](fork-customization.md).

## Two ways a deployment brands itself

| Route | Who uses it | How | Reaches Docker / Swarm? |
| --- | --- | --- | --- |
| **Edit the defaults in `fork/branding.clj`** | DMV production (the gitea fork). Its copy of the file carries DMV's name, logos and links as the defaults | the values are compiled into the image | **yes**, nothing to pass through |
| **Environment variables** (`APP_*`, `EMAIL_FROM_ADDRESS`, `PORTRAIT_ARTISTS`) | anyone running the public code unchanged | set in `.env` or the environment | **bare metal yes; Docker no for most** (below) |

On merge between the public repo and the fork, **keep production's `branding.clj`**
([fork-customization.md](fork-customization.md), "Override File Pattern"). The public copy holds
neutral OrcPub defaults.

**Docker gap (found and fixed 2026-10-19, `aaa4a9801`).** Compose and Swarm passed the app only
the names they listed, and branding was missing (only `APP_URL` in compose, none in Swarm), so a
self-hoster's `APP_*` values in `.env` never reached a Docker or Swarm server. Both lists now carry
every branding name; `settings_lists_test` keeps them complete. Consequence for DMV: an `APP_*` line
in its `.env` or Portainer variables, ignored until now, starts overriding its branding file.
[settings-registry-and-boot-report.md](settings-registry-and-boot-report.md), "Four lists".

## How a value is read

- Every value is a `def` in `src/clj/orcpub/fork/branding.clj`, evaluated **once when the namespace
  loads**. Changing one needs a restart.
- Read through `orcpub.env/value`: a blank value counts as unset ([blank-env-values.md](blank-env-values.md)).
- Addresses and numbers are checked: `checked-url` (blank, `http(s)://…`, or a site path `/x`),
  `checked-email`, `checked-int` (positive whole number). A value that fails falls back to its
  default, prints `branding: APP_X ignored (why): value`, and is added to `branding/ignored`, which
  the boot report prints as a `(!)` line ([settings-registry-and-boot-report.md](settings-registry-and-boot-report.md)).
- `PORTRAIT_ARTISTS` is JSON. Each artist's links pass `artist-links/url-problem`; a bad link is
  dropped with a warning, bad JSON drops the whole setting. Applied at load with
  `portrait-assets/set-artist-overrides!`.

## The values

| def | Env var | Default (public) | Check | Used by |
| --- | --- | --- | --- | --- |
| `app-name` | `APP_NAME` | `OrcPub` | — | emails, page titles, privacy pages, index, views |
| `app-tagline` | `APP_TAGLINE` | D&D 5e description | — | OG meta (routes) |
| `app-url` | `APP_URL` | `""` | url | artist emails' links, portrait share cards |
| `default-page-title` | `APP_PAGE_TITLE` | `<app-name>: D&D 5e Character Builder/Generator` | — | `<title>`, OG title |
| `logo-path` | `APP_LOGO_PATH` | `/image/orcpub-logo.svg` | url | header, splash, privacy, portrait |
| `og-image-filename` | `APP_OG_IMAGE` | `/image/orcpub-logo.png` | url | OG image |
| `copyright-holder` | `APP_COPYRIGHT_HOLDER` | `OrcPub` | — | footer |
| `copyright-year` | `APP_COPYRIGHT_YEAR` | current year | — | footer |
| `copyright-url` | `APP_COPYRIGHT_URL` | `""` (plain text) | url | footer |
| `email-sender-name` | `APP_EMAIL_SENDER_NAME` | `<app-name> Team` | — | every email's From name |
| `email-signoff` | `APP_EMAIL_SIGNOFF` | `The <sender name>` | — | artist emails' sign-off |
| `email-from-address` | `EMAIL_FROM_ADDRESS` | `no-reply@example.com` | email | every email's From address |
| `support-email` | `APP_SUPPORT_EMAIL` | `""` (hidden) | email | privacy page, error messages, mailto |
| `help-url` | `APP_HELP_URL` | `""` (hidden) | url | footer help link |
| `social-links` | `APP_SOCIAL_{PATREON,FACEBOOK,BLUESKY,TWITTER,REDDIT,DISCORD}` | `""` each (hidden) | url | header, emails, share links |
| `field-limits` | `APP_FIELD_LIMIT_{NOTES,TEXT,NUMBER}` | 50000 / 255 / 7 | int | form max lengths |
| `portrait-artists` | `PORTRAIT_ARTISTS` | `{}` | JSON + links | artist credits ([artist-links-and-referrer.md](artist-links-and-referrer.md)) |
| `registration-logo-class` | — (constant) | `h-55` | — | login page logo |
| `restrict-print-to-owner?` | — (constant) | `false` | — | print button on the character list |

Derived, not set directly: `sender-configured?` / `email-configured?` (a real From address, not the
placeholder), `report-recipient` (`APP_SUPPORT_EMAIL`, else `EMAIL_ERRORS_TO`).

`no-reply@example.com` is a deliberate placeholder that cannot deliver: an unset From address fails
loudly at the mail provider instead of sending as a domain the site does not own.

## Browser side

`client-config` builds the map the browser needs; `index.clj` writes it into the page as
`window.__BRANDING__`; `src/cljs/orcpub/fork/branding.cljs` reads it once at load. Each CLJS `def`
has its own hard-coded fallback, used only when the page was not served by our server (a REPL, a
test page). Keep those fallbacks equal to the server defaults; `copyright-year`'s is a fixed "2025"
where the server uses the current year, harmless because the server always sends it.

Only what is in `client-config` reaches the browser. Artist credits do not: the browser fetches
them from `/dnd/5e/portrait-artists` when a portrait is shown.

## Adding a branding value

1. A `def` in `fork/branding.clj` with a docstring, read through `env/value` and the matching
   `checked-*` when it is an address or number.
2. Its env name in `config/branding-vars`, so the boot report counts it.
   `every-branding-setting-is-counted` fails when one is missing.
3. If the browser needs it: a key in `client-config` and a `def` with the same fallback in
   `fork/branding.cljs`.
4. Operator docs: a row in `docs/BRANDING-AND-INTEGRATIONS.md`, a commented line in `.env.example`,
   and the "New in <release>" table in `docs/ENVIRONMENT.md`.
5. For Docker and Swarm: `docker-compose.yaml` `environment:` and `scripts/swarm.sh`
   `_a_env_defaults`. `settings_lists_test` fails until both (and `.env.example`) have it.
6. Tell the fork: DMV's `branding.clj` needs the same `def` with its own default, or the merge
   leaves the fork without it.

## Seeing the social links locally, and getting them to prod (2026-11-16)

- **Local, no restart:** `branding/social-links` is a def read per request by `client-config`
  (index.clj injects it as `window.__BRANDING__`), so in a running dev REPL
  `(alter-var-root #'orcpub.fork.branding/social-links assoc :patreon "<url>" :dev-tip "<url>")`
  shows the header button, the landing "Support the Server" / "Fuel the Dev" pills and the What's New
  footer on the next page load. Gone on restart. Persistent: set `APP_SOCIAL_*` in `.env` and start
  with `scripts/start.sh` (plain `lein` does not read `.env`).
- **Prod needs two things:** the code (`:dev-tip` landed on `integration` 2026-11-12; `develop` gets it
  with the release PR #32; the DMV pre-merge, Gitea PR #10, already has it) and `APP_SOCIAL_DEV_TIP`
  set in the deployment's environment. Unset, the link silently does not render; the startup report
  only counts `APP_*` settings. Check after a deploy:
  `curl -s <site>/ | grep -o '"dev-tip":"[^"]*"'`.

## The landing page's pill row (owner, 2026-11-16)

- What's New ranks above the two support links by shape, not size: `.support-pill-whats-new`, an amber
  cap (the icon in `button-ink`) at the left edge, a 1px amber border and a soft glow; hover brightens
  the glow. An amber fill at rest was rejected: it reads as a hover state. The order stays What's New,
  Support the Server, Fuel the Dev (the outer two balance the middle one).
- Stacked (480px and narrower), every pill's icon sits in one left column and the labels share a left
  edge, so the cap reads as that column filled in. Scoped with `:not(.support-pills-compact)`: the
  footer's compact pills share `.support-pills`.
- Always on, not "until seen": the panel opens by itself while a release is unseen and closing it
  stamps it seen, so an unseen-only glow would never be visible.
- The BMAC link is pre-filled in `.env.example` (`APP_SOCIAL_DEV_TIP`), so a new `.env` shows it; a
  running server's own `.env` still needs the line.
