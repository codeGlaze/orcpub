// Every on/off switch on My Content works from the keyboard: it takes focus, and Space or Enter
// toggles it the way a click does.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dbAt, dismissCookieBar, dismissWhatsNew } = require('./lib');

const PAK = 'Key Pak';
const LIB = `{"${PAK}" {:orcpub.dnd.e5/feats {:keen {:name "Keen" :key :keen :option-pack "${PAK}" :description "Sharp."}}}}`;

const checked = loc => loc.getAttribute('aria-checked');

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e).slice(0, 160)));
  try {
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await dismissWhatsNew(page);
    await dismissCookieBar(page);
    await page.evaluate(lib => {
      ['plugins', 'plugins:rev', 'plugins:pre-fix', 'plugins:pre-fix-at', 'plugins:rejected'].forEach(k => localStorage.removeItem(k));
      localStorage.setItem('plugins', lib);
    }, LIB);
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(2000);
    await dismissWhatsNew(page);

    // Press `key` on a focused switch; returns [aria-checked before, after].
    const press = async (loc, key) => {
      const before = await checked(loc);
      await loc.focus();
      await page.keyboard.press(key);
      await page.waitForTimeout(400);
      return [before, await checked(loc)];
    };

    // Found by the text beside them, so a build without keyboard support fails on the keys.
    const all = page.locator('div.pointer', { hasText: 'All homebrew' }).locator('.dev-mode-switch').first();
    check('"All homebrew" can take focus', (await all.getAttribute('tabindex')) === '0', String(await all.getAttribute('tabindex')));
    let [b, a] = await press(all, 'Space');
    check('Space toggles "All homebrew"', b === 'true' && a === 'false', `${b} -> ${a}`);
    [b, a] = await press(all, 'Enter');
    check('Enter toggles it back', a === 'true', `${b} -> ${a}`);

    const source = page.locator('.item-list-item .dev-mode-switch:not(.compact)').first();
    [b, a] = await press(source, 'Space');
    check('Space turns a source off', b === 'true' && a === 'false', `${b} -> ${a}`);
    check('and the library records it', /true/.test(await dbAt(page, `[:plugins "${PAK}" :disabled?]`)),
          await dbAt(page, `[:plugins "${PAK}" :disabled?]`));
    await press(source, 'Space');

    for (let i = 0; i < 4; i++) {
      const n = await page.evaluate(() => {
        const b = [...document.querySelectorAll('#app button, #app span')].filter(e => e.textContent.trim() === 'expand');
        b.forEach(x => x.click());
        return b.length;
      });
      await page.waitForTimeout(400);
      if (!n) break;
    }
    const compact = page.locator('.item-list-item .dev-mode-switch.compact[title="On in the builder"]');
    check('the section and item switches are shown', (await compact.count()) >= 2, String(await compact.count()));
    const item = compact.last();
    [b, a] = await press(item, 'Space');
    check('Space turns an item off', b === 'true' && a === 'false', `${b} -> ${a}`);
    check('and the library records it',
          /true/.test(await dbAt(page, `[:plugins "${PAK}" :orcpub.dnd.e5/feats :keen :disabled?]`)));
    const section = compact.first();
    [b, a] = await press(section, 'Enter');
    check('Enter toggles a section', b !== a, `${b} -> ${a}`);

    const shown = page.locator('.mc-source-disabled .dev-mode-switch').first();
    [b, a] = await press(shown, 'Space');
    check('Space toggles "show disabled"', b !== a, `${b} -> ${a}`);

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
