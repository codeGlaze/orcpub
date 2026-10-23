// Kits and Manage inventory on the character sheet at phone size, on the builder's Details tab and on a saved character's own sheet (logged in as the seeded user), including that a stepper edit is saved.
// Screenshots go to $SHOTS when set. plan-inventory-kits.md on agents/develop.
const path = require('path');
const { chromium, devices } = require(path.join(process.cwd(), 'scripts/e2e/node_modules/playwright'));
const ex = require(path.join(process.cwd(), 'scripts/e2e/export-character-pdf.js'));
const lib = require(path.join(process.cwd(), 'scripts/e2e/lib.js'));
const { settle, BASE, EXECUTABLE } = ex;
const OUT = process.env.SHOTS || require('os').tmpdir();
let failures = 0;
const check = (ok, m) => { console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${m}`); if (!ok) failures++; };

const stepTitle = page => page.evaluate(() => {
  const h = Array.from(document.querySelectorAll('h3.f-w-b')).find(e => e.getBoundingClientRect().width > 0);
  return h ? h.textContent.trim() : null;
});
const clickNav = async (page, label) => {
  await page.locator(`#options-column button:has-text("${label}")`).first().click(); await settle(page, 500);
};
// The Equipment section, heading to "Add Custom Item".
const clip = page => page.evaluate(() => {
  const head = Array.from(document.querySelectorAll('span.f-w-b')).find(e => e.textContent.trim() === 'Equipment' && e.getBoundingClientRect().width > 0);
  if (!head) return null;
  const links = Array.from(document.querySelectorAll('#options-column *')).filter(e => e.children.length === 0 && e.textContent.trim() === 'Add Custom Item');
  const top = head.getBoundingClientRect().top + window.scrollY - 24;
  const link = links.map(l => l.getBoundingClientRect().bottom + window.scrollY).filter(y => y > top).sort((a, b) => a - b)[0];
  return { x: 0, y: Math.max(0, top), width: window.innerWidth, height: (link || top + 900) - top + 24 };
});
const shot = async (page, file) => { await page.screenshot({ path: path.join(OUT, file), clip: await clip(page), fullPage: true }); };
const text = async page => (await page.locator('#options-column').innerText()).replace(/\u00a0/g, ' ');
// The torch row's quantity box in the Equipment list.
const torchQty = page => page.locator('#options-column div.flex-grow-1:text-is("Torch")').first().locator('xpath=..').locator('input');
const setTorches = async (page, n) => { const q = torchQty(page); await q.fill(String(n)); await q.press('Enter'); await q.blur(); await settle(page, 700); };

(async () => {
  const b = await chromium.launch({ executablePath: EXECUTABLE });
  const ctx = await b.newContext({ ...devices['iPhone 13'] });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  await lib.login(page, 'kaylee', 'serenity99');
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`);
  await settle(page, 600, 15000);
  await page.addStyleTag({ content: '.sticky-header{position:static !important;top:auto !important}' });
  await clickNav(page, 'Next');
  const classSel = page.locator('select').filter({ has: page.locator('option', { hasText: 'Fighter' }) }).first();
  await classSel.selectOption({ label: 'Fighter' }); await settle(page, 800);
  await clickNav(page, 'Back'); await clickNav(page, 'Back');
  await page.locator(`#options-column span.flex-grow-1:text-is("Explorer's Pack")`).first().click(); await settle(page, 1500);
  // The builder's own sheet: the Details tab.
  await page.locator('.builder-tabs >> text=Details').first().click(); await settle(page, 1200);
  const eqTab = () => page.locator('.b-b-2.pointer', { has: page.locator('img[src*="backpack"]') }).first();
  await eqTab().click(); await settle(page, 1000);
  const sec = () => page.locator('div:has(> div > span.f-w-b.f-s-18:text-is("Other Equipment"))').first();
  const stext = async () => (await sec().innerText()).replace(/ /g, ' ');
  const torchCell = () => sec().locator('tr.item', { has: page.locator('td', { hasText: /^Torch/ }) }).locator('td').nth(1);
  let t = await stext();
  check(/Explorer's Pack/.test(t) && /Torch 10/.test(t), 'the sheet shows the kit');
  check(!/Add a kit/.test(t), 'no Add a kit while not managing');
  check(await sec().locator('.sheet-stepper').count() === 0, 'no steppers while not managing');
  await sec().locator('input.inv-combo-input').fill('rope'); await settle(page, 300);
  check(await sec().locator('tr.item').count() === 1, 'the box filters my inventory');
  await sec().locator('input.inv-combo-input').fill(''); await settle(page, 300);
  await sec().locator('.toggle-switch').click(); await settle(page, 400);
  t = await stext();
  check(/Add a kit/.test(t) && /Adding items/.test(t), 'managing: Add a kit and the adding box');
  await torchCell().locator('button[aria-label="One fewer"]').click(); await settle(page, 1500);
  t = await stext();
  check(/Torch 9\/10/.test(t), 'one fewer torch comes out of the kit: Torch 9/10');
  await page.screenshot({ path: path.join(OUT, 'c0-sheet-managing.png'), fullPage: false });
  await page.locator('button.form-button:text-is("Done")').click(); await settle(page, 400);
  check(await sec().locator('.sheet-stepper').count() === 0, 'Done turns managing off');
  // Save it, then its own sheet.
  await page.locator('.builder-tabs >> text=Options').first().click(); await settle(page, 600);
  const save = page.locator('button:has-text("Save"), div.pointer:has-text("Save")').first();
  check(await save.count() > 0, 'found Save');
  const saved = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/dnd/5e/characters', { timeout: 30000 }).catch(() => null);
  await save.click();
  const resp = await saved; await settle(page, 1500);
  const bodyText = resp ? await resp.text().catch(() => '') : '';
  const m = bodyText.match(/db\/id\D{0,6}(\d{5,})/);
  const id = m && m[1];
  if (!id) console.log('  save reply:', resp && resp.status(), bodyText.slice(0, 300));
  console.log('  saved id:', id, page.url());
  check(!!id, 'the character was saved');
  if (id) {
    await page.goto(`${BASE}/pages/dnd/5e/characters/${id}`); await settle(page, 1500, 20000);
    await eqTab().click(); await settle(page, 1000);
    t = await stext();
    check(/Torch 9\/10/.test(t), 'its sheet shows the kit as saved: Torch 9/10');
    await sec().locator('.toggle-switch').click(); await settle(page, 400);
    const posts = [];
    page.on('response', r => { const u = new URL(r.url()).pathname; if (r.request().method() !== 'GET' && /characters/.test(u)) posts.push(r.request().method() + ' ' + u + ' ' + r.status()); });
    await torchCell().locator('button[aria-label="One fewer"]').click(); await settle(page, 3000);
    for (let i = 0; i < 20 && posts.length === 0; i++) await page.waitForTimeout(500);
    await page.waitForTimeout(1500);
    console.log('  saves after the edit:', JSON.stringify(posts));
    check(/Torch 8\/10/.test(await stext()), 'one fewer on the saved sheet: Torch 8/10');
    check(/Undo/.test(await stext()) === false, 'a stepper keeps no Undo line');
    const gp = page.locator('input[aria-label="Gold Pieces (GP) quantity"]');
    check(await gp.count() === 1, 'managing: Treasure lists every coin with a box');
    await gp.fill('47'); await gp.blur(); await settle(page, 3000);
    for (let i = 0; i < 20 && posts.length < 2; i++) await page.waitForTimeout(500);
    await page.waitForTimeout(1500);
    await page.reload(); await settle(page, 1500, 20000);
    await eqTab().click(); await settle(page, 1000);
    const reloaded = await stext();
    console.log('  after reload:', reloaded.slice(0, 200).replace(/\n/g, ' | '));
    check(/Torch 8\/10/.test(reloaded), 'after a reload it is still 8/10: the edit was saved');
    check(await sec().locator('.sheet-stepper').count() === 0, 'leaving and coming back: managing is off');
    check(/Gold Pieces \(GP\)\s+47/.test(await page.locator('#app').innerText()), 'the 47 gold was saved');
    await sec().locator('.toggle-switch').click(); await settle(page, 400);
    await page.locator('.b-b-2.pointer').first().click(); await settle(page, 600);
    await eqTab().click(); await settle(page, 800);
    check(await sec().locator('.sheet-stepper').count() === 0, 'switching tabs turns managing off');
    await page.screenshot({ path: path.join(OUT, 'c1-saved-sheet.png'), fullPage: false });
  }
  check(errors.length === 0, `no page errors (${errors.join(' | ')})`);
  await b.close();
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error('FAILED', e); process.exit(1); });
