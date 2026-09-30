// Restoring entries the loader set aside, as a user meets it: a stored library with a digit-led
// name and an entry with no source. Checks the panel shows what is still set aside after each
// click, fills the source from the pack, says what happened, and keeps working keys.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, dismissCookieBar, dismissWhatsNew } = require('./lib');

const PAK = 'Tide Pak';
const FEATS = `[:plugins "${PAK}" :orcpub.dnd.e5/feats]`;
// What the browser already holds: two damaged entries and one good one.
const LIB = `{"${PAK}" {:orcpub.dnd.e5/feats {` +
  `:9-lives {:name "9 Lives" :key :9-lives :option-pack "${PAK}" :description "Cheat death nine times."} ` +
  `:stone-elf-trcs {:name "@@@" :key :stone-elf-trcs :description "Stone-skinned elves of the deep."} ` +
  `:tidewalker {:name "Tidewalker" :key :tidewalker :option-pack "${PAK}" :description "Walk on water."}}}}`;

const panelText = page => page.locator('#app').innerText();
const message = async page =>
  (await page.locator('#app .message').first().innerText().catch(() => '')).trim();

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  try {
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await dismissCookieBar(page); await dismissWhatsNew(page);
    await page.evaluate(lib => { localStorage.setItem('plugins', lib); localStorage.removeItem('plugins:rejected'); }, LIB);
    await page.reload({ waitUntil: 'load' }); await page.waitForTimeout(2500);
    await dismissCookieBar(page);

    let text = await panelText(page);
    check('the loader sets aside the two damaged entries', /2 entries couldn't load/.test(text));
    check('a set-aside entry shows its description', text.includes('Stone-skinned elves of the deep.'));
    const sources = await page.locator('#app input.input').evaluateAll(els => els.map(e => e.value));
    check('an entry with no source is offered the pack it is listed under',
          sources.filter(v => v === PAK).length === 2, JSON.stringify(sources));
    await page.screenshot({ path: path.join(SHOTS, 'quarantine-1-set-aside.png'), fullPage: true });

    // Fix one by hand, leave the other: the panel must then list only what is still set aside.
    const inputs = page.locator('#app input.input');
    const values = () => inputs.evaluateAll(els => els.map(e => e.value));
    const junk = (await values()).indexOf('@@@');
    await inputs.nth(junk).fill('Stone Elf');

    // First with the library write failing: nothing is restored, and what was typed stays.
    await page.evaluate(() => {
      const set = Storage.prototype.setItem;
      window.__setItem = set;
      Storage.prototype.setItem = function (k, v) {
        if (k === 'plugins') throw new DOMException('full', 'QuotaExceededError');
        return set.call(this, k, v);
      };
    });
    await page.getByRole('button', { name: 'Restore', exact: true }).first().click();
    await page.waitForTimeout(1200);
    check('a restore whose write fails keeps what was typed',
          /2 entries couldn't load/.test(await panelText(page)) && (await values())[junk] === 'Stone Elf',
          JSON.stringify(await values()));
    await page.evaluate(() => { Storage.prototype.setItem = window.__setItem; });
    await page.locator('.message .close, .message button').first().click().catch(() => {});
    await page.getByRole('button', { name: 'Restore', exact: true }).first().click();
    await page.waitForTimeout(1200);
    text = await panelText(page);
    const msg1 = await message(page);
    check('after a partial restore the panel lists only what is still set aside',
          /1 entry couldn't load/.test(text) && !text.includes('feats / stone-elf-trcs') && text.includes('feats / 9-lives'));
    check('the message says what came back and what did not',
          msg1.includes('Restored 1 to “Tide Pak”') && msg1.includes('“@@@” is now “Stone Elf”') &&
          msg1.includes('“9 Lives” needs a name that starts with a letter'), msg1);
    await page.screenshot({ path: path.join(SHOTS, 'quarantine-2-partial.png'), fullPage: true });


    await page.getByRole('button', { name: 'Auto-name & Restore' }).first().click();
    await page.waitForTimeout(1200);
    text = await panelText(page);
    const msg2 = await message(page);
    check('after the last restore the panel is gone', !/couldn't load/.test(text));
    check('the message names the new name', msg2.includes('“9 Lives” is now “Nine Lives”'), msg2);
    await page.screenshot({ path: path.join(SHOTS, 'quarantine-3-restored.png'), fullPage: true });

    await page.reload({ waitUntil: 'load' }); await page.waitForTimeout(2500);
    const feats = await dbAt(page, FEATS);
    check('after a reload nothing is set aside', !/couldn't load/.test(await panelText(page)));
    check('the entry that had no source kept its working key and is filed under the pack',
          /:stone-elf-trcs \{[^}]*:key :stone-elf-trcs[^}]*:option-pack "Tide Pak"/.test(feats) ||
          /:stone-elf-trcs \{[^}]*:option-pack "Tide Pak"[^}]*:key :stone-elf-trcs/.test(feats), feats);
    check('the digit-led entry got a working key and remembers the old one',
          /:nine-lives \{[^}]*:former-keys \[:9-lives\]/.test(feats), feats);
    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
