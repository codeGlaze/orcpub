# DMV Production Changes — Full Analysis for Open Source Backport

Captured from the DMV production Gitea instance (`dmv` branch).
Compared against `breaking/2026-stack-modernization` (open source).
Full four-agent scan completed 2026-02-26.

## Summary

The DMV production deployment diverges from the open source repo across
source code, Docker infrastructure, transactor config, nginx routing,
email/auth flow, OG/social meta tags, analytics/ads, frontend assets,
and monitoring. Most changes are DMV-specific branding/monetization.
A handful are genuine fixes or security issues.

---

## Critical Issues to Flag to Admin

### 1. `security.clj` — Rate-limiting broken (PRODUCTION BUG)
DMV removed the call parentheses on `multiple-ip-attempts-to-same-account?`:
```clojure
;; DMV (broken — evaluates as 3 expressions, returns atom deref):
multiple-ip-attempts-to-same-account-aux username @failed-login-attempts-by-username
;; Should be (correct on breaking/):
(multiple-ip-attempts-to-same-account-aux username @failed-login-attempts-by-username)
```
Brute-force login rate-limiting is **non-functional** on DMV production.

### 2. `newrelic.yml` — License key committed in plaintext
License key is hardcoded in the repo. Should be rotated and injected via env var.

### 3. `deploy/transactor.properties` — Hardcoded passwords
Default passwords committed in plaintext. Should use env var substitution.

### 4. Minor bugs in DMV code
- `views_2.cljc` — typo: "Cookie Polciy" (should be "Cookie Policy")
- `views.cljs` — `prn "FRAME?"` debug statement left in production code
- `views.cljs` — Matomo tracking fires inside render function body (every
  React re-render = inflated analytics)
- `pdf_spec.cljc` — dead duplicate `treasure-fields` function

---

## Backport-Worthy Fixes (Priority Order)

### Must Fix
1. **`routes.clj`** — `default-image-url`: `http://` → `https://` for OG meta image
2. **`routes.clj`** — `send-updates?` arity fix through verification chain
3. **`email.clj`** — typo: `"please do no click"` → `"please do NOT click"`
4. **`classes.cljc`** — typo: `"exhaustrion"` → `"exhaustion"` (Barbarian Frenzy)

### Should Consider
5. **`index.clj`** — Twitter Card meta tags (`og:site_name`, `twitter:card`,
   `twitter:title`, etc.) — DMV added these, breaking/ doesn't have them
6. **`host=0.0.0.0`** as transactor default — works in both Compose and Swarm
7. **Configurable token expiry** — env var for JWT lifetime (DMV uses 14 days)
8. **`robots.txt`** — breaking/ has `Disallow: /` (blocks ALL crawlers).
   Should be replaced with a permissive version for production deployments
9. **`events.cljs`** — `::e5/save-to-json` export feature (homebrew as JSON)
10. **`character_builder.cljs`** — input field maxLength (255 text, 50000 textarea)

### Document Only
11. Static `.orcbrew` serving via nginx
12. `/generator/` proxy for secondary services
13. Maintenance page swap pattern
14. Patron/monetization attributes

---

## Recent Fixes Applied (dmv/hotfix-integrations branch)

The following fixes were implemented on the `dmv/hotfix-integrations` branch and
are backport-ready for any fork.

### Auth Guard for API Subscriptions (5539953c)
`reg-sub-raw` subscriptions (`::char5e/characters`, `::party5e/parties`, `:user`)
fired HTTP requests on first subscribe deref even when no auth token existed in
`app-db`. This produced spurious 401 errors in the browser console on every
unauthenticated page load.

**Fix:** Added `(when (:token (:user-data @app-db)) ...)` guard before the `go`
block in each subscription. No token = no request, return empty `[]`.

**Also fixed:** The `:user` sub checked the wrong path (`[:user :token]` instead
of `[:user-data :token]`). The canonical pattern is in `equipment_subs.cljs`.

### Fork Directory Reorganization (69eafaad)
The 6 fork-customization override files were moved into a `fork/` subdirectory
to make the override boundary explicit in the filesystem:

| Before | After |
|--------|-------|
| `src/clj/orcpub/branding.clj` | `src/clj/orcpub/fork/branding.clj` |
| `src/clj/orcpub/integrations.clj` | `src/clj/orcpub/fork/integrations.clj` |
| `src/clj/orcpub/user_data.clj` | `src/clj/orcpub/fork/user_data.clj` |
| `src/cljs/orcpub/branding.cljs` | `src/cljs/orcpub/fork/branding.cljs` |
| `src/cljs/orcpub/integrations.cljs` | `src/cljs/orcpub/fork/integrations.cljs` |
| `src/cljs/orcpub/user_tier.cljs` | `src/cljs/orcpub/fork/user_tier.cljs` |

9 consumer files updated with new require paths. See KB
[email-preferences-implementation.md](email-preferences-implementation.md) for
the full consumer list.

### Email Preferences Feature (62381b85)
Full unsubscribe + preference toggle flow:
- **JWT unsubscribe endpoint** (`GET /unsubscribe?token=...`) — stateless,
  no expiry (CAN-SPAM requires 30-day minimum)
- **My Account toggle** — checkbox on `/account` page, calls `PUT /user`
- **`PUT /user` endpoint** — accepts `{:send-updates? true/false}`, auth required
- **`social-links-footer`** in `email.clj` — self-gating (empty social URLs = hidden)
- See KB [email-preferences-implementation.md](email-preferences-implementation.md)
  for full architecture + test details

### Error Email Hardening (dmv/hotfix-integrations)
`send-error-email` in `email.clj` was overhauled for production reliability:

- **5-minute throttle per fingerprint** — prevents error storms from flooding
  inboxes. Fingerprint = exception class + first orcpub stack frame.
- **Request scrubbing** — strips credentials, cookies, request body, and Datomic
  objects (which can be very large) before including request context in emails.
- **Stack trace filtering** — shows only `orcpub.*` frames for readability,
  falls back to deepest non-infrastructure frame if no orcpub frames exist.
- **Full cause chain rendering** — `(.getCause t)` chain traversal renders
  all nested exceptions, not just the top-level one.
- **Pedestal interceptor metadata** — extracts interceptor name from Pedestal's
  exception-info data when available.

### Pedestal Logging Anti-Pattern (dmv/hotfix-integrations)
Replaced bare `(prn "T" t)` calls in Pedestal catch blocks with structured
logging via `io.pedestal.log/error`:

```clojure
;; BEFORE — bare prn, produces unstructured stdout noise
(catch Throwable t (prn "T" t))

;; AFTER — structured Pedestal logging
(catch Throwable t
  (io.pedestal.log/error :msg "request handler error"
                         :exception t
                         :route (:route context)))
```

This is a general pattern: never use `prn` or `println` for error reporting in
Pedestal services. Use `io.pedestal.log` for structured, level-aware logging.

### Figwheel Codespaces Auto-Detection (37db84fb)
`start.sh` auto-detects GitHub Codespaces and passes `--fw-opts` to override
Figwheel's WebSocket connect URL with the correct `wss://` endpoint. See KB
[remote-dev.md](remote-dev.md) for the full remote development story.

---

## 1. Docker Infrastructure

### Root Dockerfile (`Dockerfile`, new)
- Near-copy of `docker/Dockerfile` from `breaking/` but at repo root for
  standalone builds (not multi-service compose)
- 3-step build: CLJS → AOT+timeout → jar packaging
- **No backport needed** — breaking/ already has unified `docker/Dockerfile`

### Datomic Dockerfiles (`docker/datomic/`, new)
- `Dockerfile` — downloads Datomic Pro 1.0.7482, `eclipse-temurin:21-jre-alpine`
- `start.sh` — raw `sed` substitution (no escaping). Breaking/ fixed this.
- `transactor.properties.template` — same as breaking/'s template
- **No backport needed** — breaking/ is ahead

### Orcpub Dockerfile (`docker/orcpub/Dockerfile`, new)
- Same 3-step build but with `../../` relative paths (invalid for build context)
- **Has a bug** — COPY paths won't work from `docker/orcpub/` context

---

## 2. Transactor Configuration

### `deploy/transactor.properties` (new, production config)
- `protocol=free` (Java 8 only — will crash on Java 21)
- `host=0.0.0.0` with `alt-host=datomic`
- Hardcoded passwords (insecure)
- `write-concurrency=500`, `read-concurrency=1000` (aggressive)

| Setting | DMV production | breaking/ template |
|---------|---------------|-------------------|
| protocol | `free` | `dev` |
| host | `0.0.0.0` | `datomic` |
| passwords | hardcoded | `${VAR}` placeholders |
| write-concurrency | 500 | default (4) |

---

## 3. Nginx Configuration

### `deploy/nginx-dev.conf` (new)
- Proxies `/` to `orcpub-dev:8890`, `/generator/` to `dndgenerator-dev:80`
- Serves `.orcbrew` from nginx static files
- SSL with snakeoil cert
- DMV-specific, don't backport

---

## 4. Email / Auth Flow Changes (`routes.clj`)

1. **`send-updates?` param** threaded through verification (BUG FIX)
2. **`create-login-response`** writes `:last-login`, token 24h → 336h
3. **Registration** adds patron defaults
4. **`user-body`** exposes `:patron`, `:patron-tier`
5. **OAuth commented out** — `#_[orcpub.oauth :as oauth]`
6. **`default-image-url`** — `http://` → `https://` (BUG FIX)

---

## 5. Source Code Changes (CLJ/CLJS)

### Bug Fixes (backport-worthy)
| File | Change |
|------|--------|
| `routes.clj` | `https://` OG meta image URL |
| `routes.clj` | `send-updates?` arity threading |
| `email.clj` | "do no click" → "do NOT click" |
| `classes.cljc` | "exhaustrion" → "exhaustion" |

### Features (evaluate for backport)
| File | Change |
|------|--------|
| `index.clj` | Twitter Card meta tags |
| `events.cljs` | `::e5/save-to-json` (export homebrew as JSON) |
| `character_builder.cljs` | Input field maxLength limits |
| `views.cljs` | Share link split (email vs URL) |

### DMV-Specific (don't backport)
| File | Change |
|------|--------|
| `db/schema.clj` | `:patron`, `:patron-tier`, `:last-login` attributes |
| `routes.clj` | Patron defaults, 14-day token, last-login tracking |
| `privacy.clj` | Complete rewrite with DMV branding |
| `views_2.cljc` | 7 generator buttons, DMV footer |
| `views.cljs` | Patron banner, ad components, Matomo tracking in render |
| `subs.cljs` | `:patron`, `:patron-tier` subscriptions |
| `db.cljs` | `::patron`, `::patron-tier` specs |
| `styles/core.clj` | DMV UI classes, header 227→320 height |
| `constants.cljc` | `header-height` 227→320 |
| `ver.cljc` | 2.4.0.28→2.6.0.0 |

### Bugs Introduced by DMV
| File | Change | Impact |
|------|--------|--------|
| `security.clj` | Removed call parens on rate-limit fn | Brute-force protection disabled |
| `views.cljs` | Matomo in render body | Inflated analytics on every re-render |
| `views.cljs` | `prn "FRAME?"` | Debug output in production |
| `pdf_spec.cljc` | Duplicate `treasure-fields` | Dead code |

### Dead/Unused Imports
- `email.clj` — `[clj-http.client :as client]` (unused)
- `pdf.clj` — `[clj-http.client :as client]` (unused)

---

## 6. Frontend Assets

### Branding (DMV-specific, don't backport)
- Replaced OrcPub logos/favicon with DMV branding
- Patron tier badges (Adult DM, Ancient DM, etc.)
- 6 decorative SVGs for generator splash buttons
- Custom header/login images

### Monetization (DMV-specific)
- `ads.txt` — 367 lines of ad exchange config
- `dungeonmastersvault.min.js` — 305KB Network N ad-tech
- Patron buttons (desktop + mobile)

### Potentially Useful
- `robots.txt` deleted — breaking/ blocks ALL crawlers (`Disallow: /`)
- `cookies.js` — links to local `/cookies-policy` (good UX pattern)
- `SRD-OGL_V5.1.pdf` moved to `dnld/` subdirectory
- `5eActionsReferencePage.pdf` added (needs license check)

---

## 7. Monitoring / Vendored

- `newrelic.yml` + 4 JARs — DMV APM, **exposed license key**
- `lib/datomic/datomic-free/0.9.5697/` — legacy Java 8 only
- `lib/datomic/datomic-lucene-core/3.3.0/` — transitive dep of Free
- **None of these should be backported**

---

## 8. Scripts / CI / Misc

- **No differences** in `scripts/`, `.github/`, `.gitignore`
- `dev/user.clj` — trailing newline only
- `env/dev/env/index.cljs` — logo filename changed (branding)
- **Nothing backport-worthy**

---

## 9. Git / Workflow Gotchas

### Git Ref Namespace Collision
Cannot push `dmv/hotfix-integrations` to Gitea when a `dmv` branch already
exists. Git stores refs as a filesystem hierarchy (`refs/heads/dmv/...`), so
`dmv` cannot be both a leaf (branch) and a directory (branch prefix).

**Workaround:** Push with a non-colliding name:
```bash
git push gitea dmv/hotfix-integrations:hotfix-integrations
```

### Cherry-Picking Between `dmv/` and `breaking/`
Cherry-picks between DMV and breaking/ work cleanly because both branches share
the same `fork/` directory structure with identical namespace paths
(`orcpub.fork.branding`, `orcpub.fork.integrations`, etc.). DMV has real values,
breaking/ has env-var-gated stubs.

The only conflict encountered was in `views.cljs` `componentDidMount` — breaking/
didn't have the `on-app-mount!` call that DMV's integrations use. This is expected:
fork-specific behavior lives in the fork files, and the call site in shared code
needs to exist on both branches.

**Pattern:** When cherry-picking a commit that touches both fork files and shared
files, the fork file will conflict (different implementations) but the shared file
should apply cleanly (same API calls, different underlying behavior).
