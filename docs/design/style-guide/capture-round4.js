const { chromium } = require('playwright');
const WRAP = {
  'as is (700)': '',
  'letter spacing -2%': '.header-tab .title { letter-spacing: -0.02em; }',
  'letter spacing -4%': '.header-tab .title { letter-spacing: -0.04em; }',
  '13px': '.header-tab { font-size: 13px !important; }',
  '600 weight': '.header-tab { font-weight: 600 !important; }',
  'before (400, integration)': '.header-tab, .header-tab * { font-weight: 400 !important; }'
};
const PAGES = [['/pages/dnd/5e/character-builder','builder'], ['/dnd/5e/my-content','mycontent'],
               ['/pages/dnd/5e/spells','spells'], ['/pages/dnd/5e/monsters','monsters']];
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  const ctx = await b.newContext({ viewport: { width: 1600, height: 900 } });
  await ctx.addInitScript(() => { try { localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
  await p.evaluate(() => document.fonts.ready);
  const oneLine = () => p.evaluate(() => {
    const t = [...document.querySelectorAll('.header-tab .title')].find(e => /my content/i.test(e.textContent));
    if (!t || !t.offsetParent) return null;
    const lh = parseFloat(getComputedStyle(t).lineHeight) || parseFloat(getComputedStyle(t).fontSize) * 1.4;
    return t.getBoundingClientRect().height < lh * 1.5;
  });
  // A: narrowest width that keeps MY CONTENT on one line, per option
  for (const [name, css] of Object.entries(WRAP)) {
    await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
      const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c; document.head.appendChild(e); }, css);
    let fits = null;
    for (let w = 1600; w >= 700; w -= 10) {
      await p.setViewportSize({ width: w, height: 900 }); await p.waitForTimeout(40);
      const r = await oneLine();
      if (r === null) { fits = fits ?? `labels hidden below ${w}`; break; }
      if (!r) break;
      fits = w;
    }
    console.log(`${name.padEnd(28)} one line down to ${fits}px`);
  }
  await p.setViewportSize({ width: 1280, height: 900 });
  // B: dark text on every amber button, dark theme, four pages
  const DARK = `(() => { for (const el of document.querySelectorAll('*')) {
      const bg = getComputedStyle(el).backgroundImage;
      if (/rgb\\(241, 162, 15\\)/.test(bg)) { el.style.setProperty('color', '#15202e', 'important');
        el.querySelectorAll('*').forEach(c => c.style.setProperty('color', '#15202e', 'important')); } } })()`;
  const ctx2 = await b.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1 });
  await ctx2.addInitScript(() => { try { localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
  await ctx2.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const q = await ctx2.newPage();
  for (const [url, name] of PAGES) for (const state of ['white', 'dark']) {
    await q.goto('http://localhost:8890' + url, { waitUntil: 'networkidle' });
    const c = q.locator('#cookie-btn'); if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
    await q.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await q.evaluate(() => document.fonts.ready);
    if (state === 'dark') await q.evaluate(DARK);
    await q.waitForTimeout(300);
    const n = await q.evaluate(() => [...document.querySelectorAll('*')].filter(el => /rgb\(241, 162, 15\)/.test(getComputedStyle(el).backgroundImage)).length);
    if (state === 'dark') console.log(`${name}: ${n} amber elements`);
    await q.screenshot({ path: `sg4/dark-${state}-${name}.png`, clip: { x: 0, y: 150, width: 1280, height: 750 } });
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
