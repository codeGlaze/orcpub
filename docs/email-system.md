# Email System

Overview of email-related flows, schema, configuration, and behavior.

## Configuration

All email is sent via [postal](https://github.com/drewr/postal) using SMTP credentials from environment variables:

| Env var | Purpose |
|---------|---------|
| `EMAIL_ACCESS_KEY` | SMTP username |
| `EMAIL_SECRET_KEY` | SMTP password |
| `EMAIL_SERVER_URL` | SMTP host |
| `EMAIL_SERVER_PORT` | SMTP port (default `587`) |
| `EMAIL_SSL` | Enable SSL (`true`/`false`) |
| `EMAIL_TLS` | Enable TLS (`true`/`false`) |
| `EMAIL_FROM_ADDRESS` | Sender address (default `no-reply@orcpub.com`) |
| `EMAIL_ERRORS_TO` | Address for error notification emails (optional) |

Configuration is read at send-time by `email/email-cfg` (`src/clj/orcpub/email.clj`).

## Schema

User attributes related to email and verification (`src/clj/orcpub/db/schema.clj`):

| Attribute | Type | Purpose |
|-----------|------|---------|
| `:orcpub.user/email` | string | Confirmed email address |
| `:orcpub.user/pending-email` | string | Requested new email (awaiting verification) |
| `:orcpub.user/verified?` | boolean | Whether the user has verified their email |
| `:orcpub.user/verification-key` | string | UUID used in verification links |
| `:orcpub.user/verification-sent` | instant | When the verification email was sent |
| `:orcpub.user/password-reset-key` | string | UUID used in password reset links |
| `:orcpub.user/password-reset-sent` | instant | When the password reset email was sent |
| `:orcpub.user/password-reset` | instant | When the password was actually reset |

**Note:** `:orcpub.user/email` has no uniqueness constraint in the schema. Uniqueness is enforced at the application level via `email-query`. See the known issues section.

## Flows

### 1. Registration Verification

**Trigger:** `POST /register` (via `routes/register`)

1. Validate username, email, password
2. Check email/username not already taken (`email-query`, `username-query`)
3. Create user entity with `verified? false`, generate `verification-key`, set `verification-sent`
4. Send registration verification email (`email/send-verification-email`)
5. User clicks link → `GET /verify?key=...` → `routes/verify`

**Verify behavior (registration path):**
- If already verified and no `pending-email` → redirect to success
- If `verification-sent` is nil or expired (24h) → redirect to failed
- Otherwise → set `verified? true`, redirect to success

**Re-verify:** `GET /re-verify?email=...` (`routes/re-verify`) re-sends the verification email for unverified accounts.

**Login gate:** Unverified users cannot log in. If the verification has expired, the login error tells them to re-register.

**Files:** `routes.clj:register`, `routes.clj:do-verification`, `routes.clj:verify`, `email.clj:send-verification-email`

### 2. Email Change

**Trigger:** `PUT /user/email` (via `routes/request-email-change`, requires auth)

1. Validate new email (format, not same as current, not already taken)
2. Check rate limit (see Rate Limiting below)
3. Store `pending-email`, generate new `verification-key`, set `verification-sent`
4. Send email-change verification to the **new** address (`email/send-email-change-verification`)
5. If send fails → full rollback (retract `pending-email`, `verification-key`, `verification-sent`), return 500

**Verify behavior (email-change path):**
- If expired (24h) → retract `pending-email`, `verification-key`, `verification-sent`; redirect to failed
- If `pending-email` exists → re-check email availability (race-condition guard):
  - If email was claimed by another user since request → retract all pending state, redirect to failed
  - Otherwise → swap `email` to `pending-email`, retract `pending-email`, `verification-key`, `verification-sent`; redirect to success
- Key is invalidated after use (retracted) — link cannot be reused

**Free resend:** Within 1–5 minutes of the original request, resending the same email re-uses the existing `verification-key` and does not update `verification-sent` (no rolling window). See Rate Limiting.

**Files:** `routes.clj:request-email-change`, `routes.clj:verify` (pending-email branch), `email.clj:send-email-change-verification`, `events.cljs:change-email`, `views.cljs:my-account-page`

### 3. Password Reset

**Trigger:** `GET /send-password-reset?email=...` (via `routes/send-password-reset`)

1. Look up user by email
2. Generate `password-reset-key`, set `password-reset-sent`
3. Send password reset email (`email/send-reset-email`)

**Reset behavior:** `POST /reset-password` (via `routes/reset-password`)
- Validates new password and password match
- Sets new password hash, sets `password-reset` timestamp, sets `verified? true`

**Expiration:** `password-reset-expired?` checks if `password-reset-sent` is older than 24 hours.

**Files:** `routes.clj:send-password-reset`, `routes.clj:do-send-password-reset`, `routes.clj:reset-password`, `email.clj:send-reset-email`

### 4. Error Notification

**Trigger:** Called from exception handlers (e.g., Pedestal error interceptor)

- Sends a plaintext email with the request context and exception data
- Only sends if `EMAIL_ERRORS_TO` is set
- Uses `email/send-error-email`

**Files:** `email.clj:send-error-email`

## Rate Limiting (Email Change)

Rate limiting is enforced by `routes/email-change-rate-limited?` based on `verification-sent` and whether the request is a resend (same email as `pending-email`).

Three zones measured from `verification-sent`:

```
0 ──────── 1 min ──────── 5 min ──────── ∞
│  BLOCKED  │  FREE RESEND │   OPEN      │
│ (transit) │ (same email) │ (any email) │
│           │  blocked for │             │
│           │  diff email  │             │
```

- **0–1 min:** All requests blocked. Email is in transit. Client shows "Your email is on its way. You can resend in N seconds."
- **1–5 min:** Resend of same email allowed (free resend, no DB write, reuses existing key). Different email blocked. Client shows "Please wait N minutes before requesting another change."
- **5+ min:** Any request allowed. New `verification-key` generated, `verification-sent` updated.

The 429 response includes `retry-after-secs` so the client can display a specific countdown.

## Expiration Windows

| Window | Duration | Function |
|--------|----------|----------|
| Verification link | 24 hours | `verification-expired?` |
| Password reset link | 24 hours | `password-reset-expired?` |
| Email change rate limit | 5 minutes | `email-change-rate-limited?` |
| Free resend grace | 1–5 minutes | `email-change-rate-limited?` + free resend branch |

## File Map

| File | Role |
|------|------|
| `src/clj/orcpub/email.clj` | Email templates and send functions (postal) |
| `src/clj/orcpub/routes.clj` | Server handlers: register, verify, email change, password reset |
| `src/clj/orcpub/db/schema.clj` | Datomic schema for user attributes |
| `src/cljc/orcpub/route_map.cljc` | Route definitions (shared server/client) |
| `src/cljs/orcpub/dnd/e5/events.cljs` | Re-frame events for email change UI |
| `src/cljs/orcpub/dnd/e5/views.cljs` | My Account page with email change form |
| `src/cljs/orcpub/dnd/e5/subs.cljs` | Subscriptions for pending-email, email-change state |
| `test/clj/orcpub/email_change_test.clj` | Email change tests (11 tests, datomock) |

## Known Issues

- **No uniqueness constraint on email in schema.** Uniqueness is enforced at the application level by `email-query` (at request time) and a race-condition guard (at verify time). A Datomic `:db.unique/value` constraint on `:orcpub.user/email` would be the proper fix but requires a data migration to handle any existing duplicates.

- **Pending-email conflicts not checked.** Two users can simultaneously request the same new email. Both receive verification emails, but only the first to verify succeeds — the second is caught by the race-condition guard. The "loser" gets a confusing failure after clicking a valid-looking link.
