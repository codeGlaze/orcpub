// Phone gutters (round17.html): the builder on a 390px phone as built, and two previews (CSS
// injected here only) with every row on one gutter: 10px and 16px. Lines mark the gutter on both
// sides. Needs the app on :8890 (prod build) from feature/style-guide. Run from this folder:
//   NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-gutters.js
const { chromium, devices } = require('playwright');
// Each row's own spacing, set so its visible edge lands on the gutter G.
const css = G => `
  .app-header-bar .w-100-p { padding-left: ${G}px !important; padding-right: ${G}px !important; }
  .app-header-menu { padding-left: ${G - 2}px !important; padding-right: ${G - 2}px !important; }
  h1[data-g] { margin-left: ${G}px !important; }
  [data-g=buttons].flex-wrap { margin-left: ${G - 5}px !important; margin-right: ${G - 5}px !important; }
  [data-g=toggles].flex-wrap { padding-right: ${G}px !important; }
  [data-g=builder-in].flex { padding-left: ${G}px !important; }
  [data-g=builder-out].w-100-p { padding-right: ${G}px !important; }`;
(async () => {
  const fs = require('fs'); const d = fs.readdirSync('/opt/pw-browsers').filter(x => x.startsWith('chromium-') && !x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  for (const [name, G] of [['built', null], ['g10', 10], ['g16', 16]]) {
    const ctx = await b.newContext({ ...devices['iPhone 13'], deviceScaleFactor: 3 });
    await ctx.addInitScript(() => { try { localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
    const p = await ctx.newPage();
    await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
    const c = p.locator('#cookie-btn');
    await c.waitFor({ timeout: 5000 }).then(() => c.click()).catch(() => {});
    await c.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
    await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    await p.evaluate(() => document.fonts.ready);
    if (G) await p.evaluate(rules => {
      const h1 = document.querySelector('h1'); h1.dataset.g = 'title';
      h1.closest('.flex-wrap').children[1].dataset.g = 'buttons';
      [...document.querySelectorAll('div.pointer')].find(e => /Dark Button Text/.test(e.textContent) && e.children.length < 4).parentElement.dataset.g = 'toggles';
      const inner = document.querySelector('.builder-tabs').parentElement.parentElement;
      inner.dataset.g = 'builder-in'; inner.parentElement.dataset.g = 'builder-out';
      const s = document.createElement('style'); s.textContent = rules; document.head.appendChild(s);
    }, css(G));
    await p.waitForTimeout(400);
    const edges = await p.evaluate(G => {
      const W = innerWidth, r = e => e.getBoundingClientRect();
      const ink = e => { const x = document.createRange(); x.selectNodeContents(e); return x.getBoundingClientRect().left; };
      const bar = document.querySelector('.app-header-bar .w-100-p'), tabs = [...document.querySelectorAll('.header-tab')];
      const panel = document.querySelector('.builder-tabs').parentElement;
      const toggles = [...document.querySelectorAll('div.pointer')].find(e => /Dark Button Text/.test(e.textContent) && e.children.length < 4);
      const firstBtn = document.querySelector('h1').closest('.flex-wrap').children[1].firstElementChild;
      const m = {
        logo: r(bar.querySelector('img')).left, tabs: r(tabs[0]).left, title: ink(document.querySelector('h1')),
        buttons: r(firstBtn).left, panel: r(panel).left,
        'login R': W - r(bar.lastElementChild).right, 'tabs R': W - r(tabs[tabs.length - 1]).right,
        'toggles R': W - r(toggles).right, 'panel R': W - r(panel).right };
      if (G) {
        const layer = document.createElement('div');
        layer.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:99999';
        for (const x of [G, W - G]) {
          const line = document.createElement('div');
          line.style.cssText = `position:absolute;top:0;bottom:0;left:${x - 0.5}px;width:1px;background:#4da3ff`;
          layer.append(line);
        }
        document.body.appendChild(layer);
      }
      return Object.entries(m).map(([k, v]) => `${k} ${Math.round(v)}`).join(', ');
    }, G);
    console.log(name, edges);
    await p.screenshot({ path: `round17/${name}.png`, clip: { x: 0, y: 0, width: 390, height: 640 } });
    await ctx.close();
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
