// Gradient outline options for dark text on the ability buttons (round11.html). Needs the app on
// :8890 (prod build). DPR=1 or 2 sets the pixel density. Run from this folder: NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-outline-gradient.js
// A stroke can't take a gradient, so each option draws a copy of the label behind the real one
// (::before) with a transparent stroke and a gradient clipped to the text; Chrome paints the clip
// into the stroke area too. The real label keeps its dark fill and the built light glow.
const { chromium } = require('playwright');
const SEL = '.app.dark-button-text .roll-button';
const G = grad => `
  ${SEL} { position: relative; isolation: isolate; -webkit-text-stroke: 0 !important; }
  ${SEL}::before { content: attr(data-label); position: absolute; inset: 0; z-index: -1;
    display: flex; align-items: center; justify-content: center;
    -webkit-text-stroke: 2px transparent; color: transparent; text-shadow: none;
    background: ${grad}; -webkit-background-clip: text; background-clip: text; }`;
const OPT = {
  'w-white': null,
  'k-built': '',
  'o-reverse': G('linear-gradient(to bottom, #dbab50 40%, #f1a20f 62%)'),
  'p-bottom-solid': G('#dbab50'),
  'q-yellow-reverse': G('linear-gradient(to bottom, #ffd21a 40%, #dbab50 62%)'),
  'r-bevel': G('linear-gradient(to bottom, #ffd21a 40%, #b37a00 62%)'),
};
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const dark of [false, true]) {
    const ctx = await b.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: +(process.env.DPR || 2) });
    await ctx.addInitScript(dk => { try { localStorage.setItem('user', `{:theme "dark-theme" :dark-button-text? ${dk}}`); } catch (_) {} }, dark);
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const p = await ctx.newPage();
    await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
    const c = p.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
    await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await p.evaluate(() => document.querySelectorAll('.roll-button').forEach(e => e.dataset.label = e.textContent));
    await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
    for (const [k, css] of Object.entries(OPT)) {
      if ((k === 'w-white') !== !dark) continue;
      await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
        const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c || ''; document.head.appendChild(e); }, css);
      await p.waitForTimeout(120);
      const r = await p.locator('.roll-button').first().boundingBox(); const r2 = await p.locator('.roll-button').nth(5).boundingBox();
      await p.screenshot({ path: `round11/roll-${k}${process.env.DPR ? "@" + process.env.DPR : ""}.png`, clip: { x: r.x - 8, y: r.y - 8, width: r2.x + r2.width - r.x + 16, height: r.height + 16 } });
    }
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
