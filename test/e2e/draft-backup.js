// A browser draft the app cannot read is set aside, not deleted, and Data View's draft page
// offers it for download (character-rescue.md, item 5): the old single slot, and a tab's own
// per-character draft. The unreadable draft is written into
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
    // Each case: where the unreadable draft sits, and where the app should set it aside. The tab's
    // draft key is this tab's sessionStorage, as the builder writes it.
    const cases = [
      { name: 'the old single slot', slot: 'character', aside: 'character:corrupt', tabKey: null },
      { name: "this tab's per-character draft", slot: 'character-draft/new-backup-test', aside: 'character-draft-corrupt/new-backup-test', tabKey: 'new-backup-test' },
    ];
    for (const c of cases) {
      const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 }, acceptDownloads: true })).newPage();
      await page.goto(`${BASE}/pages/dnd/5e/spells`, { waitUntil: 'load' }); await wait(3000);
      await page.evaluate(({ slot, aside, tabKey, text }) => {
        localStorage.setItem(slot, text); localStorage.removeItem(aside);
        if (tabKey) sessionStorage.setItem('builder-draft-key', tabKey);
      }, { ...c, text: BROKEN });
      await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' }); await wait(5000); await dismissWhatsNew(page);
      const shown = await page.locator('#app').innerText();
      check(`[${c.name}] the builder still opens`, /Character Builder/.test(shown) && /SAVE NEW CHARACTER/i.test(shown), shown.slice(0, 80));
      const slots = await page.evaluate(({ slot, aside }) => ({ active: localStorage.getItem(slot), kept: localStorage.getItem(aside) }), c);
      check(`[${c.name}] the unreadable draft is set aside, word for word`, slots.kept === BROKEN, JSON.stringify(slots).slice(0, 160));
      check(`[${c.name}] and no longer in the active slot`, slots.active === null || slots.active !== BROKEN);
      // The builder writes a fresh draft for the tab; the set-aside one is what the page shows once that is gone.
      await page.evaluate(() => { for (const k of Object.keys(localStorage)) if (k === 'character' || k.startsWith('character-draft/')) localStorage.removeItem(k); });
      await page.goto(`${BASE}/pages/dnd/5e/character-data`, { waitUntil: 'load' });
      await page.waitForFunction(() => document.getElementById('ledger') && !/^Loading/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
      const text = await page.locator('#ledger').innerText();
      check(`[${c.name}] the draft page says the draft was set aside`, /could not be read, so the app set it aside/.test(text), text.slice(0, 120));
      check(`[${c.name}] it shows the draft as it was`, text.includes(':tidefolk'));
      const [download] = await Promise.all([page.waitForEvent('download', { timeout: 10000 }), page.getByRole('button', { name: 'Download the data' }).click()]);
      const saved = require('fs').readFileSync(await download.path(), 'utf8');
      check(`[${c.name}] Download gives the draft back exactly`, saved === BROKEN && download.suggestedFilename() === 'character-draft-set-aside.edn', download.suggestedFilename());
      await page.context().close();
    }
  } catch (e) {
    check('the run finished', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})();
