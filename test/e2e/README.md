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

### The homebrew save gate

Four scripts cover one subject between them — where a save lands, and what an author is told when
it cannot land there. `docs/kb/key-collision-behavior.md` is the map.

- **`move-between-sources.js`** — retyping Option Source Name MOVES the item rather than copying it.
- **`change-item-key.js`** — the key control: what it accepts, and that a change records
  `:former-keys` so characters rebind.
- **`source-key-tag.js`** — a source's own abbreviation, and that keys already minted do not move
  when it changes.
- **`replace-or-refuse.js`** — the two refusals and the difference between them: a key taken in THIS
  source offers *Replace it*; one that answers in another source explains why there is nothing to
  replace and offers nothing. Also pins that replacing moves rather than copies, and that a refusal
  writes nothing. Captures both banners to `target/e2e-shots/collision-*.png`.

## Gotchas these scripts encode (verified)

- Input `[1]` is the **Orcacle search box** (`placeholder="search"`); typing there opens an
  autofill suggestions overlay that intercepts clicks. Target form fields by class
  (`input.input.h-40`) / placeholder, never by index-into-all-`input`s.
- "Save to Browser Storage" exists **twice** in the DOM (hidden mobile twin + visible desktop);
  select with `button:visible`.
- **A message-banner action is an inline `span` inside a full-width detail row, and the row reports
  the same text.** `lib.js/clickText` picks the shortest match and ties go to the ancestor, so a
  click by text lands on the row — which only dismisses the banner, silently doing nothing. Click
  `#app .message span.pointer` by its exact text instead (`replace-or-refuse.js`).
- reagent re-renders async after each dispatch — pause between successive `selectOption`s so
  each on-change closure sees the latest state.

## `cljs-harness.js` — not an e2e script

The odd one out in this directory: it runs the **ClojureScript unit suite** headless (the suite CI
never runs), not the app. `lein fig:test` first, then `node test/e2e/cljs-harness.js`. It lives here
because this is where the repo's node drivers live. See `docs/kb/cljs-headless-harness.md`.
