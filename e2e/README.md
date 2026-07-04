# Frontend E2E (backend-free)

Proves behavior in the **real compiled frontend** — the project's definition of
done. The app's homebrew/import/export/loader flows are entirely client-side
(localStorage), so these run with **no backend / no Datomic**.

## One-time setup
```bash
cd e2e
npm install
npx playwright install chromium      # browsers are not committed
```

## Run
From the repo root, build the dev frontend once, then drive it:
```bash
lein fig:build                       # compiles resources/public/js/compiled/orcpub.js
node e2e/server.js &                 # backend-free SPA server on http://localhost:8899
cd e2e && npx playwright test        # scenarios in e2e/scenarios/
```

## Headless CLJS unit suite
Runs the existing `orcpub.test-runner` (events/subs/views/validation tests) in
headless chromium and reports pass/fail:
```bash
lein fig:test                        # compiles target/test/js
node e2e/run-cljs-tests.js
```

## Layout
- `server.js` — serves `resources/public`; SPA-fallback to a minimal index that
  mounts the dev build (stubs `window.start` so there's no cookie bar).
- `playwright.config.ts` — chromium, `baseURL` http://localhost:8899.
- `scenarios/` — one spec per behavior. DoD: each fix has a scenario here.
- `run-cljs-tests.js` — headless runner for the cljs unit suite.

## Convention
Every roadmap phase lands with a scenario in `scenarios/` that **fails on the
bug and passes on the fix**. Unit/JVM tests are for fast iteration and
regression; a green scenario here is the proof.

## Authoring notes / gotchas (learned the hard way)

- **Header action buttons are duplicated** — once in the main header and once in
  `#sticky-header`. The sticky copy is NOT `display:none`; it has a real bounding
  box (so it reports as "visible" and matches `:visible`), but it sits
  off-screen/covered and is **not clickable**. `.first()` / `:visible` pick it
  non-deterministically (depends on scroll state — a test can pass once and hang
  the next). **Always exclude it:**
  `page.locator('button:has-text("Save to Browser Storage"):not(#sticky-header button)')`.
- **Routes:** builders live under `/pages/dnd/5e/<x>-builder`; My Content is
  `/dnd/5e/my-content`. Navigating to a bare segment like `/dnd/5e/class-builder`
  dispatches `[:route nil]` and renders the **error fallback** (app mounts, but no
  builder). Get the real href from the menu, don't guess.
- **Field labels wrap their text in a `<span>`** inside `.personality-label`, so
  exact-match must target the span:
  `.field:has(.personality-label span:text-is("Name")) input`.
- **Downloads** go through FileSaver (`js/saveAs`) and DO fire Playwright
  `download` events. Capture with
  `const [d] = await Promise.all([page.waitForEvent('download'), btn.click()])`,
  then read `await d.path()`. Await the button's `toBeVisible()` first so the
  builder has settled.
- **Assert on `localStorage`, not the rendered list**, for content state — My
  Content rendering may not reactively reflect a save (see roadmap O4), so
  `localStorage.getItem('plugins')` is the reliable signal that something landed.
- **cljs/re-frame are reachable from the page** in the dev (`:none`) build (not
  munged) — handy for seeding state in a pinch, but prefer real UI interactions.
- **Restart the SPA server before a run** — the backgrounded `node server.js`
  doesn't always survive between sessions; `curl localhost:8899` to check.
