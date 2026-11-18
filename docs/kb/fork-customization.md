# Fork Customization — Override Files, Branding & Integrations

How the public repo supports fork-specific customization without touching shared
code. All fork-specific behavior lives in 6 override files. Shared files call
the same API on both branches — they just get different results.

## Override File Pattern

6 files differ between public and production, now organized under a `fork/`
subdirectory. On merge: **keep production's version**.
Everything else (views.cljs, events.cljs, email.clj, privacy.clj, character_builder.cljs)
is identical on both branches.

| File | Public repo | Production override |
|------|-------------|---------------------|
| `fork/branding.clj` | OrcPub defaults, empty social links | DMV defaults, real social URLs |
| `fork/branding.cljs` | OrcPub fallbacks | DMV fallbacks |
| `fork/user_tier.cljs` | `:user-tier` → always `:free` | Derived from `:patron`/`:patron-tier` |
| `fork/user_data.clj` | Pass-through stubs | Adds patron fields to API response |
| `fork/integrations.clj` | Empty `head-tags`, no CSP domains | Matomo + AdSense script injection |
| `fork/integrations.cljs` | No-op stubs + basic share links | Full Matomo/AdSense + tier-gated UI |
| `resources/public/patron-credits.md` | Empty (the What's New panel thanks patrons with no names) | DMV's patron names, by tier (BRANDING-AND-INTEGRATIONS.md, "Patron credits") |

**Note:** Files moved from `src/clj/orcpub/<name>` to `src/clj/orcpub/fork/<name>`
(and corresponding cljs paths) as of commit 69eafaad. Namespaces changed from
`orcpub.<name>` to `orcpub.fork.<name>`. 9 consumer files updated.

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│ Server-side (CLJ)                                                │
│                                                                  │
│  fork/branding.clj ──→ index.clj (OG meta, window.__BRANDING__ JSON)│
│       │           ──→ email.clj (sender name, from address)        │
│       │           ──→ privacy.clj (legal doc brand references)     │
│       │                                                            │
│  fork/integrations.clj ──→ index.clj (head-tags: SDK scripts)     │
│       │               ──→ privacy.clj (head-tags for terms pages)  │
│       │               ──→ pedestal.clj (csp-domains → CSP header)  │
│                                                                    │
│  fork/user_data.clj ──→ routes.clj (API response enrichment)      │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│ Client-side (CLJS)                                               │
│                                                                  │
│  fork/branding.cljs ──→ reads window.__BRANDING__ at load time   │
│       │            ──→ any CLJS file via [orcpub.fork.branding]  │
│                                                                  │
│  fork/user_tier.cljs ──→ registers :user-tier re-frame sub       │
│                                                                  │
│  fork/integrations.cljs ──→ views.cljs (UI hooks + lifecycle)    │
│       │                ──→ events.cljs (track-page-view!)        │
│       │                ──→ character_builder.cljs (share-links)  │
│                                                                  │
│  cookies.js ──→ index.clj (vanilla JS cookie banner)             │
└──────────────────────────────────────────────────────────────────┘
```

## Server → Client Config Bridge

`environ.core/env` is JVM-only. CLJS gets server config via JSON injection:

```
.env → branding.clj (reads env vars)
           ↓
      client-config (builds map)
           ↓
      index.clj (serializes to JSON)
           ↓
      <script>window.__BRANDING__ = {...};</script>
           ↓
      branding.cljs (reads at namespace load time)
```

## Branding (`src/clj/orcpub/fork/branding.clj`)

Moved to [branding-setup.md](branding-setup.md) on 2026-10-19: every value, its env name, default,
check and users; the browser bridge; the two ways a deployment brands itself; adding a value. The
table that was here had drifted (it gave `no-reply@orcpub.com` as the From default and lacked the
sign-off, the checks, the copyright link and the portrait artists).

## Integrations — Server-side (`fork/integrations.clj`)

Provides `<head>` tags for third-party SDKs and CSP domains.

| Env Var | What it enables |
|---------|-----------------|
| `MATOMO_URL` + `MATOMO_SITE_ID` | Matomo analytics |
| `ADSENSE_CLIENT` | Google AdSense SDK script |
| `ADSENSE_SLOT` | AdSense ad slot ID (passed to CLJS via `__INTEGRATIONS__` bridge) |

Empty value = disabled. CSP domains are auto-derived from enabled integrations.

**Config bridge:** `integrations.clj/client-config` → `index.clj` injects as `window.__INTEGRATIONS__` JSON → `integrations.cljs` reads at namespace load time. Same pattern as `window.__BRANDING__`.

**CSP flow:**
```
integrations/csp-domains → pedestal.clj → csp/build-csp-header
```

## Integrations — Client-side (`fork/integrations.cljs`)

### Lifecycle Hooks (no return value)

| Function | Signature | Called From |
|----------|-----------|-------------|
| `track-page-view!` | `[route]` | events.cljs `:route` handler |
| `on-app-mount!` | `[{:keys [user-tier username email]}]` | views.cljs `content-page` mount |
| `track-character-list!` | `[character-count user-tier]` | views.cljs character list |

**Important:** `track-page-view!` is called from the `:route` event handler (single
choke point), NOT from render bodies. Render bodies fire on every React re-render.

### UI Hooks (return hiccup or nil)

| Function | Signature | Called From | Public Returns |
|----------|-----------|-------------|----------------|
| `content-slot` | `[user-tier]` | views.cljs content page (2 slots) | `nil` |
| `supporter-link` | `[user-tier mobile? icon-fn]` | views.cljs app header | Patreon button when URL set |
| `support-banner` | `[opts-map]` | views.cljs content page | `nil` |
| `pdf-options-slot` | `[user-tier]` | views.cljs PDF options | `nil` |
| `share-links` | `[id character-name]` | views.cljs + character_builder.cljs | Single email link |
| `share-link-www` | `[id]` | views.cljs character list | Basic www link |

### Key patterns

- **`supporter-link`** receives `icon-fn` (the `svg-icon` function) as a parameter
  to avoid circular deps with views.cljs.
- **`content-slot`** is self-gating — production checks tier internally, public
  always returns nil. Callers don't need to gate.
- **`share-links`** returns a **vector of hiccup elements** (not a single element).
  Callers use `into`/`concat` to merge with other button configs.

## User Tier (`fork/user_tier.cljs`)

Registers the `:user-tier` re-frame subscription.

| Branch | Returns |
|--------|---------|
| Public | Always `:free` |
| Production | Derived from `:patron` + `:patron-tier` |

All tier gating in shared code uses `@(subscribe [:user-tier])`.

## Adding a New Integration Hook

1. Add stub on public first (empty body or `nil` return)
2. Wire the call site in shared code (views.cljs, events.cljs, etc.)
3. Implement real behavior on production
4. Add to override-api-reference.md (`.claude/override-api-reference.md`)

## Naming Conventions

- **No monetization language on public** — `content-slot` not `ad-banner`,
  `pdf-options-slot` not `pdf-upsell`
- **"default-tier"** in production docstrings, not "free-tier"
- Neutral docstrings on public: "Fork overrides: ..." not "DMV: ..."

## Cherry-Picking Between Branches

Cherry-picks between `dmv/` and `breaking/` work cleanly because both branches
share the same `fork/` directory structure with identical namespace paths. The
override files have different implementations (DMV has real values, breaking/
has env-var-gated stubs), but the shared consumer files call the same API.

**What conflicts:** Override files (`fork/*.clj`, `fork/*.cljs`) — expected,
keep the target branch's version.

**What applies cleanly:** Shared consumer files (views.cljs, events.cljs,
email.clj, etc.) — same API calls, no divergence.

**One exception found:** `views.cljs` `componentDidMount` — if one branch has
an `on-app-mount!` call and the other doesn't, the lifecycle method body differs.
This is a call-site addition, not an override file change.

See also `dmv-production-changes.md` section 9 for git ref namespace collision
when pushing branches that share a prefix with an existing branch name.

## Merge Strategy

| File type | On merge public → production |
|-----------|------------------------------|
| Override files (6 above) | Conflict → **keep ours (production)** |
| Shared files | No conflict — same API calls |

## The fork contract, and how it broke (2026-11-14)

"Keep ours" works only while a fork file holds settings and small hooks. Between the
2026-08-08 DMV sync and 2026-11-12, integration put logic every fork needs into two of them,
so DMV's whole-file copy would have dropped it:

- `fork/integrations.cljs`: the whole short-share-links feature (copy, token, homebrew upload,
  refresh, revoke, expiry), about 315 of its 424 lines (5ef7939b3, 79b79f9d9, 124c1c626 and the
  rest of 2026-09-13 to 09-15). A later comment cleanup (d499fce5c) rewrote the ns docstring to
  describe it, where the docstring was the rule.
- `fork/branding.clj`: the value checks and the rejected-values list the boot report reads
  (17db38780, d8cdfb4a6), email readiness (b032c4d81), artist-credit overrides (2b8e88a2c,
  b12977830), and `client-config`, the browser bridge. DMV's own copy already carried a
  `client-config` and `email-configured?`, so that half predates September.

Each agent copied what the file already looked like; nothing failed. **Fixed on
`fix/fork-files-data-only`:** the logic moved to shared namespaces (`orcpub.env`,
`orcpub.email`, `orcpub.artist-credit`, `orcpub.index`, `views/share.cljs`), DMV's
`?frame=true` share links became one hook (`integrations/share-url`), and
`test/clj/orcpub/fork_contract_test.clj` lists every setting and hook shared code may use.
It fails when shared code uses anything else from a fork file, and, run on a fork, when the
fork's copy lacks a listed var. **Adding to the contract** means every fork must supply it;
logic every fork needs alike goes in shared code instead.

## Patron credits and support links (owner, 2026-11-12/13)

Patron credits are a fork stub: the public repo's hook returns nil and the tab is hidden. The credits
file is markdown; any line starting with `#` is a tier heading (dropped with no names); there are no
comment lines. Never paste the Patreon CSV (emails; the repo is public). Owner's wording: "The server
is supported by patrons on Patreon. Thank you for helping keep our lights on!" Funding is labelled
tiles ("Support the server" + Patreon, "Support the dev" + Buy Me a Coffee), not text links. Use
`minmax(0,1fr)` for equal tiles with nowrap text. Utility margins like `.m-l-5` are `!important`:
change the class, not a nested override. Support links never displace legal text.

## Cookie Consent

Vanilla JS (`resources/public/js/cookies.js`), loaded server-side in `index.clj`.
NOT a CLJS component. Forks customize by replacing `cookies.js`.
