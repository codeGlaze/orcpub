// Every header tab's menu on a phone (round21.html): opens it, checks it is fully on screen and
// not clipped by the header, and saves a screenshot. Needs the app on :8890 (prod build).
// Run from this folder: NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-phone-menus.js
const { chromium, devices } = require('playwright');
(async () => {
  const fs = require('fs'); const d = fs.readdirSync('/opt/pw-browsers').filter(x => x.startsWith('chromium-') && !x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  let bad = 0;
  for (const [w, dev] of [[390, 'iPhone 13'], [320, 'iPhone SE']]) {
    const ctx = await b.newContext({ ...devices[dev], deviceScaleFactor: 2 });
    await ctx.addInitScript(() => { try { localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const p = await ctx.newPage();
    await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
    const c = p.locator('#cookie-btn');
    await c.waitFor({ timeout: 5000 }).then(() => c.click()).catch(() => {});
    await c.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
    await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await p.evaluate(() => document.fonts.ready);
    const n = await p.locator('.header-tab').count();
    for (let i = 0; i < n; i++) {
      const tab = p.locator('.header-tab').nth(i);
      if (!(await tab.locator('.header-flyout').count())) continue;
      await tab.focus(); await p.waitForTimeout(350);
      const r = await tab.locator('.header-flyout').evaluate(f => {
        const b = f.getBoundingClientRect();
        // the point at the menu's bottom-centre must be the menu itself, not what's behind it
        const hit = document.elementFromPoint(b.left + b.width / 2, b.bottom - 4);
        return { l: Math.round(b.left), r: Math.round(innerWidth - b.right), bottom: Math.round(b.bottom),
                 visible: !!hit && f.contains(hit), over: document.documentElement.scrollWidth - innerWidth };
      });
      const ok = r.l >= 0 && r.r >= 0 && r.visible && r.over <= 0;
      if (!ok) bad++;
      console.log(`${ok ? 'PASS' : 'FAIL'}  ${w}px tab ${i + 1}: left ${r.l}, right ${r.r}, bottom ${r.bottom}, fully shown ${r.visible}`);
      await p.screenshot({ path: `round21/menus/${w}-tab${i + 1}.png`, clip: { x: 0, y: 0, width: w, height: 420 } });
      await p.evaluate(() => document.activeElement.blur()); await p.waitForTimeout(150);
    }
    await ctx.close();
  }
  await b.close();
  process.exit(bad ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
