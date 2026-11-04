# Abuse limits on the account forms: what each one has, live and on integration

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

## Coverage on integration

All limits go through `security/under-limit?` (in memory, per server process; a restart clears
them), so refusals and the busiest source appear in the hourly heartbeat line.

| Endpoint | Limit | Answer to a bot |
|---|---|---|
| Sign in, one account | 5 failures / minute refuse the 6th WRONG guess; the right password still works | identical for unknown user and wrong password |
| Sign in, spray | 5 distinct usernames failing from one address in a minute, checked before credentials | refused |
| Sign in, 3 addresses on one account | none; mails the owner, once a day | n/a |
| Password reset | 3 / address / hour, 10 / host / hour | always 200, even when throttled |
| Sign-up | 10 / host / hour | unverified accounts cannot sign in |
| Email change | its own cooldown (`email-change-rate-limited?`), with a countdown in the UI | `retry-after-secs` |
| **Resend verification** (`re-verify`, `routes.clj:718`) | **NONE** | the one mail-sending form the limiter missed |
| **"Is this email taken?"** (`check-email`, `routes.clj:1835`) | **NONE** | a membership test; the sign-up form calls it on every email edit (`:registration-email` dispatches `:check-email`) |

Client address: `client_address.clj` rewrites `:remote-addr` from trusted proxies
(`client-ip-behind-proxies.md`).

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

**Owner decisions 2026-11-03:** sign-up only; **managed** mode; **fail open** when Cloudflare cannot
be reached (the 10/hour sign-up limit still holds); off unless `TURNSTILE_SITE_KEY` and
`TURNSTILE_SECRET_KEY` are set, so self-hosters and dev are unaffected. All of this, the
`re-verify` limit and sign-up never revealing a taken address (2026-11-04: same answer for every
address, the existing owner gets a notice instead) go on **one branch**
(handoff: `branches/fix/auth-abuse.md`).
