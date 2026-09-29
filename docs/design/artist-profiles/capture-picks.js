// The four site icons an artist could pick, in coral and teal, captured in the
// drawer credit and on the profile card. Injected at capture time; no source
// changes.
//
//   NODE_PATH=$(npm root -g) node docs/design/artist-profiles/capture-picks.js <icon-dir> <out-dir>

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const [ICONS, OUT] = [process.argv[2], process.argv[3] || 'pick-shots'];
const findChrome = () => {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
  return path.join(base, dir, 'chrome-linux', 'chrome');
};
const uri = f => 'data:image/svg+xml;base64,' + fs.readFileSync(path.join(ICONS, f + '.svg')).toString('base64');
const mask = (u, color, size) =>
  `<span class="lk-ico" style="width:${size}px;height:${size}px;color:${color};-webkit-mask-image:url(${u});mask-image:url(${u})"></span>`;

const PICKS = ['ph-browser-fill', 'ph-house-fill', 'ph-compass-fill', 'ph-sparkle-fill'];
const COLORS = { coral: '#ff6b57', teal: '#1fb5a3' };

const CARD_CSS = `
.mk-card { max-width: 520px; margin: 0 auto; padding: 26px 26px 20px; background: #131924;
  border: 1px solid rgba(240,161,0,0.16); border-radius: 12px; display: flex; flex-direction: column; gap: 18px; }
.ap-root.light-theme .mk-card { background: #fff; border-color: rgba(0,0,0,0.10); }
.mk-head { display: flex; flex-direction: column; gap: 10px; text-align: center; }
.mk-bio { margin: 0; font: italic 13px/1.5 'Open Sans', sans-serif; color: var(--lk-dim); }
.mk-links { display: flex; flex-direction: column; gap: 8px; }
.mk-link { display: flex; align-items: center; gap: 10px; padding: 11px 14px; border-radius: 5px;
  border: 1px solid rgba(255,255,255,0.10); color: #ebeef4; text-decoration: none; font: 600 13px/1 'Open Sans', sans-serif; }
.mk-link .host { margin-left: auto; font-weight: 400; font-size: 11px; color: var(--lk-dim); }
.mk-link.feature { border-color: #f0a100; background: rgba(240,161,0,0.07); }
.ap-root.light-theme .mk-link { border-color: rgba(0,0,0,0.14); color: #363636; }
.ap-root.light-theme .mk-link.feature { border-color: #33658A; background: rgba(51,101,138,0.06); }`;

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const theme of ['dark', 'light']) {
    const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
    await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme + '-theme');
    const page = await ctx.newPage();
    const settle = async () => {
      const c = page.locator('#cookie-btn');
      if (await c.count()) await c.click({ timeout: 3000 }).catch(() => {});
      await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
    };

    // credit
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
    await settle();
    const desc = page.locator('.builder-tab', { hasText: /^Description$/ }).first();
    if (await desc.count()) { await desc.click(); await page.waitForTimeout(300); }
    await page.locator('.pl-launcher').click();
    await page.locator('.pl-drawer').waitFor({ state: 'visible' });
    await page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
    await page.mouse.move(5, 5);
    await page.waitForTimeout(500);
    for (const icon of PICKS) for (const [cn, color] of Object.entries(COLORS)) {
      await page.evaluate(h => { document.querySelector('.pl-attribution .lk-mark').innerHTML = h; }, mask(uri(icon), color, 12));
      await page.locator('.pl-attribution').screenshot({ path: path.join(OUT, `credit-${theme}-${icon}-${cn}.png`) });
    }

    // card
    await page.goto(`${BASE}/artists/fusspot`, { waitUntil: 'networkidle' });
    await settle();
    await page.addStyleTag({ content: CARD_CSS });
    for (const icon of PICKS) for (const [cn, color] of Object.entries(COLORS)) {
      const html = `
        <div class="mk-head">
          <div class="lk-cap">Portrait artist</div>
          <div class="lk-row"><span class="lk-rule l"></span><h1 class="ap-name">Fusspot</h1><span class="lk-rule r"></span></div>
          <p class="mk-bio">[The artist's own short bio, up to 280 characters.]</p>
        </div>
        <div class="mk-links">
          <a class="mk-link feature" href="#">${mask(uri(icon), color, 16)}Site<span class="host">fusspot.rip</span></a>
          <a class="mk-link" href="#">${mask(uri('now-twitch'), '#9146ff', 16)}Twitch<span class="host">twitch.tv/fusspot</span></a>
        </div>`;
      await page.evaluate(h => { const c = document.querySelector('.ap-card, .mk-card'); c.className = 'mk-card'; c.innerHTML = h; }, html);
      await page.waitForTimeout(100);
      await page.locator('.mk-card').screenshot({ path: path.join(OUT, `card-${theme}-${icon}-${cn}.png`) });
    }
    await ctx.close();
    console.log('  ' + theme + ' done');
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
