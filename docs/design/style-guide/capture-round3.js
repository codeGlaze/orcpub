const { chromium } = require('playwright');
const PAGES = [['/pages/dnd/5e/character-builder','builder'], ['/dnd/5e/my-content','mycontent'],
               ['/pages/dnd/5e/spells','spells'], ['/pages/dnd/5e/monsters','monsters']];
const BTN = {
  arial:  'button { font-family: Arial, sans-serif !important; } .form-button { font-weight: 700 !important; }',
  os600:  '.form-button { font-weight: 600 !important; }',
  os700:  '',
  dark:   '.app:not(.light-theme) .form-button { color: #15202e !important; }'
};
const WRAP = {
  asis: '',
  ls02: '.header-tab .title { letter-spacing: -0.02em; }',
  ls04: '.header-tab .title { letter-spacing: -0.04em; }',
  s13:  '.header-tab { font-size: 13px !important; }',
  w600: '.header-tab { font-weight: 600 !important; }'
};
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const theme of ['dark','light']) {
    const ctx = await b.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1 });
    await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme + '-theme');
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const p = await ctx.newPage();
    const prep = async url => {
      await p.goto('http://localhost:8890' + url, { waitUntil: 'networkidle' });
      const c = p.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
      await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
      await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(400);
    };
    for (const [url, name] of PAGES) {
      await prep(url);
      await p.screenshot({ path: `sg3/${theme}-v2-${name}.png`, clip: { x: 0, y: 150, width: 1280, height: 750 } });
    }
    // buttons, 2x close-up of the builder's button row
    await prep('/pages/dnd/5e/character-builder');
    const set = css => p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
      const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c; document.head.appendChild(e); }, css);
    for (const [k, css] of Object.entries(BTN)) {
      await set(css); await p.waitForTimeout(150);
      const first = await p.locator('.form-button').first().boundingBox();
      const last = await p.locator('.form-button').nth(4).boundingBox();
      await p.screenshot({ path: `sg3/${theme}-btn-${k}.png`, scale: 'device',
        clip: { x: first.x - 6, y: first.y - 6, width: last.x + last.width - first.x + 12, height: first.height + 12 } });
    }
    await set('');
    // header wrap options
    for (const [k, css] of Object.entries(WRAP)) {
      await set(css); await p.waitForTimeout(150);
      const r = await p.locator('.header-tab').first().evaluate(el => { const q = el.parentElement.getBoundingClientRect(); return { x: q.x, y: q.y, width: q.width, height: q.height }; });
      await p.screenshot({ path: `sg3/${theme}-wrap-${k}.png`, clip: { x: r.x - 4, y: r.y - 4, width: Math.min(1280 - r.x + 4, r.width + 8), height: r.height + 8 } });
    }
    await ctx.close(); console.log(theme, 'done');
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
