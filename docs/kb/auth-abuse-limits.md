# Abuse limits on the account forms: what each one has, live and on `fix/auth-abuse`

Measured 2026-11-03 against `integration` (`837a2620` era) and the fork's `develop` (`15e1fe04`,
what the live site runs). Decisions with their reasons are in `account-flows.md` §6; this page is
the coverage map and what is still open.

## The incident (2026-11-03)

The live site was hit with password-reset spam. Live runs upstream `develop`, which has **no limit
on reset mail** (`send-password-reset`, `routes.clj:526` there: one mail per request) and answers
`400 :no-account` for an unknown address, a membership test. `integration` fixed both in September
(`account-flows.md` §6), but none of it is live until the release (#32 `integration` -> `develop`,
then upstream, then a deploy).

**Stopgap, owner 2026-11-03:** a Cloudflare rate-limit rule at the edge on `GET /send-password-reset`
(the address arrives as `?email=`), keyed by client IP. The admin was asked to add it. It can cover
`/re-verify` the same way, which has no limit live either.

## Coverage on `fix/auth-abuse` (2026-11-04; replaces the integration table)

**One policy, one guard.** `security/auth-guards` is the policy as data: per route, its `:limits`
(`[scope n window]`, scopes `:per-address`, `:per-host`, `:per-name`, `:names-per-host`), `:turnstile`
(`:always` / `:after-failure`), `:answer` (`:uniform` 200 / `:message` / `:error`) and options
(`:counted-on :failure`, `:spend :when-actionable`, `:notify`), or `{:none "reason"}`. Handlers in
`routes/auth.clj` call only `security/guard` (refuse or pass), `security/record!` (login failures)
and one `refusal-response`; they hold no numbers. Order inside `guard`: failure-counted limits that
only look, then the challenge, then limits that spend, so a failed challenge spends no allowance and
no refusal comes from a lookup. Tests walk the table (`security_test.clj`); a route-table test
fails if an auth route is missing from the table or the table names a dead route; a pin test fixes
the required limits of login, register, re-verify and reset (a table-driven test cannot see a
deleted row). **A new account form gets its protection and tests by adding one table entry.**

| Endpoint | Limits | Turnstile | Answer |
|---|---|---|---|
| Sign in | 5 failures / name / minute (refuses the next wrong guess only); 5 distinct names failing / host / minute | after a failure, keyed by typed name + connection, 15 min | identical for unknown name and wrong password, incl. the `:challenge?` flag |
| Sign-up | 10 / host / hour | always | taken address answered like a new one (owner mailed); no mail server: plain refusal |
| Password reset | 3 / address / hour, 10 / host / hour | always | always 200 |
| Resend verification | 3 / address / hour, 10 / host / hour | always | always 200 |
| Email change | its cooldown, signed-in only | no | `retry-after-secs` |
| verify, reset-password, reset page, unsubscribe | none: unguessable key or signed token | no | |
| check-username | none: usernames are public handles | no | |
| ~~check-email~~ | removed | | |

**Turnstile** (`security.clj`): off unless `TURNSTILE_SITE_KEY` and `TURNSTILE_SECRET_KEY` are both set;
the server verifies the browser's token with Cloudflare (4 s timeout) and lets the request through if
Cloudflare can't be reached. A missing token (script blocked) is refused with an ad-blocker message,
distinct from a failed one. Widget, blocked message and reset live in `auth-form-page :challenge`
and the `challenge-request` / `challenge-reset` interceptors in `events/auth.cljs`.

**Notices** (`security/claim-notice!`, `notices` table): sign-in attempts from 3+ hosts on one
account, and sign-up attempts on a taken address. First immediately; follow-ups only if new attempts
happened, no sooner than 6 h, with a count; never a credential, username or reset token.

Client address: `client_address.clj` rewrites `:remote-addr` from trusted proxies
(`client-ip-behind-proxies.md`). All counts live in memory, per server process.

## Memory vs record (owner asked 2026-11-03)

- **A countdown on password reset** was never mocked or built. The only countdown is on email
  change. The auth mock-ups (artifacts "Auth Redesign Target", "Auth Message Catalogue", "Auth
  Pages As Built", "Accounts Ship Check") show reset limits as numbers only, and the sent page says
  "Nothing arriving? Check the spam folder, then try again" with no wait.
- **Removing the sign-up "email taken" check** was never decided or done on any branch. Neutral
  answers were applied to sign-in and reset only; sign-up was not addressed.

## Turnstile on sign-up (researched 2026-11-03)

Cloudflare Turnstile, from its docs (`developers.cloudflare.com/turnstile/`, `/plans/`,
`/get-started/server-side-validation/`, `/reference/content-security-policy/`):
- **Free, unlimited checks**, 20 widgets, 10 hostnames per widget. Enterprise adds unbranding,
  30-day analytics, fingerprinting. Works without proxying through Cloudflare.
- **Server check is mandatory:** `POST https://challenges.cloudflare.com/turnstile/v0/siteverify`
  with `secret`, `response`, optional `remoteip`. Tokens last 300 s and work once
  (`timeout-or-duplicate` on reuse), so the form resets the widget after a failed submit. Docs
  give no timeout; their examples use 10 s. Test keys exist that always pass.
- **CSP:** `script-src` and `frame-src` `https://challenges.cloudflare.com`; nonces and
  `strict-dynamic` are supported (our strict policy), so only `frame-src` is new.
  `csp.clj` already takes `:extra-frame-src`.
- Privacy: "processes only the data strictly necessary"; the Turnstile privacy addendum is unread.

**Owner decisions 2026-11-03:** sign-up only (widened 2026-11-04 to reset, resend, and sign-in after a failure); **managed** mode; **fail open** when Cloudflare cannot
be reached (the 10/hour sign-up limit still holds); off unless `TURNSTILE_SITE_KEY` and
`TURNSTILE_SECRET_KEY` are set, so self-hosters and dev are unaffected. All of this, the
`re-verify` limit and sign-up never revealing a taken address (2026-11-04: same answer for every
address, the existing owner gets a notice instead) go on **one branch**
(handoff: `branches/fix/auth-abuse.md`).
