// Header tab and page-title weights, captured from the running site with real
// Open Sans 400-700 loaded. Each variant only overrides font-weight on the
// element in question; nothing else changes. No source changes.
//
//   NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt NODE_PATH=$(npm root -g) \
//     node docs/design/style-guide/capture-weights.js <out-dir>
// (see capture-bold.js for why fonts are fetched on the Node side)

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const OUT = process.argv[2] || 'weight-shots';
const findChrome = () => {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
  return path.join(base, dir, 'chrome-linux', 'chrome');
};
const FONTS = 'https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;500;600;700&display=block';
const VARIANTS = {
  tabs: { sel: '.header-tab', weights: [400, 500, 600, 700], clip: 'header' },
  title: { sel: 'h1.f-s-36', weights: [400, 600, 700], clip: 'title' }
};

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const theme of ['dark', 'light']) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
    await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme + '-theme');
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => { try { const resp = await r.fetch(); if (resp.status() !== 200) console.log("font", resp.status(), r.request().url()); await r.fulfill({ response: resp }); } catch (e) { console.log("fontfetch", String(e).slice(0,160)); await r.abort(); } });
    const page = await ctx.newPage();
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
    const c = page.locator('#cookie-btn');
    if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
    await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await page.evaluate(href => new Promise((ok, no) => {
      const l = document.createElement('link'); l.rel = 'stylesheet'; l.href = href; l.onload = ok; l.onerror = no;
      document.head.appendChild(l);
    }), FONTS);
    await page.evaluate(() => Promise.all(['400', '500', '600', '700'].map(w => document.fonts.load(`${w} 14px "Open Sans"`))));
    await page.mouse.move(5, 890);
    for (const [name, { sel, weights, clip }] of Object.entries(VARIANTS)) {
      for (const w of weights) {
        await page.evaluate(([s, css]) => { document.querySelectorAll('style[data-w]').forEach(e => e.remove());
          const e = document.createElement('style'); e.dataset.w = '1'; e.textContent = css; document.head.appendChild(e); },
          [null, `${sel}, ${sel} * { font-weight: ${w} !important; }`]);
        await page.waitForTimeout(150);
        const box = clip === 'header'
          ? await page.locator('.header-tab').first().evaluate(el => { const r = el.parentElement.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; })
          : await page.locator('h1.f-s-36').first().boundingBox();
        await page.screenshot({ path: path.join(OUT, `${theme}-${name}-${w}.png`),
          clip: { x: Math.max(0, box.x - 10), y: Math.max(0, box.y - 6), width: Math.min(1280, box.width + 20), height: box.height + 12 } });
      }
    }
    await ctx.close();
    console.log('  ' + theme + ' done');
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
