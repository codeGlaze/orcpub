<!-- Branch changelog. Fold into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. See docs/branch-changelog.template.md. -->

# Branch changelog — `fix/auth-abuse`

## Why this branch exists

The live site was hit with password-reset spam on 2026-11-03. An audit of the account forms found
resend-verification had no limit, sign-up told anyone whether an email had an account, and the
limits were written per form, so a new form could ship unguarded. This branch puts every account
form under one policy table and one guard (with a test that fails if a route is missing from it),
adds an optional Cloudflare Turnstile check (off unless both keys are set), and moves the account
code out of `routes.clj`, `views.cljs` and `events.cljs` into `routes/auth`, `views/auth` and
`events/auth`.

## Added

- **Accounts:** an optional Cloudflare Turnstile check on sign-up, password reset and resend verification, and on sign-in after a failed attempt. Off unless `TURNSTILE_SITE_KEY` and `TURNSTILE_SECRET_KEY` are set; if Cloudflare can't be reached, requests go through.
- **Accounts:** when the security check can't load, the form says so and suggests allowing challenges.cloudflare.com in an ad blocker.

## Changed

- **Accounts:** sign-up no longer says when an email is already in use. It answers as for a new address and emails the account's owner instead. Without a mail server, a taken address gets a plain refusal.
- **Accounts:** warnings about sign-in attempts on your account now follow up while attempts continue (at most every 6 hours, with a count), instead of once a day.

## Fixed

## Security

- **Accounts:** account emails can no longer be triggered in bulk from one form that had no limit.
