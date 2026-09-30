// For each page, every visible text element rendered at weight >= 600: its text, size,
// weight, and where the weight comes from (class, inline style, or a stylesheet rule).
const { chromium } = require('playwright');
const PAGES = [['/pages/dnd/5e/character-builder','builder'], ['/dnd/5e/my-content','mycontent'],
               ['/pages/dnd/5e/spells','spells'], ['/pages/dnd/5e/monsters','monsters']];
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  for (const [url, name] of PAGES) {
    await p.goto('http://localhost:8890' + url, { waitUntil: 'networkidle' });
    await p.evaluate(() => document.fonts.ready);
    const rows = await p.evaluate(() => {
      const out = new Map();
      for (const el of document.querySelectorAll('body *')) {
        const own = [...el.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join(' ').trim();
        if (!own || !el.offsetParent) continue;
        const s = getComputedStyle(el); const w = +s.fontWeight; if (w < 600) continue;
        const r = el.getBoundingClientRect(); if (r.top > 900 || r.width === 0) continue;
        // where does the weight come from?
        let src = 'inherited';
        for (let e = el; e && e !== document.body; e = e.parentElement) {
          if (e.style && e.style.fontWeight) { src = `inline on <${e.tagName.toLowerCase()}>`; break; }
          const cls = [...e.classList].find(c => /^f-w-/.test(c));
          if (cls) { src = `.${cls} on <${e.tagName.toLowerCase()}>`; break; }
          if (/^H[1-6]$/.test(e.tagName)) { src = `<${e.tagName.toLowerCase()}> default`; break; }
          if (e.tagName === 'BUTTON' || e.tagName === 'B' || e.tagName === 'STRONG') { src = `<${e.tagName.toLowerCase()}>`; break; }
        }
        const key = `${parseFloat(s.fontSize)}px ${w} ${s.textTransform === 'uppercase' ? 'CAPS' : 'mixed'} | ${src}`;
        if (!out.has(key)) out.set(key, []);
        const list = out.get(key); if (list.length < 3) list.push(own.slice(0, 40));
      }
      return [...out.entries()].sort((a, b) => parseFloat(b[0]) - parseFloat(a[0]));
    });
    console.log(`== ${name}`);
    for (const [k, ex] of rows) console.log(`  ${k}  e.g. ${JSON.stringify(ex)}`);
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
