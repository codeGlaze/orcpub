// A Move that has to rename says when items in other packs still use the copy already in the target
// (owner's decision Q2, homebrew-keys-design.md step 7).
//
// Stored library: "Cant" in Origin Pak and in Target Pak under the same key, and a race in Third Pak
// that grants the language by that key. Moving Origin's Cant into Target renames it; the Third Pak
// race keeps pointing at Target's Cant, and the message says so.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, dismissCookieBar, dismissWhatsNew } = require('./lib');

const LIB = '{"Origin Pak" {:orcpub.dnd.e5/languages {:cant {:name "Cant" :key :cant :option-pack "Origin Pak"}}}' +
  ' "Target Pak" {:orcpub.dnd.e5/languages {:cant {:name "Target Cant" :key :cant :option-pack "Target Pak"}}}' +
  ' "Third Pak" {:orcpub.dnd.e5/races {:thieves {:name "Thieves" :key :thieves :option-pack "Third Pak"' +
  ' :props {:language {:cant true}} :description "Speak Cant."}}}}';

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  const myContent = async () => {
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(2000);
    await dismissWhatsNew(page);
  };
  try {
    await myContent();
    await dismissCookieBar(page);
    await page.evaluate(lib => {
      ['plugins', 'plugins:rev', 'plugins:pre-fix', 'plugins:pre-fix-at'].forEach(k => localStorage.removeItem(k));
      localStorage.setItem('plugins', lib);
    }, LIB);
    await myContent();

    check('entered select mode', await page.getByText(/move \/ copy/i).first().click().then(() => true, () => false));
    await page.waitForTimeout(500);
    for (let i = 0; i < 4; i++) {
      const n = await page.evaluate(() => {
        const b = [...document.querySelectorAll('#app button, #app span')].filter(e => e.textContent.trim() === 'expand');
        b.forEach(x => x.click());
        return b.length;
      });
      await page.waitForTimeout(400);
      if (!n) break;
    }
    // Tick Origin's "Cant" (Target's is named "Target Cant").
    const ticked = await page.evaluate(() => {
      const row = [...document.querySelectorAll('#app .mc-selrow')].find(r => r.textContent.trim() === 'Cant');
      if (!row) return false;
      row.click();
      return true;
    });
    check('ticked the origin copy', ticked);
    await page.waitForTimeout(400);
    await page.locator('#app select').filter({ has: page.locator('option[value="Target Pak"]') }).first()
      .selectOption('Target Pak');
    await page.getByRole('button', { name: 'Move here' }).click();
    await page.waitForTimeout(1200);

    const msg = (await page.locator('#app .message').first().innerText().catch(() => '')).trim();
    check('the Move says it renamed', /Moved 1 item to "Target Pak" \(renamed 1 to avoid a name clash\)/.test(msg), msg);
    check('and that another pack still uses the copy already there',
          msg.includes('1 item in other packs still uses the one already in “Target Pak”.'), msg);
    await page.screenshot({ path: path.join(SHOTS, 'move-note.png'), fullPage: true });
    check('the race still names the target\'s copy',
          /:cant true/.test(await dbAt(page, '[:plugins "Third Pak" :orcpub.dnd.e5/races :thieves :props :language]')));
    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
