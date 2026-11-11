// PDF Options fills the window, Create PDF is always in view, and every option can be reached.
//
//   ./scripts/e2e/run.sh pdf-options-reach.js
//
// Builds a Wizard with spells (so the Cards and Spellbook groups show), opens PDF Options, picks
// a sheet style and opens the Spellbook group, its tallest state. Then, on a 1280x720 desktop and
// a 390x844 phone, checks that the panel covers the window, that Create PDF is inside the window
// and is what a tap there hits without any scrolling, and that the last option (the file name
// preview) scrolls into view inside the panel. Tab and Shift+Tab stay inside it; Escape closes it. Last, in the dark theme and then
// the light one, every visible text in the panel is checked for contrast against what is behind it.

const { chromium } = require('playwright');
const { settle, clickVisible, setClasses, selectSpells, BASE, EXECUTABLE } = require('./export-character-pdf');

let failures = 0;
function check(label, ok, detail) {
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail !== undefined && !ok ? '  — ' + detail : ''}`);
}

// Where the panel and Create PDF are, with nothing scrolled: {covers, fits, onTop, detail}.
async function createPdfInView(page) {
  return page.evaluate(() => {
    const sheet = document.querySelector('.pdf-sheet');
    const b = Array.from(document.querySelectorAll('button')).find(x => x.textContent.trim() === 'Create PDF');
    if (!sheet || !b) return { covers: false, fits: false, onTop: false, detail: `sheet ${!!sheet}, button ${!!b}` };
    const s = sheet.getBoundingClientRect();
    const r = b.getBoundingClientRect();
    const hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
    return {
      covers: s.top <= 0 && s.left <= 0 && s.right >= innerWidth && s.bottom >= innerHeight,
      fits: r.top >= 0 && r.bottom <= innerHeight && r.left >= 0 && r.right <= innerWidth,
      onTop: !!hit && (hit === b || b.contains(hit)),
      detail: `sheet ${Math.round(s.width)}x${Math.round(s.height)}; button ${Math.round(r.left)},${Math.round(r.top)}-${Math.round(r.right)},${Math.round(r.bottom)} in ${innerWidth}x${innerHeight}`,
    };
  });
}

// Scrolls the panel's body to its end and reports whether the file name preview is then in view.
async function lastOptionReachable(page) {
  return page.evaluate(async () => {
    const body = document.querySelector('.pdf-sheet-body');
    if (!body) return { ok: false, detail: 'no panel body' };
    body.scrollTop = body.scrollHeight;
    await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
    const note = Array.from(body.querySelectorAll('.option-note')).find(n => n.textContent.trim().endsWith('.pdf'));
    if (!note) return { ok: false, detail: 'no file name preview' };
    const r = note.getBoundingClientRect(), b = body.getBoundingClientRect();
    return { ok: r.top >= b.top && r.bottom <= b.bottom, detail: `preview ${Math.round(r.top)}-${Math.round(r.bottom)} in body ${Math.round(b.top)}-${Math.round(b.bottom)}` };
  });
}

// Every visible text in the panel whose contrast against what is actually behind it is below WCAG
// AA (4.5:1, or 3:1 for large text), as strings; ancestor opacity is applied. Amber .form-button text
// is left out: its colour is the account's Dark Button Text setting, not this panel's.
async function lowContrast(page) {
  return page.evaluate(() => {
    const rgba = c => { const m = c.match(/[\d.]+/g) || [0, 0, 0, 0]; return m.map(Number).concat([1]).slice(0, 4); };
    const over = (top, under) => { const a = top[3]; return [0, 1, 2].map(i => top[i] * a + under[i] * (1 - a)).concat([1]); };
    const lum = c => { const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
                       return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]); };
    const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
    const behind = el => {
      const layers = [];
      for (let e = el; e; e = e.parentElement) {
        const c = rgba(getComputedStyle(e).backgroundColor);
        if (c[3] > 0) { layers.push(c); if (c[3] >= 1) break; }
      }
      return layers.reverse().reduce((acc, c) => over(c, acc), [255, 255, 255, 1]);
    };
    const opacity = el => { let o = 1; for (let e = el; e; e = e.parentElement) o *= Number(getComputedStyle(e).opacity); return o; };
    const sheet = document.querySelector('.pdf-sheet');
    const out = [];
    for (const el of sheet.querySelectorAll('*')) {
      if (el.closest('.form-button')) continue;
      const own = Array.from(el.childNodes).filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join(' ').trim();
      if (!own || !el.getClientRects().length) continue;
      const cs = getComputedStyle(el);
      const fg = rgba(cs.color); fg[3] *= opacity(el);
      const bg = behind(el);
      const r = ratio(over(fg, bg), bg);
      const size = parseFloat(cs.fontSize), bold = Number(cs.fontWeight) >= 700;
      const need = size >= 24 || (bold && size >= 18.66) ? 3 : 4.5;
      if (r < need) out.push(`"${own.slice(0, 30)}" ${r.toFixed(2)}:1 (needs ${need})`);
    }
    return out;
  });
}

(async () => {
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle', timeout: 60000 });
  await settle(page);
  const quiet = (ok, m) => { if (!ok) check(m, false); };
  await setClasses(page, [['Wizard', '5']], quiet);
  await selectSpells(page, 4, quiet);

  check('Export opens PDF Options', await clickVisible(page, 'text=Export'));
  await settle(page);
  const styles = page.locator('.pdf-sheet select').first();
  check('the panel offers a sheet style', await styles.count() > 0);
  if (await styles.count()) await styles.selectOption({ index: 1 });
  check('Print Spellbook is offered', await clickVisible(page, 'text=Print Spellbook'));
  await settle(page);

  for (const [label, size] of [['desktop 1280x720', { width: 1280, height: 720 }],
                               ['phone 390x844', { width: 390, height: 844 }]]) {
    await page.setViewportSize(size);
    await settle(page, 400);
    await page.evaluate(() => { const b = document.querySelector('.pdf-sheet-body'); if (b) b.scrollTop = 0; });
    const r = await createPdfInView(page);
    check(`${label}: the panel covers the window`, r.covers, r.detail);
    check(`${label}: Create PDF is in view without scrolling`, r.fits, r.detail);
    check(`${label}: Create PDF is what a tap there hits`, r.onTop, r.detail);
    const last = await lastOptionReachable(page);
    check(`${label}: the last option scrolls into view inside the panel`, last.ok, last.detail);
  }

  await page.setViewportSize({ width: 1280, height: 720 });
  await settle(page, 300);
  const dark = await lowContrast(page);
  check('dark theme: every text in the panel is readable against its background', dark.length === 0, dark.join(' | '));

  // Tab past the last control and Shift+Tab past the first both stay inside the panel.
  const inside = () => page.evaluate(() => !!document.activeElement && !!document.activeElement.closest('.pdf-sheet'));
  await page.locator('.pdf-sheet button', { hasText: 'Create PDF' }).focus();
  const lastEl = await page.evaluate(() => {
    const els = Array.from(document.querySelectorAll('.pdf-sheet button, .pdf-sheet [href], .pdf-sheet input, .pdf-sheet select, .pdf-sheet textarea'))
      .filter(e => !e.disabled && e.getClientRects().length);
    els[els.length - 1].focus();
    return els[els.length - 1].textContent.trim().slice(0, 20);
  });
  await page.keyboard.press('Tab');
  check('Tab past the last control stays inside PDF Options', await inside(), `last control "${lastEl}"`);
  await page.locator('.pdf-sheet-close').focus();
  await page.keyboard.press('Shift+Tab');
  check('Shift+Tab before the first control stays inside PDF Options', await inside());

  await page.keyboard.press('Escape');
  await settle(page, 300);
  check('Escape closes PDF Options', await page.locator('.pdf-sheet').count() === 0);

  check('the light theme can be switched on', await clickVisible(page, 'text=Light Theme'));
  await settle(page, 300);
  await page.evaluate(() => window.scrollTo(0, 0));
  await clickVisible(page, 'text=Export');
  await settle(page);
  const light = (await page.locator('.pdf-sheet').count()) ? await lowContrast(page) : ['the panel did not open'];
  check('light theme: every text in the panel is readable against its background', light.length === 0, light.join(' | '));

  await browser.close();
  console.log(`\n${failures ? failures + ' FAILED' : 'all passed'}`);
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
