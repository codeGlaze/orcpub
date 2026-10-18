// Weapons table on a phone, from the real app: today's table (MODE=before) or the new rows (MODE=after),
// dark and light at 390px with a phone user agent, measured, plus a switched-on shot in after mode.
// Run from a code worktree: NODE_PATH=scripts/e2e/node_modules SHOTS=<dir> MODE=after E2E_PORT=<free port> \
//   ./scripts/e2e/run.sh <path to this file>
// Kind: probe
const { chromium } = require('playwright');
const path = require('path');
const ex = require(path.join(process.cwd(), 'scripts/e2e/export-character-pdf.js'));
const { settle, clickVisible, openBuilderTab, setClasses, BASE } = ex;

const OUT = process.env.SHOTS;
const MODE = process.env.MODE || 'after';
let failures = 0;
const check = (ok, m) => { console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${m}`); if (!ok) failures++; };

async function combatTab(page) {
  for (let i = 0; i < 40; i++) {
    const ok = await page.evaluate(() => {
      const t = [...document.querySelectorAll('div.b-b-2.pointer')]
        .find(d => d.offsetParent && d.innerHTML.includes('sword-clash'));
      if (t) t.click();
      return !!t;
    });
    if (ok) {
      await settle(page);
      const seen = await page.evaluate(() => [...document.querySelectorAll('span.f-w-b.f-s-18')]
        .some(e => e.offsetParent && e.textContent.trim() === 'Weapons'));
      if (seen) return true;
    }
    await page.waitForTimeout(250);
  }
  return false;
}

// Marks the Weapons section and returns layout numbers for the new rows.
function measure() {
  const head = [...document.querySelectorAll('span.f-w-b.f-s-18')].find(e => e.offsetParent && e.textContent.trim() === 'Weapons');
  const section = head.closest('.weapons-section') || head.parentElement.parentElement;
  section.setAttribute('data-shot', 'weapons');
  const box = section.getBoundingClientRect();
  const r1 = n => Math.round(n * 10) / 10;
  const cx = e => { const b = e.getBoundingClientRect(); return b.left + b.width / 2; };
  const out = { sectionW: r1(box.width), scrollW: section.scrollWidth, clientW: section.clientWidth,
                pageScrollW: document.documentElement.scrollWidth, viewW: window.innerWidth };
  const ths = [...section.querySelectorAll('.weapon-head > *')];
  const rows = [...section.querySelectorAll('.weapon-row')];
  if (!rows.length) return out;
  out.rows = rows.length;
  const hit = rows.map(r => r.querySelector('.weapon-hit-tile'));
  const dmg = rows.map(r => r.querySelector('.weapon-damage-tile, .weapon-no-roll'));
  out.hitHeadOff = Math.max(...hit.map(t => Math.abs(cx(t) - cx(ths[1]))));
  out.dmgHeadOff = Math.max(...dmg.map(t => Math.abs(cx(t) - cx(ths[2]))));
  out.tileSizes = [...new Set([...hit, ...dmg].map(t => { const b = t.getBoundingClientRect(); return `${r1(b.width)}x${r1(b.height)}`; }))];
  out.namesOneLine = rows.every(r => { const n = r.querySelector('.weapon-name'); return n.scrollHeight <= n.clientHeight + 1; });
  out.clipped = [...section.querySelectorAll('.weapon-row *')].filter(e => {
    const b = e.getBoundingClientRect(); return b.width && (b.right > box.right + 0.5 || b.left < box.left - 0.5);
  }).map(e => e.className || e.tagName).slice(0, 5);
  out.targetsUnder44 = [...section.querySelectorAll('.weapon-cell-name, .weapon-tile')].filter(b => {
    const r = b.getBoundingClientRect(); return r.width && (r.height < 44 || r.width < 44);
  }).map(b => b.textContent.trim()).slice(0, 5);
  return out;
}

async function shoot(page, name, act) {
  if (!act) {
    await page.reload({ waitUntil: 'networkidle' });
    await settle(page);
    await clickVisible(page, '.builder-tab:has-text("Details")');
    await settle(page);
    check(await combatTab(page), `${name}: Combat tab`);
  } else {
    await act(page);
    await settle(page);
  }
  const m = await page.evaluate(measure);
  console.log(`  ${name}: ${JSON.stringify(m)}`);
  check(m.pageScrollW <= m.viewW, `${name}: no sideways page scroll (${m.pageScrollW} <= ${m.viewW})`);
  check(m.scrollW <= m.clientW, `${name}: the section fits its width (${m.scrollW} <= ${m.clientW})`);
  if (MODE === 'after') {
    check(m.rows >= 8, `${name}: every weapon has a row (${m.rows})`);
    check(m.hitHeadOff <= 2 && m.dmgHeadOff <= 2, `${name}: headings within 2px of their tiles (${m.hitHeadOff}, ${m.dmgHeadOff})`);
    check(m.namesOneLine, `${name}: names stay on one line`);
    check(m.clipped.length === 0, `${name}: nothing sticks out (${m.clipped.join(', ')})`);
    check(m.targetsUnder44.length === 0, `${name}: names and tiles are 44px tap targets (${m.targetsUnder44.join(', ')})`);
  }
  // the builder's sticky header covers the top of the section unless it is scrolled clear
  await page.evaluate(() => { const el = document.querySelector('[data-shot=weapons]');
    window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - 140); });
  await page.waitForTimeout(300);
  // a full-page capture paints sticky and fixed bars where they sit in the viewport: hide them
  await page.evaluate(() => { for (const el of document.querySelectorAll('body *')) {
    const pos = getComputedStyle(el).position;
    if ((pos === 'sticky' || pos === 'fixed') && !el.closest('[data-shot=weapons]')) el.style.visibility = 'hidden'; } });
  const top = await page.evaluate(() => document.querySelector('[data-shot=weapons]').getBoundingClientRect().top + window.scrollY);
  const h = await page.evaluate(() => document.querySelector('[data-shot=weapons]').getBoundingClientRect().height);
  await page.screenshot({ path: path.join(OUT, `${MODE}-${name}.png`), fullPage: true,
    clip: { x: 0, y: top - 4, width: 390, height: h + 8 } });
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.E2E_CHROMIUM });
  const desk = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await desk.newPage();
  page.on('pageerror', e => console.log('pageerror:', e.message));
  try {
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle', timeout: 60000 });
    await settle(page);
    await clickVisible(page, 'text=Got it!');
    await setClasses(page, [['Wizard', '1']], check);
    check(await openBuilderTab(page, 'Equipment'), 'Equipment step');
    await settle(page);
    const add = async (nth, w) => {
      const input = page.locator('.inv-combo-input').nth(nth);
      await input.click();
      await input.fill(w);
      await page.waitForTimeout(300);
      const row = page.locator('.inv-combo-row').filter({ hasText: new RegExp(`^${w}$`) });
      check(await row.count() >= 1, `${w} offered`);
      await row.first().click();
      await settle(page);
    };
    for (const w of ['Dagger', 'Longsword', 'Net', 'Longbow']) await add(0, w);
    for (const w of ['Flame Tongue, Longsword', 'Holy Avenger, Longsword', 'Frost Brand, Rapier', 'Dwarven Thrower']) await add(1, w);
    const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2,
      isMobile: true, hasTouch: true,
      userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
      storageState: await desk.storageState() });
    const p = await phone.newPage();
    p.on('pageerror', e => console.log('pageerror:', e.message));
    await p.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle', timeout: 60000 });
    await settle(p);
    await shoot(p, 'dark');
    if (MODE === 'after') {
      await shoot(p, 'dark-switched', async pg => {
        const row = n => pg.locator('.weapon-row', { hasText: n }).first();
        await row('Flame Tongue').getByRole('radio', { name: 'Ablaze' }).click();
        const plain = pg.locator('.weapon-row').filter({ has: pg.locator('.weapon-name', { hasText: /^Longsword$/ }) });
        await plain.getByRole('radio', { name: '2H' }).click();
        await row('Holy Avenger').locator('.weapon-name-line').click();
        const ft = await row('Flame Tongue').innerText();
        check(/\+2d6/.test(ft), 'Ablaze on puts +2d6 on the Flame Tongue tile');
        const ls = await plain.innerText();
        check(/1d10\+2/.test(ls), '2H shows the Longsword\'s two-handed dice', ls.replace(/\s+/g, ' '));
        const opened = await row('Holy Avenger').locator('.weapon-opened').waitFor({ timeout: 5000 }).then(() => true, () => false);
        check(opened && /Two-handed\?/.test(await row('Holy Avenger').innerText()), 'the opened row shows its details');
      });
    }
    await p.locator('div.pointer', { hasText: 'Light Theme' }).first().click();
    await settle(p);
    await shoot(p, 'light');
  } catch (e) {
    check(false, `threw: ${e.message.split('\n')[0]}`);
  }
  await browser.close();
  process.exit(failures ? 1 : 0);
})();
