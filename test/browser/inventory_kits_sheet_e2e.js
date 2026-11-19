// Kits and Manage inventory on the character sheet at phone size, on the builder's Details tab and on a saved character's own sheet (logged in as the seeded user), including that a stepper edit is saved, the switch from the keyboard and Undo after the last kit.
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

// The layout widths every screen is checked at: a small phone, a tablet, a desktop browser and a wide one.
// The tablet's column count is printed, not asserted: it depends on how wide the sheet lays out there.
const LAYOUT_DEVICES = [
  { name: 'small phone', file: 'phone-344', expect: 'one column', cols: n => n === 1,
    profile: { ...devices['Galaxy S8'], viewport: { width: 344, height: 882 } } },
  { name: 'tablet', file: 'tablet-810', profile: devices['iPad (gen 7)'] },
  { name: 'desktop', file: 'desktop-1440', expect: 'two columns or more', cols: n => n >= 2,
    profile: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
  { name: 'wide desktop', file: 'desktop-1920', expect: 'two columns or more', cols: n => n >= 2,
    profile: { ...devices['Desktop Chrome'], viewport: { width: 1920, height: 1080 } } },
];

// Kit icons and the kit grid: undo the removal, manage, add three more packs, then check each pack's drawn
// icon (and that the file is served), then the grid on a small phone, a tablet and a desktop browser.
const kitIconsAndGrid = async (page, sec, theme) => {
  await sec().locator('button.link-button.quiet:text-is("Undo")').click(); await settle(page, 600);
  // Managing is off after a reload, and the added kits may not be saved yet: top the list up to four.
  const ensureKits = async () => {
    const have = async n => (await sec().innerText()).includes(n);
    if (await sec().locator('.sheet-stepper').count() === 0) { await sec().locator('.toggle-switch').click(); await settle(page, 400); }
    const box = sec().locator('input.inv-combo-input');
    for (const n of ["Burglar's Pack", "Priest's Pack", "Scholar's Pack"]) {
      if (await have(n)) continue;
      await box.fill(n); await settle(page, 300);
      await box.press('ArrowDown'); await box.press('Enter'); await settle(page, 1200);
      await box.fill(''); await settle(page, 300);
    }
  };
  await ensureKits();
  // Each kit's tile, paired with the pack named on its card.
  const imgs = () => sec().locator('.kit-card:not(.kit-add)').evaluateAll(es => es.map(e => ({
    name: (e.querySelector('.kit-name') || {}).textContent || '', src: (e.querySelector('.kit-tile img') || {}).getAttribute?.('src') })));
  const PACK_FILES = { "Explorer's Pack": 'explorers-pack', "Burglar's Pack": 'burglars-pack', "Priest's Pack": 'priests-pack', "Scholar's Pack": 'scholars-pack' };
  const iconsMatch = (tiles, theme) => tiles.length >= 4 && tiles.every(t => {
    const pack = Object.keys(PACK_FILES).find(n => t.name.startsWith(n));
    return pack && t.src === `/image/kits/${PACK_FILES[pack]}-${theme}.svg`;
  });
  const srcs = await imgs();
  console.log('  kit icons:', JSON.stringify(srcs));
  check(iconsMatch(srcs, 'dark'), "every kit tile shows its own pack's drawn icon");
  const status = await page.evaluate(async u => (await fetch(u)).status, '/image/kits/explorers-pack-dark.svg');
  check(status === 200, 'the icon file is served (HTTP ' + status + ')');
  const tops = () => sec().locator('.kit-grid > *').evaluateAll(es => es.map(e => Math.round(e.getBoundingClientRect().top)));
  const cols = async () => { const t = await tops(); return t.filter(v => v === t[0]).length; };
  const shotKits = async name => {
    await page.addStyleTag({ content: '.sticky-header{position:static !important;top:auto !important}' });
    const g = sec().locator('.kit-grid');
    await g.scrollIntoViewIfNeeded();
    await g.screenshot({ path: path.join(OUT, name) });
  };
  check(await cols() === 1, 'phone width: the kit list is one column');
  await shotKits('kits-412-dark.png');
  await theme('light-theme'); await ensureKits();
  check(iconsMatch(await imgs(), 'light'), "light theme: every tile shows its own pack's -light icon");
  await shotKits('kits-412-light.png');
  await theme('dark-theme');
  // The same sheet on a small phone, a tablet and a desktop browser: each its own device profile,
  // logged in with this page's storage, not the phone stretched wide.
  const state = await page.context().storageState();
  const url = page.url();
  for (const d of LAYOUT_DEVICES) {
    const ctx = await page.context().browser().newContext({ ...d.profile, storageState: state });
    const p = await ctx.newPage();
    await p.goto(url); await settle(p, 1500, 20000);
    await p.addStyleTag({ content: '.sticky-header{position:static !important;top:auto !important}' });
    await p.locator('.b-b-2.pointer', { has: p.locator('img[src*="backpack"]') }).first().click(); await settle(p, 1000);
    const s2 = () => p.locator('div:has(> div > span.f-w-b.f-s-18:text-is("Other Equipment"))').first();
    if (await s2().locator('.sheet-stepper').count() === 0) { await s2().locator('.toggle-switch').click(); await settle(p, 400); }
    const box = s2().locator('input.inv-combo-input');
    for (const n of ["Burglar's Pack", "Priest's Pack", "Scholar's Pack"]) {
      if ((await s2().innerText()).includes(n)) continue;
      await box.fill(n); await settle(p, 300); await box.press('ArrowDown'); await box.press('Enter'); await settle(p, 1200);
      await box.fill(''); await settle(p, 300);
    }
    const m = await s2().locator('.kit-grid').evaluate(g => {
      const kids = [...g.children];
      return { w: Math.round(g.getBoundingClientRect().width),
               cols: getComputedStyle(g).gridTemplateColumns.split(' ').length,
               frames: kids.map(e => { const c = getComputedStyle(e); return parseFloat(c.borderTopWidth) >= 1 && c.borderTopStyle === (e.classList.contains('kit-add') ? 'dashed' : 'solid'); }),
               over: document.documentElement.scrollWidth - window.innerWidth };
    });
    const tag = `${d.name} (${d.profile.viewport.width}px)`;
    console.log(`  ${tag}: kit grid ${m.w}px wide, ${m.cols} column(s)`);
    check(m.over <= 0, `${tag}: no sideways scroll`);
    if (d.cols) check(d.cols(m.cols), `${tag}: ${d.expect} (${m.cols})`);
    check(m.frames.length >= 5 && m.frames.every(Boolean), `${tag}: every kit is a framed card, and Add a kit a dashed one`);
    const g = s2().locator('.kit-grid'); await g.scrollIntoViewIfNeeded();
    await g.screenshot({ path: path.join(OUT, `kits-${d.file}.png`) });
    if (m.cols > 1) {
      await s2().locator('button.kit-summary').first().click(); await settle(p, 400);
      const open = await s2().locator('.kit-grid > .kit-open').evaluate(e => ({ w: e.getBoundingClientRect().width, g: e.parentElement.getBoundingClientRect().width }));
      check(Math.abs(open.w - open.g) < 2, `${tag}: an open kit spans the whole row`);
      await g.screenshot({ path: path.join(OUT, `kits-${d.file}-open.png`) });
    }
    await ctx.close();
  }
};

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
  check(/Add a kit/.test(t) && /add equipment/i.test(t), 'managing: Add a kit and the Add equipment box');
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
    check(/\bundo\b/i.test(await stext()) === false, 'a stepper keeps no Undo line');
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
    check(/Worn: 7 items carried/.test(await stext()), 'the backpack line: Worn, 7 items carried');
    await sec().locator('.seg button:text-is("Off")').click(); await settle(page, 3000);
    t = await stext();
    check(/Off: 7 items not carried/.test(t) && /In the backpack, not carried/.test(t), 'Off: the line says so and the list is labelled');
    check(await sec().locator('tr.item.packed').count() === 7, 'the 7 items in it are greyed, still listed');
    await page.screenshot({ path: path.join(OUT, 'c2-backpack-off.png'), fullPage: false });
    for (let i = 0; i < 20 && posts.length < 3; i++) await page.waitForTimeout(500);
    await page.waitForTimeout(1500);
    await page.reload(); await settle(page, 1500, 20000);
    await eqTab().click(); await settle(page, 1000);
    check(/Off: 7 items not carried/.test(await stext()), 'after a reload the backpack is still off: saved');
    await sec().locator('.toggle-switch').click(); await settle(page, 400);
    await page.locator('.b-b-2.pointer').first().click(); await settle(page, 600);
    await eqTab().click(); await settle(page, 800);
    check(await sec().locator('.sheet-stepper').count() === 0, 'switching tabs turns managing off');
    // From the #85 review: the switch from the keyboard, Enter after Escape, Undo after the last kit.
    await sec().locator('.toggle-switch').click(); await settle(page, 400);
    const box = sec().locator('input.inv-combo-input');
    const rows0 = await sec().locator('tr.item').count();
    await box.fill('abacus'); await settle(page, 300);
    await box.press('ArrowDown'); await box.press('Escape'); await settle(page, 200);
    await box.press('Enter'); await settle(page, 1200);
    await box.fill(''); await settle(page, 300);
    check(await sec().locator('tr.item').count() === rows0
          && await sec().locator('tr.item', { hasText: 'Abacus' }).count() === 0, 'Enter after Escape closed the list adds nothing');
    await sec().locator('.toggle-switch').click(); await settle(page, 400);
    await sec().locator('.toggle-switch').focus(); await page.keyboard.press(' '); await settle(page, 400);
    check(await sec().locator('.sheet-stepper').count() > 0, 'Manage inventory turns on from the keyboard');
    await page.keyboard.press('Enter'); await settle(page, 400);
    check(await sec().locator('.sheet-stepper').count() === 0, 'and off again');
    await sec().locator('button.kit-summary').first().click(); await settle(page, 400);
    await sec().locator('button.link-button.quiet:text-is("Remove kit")').click(); await settle(page, 300);
    t = await stext();
    check(/Removed Explorer's Pack/.test(t) && /\bundo\b/i.test(t), 'removing the last kit keeps its Undo line');
    await page.screenshot({ path: path.join(OUT, 'c1-saved-sheet.png'), fullPage: false });
    // The saved sheet has no theme switch: set the theme where the switch stores it (the `user` entry), reload.
    const theme = async t => {
      await page.waitForTimeout(3000);
      await page.evaluate(t => {
        let u = localStorage.getItem('user') || '{}';
        u = /:theme\s+"[^"]*"/.test(u) ? u.replace(/:theme\s+"[^"]*"/, `:theme "${t}"`) : u.replace(/\}\s*$/, ` :theme "${t}"}`);
        localStorage.setItem('user', u);
      }, t);
      await page.reload(); await settle(page, 1500, 20000);
      await eqTab().click(); await settle(page, 1000);
    };
    await kitIconsAndGrid(page, sec, theme);
  }
  check(errors.length === 0, `no page errors (${errors.join(' | ')})`);
  await b.close();
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error('FAILED', e); process.exit(1); });
