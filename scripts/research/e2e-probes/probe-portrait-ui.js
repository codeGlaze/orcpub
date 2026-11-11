// Probe (kept on agents/develop, scripts/research/e2e-probes): the portrait editor's fuller Randomize, Keep colours, and the picture-link field.
// Run: copy into a code branch's scripts/e2e/ (it uses that folder's helpers and test/fixtures), then E2E_BASE=http://localhost:<port> node scripts/e2e/probe-portrait-ui.js against a running dev server.
// Kind: probe
const { chromium } = require('playwright');
const path = require('path');
const { settle, openBuilderTab, clickVisible, EXECUTABLE, OUT } = require('./export-character-pdf');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const log = (...a) => console.log(...a);

async function contrastIn(page, sel) {
  return page.evaluate(sel => {
    const root = document.querySelector(sel);
    if (!root) return ['no ' + sel];
    const rgba = c => { const m = c.match(/[\d.]+/g) || [0, 0, 0, 0]; return m.map(Number).concat([1]).slice(0, 4); };
    const over = (t, u) => { const a = t[3]; return [0, 1, 2].map(i => t[i] * a + u[i] * (1 - a)).concat([1]); };
    const lum = c => { const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
                       return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]); };
    const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
    const behind = el => { const L = []; for (let e = el; e; e = e.parentElement) { const c = rgba(getComputedStyle(e).backgroundColor); if (c[3] > 0) { L.push(c); if (c[3] >= 1) break; } }
                           return L.reverse().reduce((acc, c) => over(c, acc), [255, 255, 255, 1]); };
    const out = [];
    for (const el of root.querySelectorAll('*')) {
      const own = Array.from(el.childNodes).filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join(' ').trim();
      if (!own || !el.getClientRects().length || el.closest('button')) continue;
      const bg = behind(el), r = ratio(over(rgba(getComputedStyle(el).color), bg), bg);
      if (r < 4.5) out.push(`"${own.slice(0, 30)}" ${r.toFixed(2)}:1`);
    }
    return out;
  }, sel);
}

(async () => {
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('long-rest-update-2026'));
    } catch (e) {}
  });
  const page = await ctx.newPage();
  page.on('pageerror', e => log(`  pageerror: ${e.message.slice(0, 200)}`));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle', timeout: 90000 });
  await settle(page);
  await openBuilderTab(page, 'Description');
  await settle(page);
  await page.locator('.pl-launcher').click({ timeout: 10000 });
  await page.waitForTimeout(800);
  const shot = async name => { const f = path.join(OUT, `${name}.png`); await page.screenshot({ path: f }); log(`  shot ${f}`); };
  for (let i = 1; i <= 2; i++) {
    await page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
    await page.waitForTimeout(2000);
    await shot(`p-random-${i}`);
  }
  log(`  keep colours box: ${await page.locator('.pl-keep-colours').count()}`);
  await page.locator('.pl-link').scrollIntoViewIfNeeded().catch(() => {});
  await page.waitForTimeout(400);
  await shot('p-drawer-link');
  log(`  dark drawer link contrast: ${JSON.stringify(await contrastIn(page, '.pl-link'))}`);
  await page.locator('.pl-drawer-close').click().catch(() => {});
  await settle(page, 600);
  await openBuilderTab(page, 'Portrait').catch(() => {});
  await settle(page, 800);
  await page.locator('.pl-inline').scrollIntoViewIfNeeded().catch(() => {});
  await shot('p-tab');
  log(`  dark tab link contrast: ${JSON.stringify(await contrastIn(page, '.pl-inline .pl-link'))}`);
  await clickVisible(page, 'text=Light Theme');
  await settle(page, 500);
  await openBuilderTab(page, 'Description');
  await settle(page, 600);
  await page.locator('.pl-launcher').click({ timeout: 10000 });
  await page.waitForTimeout(800);
  await page.locator('.pl-link').scrollIntoViewIfNeeded().catch(() => {});
  await shot('p-drawer-link-light');
  log(`  light drawer link contrast: ${JSON.stringify(await contrastIn(page, '.pl-link'))}`);
  await browser.close();
  log('PROBE DONE');
})().catch(e => { console.error('PROBE THREW', e); process.exit(1); });
