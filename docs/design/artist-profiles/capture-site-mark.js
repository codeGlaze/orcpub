// The artist's site mark in the credit: filled icons x colours, plus a badge
// treatment, each captured beside the real Twitch mark in the running drawer.
//
// Swaps the credit's left mark at capture time with a data-URI mask (or, for
// the badge, a coloured tile holding a white glyph). Nothing in the source
// changes.
//
//   lein fig:build && lein e2e-server
//   NODE_PATH=$(npm root -g) node docs/design/artist-profiles/capture-site-mark.js <icon-dir> <out-dir>

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const [ICONS, OUT] = [process.argv[2], process.argv[3] || 'mark-shots'];

function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
  return path.join(base, dir, 'chrome-linux', 'chrome');
}
const uri = f => 'data:image/svg+xml;base64,' + fs.readFileSync(path.join(ICONS, f)).toString('base64');

const ICON_SET = ['now-site', 'ph-browser-fill', 'ph-app-window-fill', 'ph-house-fill',
                  'ph-compass-fill', 'ph-sparkle-fill', 'gi-lorc-quill-ink'];
const BADGE_SET = ['ph-browser-fill', 'ph-house-fill', 'gi-lorc-quill-ink'];
const COLORS = { amber: '#f0a100', coral: '#ff6b57', teal: '#1fb5a3', rose: '#e6488f', green: '#43b85c' };

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const theme of ['dark', 'light']) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
    await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme + '-theme');
    const page = await ctx.newPage();
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
    const cookie = page.locator('#cookie-btn');
    if (await cookie.count()) { await cookie.click({ timeout: 3000 }).catch(() => {}); }
    await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    const desc = page.locator('.builder-tab', { hasText: /^Description$/ }).first();
    if (await desc.count()) { await desc.click(); await page.waitForTimeout(300); }
    await page.locator('.pl-launcher').click();
    await page.locator('.pl-drawer').waitFor({ state: 'visible' });
    await page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
    await page.mouse.move(5, 5);
    await page.waitForTimeout(500);

    const setMark = (html) => page.evaluate(h => {
      document.querySelector('.pl-attribution .lk-mark').innerHTML = h;
    }, html);
    const shoot = (name) => page.locator('.pl-attribution .lk-row').screenshot({ path: path.join(OUT, `${theme}-${name}.png`) });

    for (const icon of ICON_SET) {
      for (const [cname, color] of Object.entries(COLORS)) {
        const u = uri(icon + '.svg');
        await setMark(`<span class="lk-ico" style="color:${color};-webkit-mask-image:url(${u});mask-image:url(${u})"></span>`);
        await shoot(`${icon}-${cname}`);
      }
    }
    for (const icon of BADGE_SET) {
      for (const [cname, color] of Object.entries(COLORS)) {
        const u = uri(icon + '.svg');
        await setMark(`<span style="display:grid;place-items:center;width:14px;height:14px;border-radius:3px;background:${color}">` +
          `<span style="display:block;width:10px;height:10px;background:#fff;-webkit-mask:url(${u}) center/contain no-repeat;mask:url(${u}) center/contain no-repeat"></span></span>`);
        await shoot(`badge-${icon}-${cname}`);
      }
    }
    await ctx.close();
    console.log('  ' + theme + ' done');
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
