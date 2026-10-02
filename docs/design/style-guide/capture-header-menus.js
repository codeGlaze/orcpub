// Header menus (round21.html): the active tab, the Characters flyout open on desktop and on a
// phone with its current item highlighted, and the logged-in user menu. Prints each one's
// computed position and colours. Needs the app on :8890 (prod build, seeded user kaylee).
// Run from this folder: OUT=round21/before NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-header-menus.js
const { chromium, devices } = require('playwright');
const BASE = 'http://localhost:8890', OUT = process.env.OUT || 'round21';
async function open(b, context, login) {
  const ctx = await b.newContext({ ...context, deviceScaleFactor: 2 });
  await ctx.addInitScript(() => { try { if (!localStorage.getItem('user')) localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  if (login) {
    await p.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
    await p.waitForSelector('input');
    await p.locator('input').nth(0).fill('kaylee'); await p.locator('input').nth(1).fill('serenity99');
    await p.locator('button.form-button').first().click();
    await p.waitForFunction(() => /:token/.test(localStorage.getItem('user') || ''), null, { timeout: 20000 });
  }
  await p.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  const c = p.locator('#cookie-btn');
  await c.waitFor({ timeout: 5000 }).then(() => c.click()).catch(() => {});
  await c.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
  return { ctx, p };
}
const desc = (p, sel) => p.evaluate(sel => {
  const e = document.querySelector(sel); if (!e) return 'missing';
  const s = getComputedStyle(e), r = e.getBoundingClientRect();
  return `${sel}: ${Math.round(r.left)},${Math.round(r.top)} ${Math.round(r.width)}x${Math.round(r.height)} bg ${s.backgroundColor} pos ${s.position} z ${s.zIndex} display ${s.display}`;
}, sel);
(async () => {
  const fs = require('fs'); const d = fs.readdirSync('/opt/pw-browsers').filter(x => x.startsWith('chromium-') && !x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  {
    const { ctx, p } = await open(b, { viewport: { width: 1280, height: 900 } });
    const tab = p.locator('.header-tab').first();
    console.log('desktop', await desc(p, '.header-tab'));
    await tab.hover(); await p.waitForTimeout(400);
    console.log('desktop', await desc(p, '.header-tab .header-flyout'));
    console.log('desktop', await desc(p, '.header-tab .header-flyout > div:nth-child(2)'));
    await p.screenshot({ path: `${OUT}/desktop-flyout.png`, clip: { x: 560, y: 170, width: 720, height: 330 } });
    await ctx.close();
  }
  {
    const { ctx, p } = await open(b, devices['iPhone 13']);
    await p.locator('.header-tab').first().focus(); await p.waitForTimeout(400);
    console.log('phone', await desc(p, '.header-tab .header-flyout'));
    await p.screenshot({ path: `${OUT}/phone-flyout.png`, clip: { x: 0, y: 0, width: 390, height: 300 } });
    await ctx.close();
  }
  {
    const { ctx, p } = await open(b, { viewport: { width: 1280, height: 900 } }, true);
    await p.locator('#user-header').hover(); await p.waitForTimeout(400);
    console.log('user', await desc(p, '#user-menu'));
    await p.screenshot({ path: `${OUT}/user-menu.png`, clip: { x: 980, y: 0, width: 300, height: 180 } });
    await p.mouse.move(400, 600); await p.waitForTimeout(400);
    console.log('user closed', await desc(p, '#user-menu'));
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
