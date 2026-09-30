// Ability buttons on a phone (round13.html): 14px as built, and 15px and 16px with the vertical
// padding trimmed so the button keeps its 31px height. Needs the app on :8890 (prod build).
// Run from this folder: NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-size-phone.js
const { chromium } = require('playwright');
const SIZES = { 14: '', 15: '.roll-button { font-size: 15px !important; padding-top: 5.5px !important; padding-bottom: 5.5px !important; }',
                16: '.roll-button { font-size: 16px !important; padding-top: 4.5px !important; padding-bottom: 4.5px !important; }' };
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const dark of [false, true]) {
    const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true });
    await ctx.addInitScript(dk => { try { localStorage.setItem('user', `{:theme "dark-theme" :dark-button-text? ${dk}}`); } catch (_) {} }, dark);
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const p = await ctx.newPage();
    await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
    const c = p.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
    await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
    for (const [size, css] of Object.entries(SIZES)) {
      await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
        const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c; document.head.appendChild(e); }, css);
      await p.waitForTimeout(120);
      const btn = p.locator('.roll-button').first();
      await btn.scrollIntoViewIfNeeded();
      const info = await btn.evaluate(e => { const r = e.getBoundingClientRect(); return `${getComputedStyle(e).fontSize} ${Math.round(r.width)}x${r.height}`; });
      console.log(dark ? 'dark' : 'white', size, info);
      const r = await btn.boundingBox();
      await p.screenshot({ path: `round13/${size}-${dark ? 'rim' : 'white'}.png`, clip: { x: 0, y: Math.max(0, r.y - 60), width: 390, height: 150 } });
    }
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
