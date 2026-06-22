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

## Gotchas these scripts encode (verified)

- Input `[1]` is the **Orcacle search box** (`placeholder="search"`); typing there opens an
  autofill suggestions overlay that intercepts clicks. Target form fields by class
  (`input.input.h-40`) / placeholder, never by index-into-all-`input`s.
- "Save to Browser Storage" exists **twice** in the DOM (hidden mobile twin + visible desktop);
  select with `button:visible`.
- reagent re-renders async after each dispatch — pause between successive `selectOption`s so
  each on-change closure sees the latest state.
