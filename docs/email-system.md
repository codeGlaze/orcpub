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
| `EMAIL_FROM_ADDRESS` | Sender address (default `no-reply@example.com`, a placeholder that cannot deliver) |
| `EMAIL_ERRORS_TO` | Address for admin alerts (optional; falls back to `EMAIL_ADMIN_TO`, then `APP_SUPPORT_EMAIL`) |

Configuration is read at send-time by `email/email-cfg` (`src/clj/orcpub/email.clj`).

## How player emails are sent

Every email a player gets (sign-up verification, password reset, the password-changed and
email-change notices, the sign-in attempts notice, and the artist emails) goes through an
**outbox** in the database instead of being sent while the player waits.

- **Saved with the action that causes it.** The email is written in the same database transaction
  as the account, the reset key or the email change. Either both are saved or neither is, so there
  is never an account whose email was lost, or an email for a change that did not happen.
- **Sent straight away, from the background.** A worker sends it within a second or two. The
  player's request does not wait for the mail server.
- **Retried when the mail server is busy or down.** A failed send is tried again after about 1
  minute, then 5, 30, 2 hours, and every 6 hours after that. Signing up while the mail server is
  down still works: the email arrives once the server is back.
- **Given up on only when it cannot work.** A permanent refusal (the mail server says the address
  does not exist) is not retried. After 8 attempts, about a day, an email is given up on too. Either
  way it is kept, marked failed, and an admin alert says so.
- **Deleted once sent.** Verification and reset emails contain links that sign a person in or
  prove an address, so nothing is kept after the mail server accepts the email, not even in the
  database's history.
- **Several servers.** Each email is claimed by one server before it is sent, so running several
  copies of the site never sends the same email twice.
- **No mail server configured.** Emails wait in the database and are sent once `EMAIL_SERVER_URL`
  is set and the server restarted.

The player's "this character won't load" report is the one exception: it is sent directly, because
the page tells the player on the spot whether it went out and offers a copy to send themselves.

## Schema

User attributes related to email and verification (`src/clj/orcpub/db/schema.clj`):

| Attribute | Type | Purpose |
|-----------|------|---------|
| `:orcpub.user/email` | string | Confirmed email address, as stored for display and sending (registration stores it lowercased; older accounts may be mixed-case) |
| `:orcpub.user/email-key` | string | The same address trimmed and lowercased; indexed, and what every email lookup matches |
| `:orcpub.user/pending-email` | string | Requested new email (awaiting verification) |
| `:orcpub.user/verified?` | boolean | Whether the user has verified their email |
| `:orcpub.user/verification-key` | string | UUID used in verification links |
| `:orcpub.user/verification-sent` | instant | When the verification email was sent |
| `:orcpub.user/password-reset-key` | string | UUID used in password reset links |
| `:orcpub.user/password-reset-sent` | instant | When the password reset email was sent |
| `:orcpub.user/password-reset` | instant | When the password was actually reset |

**Note:** neither email attribute has a uniqueness constraint in the schema. Uniqueness is enforced at the application level via `email-query`. See the known issues section.

**Writing an email:** set both attributes with `orcpub.user-email/email-attrs`, and normalise any address you look up with `orcpub.user-email/email-key`. Accounts written without the key (older data, or an old copy of `manage-user.clj`) get it from a backfill that runs in the background on every start. Until it reaches them, lookups also match the stored address exactly, so an account whose stored address is already lowercase is found; only an older mixed-case one waits for the backfill to sign in by email.

## Flows

### 1. Registration Verification

**Trigger:** `POST /register` (via `routes/register`)

1. Validate username, email, password
2. Check email/username not already taken (`email-query`, `username-query`)
3. Create user entity with `verified? false`, generate `verification-key`, set `verification-sent`,
   and queue the verification email, all in one transaction
4. The outbox sends the email (see "How player emails are sent")
5. User clicks link → `GET /verify?key=...` → `routes/verify`

**Verify behavior (registration path):**
- If already verified and no `pending-email` → redirect to success
- If `verification-sent` is nil or expired (24h) → redirect to failed
- Otherwise → set `verified? true`, redirect to success

**Re-verify:** `GET /re-verify?email=...` (`routes/re-verify`) stores a new key and queues a new
verification email for an unverified account. For an address with no account it answers the same
way and does nothing.

**No SMTP configured:** `do-verification` checks `email/configured?` first. With no SMTP host and
`ALLOW_UNVERIFIED_REGISTRATION=true`, the account is transacted `verified? true`, no mail is sent,
and the response carries `{:verified? true}` so the client shows "you can log in" rather than
"check your email". With no SMTP host and **no** opt-in, registration is refused with
`:email-not-configured` — fail closed, so a lost SMTP value cannot quietly become open
registration.

Since the startup report and refusal landed, a deployed server without `EMAIL_SERVER_URL` does not
start at all (`config/start-refusal`) unless `DEV_MODE=true`, `EMAIL_REQUIRED=false` or
`ALLOW_UNVERIFIED_REGISTRATION=true` says that is intended. So "no mail server" signup states
occur only on a server that opted out: `EMAIL_REQUIRED=false` (or `DEV_MODE`) leaves signup
refused; the flag opens it.

The startup report has an `ALLOW_UNVERIFIED_REGISTRATION` row whose value says what signup will
do: `verified by email`, `REFUSED: no mail server` (an opted-out server with no flag),
`OPEN: no address check` (no SMTP, flag set), or `verified by email (flag set, inert)` when the
flag is set but SMTP works. The last one still matters: if the SMTP value is lost, a restart
opens signup instead of refusing the start.

**Mail server down:** the account is created and its email waits in the outbox, sent once the
server answers. The person is never told to try again for something that already worked.

**Login gate:** Unverified users cannot log in. If the verification has expired, the login error tells them to re-register.

**Files:** `routes.clj:register`, `routes.clj:do-verification`, `routes.clj:verify`, `email.clj:verification-message`, `outbox.clj`

### 2. Email Change

**Trigger:** `PUT /user/email` (via `routes/request-email-change`, requires auth)

1. Validate new email (format, not same as current, not already taken)
2. Check rate limit (see Rate Limiting below)
3. Store `pending-email`, generate new `verification-key`, set `verification-sent`, and queue two
   emails in the same transaction: the verification link to the **new** address, and a notice to
   the address that owns the account now (the only warning it gets if the change was not theirs)
4. The outbox sends both

**Verify behavior (email-change path):**
- If expired (24h) → retract `pending-email`, `verification-key`, `verification-sent`; redirect to failed
- If `pending-email` exists → re-check email availability (race-condition guard):
  - If email was claimed by another user since request → retract all pending state, redirect to failed
  - Otherwise → swap `email` to `pending-email`, retract `pending-email`, `verification-key`, `verification-sent`; redirect to success
- Key is invalidated after use (retracted) — link cannot be reused

**Free resend:** Within 1–5 minutes of the original request, resending the same email re-uses the existing `verification-key` and does not update `verification-sent` (no rolling window). See Rate Limiting.

**Files:** `routes.clj:request-email-change`, `routes.clj:verify` (pending-email branch), `email.clj:email-change-verification-message`, `email.clj:email-change-notice-message`, `events.cljs:change-email`, `views.cljs:my-account-page`

### 3. Password Reset

**Trigger:** `GET /send-password-reset?email=...` (via `routes/send-password-reset`)

1. Look up user by email
2. Generate `password-reset-key`, set `password-reset-sent`, and queue the reset email in the same
   transaction; the outbox sends it

**Reset behavior:** `POST /reset-password` (via `routes/reset-password`)
- Validates new password and password match
- Sets new password hash, sets `password-reset` timestamp, sets `verified? true`, and queues the
  "your password was changed" notice in the same transaction

**Expiration:** `password-reset-expired?` checks if `password-reset-sent` is older than 24 hours.

**Files:** `routes.clj:send-password-reset`, `routes.clj:do-send-password-reset`, `routes.clj:reset-password`, `email.clj:reset-message`, `email.clj:password-changed-message`

### 4. Admin Alerts (server errors)

**Trigger:** any request that fails with an unhandled error.

The error becomes an admin alert. It is written to the server log and emailed to the admin
address (`EMAIL_ERRORS_TO`, else `EMAIL_ADMIN_TO`, else `APP_SUPPORT_EMAIL`), if one is set. The request that failed does not wait for the email: alerts
are sent separately, and never use the database.

- **One email per problem.** Errors count as the same problem when they have the same cause and
  happen in the same place in our code. The first is emailed at once; repeats within 15 minutes
  are counted and reported in one follow-up email.
- **At most `ALERT_MAX_PER_HOUR` emails an hour** (20 by default). The rest are logged.
- **Nothing is lost to a busy mail server.** Alerts that cannot be sent wait in a file in
  `ALERT_SPOOL_DIR` and are retried every minute, on shutdown, and at the next start. After three
  failures in a row the server waits five minutes before trying again.

The settings, and what happens on restarts and with several copies of the server, are in
[ENVIRONMENT.md, Admin alerts](ENVIRONMENT.md#admin-alerts).

**Subject format:** `[AppName] ExceptionClass: message @ METHOD /uri`, with
`(N more times)` added on a follow-up.

**What the email contains:** the request with private parts removed, the error and every error
that caused it, and Pedestal's details when the error came through Pedestal. Removed from the
request: anything sent in its body (passwords and form data), the login identity, cookies, and
database objects. Only harmless headers are kept (browser, referrer, content type, Cloudflare
country and ray id, and similar).

**Stack traces** show only our own code (`orcpub.*`). When none of our code is involved, the
deepest frame that is not part of Jetty, Pedestal or Clojure itself is shown instead, and the
number of hidden frames is noted.

**Files:** `alerts.clj` (sending, counting, the spool), `email.clj` (`send-error-email`, the
email itself), `routes.clj` (`service-error-handler`), `system.clj` (starts alerts before the
site takes requests)

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
| `src/clj/orcpub/alerts.clj` | Admin alerts: one email per problem, the hourly ceiling, the spool file |
| `src/clj/orcpub/outbox.clj` | Player emails: queued with their action, sent and retried by one worker, deleted once sent |
| `src/clj/orcpub/routes.clj` | Server handlers: register, verify, email change, password reset |
| `src/clj/orcpub/db/schema.clj` | Datomic schema for user attributes |
| `src/cljc/orcpub/route_map.cljc` | Route definitions (shared server/client) |
| `src/cljs/orcpub/dnd/e5/events.cljs` | Re-frame events for email change UI |
| `src/cljs/orcpub/dnd/e5/views.cljs` | My Account page with email change form |
| `src/cljs/orcpub/dnd/e5/subs.cljs` | Subscriptions for pending-email, email-change state |
| `test/clj/orcpub/email_change_test.clj` | Email change tests (11 tests, datomock) |

## Known Issues

- **No uniqueness constraint on email in schema.** Uniqueness is enforced at the application level by `email-query` (at request time) and a race-condition guard (at verify time). A `:db.unique/value` constraint on `:orcpub.user/email-key` would be the proper fix, once any existing duplicates are found and resolved.

- **Pending-email conflicts not checked.** Two users can simultaneously request the same new email. Both receive verification emails, but only the first to verify succeeds — the second is caught by the race-condition guard. The "loser" gets a confusing failure after clicking a valid-looking link.
