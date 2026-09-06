// Which pickers in the app are actually big?
//
// Counts <option> elements per <select> across the pages that have selectors, so the
// question "does the combobox need to reach other pickers" is answered by a census
// rather than by guessing which lists feel large.
//
// Run: node test/browser/select_option_census_e2e.js /path/to/pack.orcbrew
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import');

function findChrome() {
  if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
  return path.join(b, d, 'chrome-linux', 'chrome');
}

const ROUTES = [
  ['character-builder',  '/pages/dnd/5e/character-builder'],
  ['encounter-builder',  '/pages/dnd/5e/encounter-builder'],
  ['combat-tracker',     '/pages/dnd/5e/combat-tracker'],
  ['monster-builder',    '/pages/dnd/5e/monster-builder'],
  ['spell-builder',      '/pages/dnd/5e/spell-builder'],
  ['class-builder',      '/pages/dnd/5e/class-builder'],
  ['magic-item-builder', '/pages/dnd/5e/magic-item-builder'],
];

(async () => {
  const PACK = process.argv[2];
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1500, height: 1000 } });
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();
  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  if (PACK) console.log('import:', JSON.stringify(await importPack(page, PACK)));

  for (const [name, url] of ROUTES) {
    try {
      await page.goto('http://localhost:8890' + url, { waitUntil: 'load', timeout: 900000 });
      await page.waitForTimeout(9000);
      // Clicking every "add" control surfaces selectors that only exist once a row is added
      // (monster-selector lives inside an encounter row, not on the empty page).
      for (const label of ['Add Monster', 'Add Creature', 'Add Character', 'Add Spell', 'Add']) {
        const b = page.locator(`text="${label}"`).first();
        if (await b.count().catch(() => 0)) { await b.click({ timeout: 4000 }).catch(() => {}); await page.waitForTimeout(1200); }
      }
      const r = await page.evaluate(() => {
        const sels = [...document.querySelectorAll('select')].map(s => ({
          n: s.options.length,
          cls: (s.className || '').split(/\s+/).slice(0, 2).join('.'),
        }));
        sels.sort((a, b) => b.n - a.n);
        return { total: sels.length, options: sels.reduce((a, s) => a + s.n, 0), top: sels.slice(0, 4),
                 combos: document.querySelectorAll('input.inv-combo-input').length };
      });
      const top = r.top.map(s => `${s.n}(${s.cls || 'no-class'})`).join(' ');
      console.log(`${name.padEnd(20)} selects=${String(r.total).padEnd(3)} options=${String(r.options).padEnd(5)} combos=${r.combos}  biggest: ${top || '-'}`);
    } catch (e) {
      console.log(`${name.padEnd(20)} FAILED ${String(e.message).split('\n')[0].slice(0, 70)}`);
    }
  }
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
