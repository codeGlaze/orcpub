// Overlays:  suppressed (cookie notice and What's New).
// Does a bug cost only the item it is in, in the real app? See docs/HOMEBREW_GUARD.md, 5.
//
// Events: re-frame applies no effect of a handler that did not finish; events/event-failed,
// registered by core.cljs once startup is done, says so in the error notice instead of
// rethrowing (which dropped every queued event, the notice with them).
//   1. the app starts with a library, so startup's recovery is not broken by the handler
//   2. an event that throws shows the notice once, and its change is not applied
//   3. the same event failing again straight away does not show a second notice
//   4. the next event still runs: the queue was not dropped
// Drawing: each My Content source row is in a render-guard (React's error boundary).
//   5. a row that throws while drawing shows its fallback, and the page keeps working
//
// Needs:     server (`lein e2e-server`). No login.
// Runs in:   ~25s.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { suppressOverlays } = require('./lib/orcbrew-import');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const MESSAGE = "That didn't work, so nothing was changed.";

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext();
  await suppressOverlays(ctx);
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('plugins', '{"P" {:orcpub.dnd.e5/races {:hill {:name "Hill" :key :hill :option-pack "P"}}}}');
    } catch (e) {}
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => window.re_frame && cljs.core.get(cljs.core.deref(re_frame.db.app_db),
                                                                     cljs.core.keyword('plugins')),
                             null, { timeout: 120000 });
  check('the app starts with its library', true);

  const notices = () => page.locator(`text=${MESSAGE}`).count();
  const marked = () => page.evaluate(() =>
    cljs.core.get(cljs.core.deref(re_frame.db.app_db), cljs.core.keyword('e2e-marker')) != null);

  await page.evaluate(() => {
    const kw = cljs.core.keyword;
    // writes a marker, then throws before finishing: the marker must not land
    re_frame.core.reg_event_db(kw('e2e/broken'), (db) => {
      cljs.core.assoc(db, kw('e2e-marker'), true);
      throw new Error('a deliberate bug');
    });
    re_frame.core.reg_event_db(kw('e2e/works'), (db) => cljs.core.assoc(db, kw('e2e-after'), true));
    re_frame.core.dispatch(cljs.core.vector(kw('e2e/broken')));
  });
  await page.waitForTimeout(1000);
  check('an event that throws shows the notice', (await notices()) === 1, String(await notices()));
  check('and its change is not applied', !(await marked()));

  await page.evaluate(() => {
    const kw = cljs.core.keyword;
    re_frame.core.dispatch(cljs.core.vector(kw('e2e/broken')));
    re_frame.core.dispatch(cljs.core.vector(kw('e2e/works')));
  });
  await page.waitForTimeout(1000);
  check('failing again straight away adds no second notice', (await notices()) <= 1, String(await notices()));
  check('the next event still runs', await page.evaluate(() =>
    cljs.core.get(cljs.core.deref(re_frame.db.app_db), cljs.core.keyword('e2e-after')) === true));
  check('nothing escaped as an uncaught error', errors.length === 0, errors.join(' | '));

  // ── a source row that throws while drawing ───────────────────────────────────
  const poisoned = await page.evaluate(() => {
    const v = orcpub.dnd.e5.views;
    if (!v.source_disabled_counts) return 'views ns unreachable';
    v.source_disabled_counts = function () { throw new Error('a deliberate drawing bug'); };
    return 'ok';
  });
  check('could break the row', poisoned === 'ok', poisoned);
  // the row draws again when it is expanded
  await page.locator('text=expand').first().click({ timeout: 10000 });
  await page.waitForTimeout(1500);
  check('the row shows its fallback', (await page.locator('text=couldn’t be displayed').count()) > 0);
  check('the page does not fall over',
        (await page.locator('text=Something went wrong on this page').count()) === 0);
  check('the rest of the page still works', await page.locator('button.b-export').isVisible());

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
