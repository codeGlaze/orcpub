# Override API Reference

Cross-branch integration points. Public repo has stubs, DMV overrides with real implementations. Shared files call the same API on both branches.

## Override Files (conflict on merge → "keep ours")

| File | Public | DMV |
|------|--------|-----|
| `branding.clj` | OrcPub defaults, empty social links | DMV defaults, real social URLs |
| `branding.cljs` | OrcPub fallbacks | DMV fallbacks |
| `user_tier.cljs` | `:user-tier` → always `:free` | Derives from `:patron`/`:patron-tier` |
| `user_data.clj` | Pass-through stubs | Adds patron fields to API response |
| `integrations.clj` | Empty `head-tags` | Matomo + AdSense script injection |
| `integrations.cljs` | No-op stubs + basic share links | Full Matomo/AdSense + tier-gated UI |

## integrations.cljs API

### Lifecycle Hooks

| Function | Signature | Called From |
|----------|-----------|-------------|
| `track-page-view!` | `[route]` | `events.cljs` `:route` handler |
| `on-app-mount!` | `[{:keys [user-tier username email]}]` | `views.cljs` `content-page` `component-did-mount` |
| `track-character-list!` | `[character-count user-tier]` | `views.cljs` character list render |

### UI Hooks (return hiccup or nil)

| Function | Signature | Called From | Public Returns |
|----------|-----------|-------------|----------------|
| `content-slot` | `[user-tier]` | `views.cljs` content-page (2 slots) | `nil` |
| `supporter-link` | `[user-tier mobile? icon-fn]` | `views.cljs` app-header | Patreon button when URL set |
| `support-banner` | `[{:keys [srd-message-closed? hide-header-message? frame? user-tier on-dismiss]}]` | `views.cljs` content-page | `nil` |
| `pdf-options-slot` | `[user-tier]` | `views.cljs` PDF options panel | `nil` |
| `share-links` | `[id character-name]` | `views.cljs` + `character_builder.cljs` char page | Single email link |
| `share-link-www` | `[id]` | `views.cljs` character list item | Basic www link |

### Notes

- `supporter-link` receives `icon-fn` (the `svg-icon` function) to avoid circular deps
- `content-slot` is self-gating — DMV checks tier internally, public always returns nil
- `share-links` returns a **vector of hiccup elements** (not a single element). Callers use `into`/`concat` to merge with other button configs

## branding.clj / branding.cljs API

### Server → Client Config Bridge

`branding.clj` defines `client-config` → `index.clj` serializes as `window.__BRANDING__` JSON → `branding.cljs` reads at namespace load time.

### Values

| Def | Type | Public Default | DMV Default |
|-----|------|----------------|-------------|
| `app-name` | string | `"OrcPub"` | `"Dungeon Master's Vault"` |
| `logo-path` | string | `"/image/orcpub-logo.svg"` | `"/image/dmv-logo.svg"` |
| `copyright-holder` | string | `"OrcPub"` | `"Dungeon Master's Vault"` |
| `copyright-year` | string | `"2025"` | `"2025"` |
| `support-email` | string | `""` (hidden) | `"thDM@dungeonmastersvault.com"` |
| `help-url` | string | `""` (hidden) | `"https://www.dungeonmastersvault.com/help/"` |
| `email-from-address` | string | `"no-reply@orcpub.com"` | `"no-reply@dungeonmastersvault.com"` |
| `social-links` | map | All `""` (hidden) | Real Patreon/Facebook/Twitter URLs |
| `field-limits` | map | `{:notes 50000 :text 255 :number 7}` | Same defaults |

### Server-Only Values (not in client-config)

| Def | Public Default | DMV Default |
|-----|----------------|-------------|
| `app-tagline` | Generic D&D 5e description | DMV-specific description |
| `default-page-title` | `"OrcPub: D&D 5e..."` | `"Dungeon Master's Vault: D&D 5e..."` |
| `og-image-filename` | `"/image/orcpub-logo.png"` | `"/image/dmv-box-logo.png"` |
| `email-sender-name` | `"OrcPub Team"` | `"Dungeon Master's Vault Team"` |

## user_tier.cljs API

| Subscription | Public | DMV |
|-------------|--------|-----|
| `:user-tier` | Always `:free` | Derived from `:patron`/`:patron-tier` (`:free`, `:patron`, `:gold`, etc.) |

## user_data.clj API

| Function | Signature | Public | DMV |
|----------|-----------|--------|-----|
| `enrich-response` | `[data user]` | Pass-through | Adds `:patron` and `:patron-tier` from user entity |
| `registration-defaults` | `[]` | `{}` | `{:orcpub.user/patron false :orcpub.user/patron-tier " "}` |

Called from `routes.clj`:
- `user-body` → `(user-data/enrich-response base-map user)`
- Registration → `(merge base-map (user-data/registration-defaults))`

## Merge Strategy

1. Override files: **always "keep ours" (DMV)**
2. Shared files: **no conflicts** — same API calls, same signatures
3. New integrations function: add stub on public first, then implement on DMV
