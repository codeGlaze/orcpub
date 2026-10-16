// Faux bold vs real bold, captured from the running site.
//
// The site loads Open Sans at weight 400 only (index.clj), so 600/700 text is
// synthesised by the browser. "after" adds the real 600 and 700 faces from
// Google Fonts at capture time -- the one-line fix -- and nothing else.
//
//   lein fig:build && lein e2e-server
//   NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt NODE_PATH=$(npm root -g) \
//     node docs/design/style-guide/capture-bold.js <out-dir>
//
// In the Claude Code sandbox the browser cannot verify the proxy's certificate
// for Google Fonts, so the page silently falls back to another sans. Font
// requests are therefore fetched on the Node side, which trusts the bundle in
// NODE_EXTRA_CA_CERTS, and handed to the page. Verification stays on.

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const OUT = process.argv[2] || 'bold-shots';
const findChrome = () => {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
  return path.join(base, dir, 'chrome-linux', 'chrome');
};
const FIX = 'https://fonts.googleapis.com/css2?family=Open+Sans:wght@600;700&display=block';

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const theme of ['dark', 'light']) {
    for (const state of ['before', 'after']) {
      const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
      await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme + '-theme');
      await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async route => {
        await route.fulfill({ response: await route.fetch() });
      });
      const page = await ctx.newPage();
      await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
      const c = page.locator('#cookie-btn');
      if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
      await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
      if (state === 'after') {
        await page.evaluate(href => new Promise((ok, no) => {
          const l = document.createElement('link');
          l.rel = 'stylesheet'; l.href = href; l.onload = ok; l.onerror = no;
          document.head.appendChild(l);
        }), FIX);
        await page.evaluate(() => Promise.all(['600', '700'].map(w => document.fonts.load(`${w} 12px "Open Sans"`))));
        await page.evaluate(() => document.fonts.ready);
        await page.waitForTimeout(400);
      }
      await page.evaluate(() => document.fonts.ready);
      const weights = await page.evaluate(() =>
        ([...document.fonts].filter(f => /Open Sans/.test(f.family) && f.status === 'loaded').map(f => f.weight)));
      console.log(`  ${theme} ${state}: loaded Open Sans weights ${JSON.stringify([...new Set(weights)])}`);
      // the builder's tab row and the buttons above it: 600-weight uppercase UI
      const tabs = page.locator('.builder-tabs').first();
      await tabs.scrollIntoViewIfNeeded();
      const box = await tabs.boundingBox();
      await page.screenshot({ path: path.join(OUT, `${theme}-${state}-builder.png`),
        clip: { x: 0, y: Math.max(0, box.y - 220), width: 1280, height: box.height + 260 } });
      // one button up close
      const btn = page.locator('.form-button').first();
      if (await btn.count()) await btn.screenshot({ path: path.join(OUT, `${theme}-${state}-button.png`) });
      await ctx.close();
    }
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
