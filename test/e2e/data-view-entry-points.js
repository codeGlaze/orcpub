// The ways a player reaches the Data View page (character-rescue.md, entry points):
//   a muted gear at the right end of the owner's share line, and after Delete in the owner's
//   opened character-list row (neither shown to others);
//   "Fix in Data View" in the builder's Missing Content warning, for a saved character and for
//   this browser's unsaved draft;
//   "Repair this character", first on the "couldn't be loaded" panel;
//   the notice under the loading picture when the app never loads (its script blocked here).
// Built on the trapped-pick case: the Tide Pak wizard, its pack then switched off. To bring up the
// "couldn't be loaded" panel this browser alone is handed an unreadable copy of the character (a
// network fixture; nothing stored changes). Every other change is a click or a select.
//
// Needs the seeded server (kaylee / serenity99, zoe / washburne7), lein fig:build, lein fig:ledger:
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/data-view-entry-points.js
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dismissWhatsNew, clickTab } = require('./lib');
const { wait, importPak, build, myContent, logIn, clickCard } = require('./rescue-lib');

const PHONE_UA = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36';
const onDataView = async (page, path) => {
  await page.waitForURL(u => u.pathname === path, { timeout: 15000 }).catch(() => {});
  await page.waitForFunction(() => document.getElementById('ledger') && !/^Loading/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 }).catch(() => {});
  return { path: new URL(page.url()).pathname, title: await page.title(),
           rows: await page.locator('#ledger tbody tr').count() };
};
async function openWarning(page) {
  const w = page.locator('#missing-content-warning');
  await w.waitFor({ timeout: 15000 });
  if (!(await page.locator('#missing-content-details').count())) { await w.locator('div.flex.align-items-c.pointer').first().click(); await wait(600); }
  return page.locator('#missing-content-details');
}

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  try {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await ctx.newPage();
    await logIn(page, 'kaylee', 'serenity99');
    await importPak(page);
    const id = await build(page);
    const dataPath = `/pages/dnd/5e/characters/${id}/data`;

    // An unsaved draft that uses the pack too: New, then the homebrew race, never saved.
    await page.getByText('New', { exact: true }).first().click(); await wait(1000);
    const confirmNew = page.getByText(/^create new character$/i).first();
    if (await confirmNew.isVisible().catch(() => false)) { await confirmNew.click(); await wait(2000); }
    await clickTab(page, 'Race');
    await clickCard(page, 'Tidefolk');
    await myContent(page);
    await page.locator('.item-list-item .dev-mode-switch:not(.compact)').first().click(); await wait(2000);

    // 4b. the draft's Missing Content warning
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' }); await wait(4000); await dismissWhatsNew(page);
    let details = await openWarning(page);
    check('the warning names both ways out', /restore it, or remove what you no longer need/.test(await details.innerText()));
    await details.getByText('Fix in Data View', { exact: true }).click();
    let at = await onDataView(page, '/pages/dnd/5e/character-data');
    check("for an unsaved draft, Fix in Data View opens this browser's draft", at.path === '/pages/dnd/5e/character-data' && at.rows > 0, JSON.stringify(at));

    // 1. the character page gear
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(4500); await dismissWhatsNew(page);
    const gear = page.locator('#app a.data-view-gear');
    check("the owner's character page shows the Data View gear", await gear.isVisible());
    check('it is named Data View for hover and screen readers', (await gear.getAttribute('title')) === 'Data View' && (await gear.getAttribute('aria-label')) === 'Data View');
    const placed = await gear.evaluate((g) => { const row = g.parentElement.getBoundingClientRect(), r = g.getBoundingClientRect(),
      share = g.parentElement.firstElementChild.getBoundingClientRect();
      return { rowRight: row.right, right: r.right, height: r.height, width: r.width, shareRight: share.right, x: r.x, mid: r.y + r.height / 2, shareMid: share.y + share.height / 2 }; });
    check('it sits at the right end of the share line, apart from it', Math.abs(placed.rowRight - placed.right) < 2 && placed.x > placed.shareRight + 100 && Math.abs(placed.mid - placed.shareMid) < 8, JSON.stringify(placed));
    check('its tap target is 44px', placed.height >= 44 && placed.width >= 44, JSON.stringify(placed));
    await gear.click();
    at = await onDataView(page, dataPath);
    check('the gear opens Data View for this character', at.path === dataPath && at.title === 'Data View' && at.rows > 0, JSON.stringify(at));

    // 4a. the saved character's Missing Content warning
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(4500); await dismissWhatsNew(page);
    await page.getByText('Edit', { exact: true }).first().click(); await wait(4000);
    details = await openWarning(page);
    await details.getByText('Fix in Data View', { exact: true }).click();
    at = await onDataView(page, dataPath);
    check("for a saved character, Fix in Data View opens that character's Data View", at.path === dataPath && at.rows > 0, JSON.stringify(at));

    // 2. the character list: the same gear after Delete, opening that row's character.
    await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'load' }); await wait(4000); await dismissWhatsNew(page);
    await page.getByText('open', { exact: true }).last().click(); await wait(2500);
    check("the opened list row has no Data View button", (await page.locator('#app button', { hasText: /^data view$/i }).count()) === 0);
    const rowGear = page.locator('#app a.data-view-gear');
    const after = await rowGear.first().evaluate((g) => { const del = [...document.querySelectorAll('#app button')].find(x => /^delete$/i.test(x.textContent.trim()));
      return !!del && g.getBoundingClientRect().x > del.getBoundingClientRect().right; }).catch(() => false);
    check("the owner's opened row has the gear, after Delete", (await rowGear.count()) === 1 && after, `${await rowGear.count()} found`);
    const rowHref = await rowGear.first().getAttribute('href');
    await rowGear.first().click();
    at = await onDataView(page, rowHref);
    check("it opens Data View for that row's character", /\/characters\/\d+\/data$/.test(rowHref || '') && at.path === rowHref && at.rows > 0, JSON.stringify({ rowHref, ...at }));

    // 3. the "couldn't be loaded" panel
    await page.route(`**/dnd/5e/characters/${id}`, r => r.request().resourceType() === 'document' ? r.continue()
      : r.fulfill({ status: 200, contentType: 'application/edn', body: '{:orcpub.entity.strict/selections [{:a' }));
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(5000); await dismissWhatsNew(page);
    const panelButtons = await page.locator('#app button').allInnerTexts();
    const first = panelButtons.map(t => t.trim()).find(t => /repair|delete|my characters/i.test(t));
    check('the panel offers Repair first', /^repair this character$/i.test(first || ''), panelButtons.slice(0, 6).join(' / '));
    check('its sentence says so', /You can repair it, delete it, or head back/.test(await page.locator('#app').innerText()));
    await page.unroute(`**/dnd/5e/characters/${id}`);
    await page.getByRole('button', { name: /repair this character/i }).click();
    at = await onDataView(page, `/pages/dnd/5e/characters/${id}/repair`);
    check('Repair opens the repair address, showing the character', at.path.endsWith('/repair') && at.rows > 0, JSON.stringify(at));

    // Others do not get the link; a phone keeps it beside Copy link.
    const zoe = await (await browser.newContext()).newPage();
    await logIn(zoe, 'zoe', 'washburne7');
    await zoe.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(4500); await dismissWhatsNew(zoe);
    check("someone else's view of the character has no Data View gear", (await zoe.locator('#app a.data-view-gear').count()) === 0);
    const phone = await (await browser.newContext({ viewport: { width: 412, height: 860 }, userAgent: PHONE_UA, hasTouch: true, isMobile: true, storageState: await ctx.storageState() })).newPage();
    await phone.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(5000); await dismissWhatsNew(phone);
    const pl = await phone.locator('#app a.data-view-gear').boundingBox();
    check('on a phone the gear is on screen, 44px', pl && pl.x >= 0 && pl.x + pl.width <= 412 && pl.height >= 44, JSON.stringify(pl));

    // 5. The notice under the loading picture, when the app's script never arrives.
    const dead = await (await browser.newContext({ viewport: { width: 1280, height: 900 }, storageState: await ctx.storageState() })).newPage();
    await dead.route('**/js/compiled/orcpub.js', r => r.abort());
    await dead.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' });
    await wait(4000);
    check('the notice waits: hidden 4 seconds in', !(await dead.locator('#load-rescue').isVisible()));
    await wait(5500);
    const notice = dead.locator('#load-rescue');
    check('then a character page that never loads shows it', await notice.isVisible());
    check('it says so and offers the repair', /This character didn.t load\./.test(await notice.innerText()) && /Repair this character/.test(await notice.innerText()), await notice.innerText());
    const below = await dead.evaluate(() => { const n = document.getElementById('load-rescue').getBoundingClientRect(), b = document.querySelector('.ls-box').getBoundingClientRect();
      return { top: n.top, spinnerBottom: b.bottom, mid: n.left + n.width / 2, page: window.innerWidth / 2 }; });
    check('it sits under the loading picture, centred', below.top > below.spinnerBottom && Math.abs(below.mid - below.page) < 2, JSON.stringify(below));
    await dead.locator('#load-rescue-link').click();
    at = await onDataView(dead, `/pages/dnd/5e/characters/${id}/repair`);
    check('Repair this character opens the repair address, which works without the app', at.path.endsWith('/repair') && at.rows > 0, JSON.stringify(at));
    await dead.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'load' }); await wait(9500);
    const bNote = dead.locator('#load-rescue');
    check('the builder shows its own wording', (await bNote.isVisible()) && /The builder didn.t load\./.test(await bNote.innerText()) && /Repair this draft/.test(await bNote.innerText()), await bNote.innerText().catch(() => ''));
    check('Repair this draft goes to the draft page', (await dead.locator('#load-rescue-link').getAttribute('href')) === '/pages/dnd/5e/character-data');
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(10000);
    check('a page that loads never shows it', !(await page.locator('#load-rescue').isVisible()));
  } catch (e) {
    check('the run finished', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})();
