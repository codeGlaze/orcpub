// The ways a player reaches the Data View page (character-rescue.md, entry points):
//   the "Data View" link beside Copy link on the owner's character page (not shown to others);
//   the "Data View" button in the owner's opened character-list row;
//   "Fix in Data View" in the builder's Missing Content warning, for a saved character and for
//   this browser's unsaved draft;
//   "Repair this character", first on the "couldn't be loaded" panel.
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

    // 1. the character page link
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(4500); await dismissWhatsNew(page);
    const link = page.locator('#app a', { hasText: /^Data View$/ });
    check("the owner's character page shows a Data View link", await link.isVisible());
    // The share line shows "Copy link" once shared and "Not shared · Share link" before; either
    // way it is the element just before the link.
    const share = await link.evaluate((a) => { const e = a.previousElementSibling; if (!e) return null;
      const r = e.getBoundingClientRect(); return { text: e.innerText.replace(/\s+/g, ' ').trim(), x: r.x, y: r.y, width: r.width, height: r.height }; });
    const copyBox = share;
    const linkBox = await link.boundingBox();
    const mid = (b) => b.y + b.height / 2;
    check('it sits beside the share line', copyBox && linkBox && Math.abs(mid(copyBox) - mid(linkBox)) < 8 && linkBox.x > copyBox.x, JSON.stringify({ copyBox, linkBox }));
    check('its tap target is at least 44px tall', linkBox && linkBox.height >= 44, JSON.stringify(linkBox));
    await link.click();
    at = await onDataView(page, dataPath);
    check('the link opens Data View for this character', at.path === dataPath && at.title === 'Data View' && at.rows > 0, JSON.stringify(at));

    // 4a. the saved character's Missing Content warning
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(4500); await dismissWhatsNew(page);
    await page.getByText('Edit', { exact: true }).first().click(); await wait(4000);
    details = await openWarning(page);
    await details.getByText('Fix in Data View', { exact: true }).click();
    at = await onDataView(page, dataPath);
    check("for a saved character, Fix in Data View opens that character's Data View", at.path === dataPath && at.rows > 0, JSON.stringify(at));

    // 2. the character list. The row's own "view" button names the character it is.
    const openRow = async () => {
      await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'load' }); await wait(4000); await dismissWhatsNew(page);
      await page.getByText('open', { exact: true }).last().click(); await wait(2500);
    };
    await openRow();
    await page.locator('#app button', { hasText: /^view$/i }).first().click(); await wait(2500);
    const rowId = (page.url().match(/characters\/(\d+)/) || [])[1];
    await openRow();
    const listBtn = page.locator('#app button', { hasText: /^data view$/i });
    check("the owner's opened list row has a Data View button", (await listBtn.count()) === 1, `${await listBtn.count()} found`);
    await listBtn.first().click();
    const rowPath = `/pages/dnd/5e/characters/${rowId}/data`;
    at = await onDataView(page, rowPath);
    check('it opens Data View for that row\'s character', !!rowId && at.path === rowPath && at.rows > 0, JSON.stringify({ rowId, ...at }));

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
    check("someone else's view of the character has no Data View link", (await zoe.locator('#app a', { hasText: /^Data View$/ }).count()) === 0);
    const phone = await (await browser.newContext({ viewport: { width: 412, height: 860 }, userAgent: PHONE_UA, hasTouch: true, isMobile: true, storageState: await ctx.storageState() })).newPage();
    await phone.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' }); await wait(5000); await dismissWhatsNew(phone);
    const pl = await phone.locator('#app a', { hasText: /^Data View$/ }).boundingBox();
    check('on a phone the link is on screen, 44px tall', pl && pl.x >= 0 && pl.x + pl.width <= 412 && pl.height >= 44, JSON.stringify(pl));
  } catch (e) {
    check('the run finished', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})();
