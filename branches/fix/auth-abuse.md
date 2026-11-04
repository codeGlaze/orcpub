# fix/auth-abuse

**Purpose:** close the abuse gaps on the account forms in one branch: a limit on resend
verification, Turnstile on sign-up, and the "email taken" check. **Base:** `integration`.
**PR into:** `integration`. **KB:** `docs/kb/auth-abuse-limits.md` (read it first).

## State (updated 2026-11-03)

- Done: step 1, `re-verify` limited 3/address/hr and 10/host/hr, plain 200 when throttled
  (`dffcbc17`; `routes_test.clj` resend tests, fail-first shown).
- In progress: step 1b, tests for the password-reset limits (none existed).
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
  3. Sign-up never says an address is taken (option b, owner decision below): remove the live
     `:check-email` dispatch from the form and the `check-email` route; `register` answers a taken
     address exactly as a new one, creates nothing, and mails the existing owner a notice (no
     credential in it; at most one a day per address via `claim-once?`). Drop the "already
     associated" message (`registration.cljc:258`).
  4. One branch changelog line per outcome; one CodeRabbit `full review` when ready.

## Owner decisions

- 2026-11-03: one branch for all of it. Turnstile: sign-up only, managed mode, fail open.
- 2026-11-03: live stopgap is a Cloudflare edge rule on `/send-password-reset` (admin adding it).
- 2026-11-04: option (b): sign-up never reveals a taken address. It answers every well-formed
  address the same ("check your email") and mails the existing owner instead of creating a second
  account. Chosen over softer wording on the live check, which would still be a membership test.
- 2026-11-04: with no mail server (refused, or `ALLOW_UNVERIFIED_REGISTRATION` open mode) the owner
  cannot be mailed, so a taken address gets a plain refusal in those modes only.

## Open questions for the owner

- None.

## Gotchas for the next agent

- Limits live in memory: a restart clears them and each server process counts alone.
- Turnstile tokens are single use and last 5 minutes; a failed submit must reset the widget.
- Work from a fresh, compacted chat and hand suite runs to a `haiku` subagent (AGENTS.md, "Token habits").
