# Full-app headless E2E

Playwright scripts that boot the **real** orcpub SPA headless (no backend, no auth) and drive
the actual UI, then assert on the resulting DOM / localStorage. This is the click-through layer
above the JVM (`test/cljc`) and re-frame harness (`test/cljs`) tests: only a real browser
exercises the view widgets' DOM-string → data coercion.

These are **not** wired into `lein test` (they need a compiled app build + a headless browser).
They're run on demand and documented as the prototype for "E2E in CI". Full rationale, the
no-auth-gate proof, and gotchas live in `docs/kb/cljs-headless-harness.md`.

## Run

```bash
# from repo root
~/bin/lein fig:build          # app build  -> resources/public/js/compiled/orcpub.js
~/bin/lein garden once        # CSS (optional, layout only) -> resources/public/css/compiled/styles.css

cd test/e2e
npm install playwright && npx playwright install chromium
REPO="$(cd ../.. && pwd)" node race-builder-asi.js
```

Exit 0 = pass.

## Scripts

- **`race-builder-asi.js`** — authors a homebrew race with a fixed `+2 CHA` and a floating
  `+1 to a martial stat (Str/Dex/Con)` through the real race-builder form, saves to browser
  storage, and asserts the persisted `:ability-increases` is correctly typed (namespaced
  ability keyword, integer amounts, `:from :martial` keyword). Regression guard for the widget
  coercion bug where `<select>` values persisted as raw strings.

- **`export-import-use.js`** — the full content round-trip through the real UI: author+save →
  My Content **Export** button (captures the actual `.orcbrew` download) → wipe the pack →
  **import** via the real `<input type=file>` → select the imported race in the character builder
  and assert the floating ASI choice **renders** ("Improvement: Race - Tide Touched") with the
  fixed +2 CHA applied in the on-screen grid. This is the UI-level proof above the function-level
  round-trip tests (`ability_increase_grant_test`/`_cljs_test`), which call `(str plugin)`/
  `validate-import` and `char5e/to-strict` directly. It pins two things only a rendered UI shows:
  (1) **import names the pack from the file name** — preserve `download.suggestedFilename()`
  (`<pack>.orcbrew`) or the pack is re-created under the wrong name; (2) the builder's racial
  ability-increase widget only renders a selection keyed **`:asi`** — a homebrew floating ASI
  keyed otherwise applies on a built character but does NOT render for the player to choose
  (the bug this test caught; see roadmap A4).

## Gotchas these scripts encode (verified)

- Input `[1]` is the **Orcacle search box** (`placeholder="search"`); typing there opens an
  autofill suggestions overlay that intercepts clicks. Target form fields by class
  (`input.input.h-40`) / placeholder, never by index-into-all-`input`s.
- "Save to Browser Storage" exists **twice** in the DOM (hidden mobile twin + visible desktop);
  select with `button:visible`.
- reagent re-renders async after each dispatch — pause between successive `selectOption`s so
  each on-change closure sees the latest state.
