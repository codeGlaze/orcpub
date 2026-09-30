// Every element with text sitting on an amber/orange surface (solid or gradient),
// grouped by class, across the main pages, in both themes.
const { chromium } = require('playwright');
const PAGES = ['/pages/dnd/5e/character-builder', '/dnd/5e/my-content', '/pages/dnd/5e/spells',
  '/pages/dnd/5e/monsters', '/pages/dnd/5e/magic-items', '/pages/dnd/5e/characters',
  '/pages/dnd/5e/encounter-builder', '/pages/dnd/5e/combat-tracker', '/pages/login-page', '/pages/register-page', '/'];
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const theme of ['dark-theme', 'light-theme']) {
    const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } });
    await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme);
    const p = await ctx.newPage();
    const found = new Map();
    for (const url of PAGES) {
      await p.goto('http://localhost:8890' + url, { waitUntil: 'networkidle' }).catch(() => {});
      await p.waitForTimeout(800);
      const rows = await p.evaluate(() => {
        const amber = s => {
          const m = s.match(/rgba?\((\d+), (\d+), (\d+)(?:, ([\d.]+))?\)/g) || [];
          return m.some(c => { const [r,g,b,a] = c.match(/[\d.]+/g).map(Number);
            return (a === undefined || a > 0.6) && r > 190 && g > 120 && g < 200 && b < 110; });
        };
        const out = [];
        for (const el of document.querySelectorAll('body *')) {
          if (!el.offsetParent) continue;
          const s = getComputedStyle(el);
          if (!(amber(s.backgroundColor) || amber(s.backgroundImage))) continue;
          const text = (el.innerText || '').trim().replace(/\s+/g, ' ');
          if (!text) continue;
          const tc = getComputedStyle(el).color;
          out.push({ key: `${el.tagName.toLowerCase()}.${[...el.classList].join('.') || '(no class)'}${el.getAttribute('style') ? ' +inline style' : ''}`,
                     text: text.slice(0, 24), color: tc });
        }
        return out;
      });
      for (const r of rows) {
        const k = r.key; if (!found.has(k)) found.set(k, { n: 0, pages: new Set(), ex: r.text, color: r.color });
        const f = found.get(k); f.n++; f.pages.add(url);
      }
    }
    console.log(`== ${theme}`);
    for (const [k, f] of [...found.entries()].sort((a, b) => b[1].n - a[1].n))
      console.log(`  ${String(f.n).padStart(3)}  ${k.slice(0, 70)}  text ${f.color}  e.g. "${f.ex}"  on ${[...f.pages].join(' ')}`);
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
