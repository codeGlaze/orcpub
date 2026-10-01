// The phone header (round15.html): 390px and 320px phones, and desktop at 1280 to show it is
// unchanged. Needs the app on :8890 (prod build) from feature/style-guide. Run from this folder:
//   NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-phone-header.js
const { chromium, devices } = require('playwright');
const SHOTS = [
  ['phone-390', { ...devices['iPhone 13'] }, 300],
  ['phone-320', { ...devices['iPhone SE'] }, 300],
  ['desktop-1280', { viewport: { width: 1280, height: 800 } }, 520],
];
(async () => {
  const fs = require('fs'); const d = fs.readdirSync('/opt/pw-browsers').filter(x => x.startsWith('chromium-') && !x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const [name, context, height] of SHOTS) {
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
    const w = p.viewportSize().width;
    const over = await p.evaluate(() => document.documentElement.scrollWidth - innerWidth);
    console.log(name, 'sideways overflow', over);
    await p.screenshot({ path: `round15/${name}.png`, clip: { x: 0, y: 0, width: w, height } });
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
