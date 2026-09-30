// The two-step colour picker: five colours, then how the colour looks in the
// light theme -- a deeper shade of itself, or a paired colour. Drawn inside
// the running site in place of the profile card, since the account page it
// belongs on doesn't exist yet. Injected at capture time; no source changes.
//
//   NODE_PATH=$(npm root -g) node docs/design/artist-profiles/capture-picker.js <icon-dir> <out-dir>

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const [ICONS, OUT] = [process.argv[2], process.argv[3] || 'picker-shots'];
const findChrome = () => {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
  return path.join(base, dir, 'chrome-linux', 'chrome');
};
const uri = f => 'data:image/svg+xml;base64,' + fs.readFileSync(path.join(ICONS, f + '.svg')).toString('base64');
const mark = (u, color, size) =>
  `<span class="lk-ico" style="width:${size}px;height:${size}px;color:${color};-webkit-mask-image:url(${u});mask-image:url(${u})"></span>`;

// name: [dark, light as a deeper shade, light as a paired colour]
const COLORS = {
  yellow: ['#ffd21a', '#9a7400', '#33658A'],
  coral:  ['#ff6b57', '#d9432f', '#0f7c80'],
  teal:   ['#1fb5a3', '#0f8577', '#b4532a'],
  rose:   ['#f06aa6', '#c2386f', '#1f7a4a'],
  green:  ['#43b85c', '#2f8a44', '#8e3a78']
};
const ICON_SET = [['ph-browser-fill', 'Browser'], ['ph-house-fill', 'House'], ['ph-compass-fill', 'Compass'], ['ph-sparkle-fill', 'Sparkle']];

const CSS = `
.pk { max-width: 520px; margin: 0 auto; padding: 22px 24px; background: #131924; border: 1px solid rgba(255,255,255,0.08);
  border-radius: 12px; display: flex; flex-direction: column; gap: 18px; font-family: 'Open Sans', sans-serif; color: #ebeef4; }
.ap-root.light-theme .pk { background: #fff; border-color: rgba(0,0,0,0.10); color: #363636; }
.pk h2 { margin: 0; font: 700 16px/1.3 'Open Sans', sans-serif; }
.pk .row { display: flex; flex-direction: column; gap: 9px; }
.pk .lbl { font: 600 10px/1 'Open Sans', sans-serif; letter-spacing: .14em; text-transform: uppercase; color: var(--lk-dim); }
.pk .icons, .pk .swatches { display: flex; flex-wrap: wrap; gap: 8px; }
.pk .ib { display: flex; flex-direction: column; align-items: center; gap: 6px; width: 72px; padding: 10px 0 8px; border-radius: 6px;
  border: 1px solid rgba(255,255,255,0.10); font: 600 11px/1 'Open Sans', sans-serif; color: var(--lk-dim); }
.ap-root.light-theme .pk .ib { border-color: rgba(0,0,0,0.14); }
.pk .ib.on { border-color: #f0a100; color: #ebeef4; box-shadow: 0 0 0 2px rgba(240,161,0,0.25); }
.ap-root.light-theme .pk .ib.on { border-color: #33658A; color: #363636; box-shadow: 0 0 0 2px rgba(51,101,138,0.2); }
.pk .sw { width: 30px; height: 30px; border-radius: 50%; border: 1px solid rgba(255,255,255,0.25); }
.ap-root.light-theme .pk .sw { border-color: rgba(0,0,0,0.2); }
.pk .sw.on { box-shadow: 0 0 0 2px #131924, 0 0 0 4px #f0a100; }
.ap-root.light-theme .pk .sw.on { box-shadow: 0 0 0 2px #fff, 0 0 0 4px #33658A; }
.pk .swl { display: flex; flex-direction: column; align-items: center; gap: 6px; font: 600 10px/1 'Open Sans', sans-serif; color: var(--lk-dim); width: 46px; }
.pk .mode { display: inline-flex; border: 1px solid rgba(255,255,255,0.12); border-radius: 6px; overflow: hidden; align-self: flex-start; }
.ap-root.light-theme .pk .mode { border-color: rgba(0,0,0,0.14); }
.pk .mode span { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; font: 600 12px/1 'Open Sans', sans-serif; color: var(--lk-dim); }
.pk .mode span.on { background: rgba(240,161,0,0.10); color: #ffcc5e; }
.ap-root.light-theme .pk .mode span.on { background: rgba(51,101,138,0.08); color: #33658A; }
.pk .mode i { width: 14px; height: 14px; border-radius: 50%; display: inline-block; }
.pk .note { margin: 0; font-size: 12px; color: var(--lk-dim); }
.pk .prev { border-top: 1px solid rgba(255,255,255,0.06); padding-top: 14px; display: flex; flex-direction: column; gap: 7px; }
.ap-root.light-theme .pk .prev { border-top-color: rgba(0,0,0,0.08); }
.pk .lk-name { text-decoration: underline dotted var(--lk-under); text-decoration-thickness: 1px; text-underline-offset: 3px; }`;

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const theme of ['dark', 'light']) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
    await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme + '-theme');
    const page = await ctx.newPage();
    await page.goto(`${BASE}/artists/fusspot`, { waitUntil: 'networkidle' });
    const c = page.locator('#cookie-btn');
    if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
    await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' + CSS });
    for (const [icon, color, mode] of [['ph-browser-fill', 'teal', 'shade'], ['ph-browser-fill', 'teal', 'paired'],
                                       ['ph-sparkle-fill', 'yellow', 'shade'], ['ph-sparkle-fill', 'yellow', 'paired']]) {
      const li = mode === 'shade' ? 1 : 2;
      const [d, s, p] = COLORS[color];
      const shown = theme === 'dark' ? d : (mode === 'shade' ? s : p);
      const html = `
        <h2>Your artist page</h2>
        <div class="row"><div class="lbl">Icon for your site</div><div class="icons">
          ${ICON_SET.map(([f, l]) => `<div class="ib${f === icon ? ' on' : ''}">${mark(uri(f), f === icon ? shown : 'currentColor', 18)}${l}</div>`).join('')}
        </div></div>
        <div class="row"><div class="lbl">Colour</div><div class="swatches">
          ${Object.entries(COLORS).map(([n, v]) => `<div class="swl"><span class="sw${n === color ? ' on' : ''}" style="background:linear-gradient(135deg, ${v[0]} 0 50%, ${v[li]} 50% 100%)"></span>${n}</div>`).join('')}
        </div></div>
        <div class="row"><div class="lbl">In the light theme</div>
          <div class="mode">
            <span class="${mode === 'shade' ? 'on' : ''}"><i style="background:${s}"></i>A deeper shade</span>
            <span class="${mode === 'paired' ? 'on' : ''}"><i style="background:${p}"></i>A paired colour</span>
          </div>
          <p class="note">The left half of each swatch is the dark theme; the right half is the light theme. Visitors see whichever matches theirs.</p>
        </div>
        <div class="prev"><div class="lbl">Preview</div>
          <div class="lk-cap">Art by</div>
          <div class="lk-row"><span class="lk-rule l"></span>${mark(uri(icon), shown, 12)}<span class="lk-name">Fusspot</span>${mark(uri('now-twitch'), '#9146ff', 12)}<span class="lk-rule r"></span></div>
        </div>`;
      await page.evaluate(h => { const el = document.querySelector('.ap-card, .pk'); el.className = 'pk'; el.innerHTML = h; }, html);
      await page.waitForTimeout(120);
      await page.locator('.pk').screenshot({ path: path.join(OUT, `picker-${theme}-${icon}-${color}-${mode}.png`) });
    }
    await ctx.close();
    console.log('  ' + theme + ' done');
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
