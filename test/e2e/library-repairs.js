// What a library already damaged by past renames shows, and the fixes it offers.
//
// Seeds a stored library (what a browser would already hold): a subrace still naming a race by the
// key it had before a rename, a subrace naming a race that does not exist, and a race naming a
// homebrew language by display name. Items without a stored :key make the load tidy up and keep a
// copy first.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, fill, clickText, dismissCookieBar, dismissWhatsNew } = require('./lib');

const LIB = '{"Folk Pak" {' +
  ':orcpub.dnd.e5/races {:folk-x {:name "Folk X" :key :folk-x :option-pack "Folk Pak" :former-keys [:folk] :description "Renamed."}' +
  ' :tidefolk {:name "Tidefolk" :option-pack "Folk Pak" :languages #{"Tidetongue"} :description "Sea folk."}}' +
  ' :orcpub.dnd.e5/subraces {:hill-folk {:name "Hill Folk" :option-pack "Folk Pak" :race :folk :description "Old link."}' +
  ' :lost-folk {:name "Lost Folk" :option-pack "Folk Pak" :race :gone :description "No race."}}' +
  ' :orcpub.dnd.e5/languages {:tidetongue-fp {:name "Tidetongue" :key :tidetongue-fp :option-pack "Folk Pak"}}}}';

const myContent = async page => {
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
  await page.waitForTimeout(2000);
  await dismissWhatsNew(page);
};

const expandAll = page => page.evaluate(() => {
  const b = [...document.querySelectorAll('#app button, #app span')].filter(e => e.textContent.trim() === 'expand');
  b.forEach(x => x.click());
  return b.length;
});

const editRow = (page, name) => page.evaluate(nm => {
  const rows = [...document.querySelectorAll('#app div')].filter(e =>
    new RegExp(nm).test(e.textContent || '') &&
    [...e.querySelectorAll('button')].some(b => b.textContent.trim() === 'edit'));
  const row = rows[rows.length - 1];
  if (!row) return false;
  [...row.querySelectorAll('button')].find(b => b.textContent.trim() === 'edit').click();
  return true;
}, name);

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  try {
    await myContent(page);
    await dismissCookieBar(page);
    await page.evaluate(lib => {
      ['plugins', 'plugins:rev', 'plugins:pre-fix', 'plugins:pre-fix-at', 'plugins:repairs-dismissed']
        .forEach(k => localStorage.removeItem(k));
      localStorage.setItem('plugins', lib);
    }, LIB);
    await myContent(page);
    for (let i = 0; i < 3 && await expandAll(page); i++) await page.waitForTimeout(400);
    let text = await page.locator('#app').innerText();

    check('the load kept a copy before tidying', /A copy of your library from .* is kept/.test(text));
    check('the repair panel offers the renamed race',
          /1 link to fix/.test(text) && text.includes('“Hill Folk” uses “folk”, which was renamed. Switch it to “Folk X”.'),
          text.split('\n').filter(l => /link|Hill Folk/.test(l)).join(' | '));
    check('My Content marks the subrace whose race does not exist',
          text.includes('Uses race “gone”, which isn\'t in your library.'));
    await page.screenshot({ path: path.join(SHOTS, 'repairs-1-offered.png'), fullPage: true });

    await page.getByRole('button', { name: 'Fix', exact: true }).first().click();
    await page.waitForTimeout(1000);
    check('Fix repoints the subrace', /:race :folk-x/.test(await dbAt(page, '[:plugins "Folk Pak" :orcpub.dnd.e5/subraces :hill-folk]')));
    check('and the panel is gone', !/link to fix|links to fix/.test(await page.locator('#app').innerText()));

    // The builder shows the same mark beside the key.
    check('opened the subrace with no race', await editRow(page, 'Lost Folk'));
    await page.waitForTimeout(1600);
    check('the builder marks it too',
          (await page.locator('#app').innerText()).includes('The saved version uses race “gone”'));
    await page.screenshot({ path: path.join(SHOTS, 'repairs-2-builder-mark.png'), fullPage: true });

    // Renaming the language carries the new name into the race that names it.
    await myContent(page);
    for (let i = 0; i < 3 && await expandAll(page); i++) await page.waitForTimeout(400);
    check('opened the language', await editRow(page, 'Tidetongue'));
    await page.waitForTimeout(1600);
    await fill(page, 'Name', 'Tide-tongue');
    check('saved it under its new name', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(1000);
    const race = await dbAt(page, '[:plugins "Folk Pak" :orcpub.dnd.e5/races :tidefolk :languages]');
    check('the race now names the language by its new name', /Tide-tongue/.test(race) && !/"Tidetongue"/.test(race), race);

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
