# Branding and Integrations

Everything that makes the site yours (its name, logos, footer, contact details, social links and
field limits) is set in `.env`. You do not edit code. Set a value, restart the site, and it
applies. If a value is not usable (a help link that is not a web address, for example), the server
ignores it, uses the default, and names it in the boot report.

For how `.env` is read and where to put it, see [ENVIRONMENT.md](ENVIRONMENT.md). The boot report
shows how many of the branding settings you have set.

## App identity

| Variable | Default | Where it shows up |
|----------|---------|-------------------|
| `APP_NAME` | `OrcPub` | Page titles, emails, the privacy policy, link previews |
| `APP_URL` | empty | Privacy policy, and every link in account and artist emails. Set it in production. |
| `APP_LOGO_PATH` | `/image/orcpub-logo.svg` | Header, splash page, privacy page. A path on this site or a web address. |
| `APP_OG_IMAGE` | `/image/orcpub-logo.png` | The picture shown when a link to the site is shared |
| `APP_TAGLINE` | a generic D&D 5e description | The description in search results and link previews |
| `APP_PAGE_TITLE` | `<APP_NAME>: D&D 5e Character Builder/Generator` | Browser tab title |

To use your own logo, put the file in `resources/public/image/` and set `APP_LOGO_PATH` to
`/image/your-file.svg`. Rebuild the image if you run Docker.

## Copyright and contact

| Variable | Default | Where it shows up |
|----------|---------|-------------------|
| `APP_COPYRIGHT_HOLDER` | `OrcPub` | Footer |
| `APP_COPYRIGHT_YEAR` | the current year | Footer |
| `APP_COPYRIGHT_URL` | empty (plain text) | Where the holder's name in the footer links |
| `APP_SUPPORT_EMAIL` | empty (hidden) | Privacy page and error messages. Also where admin alerts and "character won't load" reports go when `EMAIL_ERRORS_TO` is not set. |
| `APP_HELP_URL` | empty (hidden) | Help link in the footer |

## Email identity

| Variable | Default | Where it shows up |
|----------|---------|-------------------|
| `APP_EMAIL_SENDER_NAME` | `<APP_NAME> Team` | The "From" display name |
| `APP_EMAIL_SIGNOFF` | `The <sender name>` | Who the friendlier emails (such as the artist welcome) are signed by, for example "both of us at OrcPub" |
| `EMAIL_FROM_ADDRESS` | `no-reply@example.com` | The "From" address. The default is a placeholder that cannot deliver. Set an address on a domain your mail provider has verified. |

## Social links

A link shows in the header and footer when it has a value. Leave one empty to hide it.

| Variable | Default |
|----------|---------|
| `APP_SOCIAL_PATREON` | `https://www.patreon.com/DungeonMastersVault` |
| `APP_SOCIAL_DEV_TIP` | `https://buymeacoffee.com/tinkan` |
| `APP_SOCIAL_FACEBOOK` | empty (hidden) |
| `APP_SOCIAL_BLUESKY` | empty (hidden) |
| `APP_SOCIAL_TWITTER` | empty (hidden) |
| `APP_SOCIAL_REDDIT` | empty (hidden) |
| `APP_SOCIAL_DISCORD` | empty (hidden) |

The Patreon ("Support the Server") and developer tip ("Fuel the Dev") links default to the
maintainer's. Set either to another address to change it. A blank value counts as unset, so it
does not hide them. The supporter button shows in the header whenever the Patreon link has a
value. The What's New panel's footer shows the Patreon link as a Server icon and the tip link as a
Dev icon, each only when set, and the site footer adds "Support the dev" for the tip link.

## Patron credits

When `APP_SOCIAL_PATREON` is set, the What's New panel closes its release notes with "Made possible
by our patrons" and a thank-you line, reached from a Credits link under the title. The names come
from `resources/public/patron-credits.md`, which the panel fetches the first time it opens. The
file ships empty. Put your own names in it and redeploy.

- One name per line.
- A line that starts with `#` starts a tier. Names before any heading form a list with no tier heading.
- Every tier shows in full except the last. Past 24 names, the last tier shows a random 24,
  reshuffled on each page load, and "Show all N patrons" expands the full list alphabetically.

```markdown
## Archmage
Mira Ashgrove
## Adventurer
Kessa
```

[patron-credits.example.md](patron-credits.example.md) is a filled-in sample with three tiers, list
markers, accents, handles and a last tier long enough to show the "Show all" button. Copy it over
`resources/public/patron-credits.md` to try the panel.

Copy names in by hand. List paid patrons only, as first name and last initial or a handle unless a
patron agreed to more. Never commit Patreon's member export: it holds members' email addresses.

## Field limits

How much a player may type.

| Variable | Default | Used for |
|----------|---------|----------|
| `APP_FIELD_LIMIT_NOTES` | `50000` | Character notes and backstory |
| `APP_FIELD_LIMIT_TEXT` | `255` | Names and short text fields |
| `APP_FIELD_LIMIT_NUMBER` | `7` | Number fields |

Each must be a positive whole number.

## Analytics and ads

This build does not include Matomo or AdSense. `MATOMO_URL`, `MATOMO_SITE_ID`, `ADSENSE_CLIENT`
and `ADSENSE_SLOT` appear as examples in `.env.example`, but nothing reads them, so setting them
has no effect. The files `src/clj/orcpub/fork/integrations.clj` and
`src/cljs/orcpub/fork/integrations.cljs` are the place a fork adds such a service (see below).

## For developers: the fork files

A fork's customizations live in a few small files, so shared code stays the same everywhere and
merges stay clean. On a merge, take the shared code's version of each file, with any new
definitions it adds, and then put the fork's own values back in. Taking either whole file breaks
the build or loses the fork's branding.

| File | What it controls |
|------|------------------|
| `src/clj/orcpub/fork/branding.clj` | Every `APP_*` value above, read from the environment, with its default |
| `src/cljs/orcpub/fork/branding.cljs` | The same values in the browser |
| `src/cljs/orcpub/fork/user_tier.cljs` | The `:user-tier` subscription. It returns `:free` in the public code. |
| `src/clj/orcpub/fork/user_data.clj` | Enrichment of API responses. It passes data through in the public code. |
| `src/clj/orcpub/fork/integrations.clj` | Third-party `<head>` script tags and their CSP domain allowlists. Empty by default. |
| `src/cljs/orcpub/fork/integrations.cljs` | In-app hooks (analytics, banners, share links). Stubs in the public code. |

**How values reach the browser.** `fork/branding.clj` reads the environment and builds a map.
`index.clj` writes it into the page `<head>` as `window.__BRANDING__`, and `fork/branding.cljs`
reads it at load time. Integrations work the same way through `window.__INTEGRATIONS__`.
ClojureScript cannot read environment variables, so the page carries them.

**Hooks the shared code calls** (in `fork/integrations.cljs`): `track-page-view!`,
`on-app-mount!`, `track-character-list!` (no return value); `content-slot`, `supporter-link`,
`support-banner`, `pdf-options-slot`, `share-links`, `share-link-www` (return hiccup or nil).
Gate anything tier-specific with `@(subscribe [:user-tier])`.

**Adding an integration.** Add a stub in the shared file first (empty body or `nil`), then the real
implementation in the fork's copy, then call it from shared code. For a server-side script, gate
it on an environment variable, return hiccup (with the nonce on each `<script>`) from
`head-tags`, and list its domains in `csp-domains`.

**Where each file is used.** `index.clj` and `privacy.clj` read branding and `head-tags`.
`email.clj` reads the sender name and address. `routes.clj` reads the app name and user-data
enrichment. `pedestal.clj` reads `csp-domains`. `views.cljs`, `events.cljs` and
`character_builder.cljs` call the branding values and integration hooks.
