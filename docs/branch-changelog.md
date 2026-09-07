# Branch changelog — `test/overlay-probe-user-menu-and-modals`

## Why this branch exists

The overlay probe printed three SKIP lines: the user menu, the import modals and
the delete confirmation. A skip is honest, but it is not coverage. It also carries
`fix/import-probe-button-labels`, because driving the import modal is what turned
up the stale button labels underneath it.

The import helper every homebrew probe depends on clicked a button matched from
`Import / Confirm / Apply / OK`. The summary view's primary was renamed to "Import
with these fixes" in `f7285198` and the full panel's is "Apply & Import" — the
helper kept working only because `has-text` matches substrings. Its cookie-banner
fallback searched the whole page for "Got it", which is also the release panel's
dismiss button.

The user menu needs an account the server accepts, so that lane logs in through
the form and skips when no credentials are given. Seeding a session in
localStorage does not work: the app verifies the stored token on boot and clears
it, which is the app being right.

## Added

- The overlay probe drives the **import conflict modal** — the fixture pack is
  imported twice, the second time with its source renamed so all 180 keys collide,
  which is the conflict a user hits when two packs overlap.
- A **signed-in lane** that logs in through the real form when `ORCPUB_TEST_USER`
  and `ORCPUB_TEST_PASSWORD` are set, and audits the user menu.

## Fixed

- **The import helper targets the labels that exist**, most specific first, so a
  rename fails loudly instead of quietly matching something else.
- **The cookie-banner fallback is scoped to the banner**, so it can no longer
  close the release panel and record that as consent.
