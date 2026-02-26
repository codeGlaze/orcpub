# Fork Customization — Branding, Integrations, and CSP

How the public repo supports fork-specific customization (branding, analytics,
ads, etc.) without requiring source code changes. Forks override via env vars
and file-level overrides.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│ Server-side (CLJ)                                                   │
│                                                                     │
│  branding.clj ──→ routes.clj (OG meta title/desc/image)            │
│       │      ──→ email.clj (sender name, greeting, subjects)       │
│       │      ──→ views_2.cljc (splash logo, copyright)             │
│       │      ──→ privacy.clj (legal doc brand references)          │
│       │                                                             │
│  integrations.clj ──→ index.clj (head-tags: SDK scripts)           │
│       │           ──→ pedestal.clj (csp-domains → CSP header)      │
│       │                                                             │
│  csp.clj ←── pedestal.clj (merges integration domains into CSP)    │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ Client-side (CLJS)                                                  │
│                                                                     │
│  integrations.cljs ──→ events.cljs (:route handler calls            │
│       │                 track-page-view! on every navigation)       │
│       │             ──→ views.cljs (ad-banner component hook)       │
│                                                                     │
│  cookies.js ──→ index.clj (vanilla JS cookie banner, not CLJS)     │
└─────────────────────────────────────────────────────────────────────┘
```

## Branding (`src/clj/orcpub/branding.clj`)

Centralizes app identity. All values are env-var-gated with neutral defaults.

| Var | Env Var | Default | Used By |
|-----|---------|---------|---------|
| `app-name` | `APP_NAME` | `"OrcPub"` | email, routes, privacy, views_2 |
| `app-tagline` | `APP_TAGLINE` | D&D 5e description | routes (OG meta) |
| `default-page-title` | `APP_PAGE_TITLE` | `"OrcPub: D&D 5e..."` | routes (OG/title) |
| `logo-path` | `APP_LOGO_PATH` | `"/image/orcpub-logo.svg"` | views_2, privacy |
| `og-image-filename` | `APP_OG_IMAGE` | `"/image/orcpub-logo.png"` | routes (OG meta) |
| `copyright-holder` | `APP_COPYRIGHT_HOLDER` | `"OrcPub"` | views_2 footer |
| `copyright-year` | `APP_COPYRIGHT_YEAR` | `"2025"` | views_2 footer |
| `email-sender-name` | `APP_EMAIL_SENDER_NAME` | `"OrcPub Team"` | email from/body |
| `social-links` | `APP_SOCIAL_*` | all `""` (hidden) | views.cljs header |

### Not Yet Wired (future pass)
- `views.cljs` — logo, social links, "OrcPub" text (CLJS-side, needs `goog-define` or server-injected values)
- `styles/core.clj` — header background image path
- `index.clj` — `og:site_name` / `twitter:*` tags (not yet added to breaking/)

## Integrations

### Server-side (`src/clj/orcpub/integrations.clj`)

Provides `<head>` tags for third-party SDKs (analytics, ad networks). Stub
returns `()` for `head-tags` and `{}` for `csp-domains`.

**Pattern:**
1. Define env-var-gated config: `(def my-id (env :my-id))`
2. Write a tag function returning hiccup or nil
3. Add to `head-tags` concat list
4. Add required domains to `csp-domains`

**CSP flow:**
```
integrations/csp-domains → pedestal.clj reads :connect-src/:frame-src
  → csp/build-csp-header merges them into the CSP header
```

### Client-side (`src/cljs/orcpub/integrations.cljs`)

No-op stubs for in-app integration hooks. Forks override these functions.

| Function | Purpose | Wired Into |
|----------|---------|------------|
| `track-page-view!` | Analytics page navigation tracking | `:route` event (events.cljs) |
| `ad-banner` | Ad placement component (returns nil) | views.cljs (placeholder) |

**Important:** `track-page-view!` is called from the `:route` event handler
(single choke point), NOT from render bodies. Calling from render = inflated
analytics on every React re-render.

### Server → Client Config Bridge

For forks needing to pass server-side env vars to CLJS components:
1. Add `client-config` function to `integrations.clj`
2. Inject as JS global in `index.clj` via `script-tag`
3. Read from `js/window.__INTEGRATIONS__` in CLJS

See commented example in `integrations.clj`.

## Cookie Consent

Cookie banner is vanilla JS (`resources/public/js/cookies.js`), loaded
server-side in `index.clj`. NOT a CLJS component. Forks customize by
replacing `cookies.js` and updating the init call in `index.clj`.

## CSP Extensibility (`src/clj/orcpub/csp.clj`)

`build-csp-header` accepts `:extra-connect-src` and `:extra-frame-src`
sequences. These are populated from `integrations/csp-domains` via
`pedestal.clj`'s nonce interceptor.

When no integrations are configured: CSP is `connect-src 'self'` with
no `frame-src` directive. When integrations add domains: they appear
in `connect-src` and a `frame-src 'self' ...` directive is added.

## Fork Upgrade Checklist

When upgrading a fork from a new public repo release:

1. **branding.clj** — no conflicts if fork only sets env vars
2. **integrations.clj** — merge carefully; fork has real implementations
3. **integrations.cljs** — fork overrides functions; merge new stubs
4. **views.cljs** — still has hardcoded branding (CLJS side not yet centralized)
5. **cookies.js** — fork may have custom version; manual merge
6. **csp.clj** — no conflicts if fork uses `csp-domains` properly
7. **.env** — add any new env vars from `.env.example`
