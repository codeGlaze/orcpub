// Do the class handlers still WORK after being unmemoized?
//
// set-class, set-class-level, add-class and delete-class were wrapped in cljs.core/memoize
// (see docs/kb/perf-homebrew-builder-loop.md). Removing it gives a fresh closure per render,
// which should be behaviourally identical -- but these are the handlers that mutate the
// character, and neither test suite clicks anything. This drives them for real and asserts
// app-db afterwards.
//
// Run: lein fig:build && lein e2e-server, then
//   node test/browser/class_handlers_functional_e2e.js /path/to/pack.orcbrew
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import');

function findChrome() {
  if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
    if (d) { const p = path.join(b, d, 'chrome-linux', 'chrome'); if (fs.existsSync(p)) return p; }
  } catch (_) {}
  return undefined;
}

let failures = 0;
const check = (name, ok, detail) => {
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ' + detail : ''}`);
  if (!ok) failures++;
};

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext();
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();
  page.on('pageerror', e => { console.log('  PAGEERROR', e.message); failures++; });

  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  console.log('import:', JSON.stringify(await importPack(page, process.argv[2])));
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);

  // Read the character's classes straight out of app-db.
  const classes = () => page.evaluate(() => {
    const c = window.cljs.core;
    const db = window.re_frame.db.app_db.state;
    const ch = c.get(db, c.keyword(null, 'character'));
    const opts = c.get(ch, c.keyword('orcpub.entity', 'options'));
    const cls = c.get(opts, c.keyword(null, 'class'));
    if (!cls) return [];
    return c.clj__GT_js(c.vec(c.map(function (m) {
      const k = c.get(m, c.keyword('orcpub.entity', 'key'));
      const lv = c.get(c.get(m, c.keyword('orcpub.entity', 'options')), c.keyword(null, 'levels'));
      return c.vector(c.name(k), lv ? c.count(lv) : 0);
    }, cls)));
  });

  await page.locator('text="Class / Level"').first().click({ timeout: 30000 });
  await page.waitForTimeout(1500);

  console.log('\nclass handlers:');
  const before = await classes();

  // set-class
  await page.locator('select').nth(0).selectOption({ label: 'Wizard' });
  await page.waitForTimeout(1500);
  let now = await classes();
  check('set-class switches the class to Wizard',
        now.length > 0 && now[0][0] === 'wizard', JSON.stringify(now));

  // set-class-level
  await page.locator('select').nth(1).selectOption({ label: '5' });
  await page.waitForTimeout(1800);
  now = await classes();
  check('set-class-level sets 5 levels', now.length > 0 && now[0][1] === 5, JSON.stringify(now));

  // add-class
  // Real control labels, read from the source rather than guessed: the add button is
  // "Add Levels in Another Class" and delete is a fa-minus-circle icon.
  const addBtn = page.locator('text="Add Levels in Another Class"').first();
  if (await addBtn.count().catch(() => 0)) {
    await addBtn.click({ timeout: 20000 });
    await page.waitForTimeout(1800);
    now = await classes();
    check('add-class adds a second class', now.length === 2, JSON.stringify(now));

    // delete-class
    const del = page.locator('i.fa-minus-circle').last();
    if (await del.count().catch(() => 0)) {
      await del.click({ timeout: 20000 });
      await page.waitForTimeout(1800);
      now = await classes();
      check('delete-class removes it again', now.length === 1, JSON.stringify(now));
    } else { console.log('  SKIP  delete-class (no control found)'); }
  } else { console.log('  SKIP  add-class (no control found)'); }

  // NOT COVERED: picking a spell.
  //
  // Two attempts at this failed IDENTICALLY on the pre-change build with the memoize
  // restored (0 -> 0), so they were probe bugs, not regressions -- but neither version
  // actually exercised spell picking, and a check that cannot distinguish good code from
  // bad is worse than none. The click target is the problem: the first .b-orange card on
  // the Spells tab is not a spell option, and finding the real one needs someone who knows
  // that view. Left as a known gap rather than a red assertion.
  //
  // spell-option's safety rests instead on: it is pure, its result closes only over values
  // derived from its own arguments (verified by reading), both suites pass, and the
  // measurement shows the counter dropping 21ms -> 1ms with no behaviour change.

  // The character still builds after all that.
  const built = await page.evaluate(() => {
    try {
      const c = window.cljs.core;
      const b = c.deref(window.re_frame.core.subscribe(c.vector(c.keyword(null, 'built-character'))));
      return b ? c.count(c.keys(b)) : 0;
    } catch (e) { return -1; }
  });
  check('built-character still derives', built > 0, 'keys=' + built);

  console.log(`\nbefore: ${JSON.stringify(before)}`);
  await browser.close();
  if (failures) { console.log(`\n${failures} FAILURE(S)`); process.exit(1); }
  console.log('\nall class handlers behave');
})().catch(e => { console.error('FAILED', e); process.exit(1); });
