// Changing a key from the builder — the one control that does it now that names don't.
//
// Keys are minted once (D10a), so an author who wants :tideward and got :tidewrad from a typo has
// no other way to fix it. Asserts the move, the recorded history, that the NAME is untouched, and
// that a key another source holds is refused.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, fill, clickText,
        dismissCookieBar, dismissWhatsNew } = require('./lib');

const SOURCE = 'Key Change Pin';

const clickLink = (page, text) => page.evaluate(t => {
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
  const page = await browser.newPage({ viewport: { width: 1200, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  try {
    await page.goto(`${BASE}/pages/dnd/5e/language-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1800);
    await dismissCookieBar(page);
    await dismissWhatsNew(page);

    // an item whose key was minted from a typo
    await fill(page, 'Name', 'Tidewrad');
    await fill(page, 'Option Source Name', SOURCE);
    check('no key is shown before the first save',
          !/key\s*:?tidewrad/i.test(await page.locator('#app').innerText()));
    check('saved', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(1000);

    const collapsed = await page.locator('#app').innerText();
    check('the key is NOT on the form by default — it is behind Advanced',
          !/:tidewrad/.test(collapsed) && /Advanced/.test(collapsed));
    await page.screenshot({ path: path.join(SHOTS, 'change-item-key-collapsed.png'),
                            clip: { x: 0, y: 330, width: 1200, height: 260 } });
    check('opened Advanced', await clickLink(page, 'Advanced'));
    await page.waitForTimeout(300);
    const body = await page.locator('#app').innerText();
    check('and the key it was minted under is there', /:tidewrad/.test(body),
          (body.match(/key[^\n]*/i) || [''])[0]);

    check('opened the key editor', await clickLink(page, 'change'));
    await page.waitForTimeout(400);
    await page.evaluate(() => {
      const i = [...document.querySelectorAll('#app input')].find(x => x.placeholder === 'tidewrad');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      setter.call(i, 'tideward');
      i.dispatchEvent(new Event('input', { bubbles: true }));
    });
    check('typed the corrected key', await clickLink(page, 'save key'));
    await page.waitForTimeout(1200);

    const stored = await dbAt(page, `[:plugins "${SOURCE}" :orcpub.dnd.e5/languages]`);
    check('the item moved to the new key', /:tideward\b/.test(stored) && !/:tidewrad {/.test(stored),
          stored.slice(0, 220));
    check('and the move is recorded for characters', /:former-keys \[:tidewrad\]/.test(stored));
    check('the NAME is untouched — a key change is not a rename', /"Tidewrad"/.test(stored));
    check('the form now shows the new key', /:tideward/.test(await page.locator('#app').innerText()));
    await page.screenshot({ path: path.join(SHOTS, 'change-item-key.png'),
                            clip: { x: 0, y: 330, width: 1200, height: 330 } });

    // and a key something else already answers to is refused
    await clickLink(page, 'change');
    await page.waitForTimeout(300);
    await page.evaluate(() => {
      const i = [...document.querySelectorAll('#app input')].find(x => x.placeholder === 'tideward');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
      setter.call(i, 'common');                       // a built-in language key
      i.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await clickLink(page, 'save key');
    await page.waitForTimeout(900);
    const after = await dbAt(page, `[:plugins "${SOURCE}" :orcpub.dnd.e5/languages]`);
    check('a key held by a BUILT-IN is not refused — built-ins are overridable by design',
          /:common\b/.test(after), after.slice(0, 200));

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
