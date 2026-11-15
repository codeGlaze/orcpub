// Kits on the builder's Equipment step at phone size: a pack becomes a kit, use order, Re-bundle, Restock's question, Undo, the Packs preset, a second pack making the kit x2, Remove one pack.
// Screenshots go to $SHOTS when set. plan-inventory-kits.md on agents/develop.
const path = require('path');
const { chromium, devices } = require(path.join(process.cwd(), 'scripts/e2e/node_modules/playwright'));
const ex = require(path.join(process.cwd(), 'scripts/e2e/export-character-pdf.js'));
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
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`);
  await settle(page, 600, 15000);
  await page.addStyleTag({ content: '.sticky-header{position:static !important;top:auto !important}' });
  await clickNav(page, 'Next');
  const classSel = page.locator('select').filter({ has: page.locator('option', { hasText: 'Fighter' }) }).first();
  await classSel.selectOption({ label: 'Fighter' }); await settle(page, 800);
  await clickNav(page, 'Back'); await clickNav(page, 'Back');
  check(await stepTitle(page) === 'Equipment', `reached Equipment (${await stepTitle(page)})`);
  await shot(page, 'k0-empty-slot.png');
  check((await text(page)).includes('Kits'), 'Kits block shown before any pack');

  check(!/Remove what the/.test(await text(page)), 'switching an untouched new character to Fighter asks nothing');
  await page.locator(`#options-column span.flex-grow-1:text-is("Explorer's Pack")`).first().click(); await settle(page, 1500);
  let t = await text(page);
  check(!/Remove what the/.test(t), 'choosing the pack asks nothing either');
  check(/Explorer's Pack/.test(t) && /Torch 10/.test(t), 'choosing the pack makes a kit: Torch 10');
  check(/Explorer's 10\/10/.test(t), "torch row shows Explorer's 10/10");
  check(await torchQty(page).inputValue() === '10', 'torches are a real row, quantity 10');
  await shot(page, 'k1-pack-chosen.png');

  await setTorches(page, 13);
  await setTorches(page, 10);
  t = await text(page);
  check(/Torch 7\/10/.test(t), 'using 3 takes from the kit first: Torch 7/10');
  check(/Unbundled: 3 torches/.test(t) && await page.locator('#options-column button:text-is("Re-bundle")').count() === 1, 'unbundled torches offer Re-bundle');
  check(await page.locator('#options-column button:has-text("Restock")').count() === 1, 'Restock shown on the low kit');
  await shot(page, 'k2-low-kit.png');

  await page.locator('#options-column button:has-text("Restock")').click(); await settle(page, 400);
  t = await text(page);
  check(/Re-bundle items first\?/.test(t) && /Unbundled: 3 torches/.test(t), 'Restock asks in the strip');
  check(await page.locator('#options-column button:text-is("Re-bundle")').count() === 0, 'the strip turns into the question, not a second box');
  check(await page.locator('#options-column button.kit-restock').evaluate(b => getComputedStyle(b).opacity) === '0.5', 'Restock stays, dimmed, on the asking kit');
  await shot(page, 'k3-restock-asks.png');
  const tops = await page.evaluate(() => ['Yes', 'No', 'Cancel'].map(t =>
    Array.from(document.querySelectorAll('#options-column button')).find(b => b.textContent.trim() === t).getBoundingClientRect().top));
  const q = await page.evaluate(() => {
    const t = document.querySelector('#options-column .callout-quiet .callout-text');
    const yes = Array.from(document.querySelectorAll('#options-column button')).find(b => b.textContent.trim() === 'Yes');
    const cancel = Array.from(document.querySelectorAll('#options-column button')).find(b => b.textContent.trim() === 'Cancel');
    const range = document.createRange(); range.selectNodeContents(t);
    const yesText = document.createRange(); yesText.selectNodeContents(yes);
    return { textBottom: range.getBoundingClientRect().bottom, yesTop: yesText.getBoundingClientRect().top,
             yesRight: yes.getBoundingClientRect().right, cancelLeft: cancel.getBoundingClientRect().left };
  });
  check(q.textBottom <= q.yesTop + 2, `the question sits above its buttons (${Math.round(q.textBottom)} <= ${Math.round(q.yesTop)})`);
  check(q.cancelLeft < q.yesRight, 'Cancel, No, Yes with Yes at the right');
  check(Math.max(...tops) - Math.min(...tops) < 20, `Restock's three buttons sit on one line (${tops.map(Math.round)})`);
  await page.locator('#options-column button.link-button:text-is("Yes")').click(); await settle(page, 1000);
  t = await text(page);
  check(/Explorer's Pack: \+3 torches re-bundled/.test(t), 'Undo line says what moved');
  check(!/Torch 7\/10/.test(t) && await torchQty(page).inputValue() === '10', 'kit full again, nothing bought');
  await shot(page, 'k4-undo-line.png');
  await page.locator('#options-column button:has-text("Undo")').click(); await settle(page, 1000);
  check(/Torch 7\/10/.test(await text(page)), 'Undo puts the kit back to 7/10');

  await page.locator('#options-column button:has-text("Add a kit")').first().click(); await settle(page, 600);
  const rows = await page.locator('.inv-combo-pop:popover-open .inv-combo-row').allInnerTexts();
  check(await page.locator('#options-column button.inv-combo-preset:has-text("Packs")').count() === 1, 'Add a kit opens the search box with a Packs tag');
  check(rows.length === 8 && rows.every(r => / Pack(You have \d+)?$/.test(r.trim())), `only the 8 packs are listed, no Backpack (${rows.join(', ')})`);
  check(rows.some(r => /Explorer's PackYou have 1/.test(r)), "the pack already a kit is noted You have 1");
  await page.screenshot({ path: path.join(OUT, 'k5-pack-picker.png') });
  await page.locator('.inv-combo-pop:popover-open .inv-combo-row:has-text("Dungeoneer\'s Pack")').click(); await settle(page, 1500);
  check(await page.locator('#options-column button.inv-combo-preset').count() === 0, 'picking clears the Packs tag');
  t = await text(page);
  check(/Spent items come out of the top kit first/.test(t), 'two kits: the order note shows');
  check(await torchQty(page).inputValue() === '20', 'the second pack adds its 10 torches: 20');
  await shot(page, 'k6-two-kits.png');

  await page.locator('#options-column button.kit-summary').first().click(); await settle(page, 400);
  t = await text(page);
  check(/Items stay in inventory/.test(t) && /Also removes Torch 7/.test(t), 'the opened kit shows both removes and what the second takes');
  check(!/Move to top/i.test(await page.locator('#options-column .kit-indent').first().innerText()), 'no Move to top on the top kit');
  await shot(page, 'k7-kit-open.png');
  await page.locator('#options-column button.kit-summary').first().click(); await settle(page, 300);
  await page.locator('#options-column button:has-text("Add a kit")').first().click(); await settle(page, 600);
  const explorerRow = page.locator('.inv-combo-pop:popover-open .inv-combo-row:has-text("Explorer\'s Pack")');
  check(/You have 1/.test(await explorerRow.innerText()), "the Packs list offers Explorer's again, noted You have 1");
  await explorerRow.click(); await settle(page, 1500);
  t = await text(page);
  check(/Explorer's Pack \u00d72/.test(t), 'a second Explorer\'s Pack makes the kit \u00d72, not a second kit');
  check(await torchQty(page).inputValue() === '30', 'its 10 torches are added: 30');
  check((await page.locator('#options-column button.kit-summary').count()) === 2, 'still two kits');
  await page.locator('#options-column button.kit-summary').first().click(); await settle(page, 400);
  await shot(page, 'k8-two-packs.png');
  await page.locator('#options-column button.link-button.quiet:text-is("Remove one pack")').click(); await settle(page, 1500);
  t = await text(page);
  check(!/\u00d72/.test(t) && await torchQty(page).inputValue() === '30', 'Remove one pack: back to one, items stay');
  check(errors.length === 0, `no page errors (${errors.join(' | ')})`);
  await b.close();
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error('FAILED', e); process.exit(1); });
