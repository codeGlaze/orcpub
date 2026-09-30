// Ability-button label size options (round12.html): 14px (as built), 15px and 16px, each with
// white text, dark text with the glow only, the built yellow rim, and the lit-from-above rim.
// Needs the app on :8890 (prod build). DPR=1 or 2 sets the pixel density.
// Run from this folder: NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt DPR=1 node capture-size.js
const { chromium } = require('playwright');
const SEL = '.app.dark-button-text .roll-button';
const LIT = `
  ${SEL} { position: relative; isolation: isolate; -webkit-text-stroke: 0 !important; }
  ${SEL}::before { content: attr(data-label); position: absolute; inset: 0; z-index: -1;
    display: flex; align-items: center; justify-content: center;
    -webkit-text-stroke: 2px transparent; color: transparent; text-shadow: none;
    background: linear-gradient(to bottom, #ffd21a 40%, #dbab50 62%);
    -webkit-background-clip: text; background-clip: text; }`;
const STYLES = {
  white: null,
  glow: `${SEL} { -webkit-text-stroke: 0 !important; }`,
  rim: '',
  lit: LIT,
};
const DPR = +(process.env.DPR || 2);
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const dark of [false, true]) {
    const ctx = await b.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: DPR });
    await ctx.addInitScript(dk => { try { localStorage.setItem('user', `{:theme "dark-theme" :dark-button-text? ${dk}}`); } catch (_) {} }, dark);
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const p = await ctx.newPage();
    await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
    const c = p.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
    await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await p.evaluate(() => document.querySelectorAll('.roll-button').forEach(e => e.dataset.label = e.textContent));
    await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
    for (const size of [14, 15, 16]) {
      for (const [k, css] of Object.entries(STYLES)) {
        if ((k === 'white') !== !dark) continue;
        await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
          const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c; document.head.appendChild(e); },
          `.roll-button { font-size: ${size}px !important; } ${css || ''}`);
        await p.waitForTimeout(120);
        const got = await p.locator('.roll-button').first().evaluate(e => `${getComputedStyle(e).fontSize} h=${e.getBoundingClientRect().height}`);
        if (DPR === 1) console.log(size, k, got);
        const r = await p.locator('.roll-button').first().boundingBox(); const r2 = await p.locator('.roll-button').nth(5).boundingBox();
        await p.screenshot({ path: `round12/${size}-${k}@${DPR}.png`, clip: { x: r.x - 8, y: r.y - 8, width: r2.x + r2.width - r.x + 16, height: r.height + 16 } });
      }
    }
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
