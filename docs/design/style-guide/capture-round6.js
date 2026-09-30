const { chromium } = require('playwright');
const V = {
  '6-white-shadow':  '.form-button { text-shadow: 0 1px 1px rgba(0,0,0,0.35), 0 0 1px rgba(0,0,0,0.25); }',
  '7-dark':          '.form-button { color: #15202e !important; }',
  '8-dark-emboss':   '.form-button { color: #15202e !important; text-shadow: 0 1px 0 rgba(255,255,255,0.35); }',
  '9-dark-glow':     '.form-button { color: #15202e !important; text-shadow: 0 0 2px rgba(255,255,255,0.45), 0 1px 0 rgba(255,255,255,0.3); }',
  '10-dark-emboss-strong': '.form-button { color: #15202e !important; text-shadow: 0 1px 0 rgba(255,255,255,0.55), 0 0 1px rgba(255,255,255,0.3); }'
};
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  const ctx = await b.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
  await ctx.addInitScript(() => { try { localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
  const c = p.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
  await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  await p.evaluate(() => document.fonts.ready);
  for (const [k, css] of Object.entries(V)) {
    await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
      const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c; document.head.appendChild(e); }, css);
    await p.waitForTimeout(150);
    const f = await p.locator('.form-button').first().boundingBox();
    const l = await p.locator('.form-button').nth(4).boundingBox();
    await p.screenshot({ path: `sg7/${k}.png`, clip: { x: f.x - 8, y: f.y - 8, width: l.x + l.width - f.x + 16, height: f.height + 16 } });
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
