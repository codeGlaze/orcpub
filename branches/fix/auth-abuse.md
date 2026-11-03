# fix/auth-abuse

**Purpose:** close the abuse gaps on the account forms in one branch: a limit on resend
verification, Turnstile on sign-up, and the "email taken" check. **Base:** `integration`.
**PR into:** `integration`. **KB:** `docs/kb/auth-abuse-limits.md` (read it first).

## State (updated 2026-11-03)

- Done: branch created from `integration` (`5ec530cb`) on 2026-11-03; nothing committed yet.
- Next, in order:
  1. `re-verify` (`routes.clj:718`): reuse `security/under-limit?` with per-address and per-host
     keys like `reset-email-allowed?`, add both to `busiest-summary`'s caps, answer 200 either way.
     Test first, seen to fail.
  2. Turnstile on sign-up: server check in a small namespace, called in `register`
     (`routes.clj:600`) beside `registration-allowed?`; widget on the sign-up form, script loaded
     only when the form opens; `challenges.cloudflare.com` via `:extra-frame-src`; settings rows
     for both keys (secret never printed); tests with a stubbed `siteverify` (pass, fail,
     unreachable -> let through). Allow the script in the page-load budget test for the sign-up
     page only.
  3. `check-email` / sign-up: a neutral answer for a taken address (owner decision below).
  4. One branch changelog line per outcome; one CodeRabbit `full review` when ready.

## Owner decisions

- 2026-11-03: one branch for all of it. Turnstile: sign-up only, managed mode, fail open.
- 2026-11-03: live stopgap is a Cloudflare edge rule on `/send-password-reset` (admin adding it).
- 2026-11-03: a taken address gets a neutral answer, in the owner's words "cannot use this email,
  check again" or similar, not "already associated with another account"
  (`registration.cljc:258`).

## Open questions for the owner

- How neutral is neutral? Softer wording still tells a bot the address is unusable, which for a
  well-formed address means "has an account", as long as the live check answers differently for
  taken addresses. Put both to the owner before building: (a) the owner's wording on the live
  check, plus a per-host limit on `check-email`; or (b) no live check: sign-up answers the same for
  every well-formed address ("check your email") and mails the existing owner instead of creating
  a second account. (b) closes the membership test; (a) is smaller.

## Gotchas for the next agent

- Limits live in memory: a restart clears them and each server process counts alone.
- Turnstile tokens are single use and last 5 minutes; a failed submit must reset the widget.
- Work from a fresh, compacted chat and hand suite runs to a `haiku` subagent (AGENTS.md, "Token habits").
