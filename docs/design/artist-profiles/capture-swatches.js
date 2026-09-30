// Themed colour pairs for the artist's site mark, and a picker with split
// swatches. The picker is drawn inside the real site (on /artists/fusspot, in
// place of the card) because the account page it belongs on doesn't exist
// yet. The credit rows are the real drawer credit. Injected at capture time;
// no source changes.
//
//   NODE_PATH=$(npm root -g) node docs/design/artist-profiles/capture-swatches.js <icon-dir> <out-dir>

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const [ICONS, OUT] = [process.argv[2], process.argv[3] || 'swatch-shots'];
const findChrome = () => {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
  return path.join(base, dir, 'chrome-linux', 'chrome');
};
const uri = f => 'data:image/svg+xml;base64,' + fs.readFileSync(path.join(ICONS, f + '.svg')).toString('base64');

// [dark-theme colour, light-theme colour]; each >= 4.3:1 on its own ground
// PAIRS_JSON and SELECTED_JSON override these, to capture another set.
const PAIRS = process.env.PAIRS_JSON ? JSON.parse(process.env.PAIRS_JSON) : {
  coral:  ['#ff6b57', '#d9432f'],
  teal:   ['#1fb5a3', '#0f8577'],
  yellow: ['#ffd21a', '#9a7400'],
  rose:   ['#f06aa6', '#c2386f'],
  green:  ['#43b85c', '#2f8a44']
};
const SELECTED = process.env.SELECTED_JSON ? JSON.parse(process.env.SELECTED_JSON)
  : [['ph-browser-fill', 'teal'], ['ph-sparkle-fill', 'yellow']];
const ICON_SET = [['ph-browser-fill', 'Browser'], ['ph-house-fill', 'House'], ['ph-compass-fill', 'Compass'], ['ph-sparkle-fill', 'Sparkle']];

const mark = (u, color, size) =>
  `<span class="lk-ico" style="width:${size}px;height:${size}px;color:${color};-webkit-mask-image:url(${u});mask-image:url(${u})"></span>`;

const PANEL_CSS = `
.pk { max-width: 520px; margin: 0 auto; padding: 22px 24px; background: #131924; border: 1px solid rgba(255,255,255,0.08);
  border-radius: 12px; display: flex; flex-direction: column; gap: 18px; font-family: 'Open Sans', sans-serif; color: #ebeef4; }
.ap-root.light-theme .pk { background: #fff; border-color: rgba(0,0,0,0.10); color: #363636; }
.pk h2 { margin: 0; font: 700 16px/1.3 'Open Sans', sans-serif; }
.pk .row { display: flex; flex-direction: column; gap: 8px; }
.pk .lbl { font: 600 10px/1 'Open Sans', sans-serif; letter-spacing: .14em; text-transform: uppercase; color: var(--lk-dim); }
.pk .icons, .pk .swatches { display: flex; flex-wrap: wrap; gap: 8px; }
.pk .ib { display: flex; flex-direction: column; align-items: center; gap: 6px; width: 72px; padding: 10px 0 8px; border-radius: 6px;
  border: 1px solid rgba(255,255,255,0.10); font: 600 11px/1 'Open Sans', sans-serif; color: var(--lk-dim); }
.ap-root.light-theme .pk .ib { border-color: rgba(0,0,0,0.14); }
.pk .ib.on { border-color: #f0a100; color: #ebeef4; box-shadow: 0 0 0 2px rgba(240,161,0,0.25); }
.ap-root.light-theme .pk .ib.on { border-color: #33658A; color: #363636; box-shadow: 0 0 0 2px rgba(51,101,138,0.2); }
.pk .sw { width: 30px; height: 30px; border-radius: 50%; position: relative; border: 1px solid rgba(255,255,255,0.25); }
.ap-root.light-theme .pk .sw { border-color: rgba(0,0,0,0.2); }
.pk .sw.on { box-shadow: 0 0 0 2px #131924, 0 0 0 4px #f0a100; }
.ap-root.light-theme .pk .sw.on { box-shadow: 0 0 0 2px #fff, 0 0 0 4px #33658A; }
.pk .swl { display: flex; flex-direction: column; align-items: center; gap: 6px; font: 600 10px/1 'Open Sans', sans-serif; color: var(--lk-dim); width: 46px; }
.pk .note { margin: 0; font-size: 12px; color: var(--lk-dim); }
.pk .prev { border-top: 1px solid rgba(255,255,255,0.06); padding-top: 14px; display: flex; flex-direction: column; gap: 7px; }
.ap-root.light-theme .pk .prev { border-top-color: rgba(0,0,0,0.08); }
.pk .lk-name { text-decoration: underline dotted var(--lk-under); text-decoration-thickness: 1px; text-underline-offset: 3px; }`;

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const theme of ['dark', 'light']) {
    const ti = theme === 'dark' ? 0 : 1;
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
    await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme + '-theme');
    const page = await ctx.newPage();
    const settle = async () => {
      const c = page.locator('#cookie-btn');
      if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
      await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    };

    // --- the picker, inside the site ---
    await page.goto(`${BASE}/artists/fusspot`, { waitUntil: 'networkidle' });
    await settle();
    await page.addStyleTag({ content: PANEL_CSS });
    for (const [selIcon, selColor] of SELECTED) {
      const c = PAIRS[selColor][ti];
      const html = `
        <h2>Your artist page</h2>
        <div class="row"><div class="lbl">Icon for your site</div><div class="icons">
          ${ICON_SET.map(([f, l]) => `<div class="ib${f === selIcon ? ' on' : ''}">${mark(uri(f), f === selIcon ? c : 'currentColor', 18)}${l}</div>`).join('')}
        </div></div>
        <div class="row"><div class="lbl">Colour</div><div class="swatches">
          ${Object.entries(PAIRS).map(([n, [d, l]]) => `<div class="swl"><span class="sw${n === selColor ? ' on' : ''}" style="background:linear-gradient(135deg, ${d} 0 50%, ${l} 50% 100%)"></span>${n}</div>`).join('')}
        </div>
        <p class="note">Each colour has a version for the dark theme and one for the light theme. Visitors see whichever matches their theme.</p></div>
        <div class="prev"><div class="lbl">Preview</div>
          <div class="lk-cap">Art by</div>
          <div class="lk-row"><span class="lk-rule l"></span>${mark(uri(selIcon), c, 12)}<span class="lk-name">Fusspot</span>${mark(uri('now-twitch'), '#9146ff', 12)}<span class="lk-rule r"></span></div>
        </div>`;
      await page.evaluate(h => { const el = document.querySelector('.ap-card, .pk'); el.className = 'pk'; el.innerHTML = h; }, html);
      await page.waitForTimeout(120);
      await page.locator('.pk').screenshot({ path: path.join(OUT, `panel-${theme}-${selIcon}-${selColor}.png`) });
    }

    // --- the real credit, each pair ---
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
    await settle();
    const desc = page.locator('.builder-tab', { hasText: /^Description$/ }).first();
    if (await desc.count()) { await desc.click(); await page.waitForTimeout(300); }
    await page.locator('.pl-launcher').click();
    await page.locator('.pl-drawer').waitFor({ state: 'visible' });
    await page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
    await page.mouse.move(5, 5);
    await page.waitForTimeout(500);
    for (const [f] of ICON_SET) for (const [n, pair] of Object.entries(PAIRS)) {
      await page.evaluate(h => { document.querySelector('.pl-attribution .lk-mark').innerHTML = h; }, mark(uri(f), pair[ti], 12));
      await page.locator('.pl-attribution .lk-row').screenshot({ path: path.join(OUT, `credit-${theme}-${f}-${n}.png`) });
    }
    await ctx.close();
    console.log('  ' + theme + ' done');
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
