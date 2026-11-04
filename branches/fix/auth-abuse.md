# fix/auth-abuse

**Purpose:** close the abuse gaps on the account forms in one branch: a limit on resend
verification, Turnstile on sign-up, and the "email taken" check. **Base:** `integration`.
**PR into:** `integration`. **KB:** `docs/kb/auth-abuse-limits.md` (read it first).

## State (updated 2026-11-04)

- Done: step 1, `re-verify` limited 3/address/hr and 10/host/hr, plain 200 when throttled
  (`dffcbc17`; `routes_test.clj` resend tests, fail-first shown).
- Done: step 1b, password-reset limit tests (`e082c3b8`).
- Done: step 2, Turnstile on sign-up (`07cff998`; `turnstile.clj`/`.cljs`, `turnstile_test.clj`,
  9 tests, fail-first shown; full suite 1479/0; prod CLJS compiles). Not yet tried in a browser
  against Cloudflare (use the always-pass test keys in `docs/ENVIRONMENT.md`). A browser that
  blocks the Cloudflare script sends no token and is refused; only the server side fails open.
  The page-load budget e2e does not visit sign-up, so it needed no change.
- Done 2026-11-04 (pushed, tip `93cdb2d3`): auth code moved to `routes/auth.clj`, `views/auth.cljs`,
  `events/auth.cljs` (page wrappers in `index.clj`, auth styles in Garden, widget reset as an fx);
  `security/auth-guards` table + route-table test; Turnstile on reset, resend, and login after a
  failure (mark = typed name + connection, 15 min); ad-blocker message; step 3 option (b) built
  (`check-email` removed, taken address answered like a new one, owner notice once a day, plain
  refusal with no mail server). Full suite 1497/0; browser-checked with the test keys.
- 2026-11-04 later: protections unified (`auth-guards` policy + one `guard`, table-driven tests, one
  `claim-notice!`, `auth-form-page :challenge`); branch changelog written; KB page updated; PR opened.
- Left: one CodeRabbit `full review`, its fixes, owner's merge call.
- Known, accepted: `check-username` stays live (public handles); email change still says
  `:email-taken` (signed-in only); `text-color` still in `components.cljc` until `views.cljs`
  inline styles go to Garden; page-load bundle already over its 900 KB ceiling before this branch.
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

- 2026-11-04: auth code moves on THIS branch (no new branch), pure moves, no renames, following
  existing folders: `routes/auth.clj` (handlers out of `routes.clj`), `views/auth.cljs` (screens +
  Turnstile widget), `events/auth.cljs` (per `monolith-decomposition-plan.md`). Turnstile server
  check folds into `security.clj`; tests into existing files. Shared helpers go to
  `event_utils.cljc` / `orcpub.components`; children never require their monolith.
- 2026-11-04: one guard table in `security.clj` listing every auth form and its checks, plus a test
  that reads the route table and fails if an auth route is missing from it.
- 2026-11-04: Turnstile also on password reset and resend-verification. Login: only after a failed
  attempt, counted by the typed name (not the account) and shown after any failure, so it reveals
  nothing; the failure message stays as is. Show an ad-blocker message when the script can't load.
- 2026-11-04: summaries include a before -> after line count per touched file.

## Open questions for the owner

- None.

## Gotchas for the next agent

- Limits live in memory: a restart clears them and each server process counts alone.
- Turnstile tokens are single use and last 5 minutes; a failed submit must reset the widget.
- Work from a fresh, compacted chat and hand suite runs to a `haiku` subagent (AGENTS.md, "Token habits").
