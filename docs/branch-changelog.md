<!-- Branch changelog. Fold into CHANGELOG.md at merge-to-integration with
     `scripts/fold-branch-changelog.sh "<release>"`. House style: docs/branch-changelog.template.md. -->

# Branch changelog — `feature/registration-in-boot-report`

## Why this branch exists

Whether signup works depends on two settings together, `EMAIL_SERVER_URL` and
`ALLOW_UNVERIFIED_REGISTRATION`, and the startup report showed only the first. This adds a row that
names the outcome. It also replaces the no-mail warning, which still described the signup bug the
registration changes fixed, and drops the separate startup warnings from `routes.clj` that the row
now covers.

## Added

- **Server:** the startup report says what signup will do: verified by email, refused, or open without an address check.

## Changed

## Fixed

## Security
