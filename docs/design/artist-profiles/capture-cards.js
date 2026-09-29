// Artist card layouts, captured from the running app.
//
// Loads the real /artists/fusspot page, then replaces the card's markup with
// each candidate layout just before the screenshot -- the method
// docs/design/portrait-credit used -- so what is captured is the site, not a
// drawing of it. Nothing in the source changes.
//
// The examples are what the proposal would serve: one flattened picture per
// portrait, shrunk to 240x300, a watermark tiled across the whole image, saved
// as a quality-0.6 JPEG. Made here in the browser from the page's own
// composites; the real version would render them on the server.
//
//   lein fig:build && lein e2e-server
//   NODE_PATH=$(npm root -g) node docs/design/artist-profiles/capture-cards.js <out-dir>

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const OUT = process.argv[2] || 'card-shots';

function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
  return path.join(base, dir, 'chrome-linux', 'chrome');
}

// Shrink, watermark, compress. Runs in the page.
async function degrade(page, pngBuffers) {
  return page.evaluate(async (b64s) => {
    const out = [];
    for (const b64 of b64s) {
      const img = new Image();
      img.src = 'data:image/png;base64,' + b64;
      await img.decode();
      const w = 240, h = 300;
      const c = document.createElement('canvas');
      c.width = w; c.height = h;
      const g = c.getContext('2d');
      g.drawImage(img, 0, 0, w, h);
      // Tiled across the whole picture, through the figure, so no single crop
      // or small inpaint removes it.
      g.save();
      g.translate(w / 2, h / 2);
      g.rotate(-Math.PI / 6);
      g.font = '600 11px "Open Sans", sans-serif';
      g.textAlign = 'center';
      const mark = 'fusspot · orcpub';
      for (let y = -h; y < h; y += 34) {
        for (let x = -w; x < w; x += 118) {
          const dx = x + ((y / 34) % 2 ? 59 : 0);
          g.lineWidth = 2; g.strokeStyle = 'rgba(0,0,0,0.18)'; g.strokeText(mark, dx, y);
          g.fillStyle = 'rgba(255,255,255,0.26)'; g.fillText(mark, dx, y);
        }
      }
      g.restore();
      out.push(c.toDataURL('image/jpeg', 0.6));
    }
    return out;
  }, pngBuffers.map(b => b.toString('base64')));
}

const CSS = `
.mk-card { max-width: 560px; margin: 0 auto; padding: 28px 28px 22px; background: #131924;
  border: 1px solid rgba(240,161,0,0.16); border-radius: 12px; display: flex; flex-direction: column; gap: 20px; }
.ap-root.light-theme .mk-card { background: #fff; border-color: rgba(0,0,0,0.10); }
.mk-head { display: flex; flex-direction: column; gap: 10px; text-align: center; }
.mk-bio { margin: 0; font: italic 13px/1.5 'Open Sans', sans-serif; color: var(--lk-dim); }
.mk-pill { align-self: center; font: 600 10px/1 'Open Sans', sans-serif; letter-spacing: .12em; text-transform: uppercase;
  color: var(--lk-dim); border: 1px dashed var(--lk-rule); padding: 5px 9px; border-radius: 99px; }
.mk-links { display: flex; flex-direction: column; gap: 8px; }
.mk-link { display: flex; align-items: center; gap: 10px; padding: 11px 14px; border-radius: 5px;
  border: 1px solid rgba(255,255,255,0.10); color: #ebeef4; text-decoration: none;
  font: 600 13px/1 'Open Sans', sans-serif; }
.mk-link .host { margin-left: auto; font-weight: 400; font-size: 11px; color: var(--lk-dim); }
.mk-link.feature { border-color: #f0a100; background: rgba(240,161,0,0.07); }
.ap-root.light-theme .mk-link { border-color: rgba(0,0,0,0.14); color: #363636; }
.ap-root.light-theme .mk-link.feature { border-color: #33658A; background: rgba(51,101,138,0.06); }
.mk-link .lk-ico { width: 15px; height: 15px; }
.mk-ex { display: flex; justify-content: center; gap: 10px; }
.mk-ex img { width: 150px; height: 188px; border-radius: 8px; display: block; border: 1px solid rgba(240,161,0,0.16); }
.ap-root.light-theme .mk-ex img { border-color: rgba(0,0,0,0.12); }
.mk-excap { text-align: center; font: italic 12px/1.4 'Vollkorn', Georgia, serif; color: var(--lk-dim); margin-top: -8px; }
.mk-pieces { text-align: center; font-size: 12px; color: var(--lk-dim); margin: 0; }
.mk-pieces a { color: inherit; text-decoration: underline dotted var(--lk-under); text-underline-offset: 3px; }
.mk-foot { margin: 0; padding-top: 14px; border-top: 1px solid rgba(255,255,255,0.06); font-size: 12px; color: var(--lk-dim); text-align: center; }
.ap-root.light-theme .mk-foot { border-top-color: rgba(0,0,0,0.08); }
/* split */
.mk-split { display: grid; grid-template-columns: 150px 1fr; gap: 22px; align-items: start; }
.mk-split .mk-head { text-align: left; }
.mk-split .mk-pill { align-self: flex-start; }
.mk-split .lk-row .lk-rule.l { display: none; }
.mk-split .ap-name { font-size: 34px; }
@media (max-width: 560px) {
  .mk-card { padding: 22px 16px 18px; }
  .mk-split { grid-template-columns: 1fr; }
  .mk-split .mk-ex { justify-content: center; }
  .mk-split .mk-head { text-align: center; } .mk-split .mk-pill { align-self: center; }
  .mk-split .lk-row .lk-rule.l { display: block; }
}`;

function ico(icon, color) {
  return `<span class="lk-ico" aria-hidden="true" style="color:${color};-webkit-mask-image:url(/image/social/${icon}.svg);mask-image:url(/image/social/${icon}.svg)"></span>`;
}

const HEAD = `
  <div class="lk-cap">Portrait artist</div>
  <div class="lk-row"><span class="lk-rule l"></span><h1 class="ap-name">Fusspot</h1><span class="lk-rule r"></span></div>
  <p class="mk-bio">[The artist's own short bio, up to 280 characters.]</p>
  <span class="mk-pill">[Commission status]</span>`;

const LINKS = `
  <div class="mk-links">
    <a class="mk-link feature" href="#">${ico('site', '#f0a100')}Site<span class="host">fusspot.rip</span></a>
    <a class="mk-link" href="#">${ico('twitch', '#9146ff')}Twitch<span class="host">twitch.tv/fusspot</span></a>
  </div>`;

const PIECES = `<p class="mk-pieces">28 pieces in the portrait maker: heads, hair, eyes and 7 more layers.
  <a href="#">Try them in a portrait ›</a></p>`;

const FOOT = `<p class="mk-foot">Every portrait made with these pieces credits Fusspot.</p>`;

const LAYOUTS = {
  // A: one column, links first, two small examples after
  column: (ex) => `
    <div class="mk-head">${HEAD}</div>
    ${LINKS}
    <div class="mk-ex"><img src="${ex[0]}" alt="Example portrait"><img src="${ex[1]}" alt="Example portrait"></div>
    <div class="mk-excap">Made with Fusspot's pieces</div>
    ${FOOT}`,
  // B: a Carrd-style split card, one example beside the name and links
  split: (ex) => `
    <div class="mk-split">
      <div class="mk-ex"><img src="${ex[0]}" alt="Example portrait"></div>
      <div style="display:flex;flex-direction:column;gap:16px;min-width:0">
        <div class="mk-head">${HEAD}</div>
        ${LINKS}
      </div>
    </div>
    ${FOOT}`,
  // C: no pictures at all; the pieces are seen in use
  minimal: () => `
    <div class="mk-head">${HEAD}</div>
    ${LINKS}
    ${PIECES}
    ${FOOT}`
};

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const theme of ['dark-theme', 'light-theme']) {
    for (const [vw, vh, tag] of [[1280, 900, 'desktop'], [390, 844, 'phone']]) {
      const ctx = await browser.newContext({ viewport: { width: vw, height: vh }, deviceScaleFactor: 1 });
      await ctx.addInitScript(t => {
        try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {}
      }, theme);
      const page = await ctx.newPage();
      await page.goto(`${BASE}/artists/fusspot`, { waitUntil: 'networkidle' });
      await page.waitForSelector('.ap-example .portrait-layer');
      await page.waitForTimeout(600);
      // The cookie banner sits over the lower half of the card; accept it.
      const cookie = page.locator('#cookie-btn');
      if (await cookie.count()) { await cookie.click({ timeout: 3000 }).catch(() => {}); await page.waitForTimeout(300); }
      await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
      const frames = page.locator('.ap-example .ap-frame');
      const shots = [await frames.nth(0).screenshot(), await frames.nth(1).screenshot()];
      const ex = await degrade(page, shots);
      if (theme === 'dark-theme' && tag === 'desktop') {
        ex.forEach((d, i) => fs.writeFileSync(path.join(OUT, `example-${i}.jpg`), Buffer.from(d.split(',')[1], 'base64')));
      }
      await page.addStyleTag({ content: CSS });
      for (const [name, render] of Object.entries(LAYOUTS)) {
        await page.evaluate(html => {
          const card = document.querySelector('.ap-card, .mk-card');
          card.className = 'mk-card';
          card.innerHTML = html;
        }, render(ex));
        await page.waitForTimeout(250);
        const box = await page.locator('.ap-root').boundingBox();
        const file = path.join(OUT, `${name}-${theme.replace('-theme', '')}-${tag}.png`);
        await page.screenshot({ path: file, fullPage: true, clip: { x: 0, y: box.y, width: vw, height: box.height } });
        console.log('  ' + file);
      }
      await ctx.close();
    }
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
