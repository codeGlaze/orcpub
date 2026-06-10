# E2E Notes (orcpub)

Verified facts for writing and running the browser end-to-end tests.

> **This file is kept in sync across `agents/develop` and `testing/develop`.**
> Edit one, copy to the other. Keep it self-contained so the copy is trivial.

## Where the harness is

The Playwright end-to-end suite lives on the **`testing/develop`** branch under
`e2e/` (in the codespace: `/workspaces/orcpub-testing/e2e`). It drives whatever
URL you set in `APP_URL` (default `http://localhost:8890`), so the harness on
`testing/develop` can test an app running from **any other branch** — point it
at the running app and go.

## Running a scenario

From the `e2e/` directory, with Node available and the app already running:

```
APP_URL=http://localhost:8890 ./node_modules/.bin/playwright test scenarios/<file> --project=chromium
```

Notes:
- Node is required but is not always pre-installed (see the dev-loop doc for the
  codespace case). Playwright's browsers are pre-cached in the codespace.
- Scenarios live in `e2e/scenarios/`; shared helpers in `e2e/fixtures/`.

## Builder-test gotchas (verified 2026-06-01)

These cost real debugging time; they are app behavior, true in any environment.

1. **The character builder shows one section at a time.** Race, then
   "Class / Level," etc. are separate pages. A given control only exists in the
   DOM when its section is active — e.g. the class dropdown only appears after
   you click the **"Class / Level"** tab. Navigate to the right section first.
2. **Cookie-consent bar.** A bar across the bottom of the page ("This website
   uses cookies… Got it!") overlays content and intercepts clicks until
   dismissed. Dismiss it before clicking builder controls.
3. **Imported content is labeled by file name, not the field inside the file.**
   When you import a homebrew `.orcbrew` file, the app shows that content's
   "source" as the **imported file's name** (importing `sourced-classes.orcbrew`
   shows source `sourced-classes`), **not** the `:option-pack` value written
   inside the file. Name fixtures accordingly, and assert against the filename.
4. **Force a desktop viewport** (e.g. 1440×900) if your test depends on section
   tab labels — they are hidden on the mobile layout.

## Fixtures

- `e2e/fixtures/` holds shared helpers (`test-utils.ts`) and sample content.
- A homebrew `.orcbrew` with actual classes (e.g. for source-label tests) exists
  at `test/duplicate-external-a.orcbrew` (classes Artificer, Monster Hunter,
  `:option-pack "Homebrew Pack A"`). Note point 3 above: the displayed source
  will be the imported file's name, not "Homebrew Pack A".
