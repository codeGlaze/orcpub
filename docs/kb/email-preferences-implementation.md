# Email Preferences Implementation

Verified findings from the email preferences / unsubscribe implementation.

## Architecture

### Datomic Schema
- `:orcpub.user/send-updates?` — boolean, already in `schema.clj` line 124
- No new schema attributes needed for the unsubscribe feature

### JWT Unsubscribe Tokens
- Uses `buddy.sign.jwt` (already in project, used for auth)
- Token: `{:email "user@example.com" :action "unsubscribe"}`
- Signed with `SIGNATURE` env var (same as auth JWTs)
- No expiry — CAN-SPAM requires 30 days minimum, and old marketing emails should always work
- Stateless — no DB storage, verified by JWT signature check
- Token generated in `routes.clj` (`unsubscribe-token` fn), URL helper in `email.clj` (`unsubscribe-url` fn)
- This avoids circular dependency (email.clj can't require routes.clj)

### How send-updates? Is Used
- Admin queries Datomic directly for opted-in users, sends marketing emails manually
- The preference is NOT used by transactional emails (verification, password reset)
- Transactional emails should NOT include unsubscribe links

### User-Facing Controls
1. **Registration**: checkbox during signup (already existed)
2. **My Account page**: toggle checkbox (added in this feature)
3. **Email unsubscribe link**: JWT-signed GET endpoint (added in this feature)

## DMV Arity Bug (Fixed)

DMV's `routes.clj` had a 4-arg call to `email/send-verification-email` which only accepts 3 args:

```clojure
;; BROKEN (DMV before fix):
(email/send-verification-email base-url params verification-key send-updates?)

;; FIXED (matches breaking/):
(email/send-verification-email base-url params verification-key)
```

The `send-updates?` value was already persisted in the Datomic transaction data — the email function never needed it. Similar threading through `do-verification` and `register` was also removed.

The `re-verify` function also had a leftover `nil` arg where `send-updates?` used to be.

## Fork File Organization

The 6 fork-customization files were moved into a `fork/` subdirectory:

| Before | After |
|--------|-------|
| `src/clj/orcpub/branding.clj` | `src/clj/orcpub/fork/branding.clj` |
| `src/clj/orcpub/integrations.clj` | `src/clj/orcpub/fork/integrations.clj` |
| `src/clj/orcpub/user_data.clj` | `src/clj/orcpub/fork/user_data.clj` |
| `src/cljs/orcpub/branding.cljs` | `src/cljs/orcpub/fork/branding.cljs` |
| `src/cljs/orcpub/integrations.cljs` | `src/cljs/orcpub/fork/integrations.cljs` |
| `src/cljs/orcpub/user_tier.cljs` | `src/cljs/orcpub/fork/user_tier.cljs` |

All namespaces changed from `orcpub.<name>` to `orcpub.fork.<name>`.

### Files That Required Require Updates (9 consumers)
Server: `email.clj`, `privacy.clj`, `index.clj`, `routes.clj`, `pedestal.clj`
Client: `views.cljs`, `events.cljs`, `character_builder.cljs`, `views_2.cljc`

## social-links-footer Pattern

The `social-links-footer` function in `email.clj` is self-gating:
- Reads from `branding/social-links` (a map like `{:patreon "..." :twitter "..."}`)
- Each link is wrapped in `(when (seq url) ...)` — empty strings are hidden
- On breaking/ (where social links default to `{}`), the function returns `[[:br]]` — just a line break
- Uses `into` with the email body: `(into [:div ...content...] (social-links-footer))`
- Safe to share across both branches

## PUT /user Endpoint

Added `update-user-preferences` handler on existing `/user` route (which already had GET and DELETE):
- Requires authentication (`check-auth` interceptor)
- Accepts `{:send-updates? true/false}` in transit params
- Uses `(contains? transit-params :send-updates?)` to only update if the field was sent
- Returns `{:send-updates? <value>}` on success

## Test Infrastructure

New tests added to `routes_test.clj`:
- `test-unsubscribe-token-roundtrip` — JWT sign/verify cycle
- `test-unsubscribe-handler` — valid token, idempotent, tampered, missing, unknown email
- `test-update-user-preferences` — toggle on/off, unknown user
- `test-user-body-includes-send-updates` — response serialization

Uses existing `with-conn` + `datomock` pattern. Added `buddy.sign.jwt` and `environ.core` to test requires.
