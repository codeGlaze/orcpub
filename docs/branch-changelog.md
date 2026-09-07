# Branch changelog — `test/overlay-probe-user-menu-and-modals`

## Why this branch exists

The overlay probe printed three SKIP lines: the user menu, the import modals and
the delete confirmation. A skip is honest, but it is not coverage.

The import conflict modal is now driven for real. The user menu needs an account
the server accepts, so the lane logs in through the form and skips when no
credentials are given — seeding a session in localStorage does not work, because
the app verifies the stored token on boot and clears it, which is the app being
right.

## Added

- The overlay probe drives the **import conflict modal** — the fixture pack is
  imported twice, the second time with its source renamed so all 180 keys collide,
  which is the conflict a user hits when two packs overlap.
- A **signed-in lane** that logs in through the real form when `ORCPUB_TEST_USER`
  and `ORCPUB_TEST_PASSWORD` are set, and audits the user menu.
