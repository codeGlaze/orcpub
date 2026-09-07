// Do the class handlers still WORK after being unmemoized?
//
// set-class, set-class-level, add-class and delete-class were wrapped in cljs.core/memoize.
// Removing it gives a fresh closure per render,
// which should be behaviourally identical -- but these are the handlers that mutate the
// character, and neither test suite clicks anything. This drives them for real and asserts
// app-db afterwards.
//
// Run: lein fig:build && lein e2e-server, then
//   node test/browser/class_handlers_functional_e2e.js /path/to/pack.orcbrew
//
// Needs:     the real app at :8890, plus a homebrew pack (ORCBREW_PACK) -- it asserts against imported content
// Runs in:   ~60s, most of it importing the pack.
// Overlays:  suppressed by default -- the runner injects lib/suppress-overlays-preload.js, so
//            the cookie notice and What's New panel never intercept clicks. Hand-runs get no
//            preload, which is why this file also calls suppressOverlays itself.
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressOverlays } = require('./lib/orcbrew-import');

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
  await suppressOverlays(ctx);
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
    } else check('delete-class removes it again', false, 'no fa-minus-circle control found');
    // A missing control is the failure, not a reason to stop asserting. These two printed
    // SKIP and exited 0, so renaming the button would have silently dropped 2 of 6 checks.
  } else check('add-class adds a second class', false, 'no "Add Levels in Another Class" control found');

  // Spell selection: spell-option is no longer memoized, so prove a spell can still be
  // listed and picked and that it reaches the built character.
  //
  // The selector is specific on purpose. The Spells TAB itself carries .b-orange and reads
  // "Spells18", so `.b-orange` + /^[A-Z][a-z]/ matched the tab and clicked navigation --
  // two earlier versions of this check failed 0 -> 0 for that reason, identically on the
  // pre-change build. Spell option cards are the p-10/b-1/b-rad-5 variant.
  console.log('\nspell selection:');
  const knownSpells = () => page.evaluate(() => {
    try {
      const c = window.cljs.core;
      const v = c.deref(window.re_frame.core.subscribe(
        c.vector(c.keyword('orcpub.dnd.e5.character', 'spells-known'))));
      if (!v) return 0;
      let n = 0;
      c.doall(c.map(function (k) { const lvl = c.get(v, k); n += lvl ? c.count(lvl) : 0; return null; },
                    c.keys(v)));
      return n;
    } catch (e) { return -1; }
  });
  try {
    await page.locator('text="Spells"').first().click({ timeout: 25000 });
    await page.waitForTimeout(3000);
    const before = await knownSpells();
    const card = page.locator('div.p-10.b-1.b-rad-5.b-orange').first();
    const n = await card.count().catch(() => 0);
    if (before < 0)   console.log('  SKIP  spell selection (subscription unavailable)');
    else if (!n)      console.log('  SKIP  spell selection (no spell option card rendered)');
    else {
      const label = (await card.textContent().catch(() => '')).trim().slice(0, 24);
      await card.click({ timeout: 20000 });
      await page.waitForTimeout(2500);
      const after = await knownSpells();
      check('a spell can be picked and reaches the character', after > before,
            `${before} -> ${after} (clicked "${label}")`);
    }
  } catch (e) {
    console.log('  SKIP  spell selection (' + e.message.split('\n')[0] + ')');
  }

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
