// Outline options for dark text on the ability buttons (round9.html). Needs the app on :8890 (prod build).
// Run from this folder: NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-outline.js
const { chromium } = require('playwright');
const SEL = '.app.dark-button-text .roll-button';
const OPT = {
  'w-white': null,
  'd-built': '',
  'e-shadow-ring': `${SEL} { text-shadow: 1px 0 0 rgba(255,255,255,.55), -1px 0 0 rgba(255,255,255,.55), 0 1px 0 rgba(255,255,255,.55), 0 -1px 0 rgba(255,255,255,.55) !important; }`,
  'f-stroke-hair': `${SEL} { -webkit-text-stroke: 1px rgba(255,255,255,.75); paint-order: stroke fill; }`,
  'g-stroke-hair-glow': `${SEL} { -webkit-text-stroke: 1px rgba(255,255,255,.75); paint-order: stroke fill; }`,
  'h-stroke-full': `${SEL} { -webkit-text-stroke: 2px rgba(255,255,255,.85); paint-order: stroke fill; text-shadow: none !important; }`,
  'i-stroke-full-glow': `${SEL} { -webkit-text-stroke: 2px rgba(255,255,255,.85); paint-order: stroke fill; }`,
};
// f: stroke only, no glow
OPT['f-stroke-hair'] += ` ${SEL} { text-shadow: none !important; }`;
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const dark of [false, true]) {
    const ctx = await b.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
    await ctx.addInitScript(dk => { try { localStorage.setItem('user', `{:theme "dark-theme" :dark-button-text? ${dk}}`); } catch (_) {} }, dark);
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const p = await ctx.newPage();
    await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
    const c = p.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
    await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
    for (const [k, css] of Object.entries(OPT)) {
      if ((k === 'w-white') !== !dark) continue;
      await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
        const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c || ''; document.head.appendChild(e); }, css);
      await p.waitForTimeout(120);
      const r = await p.locator('.roll-button').first().boundingBox(); const r2 = await p.locator('.roll-button').nth(5).boundingBox();
      await p.screenshot({ path: `round9/roll-${k}.png`, clip: { x: r.x - 8, y: r.y - 8, width: r2.x + r2.width - r.x + 16, height: r.height + 16 } });
    }
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
