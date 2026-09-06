// What the form looks like as effects are removed.
//
// Every picture so far has been of a form being FILLED. Removal is the other half of a `:rows`
// form and it had never been photographed — the add-bar has to come back, the remaining rows have
// to close up, and the form has to end where it started rather than in some third state.
//
// Frames: authored (3 effects) -> one x'd out -> all x'd out.
//
// Prereqs:  lein garden once && lein fig:build && lein e2e-server
// Run:      node test/e2e/row-removal-states.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, dismissCookieBar, fillEffectBonus, dbAt } = require('./lib');

const shot = (page, file) => page.screenshot({ path: file, fullPage: true, type: 'jpeg', quality: 72 });

const removeRow = (page, title) => page.evaluate((t) => {
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const hdr = [...document.querySelectorAll('.effect-row-header span')]
    .find(e => e.textContent.trim().toLowerCase() === t.toLowerCase() && vis(e));
  if (!hdr) return false;
  const x = hdr.parentElement.querySelector('i.fa-times');
  if (!x) return false;
  x.click();
  return true;
}, title);

const state = (page) => page.evaluate(() => {
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  return {
    rows: [...document.querySelectorAll('.effect-row-header span')].filter(vis).map(e => e.textContent.trim()),
    chips: [...document.querySelectorAll('.addbar .chip')].filter(vis).map(e => e.textContent.trim()),
    height: document.querySelector('#app').scrollHeight,
  };
});

(async () => {
  const dir = path.join(SHOTS, 'row-removal');
  fs.mkdirSync(dir, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1100, height: 1400 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  await page.goto(`${BASE}/pages/dnd/5e/fighting-style-builder`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1800);
  await dismissCookieBar(page);
  const bare = await state(page);

  for (const t of ['AC Bonus', 'Attack Bonus', 'Damage Bonus']) {
    await page.evaluate((title) => {
      const b = [...document.querySelectorAll('button')].find(e => e.textContent.trim() === `+ ${title}`);
      if (b) b.click();
    }, t);
    await page.waitForTimeout(250);
  }
  for (const [l, v] of [['AC Bonus', '1'], ['Attack Bonus', '2'], ['Damage Bonus', '2']]) {
    await fillEffectBonus(page, l, v);
  }
  await page.waitForTimeout(400);
  const authored = await state(page);
  await shot(page, path.join(dir, '01-authored.jpg'));

  await removeRow(page, 'Damage Bonus');
  await page.waitForTimeout(500);
  const oneGone = await state(page);
  await shot(page, path.join(dir, '02-one-removed.jpg'));

  for (const t of ['AC Bonus', 'Attack Bonus']) { await removeRow(page, t); await page.waitForTimeout(400); }
  const allGone = await state(page);
  await shot(page, path.join(dir, '03-all-removed.jpg'));
  const props = await dbAt(page, '[:orcpub.dnd.e5.classes/fighting-style-builder-item :props]');

  await browser.close();

  const rep = { bare, authored, oneGone, allGone, propsAfterRemoval: props, jsErrors: errors };
  fs.writeFileSync(path.join(dir, 'states.json'), JSON.stringify(rep, null, 2));
  console.log(JSON.stringify(rep, null, 2));

  // The point of the frames: removal has to be a real return, not a different-looking empty.
  const ok = allGone.rows.length === 0
    && allGone.chips.length === bare.chips.length
    && oneGone.chips.join() === '+ Damage Bonus'
    && /^(nil|\{\})$/.test((props || '').trim())
    && errors.length === 0;
  console.log(ok ? '\nOK  removal returns the form to its starting state and clears the data'
                 : '\nPROBLEM  see the report above');
  process.exit(ok ? 0 : 1);
})().catch(e => { console.error(e); process.exit(2); });
