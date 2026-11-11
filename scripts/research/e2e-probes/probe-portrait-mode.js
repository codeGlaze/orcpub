// Probe (kept on agents/develop, scripts/research/e2e-probes): the portrait editor's Compose | Picture link switch.
// Run: copy into a code branch's scripts/e2e/ (it uses that folder's helpers and test/fixtures), then E2E_BASE=http://localhost:<port> node scripts/e2e/probe-portrait-mode.js against a running dev server.
// Kind: probe
const { chromium, devices } = require('playwright');
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


async function run(browser, label, options, phone) {
  const ctx = await browser.newContext(options);
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('long-rest-update-2026'));
    } catch (e) {}
  });
  const page = await ctx.newPage();
  page.on('pageerror', e => log(`  pageerror: ${e.message.slice(0, 200)}`));
  const shot = async name => { const f = path.join(OUT, `m-${label}-${name}.png`); await page.screenshot({ path: f }); log(`  shot ${f}`); };
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle', timeout: 90000 });
  await settle(page);
  if (phone) await page.evaluate(() => { const t = Array.from(document.querySelectorAll('*')).find(e => e.textContent.trim() === 'DESCRIPTION' || e.textContent.trim() === 'Description'); if (t) t.click(); });
  else await openBuilderTab(page, 'Description');
  await settle(page);
  const row = page.locator('button', { hasText: 'Change picture' }).first();
  await row.scrollIntoViewIfNeeded().catch(() => {});
  await page.evaluate(() => window.scrollBy(0, -150));
  await page.waitForTimeout(400);
  await shot('description');
  await row.click();
  await page.waitForTimeout(900);
  await shot('drawer-compose');
  await page.locator('.pl-mode .pl-segmented button', { hasText: 'Use an image from the web' }).click();
  await page.waitForTimeout(500);
  await shot('drawer-link-empty');
  const input = page.locator('.pl-link input').first();
  await input.fill('https://i.imgur.com/2O4oP3l.png');
  await input.dispatchEvent('blur');
  await page.waitForTimeout(2000);
  await shot('drawer-link-filled');
  log(`  [${label}] link mode contrast: ${JSON.stringify(await contrastIn(page, '.pl-link-mode'))}`);
  if (!phone) {
    const pic = () => page.evaluate(() => {
      const box = document.querySelector('.image-character-thumbnail');
      if (!box) return 'no thumbnail';
      if (box.querySelector('.pl-portrait-frame, .pl-thumb-box') || box.classList.contains('pl-thumb-box')) return 'portrait';
      const img = box.tagName === 'IMG' ? box : box.querySelector('img');
      return img ? 'link ' + img.getAttribute('src') : 'empty';
    });
    // confirm-before-replace: a portrait, then a drafted link cancelled, then one used
    await page.locator('button', { hasText: 'Cancel' }).last().click();
    await page.waitForTimeout(500);
    await page.locator('button', { hasText: 'Change picture' }).first().click();
    await page.waitForTimeout(700);
    await page.locator('.pl-mode .pl-segmented button', { hasText: 'Build a portrait' }).click();
    await page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
    await page.waitForTimeout(1500);
    await page.locator('.pl-btn-primary', { hasText: 'Save portrait' }).click();
    await page.waitForTimeout(1000);
    log(`  CHECK after saving a portrait: ${await pic()}`);
    await page.locator('button', { hasText: 'Change picture' }).first().click();
    await page.waitForTimeout(700);
    log(`  CHECK the editor opens on: ${await page.locator('.pl-mode .pl-segmented button.on').innerText()}`);
    await page.locator('.pl-mode .pl-segmented button', { hasText: 'Use an image from the web' }).click();
    await page.locator('.pl-link input').first().fill('https://i.imgur.com/2O4oP3l.png');
    await page.waitForTimeout(800);
    await page.locator('button', { hasText: 'Cancel' }).last().click();
    await page.waitForTimeout(800);
    log(`  CHECK after a drafted link and Cancel: ${await pic()}`);
    await page.locator('button', { hasText: 'Change picture' }).first().click();
    await page.waitForTimeout(700);
    await page.locator('.pl-mode .pl-segmented button', { hasText: 'Use an image from the web' }).click();
    await page.locator('.pl-link input').first().fill('https://i.imgur.com/2O4oP3l.png');
    await page.waitForTimeout(2500);
    await shot('drawer-link-imgur');
    await page.locator('button', { hasText: /^(Switch to this image|Use this image)$/ }).click();
    await page.waitForTimeout(1500);
    log(`  CHECK after Switch to this image: ${await pic()}`);
    await page.locator('button', { hasText: 'Change picture' }).first().click();
    await page.waitForTimeout(800);
    await shot('drawer-image-showing');
    await page.locator('.pl-mode .pl-segmented button', { hasText: 'Build a portrait' }).click();
    await page.waitForTimeout(1500);
    await shot('drawer-portrait-kept');
    await page.locator('button', { hasText: 'Switch to this portrait' }).click();
    await page.waitForTimeout(1500);
    log(`  CHECK after Switch to this portrait: ${await pic()}`);
    await clickVisible(page, 'text=Light Theme');
    await settle(page, 400);
    await page.locator('button', { hasText: 'Change picture' }).first().click();
    await page.waitForTimeout(800);
    await shot('light-selected-active');
    await page.locator('.pl-mode .pl-segmented button', { hasText: 'Use an image from the web' }).click();
    await page.waitForTimeout(800);
    await shot('light-unselected-active');
    await page.locator('button', { hasText: 'Cancel' }).last().click();
    await page.waitForTimeout(400);
    await clickVisible(page, 'text=Light Theme');
    await settle(page, 400);
    await page.locator('button', { hasText: 'Change picture' }).first().click();
    await page.waitForTimeout(800);
    await shot('dark-selected-active');
    await shot('description-after');
    await openBuilderTab(page, 'Portrait').catch(() => {});
    await settle(page, 600);
    await page.locator('.pl-inline').scrollIntoViewIfNeeded().catch(() => {});
    await shot('tab');
    await clickVisible(page, 'text=Light Theme');
    await settle(page, 400);
    await page.locator('button', { hasText: 'Edit image' }).first().click().catch(() => {});
    await page.waitForTimeout(900);
    await shot('drawer-link-light');
    log(`  [${label}] light link mode contrast: ${JSON.stringify(await contrastIn(page, '.pl-link-mode'))}`);
  }
  await ctx.close();
}

(async () => {
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  await run(browser, 'desktop', { viewport: { width: 1280, height: 900 } }, false);
  await run(browser, 'phone', { ...devices['iPhone 13'], viewport: { width: 390, height: 844 } }, true);
  await browser.close();
  log('PROBE DONE');
})().catch(e => { console.error('PROBE THREW', e); process.exit(1); });
