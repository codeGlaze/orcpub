// The phone header's left edges (round16.html): a line at the left edge of each element down the
// page, as built and with a preview (CSS injected here only) that puts them all on 10px; and the
// bar with the search and login boxes. Needs the app on :8890 (prod build) from
// feature/style-guide. Run from this folder:
//   NODE_EXTRA_CA_CERTS=/root/.ccr/ca-bundle.crt node capture-left-edges.js
const { chromium, devices } = require('playwright');
(async () => {
  const fs = require('fs'); const d = fs.readdirSync('/opt/pw-browsers').filter(x => x.startsWith('chromium-') && !x.includes('headless')).pop();
  const b = await chromium.launch({ executablePath: `/opt/pw-browsers/${d}/chrome-linux/chrome` });
  const ctx = await b.newContext({ ...devices['iPhone 13'], deviceScaleFactor: 3 });
  await ctx.addInitScript(() => { try { localStorage.setItem('user', '{:theme "dark-theme"}'); } catch (_) {} });
  await ctx.route(/fonts\.(googleapis|gstatic)\.com/, async r => r.fulfill({ response: await r.fetch() }));
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'networkidle' });
  const c = p.locator('#cookie-btn');
  await c.waitFor({ timeout: 5000 }).then(() => c.click()).catch(() => {});
  await c.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  await p.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
  await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(400);
  await p.screenshot({ path: 'round16/bar.png', clip: { x: 0, y: 0, width: 390, height: 52 } });
  const ALIGN = `
    .app-header-bar .w-100-p { padding-left: 10px !important; padding-right: 10px !important; }
    .app-header-menu { padding-left: 8px !important; padding-right: 8px !important; }
    .flex-wrap.edge-align-buttons.edge-align-buttons { margin-left: 5px !important; }`;
  for (const state of ['now', 'aligned']) {
    if (state === 'aligned') {
      await p.evaluate(css => {
        document.querySelector('h1').closest('.flex-wrap').children[1].classList.add('edge-align-buttons');
        const s = document.createElement('style'); s.textContent = css; document.head.appendChild(s);
      }, ALIGN);
      await p.waitForTimeout(300);
      await p.screenshot({ path: 'round16/bar-aligned.png', clip: { x: 0, y: 0, width: 390, height: 52 } });
    }
    const edges = await p.evaluate(() => {
      document.querySelectorAll('.edge-layer').forEach(e => e.remove());
      // the ink, not the box: a text's left edge is where its first glyph starts
      const ink = e => { const r = document.createRange(); r.selectNodeContents(e); return r.getBoundingClientRect().left; };
      const list = [
        ['logo', document.querySelector('.app-header-bar img').getBoundingClientRect().left, '#ff5c5c'],
        ['tabs', document.querySelector('.header-tab').getBoundingClientRect().left, '#3ddc84'],
        ['title', ink(document.querySelector('h1')), '#4da3ff'],
        ['buttons', document.querySelector('h1').closest('.flex-wrap').children[1].firstElementChild.getBoundingClientRect().left, '#c084fc'],
        ['panel', document.querySelector('.builder-tabs').parentElement.getBoundingClientRect().left, '#ff8fd1'],
      ];
      const layer = document.createElement('div');
      layer.className = 'edge-layer';
      layer.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:99999';
      list.forEach(([, x, color]) => {
        const line = document.createElement('div');
        line.style.cssText = `position:absolute;top:0;bottom:0;left:${x - 0.5}px;width:1px;background:${color}`;
        layer.append(line);
      });
      document.body.appendChild(layer);
      return list.map(([n, x]) => `${n} ${Math.round(x)}`);
    });
    console.log(state, edges.join(', '));
    await p.screenshot({ path: `round16/edges-${state}.png`, clip: { x: 0, y: 0, width: 390, height: 600 } });
    await p.screenshot({ path: `round16/strip-${state}.png`, clip: { x: 0, y: 0, width: 70, height: 600 } });
  }
  await b.close();
})().catch(e => { console.error(e); process.exit(1); });
