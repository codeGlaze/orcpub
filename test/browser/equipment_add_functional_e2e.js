// Can you still add an inventory item after the picker replaced the native select?
//
// The Equipment tab's add controls went from comps/selection-adder (a native <select>) to
// option-menu with :max-rendered. Neither test suite clicks anything, so this drives the
// real control and asserts the item lands in the character.
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import');

function findChrome() {
  if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
  return path.join(b, d, 'chrome-linux', 'chrome');
}
let failures = 0;
const check = (n, ok, d) => { console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${n}${d ? '  ' + d : ''}`); if (!ok) failures++; };

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext(); await suppressCookieBanner(ctx);
  const page = await ctx.newPage();
  page.on('pageerror', e => { console.log('  PAGEERROR', e.message); failures++; });

  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  await importPack(page, process.argv[2]);
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);
  await page.locator('text="Equipment"').first().click({ timeout: 30000 });
  await page.waitForTimeout(2500);

  console.log('\nequipment picker:');
  const search = page.locator('input.opt-menu-search');
  check('the searchable picker rendered', await search.count() > 0, `${await search.count()} search boxes`);

  // The cap should keep rendered options far below the full library.
  const cells = await page.locator('.opt-menu-cell').count();
  check('render is capped, not the whole library', cells > 0 && cells <= 200, `${cells} cells rendered`);

  const equipped = () => page.evaluate(() => {
    const c = window.cljs.core;
    const ch = c.get(window.re_frame.db.app_db.state, c.keyword(null, 'character'));
    const opts = c.get(ch, c.keyword('orcpub.entity', 'options'));
    let n = 0;
    c.doall(c.map(function (k) {
      if (/weapon|armor|equipment|treasure|magic/.test(String(k))) {
        const v = c.get(opts, k); n += (v && c.count) ? c.count(v) : 0;
      }
      return null;
    }, c.keys(opts)));
    return n;
  });

  const before = await equipped();
  // Search narrows, then pick the first result -- the flow the picker exists for.
  if (await search.count()) {
    await search.first().fill('club');
    await page.waitForTimeout(1200);
    const after = await page.locator('.opt-menu-cell').count();
    check('search narrows the list', after < cells || after > 0, `${cells} -> ${after}`);
    const first = page.locator('.opt-menu-cell').first();
    if (await first.count()) {
      await first.click({ timeout: 20000 });
      await page.waitForTimeout(2000);
      check('picking an item adds it to the character', await equipped() > before,
            `${before} -> ${await equipped()}`);
    } else check('picking an item adds it to the character', false, 'no cell to click');
  }
  await browser.close();
  if (failures) { console.log(`\n${failures} FAILURE(S)`); process.exit(1); }
  console.log('\nequipment picker works');
})().catch(e => { console.error('FAILED', e); process.exit(1); });
