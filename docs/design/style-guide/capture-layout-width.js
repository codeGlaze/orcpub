// Layout follows the window width (round14.html): one desktop browser at 1280, then the
// same window narrowed to 390, then a real phone. Needs the app on :8890 (prod build) from
// feature/style-guide. Run from this folder:
//   NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-layout-width.js
const { chromium, devices } = require('playwright');
async function open(b, context) {
  const ctx = await b.newContext({ ...context, deviceScaleFactor: 2 });
  await ctx.addInitScript(() => { try { localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
  const c = p.locator('#cookie-btn');
  await c.waitFor({ timeout: 5000 }).then(() => c.click()).catch(() => {});
  await c.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(400);
  return { ctx, p };
}
const tab = async (p, name) => { await p.locator('.builder-tab', { hasText: name }).click(); await p.waitForTimeout(400); };
(async () => {
  const fs = require('fs'); const d = fs.readdirSync('/opt/pw-browsers').filter(x => x.startsWith('chromium-') && !x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  {
    const { ctx, p } = await open(b, { viewport: { width: 1280, height: 800 } });
    await p.screenshot({ path: 'round14/desktop-1280.png' });
    await p.setViewportSize({ width: 390, height: 844 }); await p.waitForTimeout(500);
    await p.evaluate(() => scrollTo(0, 0));
    await p.screenshot({ path: 'round14/desktop-narrowed-options.png' });
    await tab(p, 'Details');
    const a = await p.locator('.roll-button').first().boundingBox();
    await p.evaluate(y => scrollTo(0, y - 300), a.y + await p.evaluate(() => scrollY));
    await p.screenshot({ path: 'round14/desktop-narrowed-details.png' });
    await ctx.close();
  }
  {
    const { ctx, p } = await open(b, { ...devices['iPhone 13'] });
    await p.screenshot({ path: 'round14/phone-options.png' });
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
