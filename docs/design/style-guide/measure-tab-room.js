const { chromium } = require('playwright');
const WRAP = {
  'before (400, integration)': '.header-tab, .header-tab * { font-weight: 400 !important; }',
  'as is (700)': '',
  'letter spacing -2%': '.header-tab .title { letter-spacing: -0.02em; }',
  'letter spacing -4%': '.header-tab .title { letter-spacing: -0.04em; }',
  '13px': '.header-tab { font-size: 13px !important; }',
  '600 weight': '.header-tab, .header-tab * { font-weight: 600 !important; }'
};
(async () => {
  const fs=require('fs'); const d=fs.readdirSync('/opt/pw-browsers').filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
  await p.evaluate(() => document.fonts.ready);
  for (const [name, css] of Object.entries(WRAP)) {
    await p.evaluate(c => { document.querySelectorAll('style[data-x]').forEach(e => e.remove());
      const e = document.createElement('style'); e.dataset.x = 1; e.textContent = c; document.head.appendChild(e); }, css);
    await p.waitForTimeout(80);
    const r = await p.evaluate(() => {
      const t = [...document.querySelectorAll('.header-tab .title')].find(e => /my content/i.test(e.textContent));
      const box = t.parentElement; // the padded area the title sits in
      const cs = getComputedStyle(box);
      const avail = box.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
      // natural one-line width of the label
      const probe = t.cloneNode(true); probe.style.cssText += ';position:absolute;visibility:hidden;white-space:nowrap;width:auto';
      t.parentElement.appendChild(probe); const need = probe.getBoundingClientRect().width; probe.remove();
      const tab = t.closest('.header-tab').getBoundingClientRect().width;
      return { tab: Math.round(tab), avail: Math.round(avail * 10) / 10, need: Math.round(need * 10) / 10 };
    });
    console.log(`${name.padEnd(27)} tab ${r.tab}px, room ${r.avail}px, label needs ${r.need}px -> ${r.avail - r.need >= 0 ? 'fits, ' + (r.avail - r.need).toFixed(1) + 'px spare' : 'wraps, ' + (r.need - r.avail).toFixed(1) + 'px short'}`);
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
