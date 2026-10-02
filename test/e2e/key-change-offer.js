// Changing a key moves links in the item's own pack and asks about the rest (homebrew-keys-design.md,
// "One rule for links in other packs").
//
// Stored library: a language "Cant" in Tongue Pak, and a race in Folk Pak that grants it by key.
// Changing the language's key leaves the race alone and offers to point it at the new key; the
// offer moves it only when clicked.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, dismissCookieBar, dismissWhatsNew } = require('./lib');

const LIB = '{"Tongue Pak" {:orcpub.dnd.e5/languages {:cant {:name "Cant" :key :cant :option-pack "Tongue Pak"}}}' +
  ' "Folk Pak" {:orcpub.dnd.e5/races {:thieves {:name "Thieves" :key :thieves :option-pack "Folk Pak"' +
  ' :props {:language {:cant true}} :description "Speak Cant."}}}}';
const RACE_LANGS = '[:plugins "Folk Pak" :orcpub.dnd.e5/races :thieves :props :language]';

const clickLeaf = (page, text) => page.evaluate(t => {
  const e = [...document.querySelectorAll('#app span,#app a,#app button')]
    .filter(x => x.children.length === 0 && x.textContent.trim() === t);
  if (!e.length) return false;
  e[e.length - 1].click();
  return true;
}, text);

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  try {
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(2000);
    await dismissWhatsNew(page);
    await dismissCookieBar(page);
    await page.evaluate(lib => {
      ['plugins', 'plugins:rev', 'plugins:pre-fix', 'plugins:pre-fix-at'].forEach(k => localStorage.removeItem(k));
      localStorage.setItem('plugins', lib);
    }, LIB);
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(2000);
    await dismissWhatsNew(page);

    for (let i = 0; i < 4; i++) {
      const n = await page.evaluate(() => {
        const b = [...document.querySelectorAll('#app button, #app span')].filter(e => e.textContent.trim() === 'expand');
        b.forEach(x => x.click());
        return b.length;
      });
      await page.waitForTimeout(400);
      if (!n) break;
    }
    const opened = await page.evaluate(() => {
      const rows = [...document.querySelectorAll('#app div')].filter(e =>
        (e.textContent || '').includes('Cant') && !(e.textContent || '').includes('Thieves') &&
        [...e.querySelectorAll('button')].some(b => b.textContent.trim() === 'edit'));
      const row = rows[rows.length - 1];
      if (!row) return false;
      [...row.querySelectorAll('button')].find(b => b.textContent.trim() === 'edit').click();
      return true;
    });
    check('opened the language builder', opened);
    await page.waitForTimeout(1500);

    check('opened the key editor', await clickLeaf(page, 'change'));
    await page.waitForTimeout(400);
    await page.evaluate(() => {
      const i = document.querySelector('.bf-meta input');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      setter.call(i, 'thieves-cant');
      i.dispatchEvent(new Event('input', { bubbles: true }));
    });
    check('saved the new key', await clickLeaf(page, 'save'));
    await page.waitForTimeout(1500);

    const msg = (await page.locator('#app .message').first().innerText().catch(() => '')).trim();
    check('the message offers the other pack\'s link',
          msg.includes('“Thieves” (Folk Pak) in other packs still uses the old key. Point it at the new one'), msg);
    check('the other pack is not changed yet', /:cant true/.test(await dbAt(page, RACE_LANGS)));
    await page.screenshot({ path: path.join(SHOTS, 'key-change-offer.png'), fullPage: true });

    check('accepted the offer', await clickLeaf(page, 'Point it at the new one'));
    await page.waitForTimeout(1200);
    check('the race now grants the renamed language', /:thieves-cant true/.test(await dbAt(page, RACE_LANGS)),
          await dbAt(page, RACE_LANGS));
    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
