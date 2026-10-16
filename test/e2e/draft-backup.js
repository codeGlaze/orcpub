// A browser draft the app cannot read is set aside, not deleted, and Data View's draft page
// offers it for download (character-rescue.md, item 5). The unreadable draft is written into
// this browser's storage before the app starts; everything after that is a page load or a click.
//
// Needs the seeded server, lein fig:build and lein fig:ledger:
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/draft-backup.js
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dismissWhatsNew } = require('./lib');
const { wait } = require('./rescue-lib');

const BROKEN = '{:orcpub.entity.strict/selections [{:orcpub.entity.strict/key :race :orcpub.entity.strict/option {:orcpub.entity.strict/key :tidefolk}}';

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  try {
    const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 }, acceptDownloads: true })).newPage();
    await page.goto(`${BASE}/pages/dnd/5e/spells`, { waitUntil: 'load' }); await wait(3000);
    await page.evaluate(t => { localStorage.setItem('character', t); localStorage.removeItem('character:corrupt'); }, BROKEN);
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' }); await wait(5000); await dismissWhatsNew(page);
    const shown = await page.locator('#app').innerText();
    check('the builder still opens', /Character Builder/.test(shown) && /SAVE NEW CHARACTER/i.test(shown), shown.slice(0, 80));
    const slots = await page.evaluate(() => ({ active: localStorage.getItem('character'), kept: localStorage.getItem('character:corrupt') }));
    check('the unreadable draft is set aside, word for word', slots.kept === BROKEN, JSON.stringify(slots).slice(0, 160));
    check('and no longer in the active slot', slots.active === null || slots.active !== BROKEN);
    await page.evaluate(() => localStorage.removeItem('character'));
    await page.goto(`${BASE}/pages/dnd/5e/character-data`, { waitUntil: 'load' });
    await page.waitForFunction(() => document.getElementById('ledger') && !/^Loading/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
    const text = await page.locator('#ledger').innerText();
    check("the draft page says the draft was set aside", /could not be read, so the app set it aside/.test(text), text.slice(0, 120));
    check('it shows the draft as it was', text.includes(':tidefolk'));
    const [download] = await Promise.all([page.waitForEvent('download', { timeout: 10000 }), page.getByRole('button', { name: 'Download the data' }).click()]);
    const saved = require('fs').readFileSync(await download.path(), 'utf8');
    check('Download gives the draft back exactly', saved === BROKEN && download.suggestedFilename() === 'character-draft-set-aside.edn', download.suggestedFilename());
  } catch (e) {
    check('the run finished', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})();
