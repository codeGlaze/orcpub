const { chromium } = require('playwright');
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const theme of ['dark-theme', 'light-theme']) for (const dark of [false, true]) {
    if (theme === 'light-theme' && dark) continue;
    const ctx = await b.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
    await ctx.addInitScript(([t, dk]) => { try { localStorage.setItem('user', `{:theme "${t}" :dark-button-text? ${dk}}`); } catch (_) {} }, [theme, dark]);
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const p = await ctx.newPage();
    await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
    const c = p.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
    await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(300);
    const sh = await p.evaluate(() => ['.form-button', '.roll-button'].map(s => {
      const e = document.querySelector(s); const cs = getComputedStyle(e); return `${s}: ${cs.color} / ${cs.textShadow.slice(0, 40)}`; }));
    const tag = `${theme.replace('-theme','')}${dark ? '-darktext' : ''}`;
    console.log(tag, sh.join(' | '));
    const f = await p.locator('.form-button').first().boundingBox(); const l = await p.locator('.form-button').nth(4).boundingBox();
    await p.screenshot({ path: `sg8/${tag}-buttons.png`, clip: { x: f.x - 8, y: f.y - 8, width: l.x + l.width - f.x + 16, height: f.height + 16 } });
    const r = await p.locator('.roll-button').first().boundingBox(); const r2 = await p.locator('.roll-button').nth(5).boundingBox();
    await p.screenshot({ path: `sg8/${tag}-roll.png`, clip: { x: r.x - 8, y: r.y - 8, width: r2.x + r2.width - r.x + 16, height: r.height + 16 } });
    if (!dark) {
      // header tab: as built, and with the dark lift (not built)
      for (const [k, css] of [['tab-now', ''], ['tab-lift', `.header-tab { text-shadow: 0 1px 1px rgba(0,0,0,0.35), 0 0 1px rgba(0,0,0,0.25); }`]]) {
        await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
          const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c; document.head.appendChild(e); }, css);
        await p.waitForTimeout(100);
        const t = await p.locator('.header-tab').first().evaluate(el => { const q = el.parentElement.getBoundingClientRect(); return { x: q.x, y: q.y, width: q.width, height: q.height }; });
        await p.screenshot({ path: `sg8/${tag}-${k}.png`, clip: { x: t.x - 4, y: t.y - 4, width: Math.min(1280 - t.x + 4, 470), height: t.height + 8 } });
      }
    }
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
