# Branch changelog — `fix/release-panel-hold-ceiling`

## Why this branch exists

The release panel was held back while the cookie notice was up, and released when
the notice was dismissed. Plenty of people never dismiss a cookie notice — they
read past it — and it returns on every visit, so for them the hold never ended
and the release panel would never have appeared at all.

## Fixed

- **An ignored cookie notice no longer hides the release panel** — the hold has a
  ten-second ceiling, after which the panel shows anyway with the notice left
  where it was, behind it.
