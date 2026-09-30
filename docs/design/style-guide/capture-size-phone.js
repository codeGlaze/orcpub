// The builder on a phone (round13.html), at 320px and 390px: the toggle row, and the ability
// buttons at 14px (as built), 15px and 16px, with the vertical padding trimmed so the button
// keeps its 31px height. Uses an iPhone user agent: the app picks its phone layout from the
// user agent, not the window width. Needs the app on :8890 (prod build).
// Run from this folder: NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-size-phone.js
const { chromium, devices } = require('playwright');
const SIZES = { 14: '', 15: '.roll-button { font-size: 15px !important; padding-top: 5.5px !important; padding-bottom: 5.5px !important; }',
                16: '.roll-button { font-size: 16px !important; padding-top: 4.5px !important; padding-bottom: 4.5px !important; }' };
const OUT = process.env.OUT || 'round13';
async function open(b, width, darkText) {
  const ctx = await b.newContext({ ...devices['iPhone 13'], viewport: { width, height: 844 }, deviceScaleFactor: 3 });
  await ctx.addInitScript(dk => { try { localStorage.setItem('user', `{:theme "dark-theme" :dark-button-text? ${dk}}`); } catch (_) {} }, darkText);
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
  const c = p.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
  await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
  return { ctx, p };
}
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const width of [320, 390]) {
    const { ctx, p } = await open(b, width, false);
    const row = p.locator('div.pointer', { hasText: 'Light Theme' }).first();
    const r = await row.boundingBox();
    const right = await p.locator('div.pointer', { hasText: 'Dark Button Text' }).first().evaluate(e => e.getBoundingClientRect().right);
    console.log(`toggles at ${width}px: right edge ${Math.round(right)}, gap ${Math.round(width - right)}px`);
    await p.screenshot({ path: `${OUT}/toggles-${width}.png`, clip: { x: 0, y: r.y - 90, width, height: 150 } });
    await ctx.close();
  }
  for (const width of [320, 390]) for (const darkText of [false, true]) {
    const { ctx, p } = await open(b, width, darkText);
    await p.getByText(/^details$/i).first().click();
    await p.waitForTimeout(400);
    for (const [size, css] of Object.entries(SIZES)) {
      await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
        const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c; document.head.appendChild(e); }, css);
      await p.waitForTimeout(120);
      const btn = p.locator('.roll-button').first();
      await btn.scrollIntoViewIfNeeded();
      const info = await btn.evaluate(e => { const r = e.getBoundingClientRect(); return `${getComputedStyle(e).fontSize} ${Math.round(r.width)}x${r.height}`; });
      console.log(width, darkText ? 'dark' : 'white', size, info);
      const a = await p.locator('.roll-button').first().boundingBox();
      await p.screenshot({ path: `${OUT}/${size}-${darkText ? 'rim' : 'white'}-${width}.png`, clip: { x: 0, y: Math.max(0, a.y - 110), width, height: 160 } });
    }
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
