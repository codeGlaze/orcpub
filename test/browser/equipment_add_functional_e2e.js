// Can you still add an inventory item through whatever control the Equipment tab uses?
//
// The add control has changed four times on this branch: a native <select>, then
// option-menu with :max-rendered, then a hand-rolled popover, then inventory-combobox.
// This test asserts the one thing that survives all of them -- that picking an item puts
// it in the CHARACTER ENTITY, read back out of re-frame's app-db. Neither test suite
// clicks anything, so without this the add path has no functional coverage at all.
//
// It was left targeting .opt-menu-* selectors after the combobox swap and failed three
// assertions against a control that was no longer wired; retargeted rather than deleted,
// because the app-db assertion is the part worth keeping.
//
// Needs:     the real app at :8890, plus a homebrew pack (ORCBREW_PACK) -- it asserts against imported content
// Runs in:   ~55s, most of it importing the pack.
// Overlays:  suppressed by default -- the runner injects lib/suppress-overlays-preload.js, so
//            the cookie notice and What's New panel never intercept clicks. Hand-runs get no
//            preload, which is why this file also calls suppressOverlays itself.
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressOverlays } = require('./lib/orcbrew-import');

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
  const ctx = await browser.newContext(); await suppressOverlays(ctx);
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
  const search = page.locator('input.inv-combo-input');
  check('the add control rendered', await search.count() > 0, `${await search.count()} inputs`);

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
  if (await search.count()) {
    const input = search.first();
    await input.scrollIntoViewIfNeeded();
    await input.click();
    await page.waitForTimeout(600);
    const opened = await page.locator('.inv-combo-row').count();
    check('opening lists options', opened > 0, `${opened} rows`);

    // Filter by a term taken from a row actually in THIS section. A hardcoded term made a
    // working filter look broken once already -- "leather" matches nothing in Weapons.
    const term = (await page.locator('.inv-combo-row').first().textContent()).trim().slice(0, 4);
    await input.fill(term);
    await page.waitForTimeout(1200);
    const after = await page.locator('.inv-combo-row').count();
    // Strictly fewer. `after <= opened` was the original here and it passes when filtering
    // does nothing at all -- the name claims "narrows" while the assertion permits
    // "unchanged", which is an assertion written to pass.
    check('filtering narrows the list', after > 0 && after < opened, `${opened} -> ${after} on "${term}"`);

    const first = page.locator('.inv-combo-row').first();
    if (await first.count()) {
      await first.click({ timeout: 20000 });
      await page.waitForTimeout(2000);
      check('picking an item adds it to the character', await equipped() > before,
            `${before} -> ${await equipped()}`);
    } else check('picking an item adds it to the character', false, 'no row to click');

    // No cap: the whole section must be reachable without typing. This is the assertion
    // that replaced the old "the cap binds" one -- the cap was removed on purpose.
    // Click to reopen, do not fill(''): picking already cleared the query, so filling it
    // with the same empty string dispatches no input event and the popover stays shut.
    await input.click();
    await page.waitForTimeout(1000);
    const declared = await page.evaluate(() => {
      const el = document.querySelector('input.inv-combo-input');
      const m = (el.placeholder || '').match(/\((\d+)\)/);
      return m ? +m[1] : -1;
    });
    const rendered = await page.locator('.inv-combo-row').count();
    check('every option is reachable without typing', rendered === declared,
          `${rendered} rendered vs ${declared} in section`);
  }

  await browser.close();
  if (failures) { console.log(`\n${failures} FAILURE(S)`); process.exit(1); }
  console.log('\nequipment picker works');
})().catch(e => { console.error('FAILED', e); process.exit(1); });
