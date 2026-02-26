# Branding & Integrations

How to customize the app's identity and add third-party integrations.

## Branding

All branding values are configured via environment variables in `.env`. Defaults are set in `src/clj/orcpub/fork/branding.clj`.

Now all fork-specific behavior lives in **6 small override files**. Shared files call the same functions on both branches — they just get different results.

### Before

```
views.cljs:438     → hardcoded Patreon URL
views.cljs:1522    → hardcoded ad reload script
views.cljs:1576    → hardcoded donation banner HTML
views.cljs:3769    → hardcoded PDF upsell content
email.clj:93       → hardcoded "no-reply@dungeonmastersvault.com"
privacy.clj:127    → hardcoded ad-network <script> tags
events.cljs:1794   → hardcoded support email
```

### After

```
views.cljs:438     → integrations/supporter-link
views.cljs:1522    → integrations/on-app-mount!
views.cljs:1576    → integrations/support-banner
views.cljs:3769    → integrations/pdf-options-slot
email.clj:93       → branding/email-from-address
privacy.clj:127    → integrations/head-tags
events.cljs:1794   → branding/support-email
```

All the actual content now lives in override files that never conflict on merge.

---

## The 6 Override Files

All live under `src/clj/orcpub/fork/` (server) and `src/cljs/orcpub/fork/` (client). These are the only files that differ between public and production. On merge, always **keep production's version**.

| File | Path | What it controls | Public repo | Production |
|------|------|-----------------|-------------|------------|
| `branding.clj` | `src/clj/orcpub/fork/` | App name, logos, emails, social links, field limits | OrcPub defaults | DMV defaults |
| `branding.cljs` | `src/cljs/orcpub/fork/` | Same values on the client side | OrcPub fallbacks | DMV fallbacks |
| `user_tier.cljs` | `src/cljs/orcpub/fork/` | User tier subscription (`:user-tier`) | Always `:free` | Derived from patron status |
| `user_data.clj` | `src/clj/orcpub/fork/` | API response enrichment | Pass-through | Adds patron fields |
| `integrations.clj` | `src/clj/orcpub/fork/` | Server-side `<head>` script tags | Empty | Matomo + AdSense |
| `integrations.cljs` | `src/cljs/orcpub/fork/` | Client-side UI hooks + analytics | No-op stubs | Full implementation |

Everything else — views.cljs, events.cljs, email.clj, privacy.clj, character_builder.cljs — is **identical** on both branches.

---

## How the Config Bridge Works

Server-side values (from `.env` → `fork/branding.clj`) get to the browser through a JSON bridge:

```
.env  →  fork/branding.clj (reads env vars)
              ↓
         client-config (builds a map)
              ↓
         index.clj (serializes to JSON in <head>)
              ↓
         <script>window.__BRANDING__ = {...};</script>
              ↓
         fork/branding.cljs (reads window.__BRANDING__ at load time)
              ↓
         Any CLJS file can require [orcpub.fork.branding :as branding]
```

A parallel bridge exists for integrations: `fork/integrations.clj` provides `client-config` which index.clj injects as `window.__INTEGRATIONS__`, read by `fork/integrations.cljs`.

Why not just read env vars in ClojureScript? `environ.core/env` is JVM-only. CLJS runs in the browser — it needs the values injected.

---

## Configuration Reference

All values have defaults in `fork/branding.clj`. Set env vars in `.env` to override.

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

- **`fork/integrations.clj`** (server-side) — injects `<script>` tags in `<head>`, exports CSP domain allowlists for `pedestal.clj`
- **`fork/integrations.cljs`** (client-side) — provides lifecycle hooks and UI components, reads config from `window.__INTEGRATIONS__`

### Analytics & Ads

Server-side (`fork/integrations.clj`) injects SDK scripts in `<head>` and exports CSP domain allowlists for `pedestal.clj`. Client-side (`fork/integrations.cljs`) handles in-app behavior, reading ad client/slot IDs from the `window.__INTEGRATIONS__` config bridge.

| Env Var | Default | What it enables |
|---------|---------|-----------------|
| `MATOMO_URL` | *(empty = disabled)* | Matomo analytics tracking |
| `MATOMO_SITE_ID` | *(empty = disabled)* | Matomo site ID |
| `ADSENSE_CLIENT` | *(empty = disabled)* | Google AdSense |

### Integration Hooks

The app calls these functions at specific points. By default they're no-ops — override them in `fork/integrations.cljs` to add custom behavior.

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

1. Add the stub function in `fork/integrations.cljs` (empty body or `nil` return)
2. Wire the call site in the appropriate shared file (views.cljs, etc.)
3. Implement the real behavior in the stub body

---

## User Tier System (fork/user_tier.cljs)

| Branch | `:user-tier` subscription returns |
|--------|----------------------------------|
| Public | Always `:free` |
| Production | Derived from `:patron` + `:patron-tier` → `:free`, `:patron`, `:gold`, etc. |

All tier gating in shared code uses `@(subscribe [:user-tier])`. The integration hooks also self-gate — `content-slot` checks tier internally, so callers don't need to.

---

## Merging

When merging public → production:

| File type | What happens | Action |
|-----------|-------------|--------|
| Override files (6 listed above) | Conflict | **Keep ours (production)** |
| Everything else | No conflict | Auto-merge |

When adding a new integration hook:
1. Add stub on public first (empty body or `nil` return)
2. Add real implementation on production
3. Wire the call site in shared code (same on both branches)

---

## Files That Read Branding/Integration Config

| File | What it reads |
|------|--------------|
| `fork/branding.clj` | All `APP_*` env vars, `EMAIL_FROM_ADDRESS` |
| `fork/integrations.clj` | `MATOMO_URL`, `MATOMO_SITE_ID`, `ADSENSE_CLIENT`, `ADSENSE_SLOT` |
| `index.clj` | Calls `fork/branding/client-config` + `fork/integrations/head-tags` + `fork/integrations/client-config` |
| `privacy.clj` | Calls `fork/branding/*` for names + `fork/integrations/head-tags` for scripts |
| `email.clj` | `fork/branding/email-from-address`, `fork/branding/email-sender-name` |
| `routes.clj` | `fork/branding/*` (app-name), `fork/user-data/*` (response enrichment) |
| `pedestal.clj` | `fork/integrations/csp-domains` (CSP allowlists) |
| `fork/branding.cljs` | Reads `window.__BRANDING__` (injected by index.clj) |
| `fork/integrations.cljs` | Reads `window.__INTEGRATIONS__` + `fork/branding/*` via branding.cljs |
| `views.cljs` | Reads `fork/branding/*` + calls `fork/integrations/*` hooks |
| `events.cljs` | Reads `fork/branding/support-email`, calls `fork/integrations/track-page-view!` |
| `character_builder.cljs` | Calls `fork/integrations/share-links` |
