# Branding & Integrations

How to customize the app's identity and add third-party integrations.

## Branding

All branding values are configured via environment variables in `.env`. Defaults are set in `src/clj/orcpub/branding.clj`.

Server-side values reach the browser through a config bridge: `branding.clj` builds a map, `index.clj` injects it as `window.__BRANDING__` JSON in `<head>`, and `branding.cljs` reads it at runtime.

### App Identity

| Env Var | Default | Where it shows up |
|---------|---------|-------------------|
| `APP_NAME` | OrcPub | Page titles, emails, privacy policy, OG tags |
| `APP_LOGO_PATH` | /image/orcpub-logo.svg | Header, splash page, privacy page |
| `APP_OG_IMAGE` | /image/orcpub-logo.png | Social sharing preview |
| `APP_TAGLINE` | D&D 5e character builder... | OG meta tags |
| `APP_PAGE_TITLE` | OrcPub: D&D 5e... | Browser tab title |

### Copyright & Contact

| Env Var | Default | Where it shows up |
|---------|---------|-------------------|
| `APP_COPYRIGHT_HOLDER` | OrcPub | Footer |
| `APP_COPYRIGHT_YEAR` | 2025 | Footer |
| `APP_SUPPORT_EMAIL` | *(empty = hidden)* | Privacy page, error messages |
| `APP_HELP_URL` | *(empty = hidden)* | Footer help link |

### Email

| Env Var | Default | Where it shows up |
|---------|---------|-------------------|
| `APP_EMAIL_SENDER_NAME` | OrcPub Team | "From" display name |
| `EMAIL_FROM_ADDRESS` | no-reply@orcpub.com | "From" address |

### Social Links

Shown in the app header/footer when non-empty. Leave unset to hide.

| Env Var | Example |
|---------|---------|
| `APP_SOCIAL_PATREON` | `https://www.patreon.com/YourProject` |
| `APP_SOCIAL_FACEBOOK` | `https://www.facebook.com/groups/yourgroup/` |
| `APP_SOCIAL_TWITTER` | `https://twitter.com/yourhandle` |
| `APP_SOCIAL_REDDIT` | `https://reddit.com/r/yoursubreddit` |
| `APP_SOCIAL_DISCORD` | `https://discord.gg/your-invite` |

When `APP_SOCIAL_PATREON` is set, a supporter button appears in the header.

### Field Limits

Input validation constraints for form fields.

| Env Var | Default | Used for |
|---------|---------|----------|
| `APP_FIELD_LIMIT_NOTES` | 50000 | Character notes, backstory |
| `APP_FIELD_LIMIT_TEXT` | 255 | Name fields, short text |
| `APP_FIELD_LIMIT_NUMBER` | 7 | Numeric inputs |

---

## Integrations

Third-party services (analytics, ads) are managed through two files:

- **`integrations.clj`** (server-side) — injects `<script>` tags in `<head>`
- **`integrations.cljs`** (client-side) — provides lifecycle hooks and UI components

### Analytics & Ads

| Env Var | Default | What it enables |
|---------|---------|-----------------|
| `MATOMO_URL` | *(empty = disabled)* | Matomo analytics tracking |
| `MATOMO_SITE_ID` | *(empty = disabled)* | Matomo site ID |
| `ADSENSE_CLIENT` | *(empty = disabled)* | Google AdSense |

### Integration Hooks

The app calls these functions at specific points. By default they're no-ops — override them in `integrations.cljs` to add custom behavior.

**Lifecycle hooks** (called from events/views):

| Function | When it's called |
|----------|-----------------|
| `track-page-view!` | Every route change |
| `on-app-mount!` | App root component mount |
| `track-character-list!` | Character list render |

**UI hooks** (return hiccup or nil):

| Function | Where it renders |
|----------|-----------------|
| `content-slot` | Content page body (2 slots) |
| `supporter-link` | App header |
| `support-banner` | Content page top |
| `pdf-options-slot` | Below PDF sheet options |
| `share-links` | Character page + builder |
| `share-link-www` | Character list items |

---

## Adding a New Integration Hook

1. Add the stub function in `integrations.cljs` (empty body or `nil` return)
2. Wire the call site in the appropriate shared file (views.cljs, etc.)
3. Implement the real behavior in the stub body

---

## Files That Read Config

| File | What it reads |
|------|--------------|
| `branding.clj` | All `APP_*` env vars |
| `integrations.clj` | `MATOMO_URL`, `MATOMO_SITE_ID`, `ADSENSE_CLIENT` |
| `index.clj` | Calls `branding/client-config` + `integrations/head-tags` |
| `privacy.clj` | `branding/*` for app name + `integrations/head-tags` for scripts |
| `email.clj` | `branding/email-from-address`, `branding/email-sender-name` |
| `branding.cljs` | Reads `window.__BRANDING__` (injected by index.clj) |
| `integrations.cljs` | Reads `branding/*` via branding.cljs |
| `views.cljs` | `branding/*` + `integrations/*` hooks |
| `events.cljs` | `branding/support-email` |
| `character_builder.cljs` | `integrations/share-links` |
