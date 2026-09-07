# Branch changelog — `fix/import-probe-button-labels`

## Why this branch exists

The import helper every homebrew probe depends on clicked a button matched from
the list `Import / Confirm / Apply / OK`. The summary view's primary was renamed
to "Import with these fixes" in `f7285198`, and the full panel's is "Apply &
Import" or "Apply" — the helper kept working only because `has-text` matches
substrings. One more rename and it clicks nothing, or the wrong control.

Its cookie-banner fallback had the same shape of problem: it searched the whole
page for a button labelled "Got it", and the release panel's dismiss button is
labelled exactly that.

## Fixed

- **The import helper targets the labels that exist**, most specific first, so a
  rename fails loudly instead of silently matching something else.
- **The cookie-banner fallback is scoped to the banner**, so it can no longer
  close the release panel and record that as consent.
