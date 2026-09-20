const { chromium } = require('playwright');
const { suppressOverlays } = require('./test/browser/lib/orcbrew-import');
const fs = require('fs'), path = require('path');
const D = '/tmp/claude-0/-home-user-orcpub/6676a676-d0da-5122-b0d1-b52816592841/scratchpad/shots';
fs.mkdirSync(D, { recursive: true });
const BASE = 'http://localhost:8890';
const PHONE = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';
  const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();
  return d?path.join(b,d,'chrome-linux','chrome'):undefined;}
const clickIfVisible = async (l,{timeout=2500}={}) => { try{await l.click({timeout});return true}catch{return false} };

async function builder(ctx) {
  const p = await ctx.newPage();
  await p.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  await p.waitForSelector('#app', { timeout: 30000 });
  await clickIfVisible(p.locator('#cookie-btn'));
  return p;
}
const shot = async (loc, name, pad = 10) => {
  await loc.scrollIntoViewIfNeeded(); await loc.page().waitForTimeout(250);
  await loc.screenshot({ path: `${D}/${name}.png` });
  console.log('  ', name);
};

(async () => {
  const br = await chromium.launch({ executablePath: findChrome() });

  // ---------------- desktop ----------------
  const dctx = await br.newContext({ viewport: { width: 1400, height: 1000 }, deviceScaleFactor: 2 });
  await suppressOverlays(dctx);
  let p = await builder(dctx);

  // 1. empty summary slot + pencil, before anything exists
  await shot(p.locator('.pl-thumb-empty').first(), '01-empty-slot');

  // 2. the launcher in the Description tab
  await p.locator('.builder-tab', { hasText: /^Description$/ }).first().click();
  await p.waitForTimeout(400);
  await shot(p.locator('.pl-launcher').locator('..').locator('..'), '02-launcher-in-description');

  // 3. the drawer, freshly randomized
  await p.locator('.pl-launcher').click();
  await p.locator('.pl-drawer').waitFor({ state: 'visible' });
  await p.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
  await p.waitForTimeout(600);
  await shot(p.locator('.pl-drawer'), '03-drawer-desktop');

  // 4. colour panel open, presets + per-piece shading
  const hair = p.locator('.pl-slot').filter({ hasText: 'Hair' }).first();
  await hair.locator('.pl-slot-swatch').click(); await p.waitForTimeout(250);
  await p.locator('.pl-slot-panel .pl-preset').nth(3).click(); await p.waitForTimeout(250);
  const skin = p.locator('.pl-slot').filter({ hasText: 'Skin' }).first();
  await skin.locator('.pl-slot-swatch').click(); await p.waitForTimeout(250);
  await p.locator('.pl-slot-panel .pl-preset').nth(2).click(); await p.waitForTimeout(300);
  await shot(p.locator('.pl-canvas-side'), '04-colour-panel');

  // 5. gap swatches in a picker
  await shot(p.locator('.pl-picker').filter({ hasText: 'Head' }).first(), '05-gap-markers');

  // 6. attribution strip in the drawer foot
  await shot(p.locator('.pl-drawer-foot'), '06-attribution');

  await p.locator('.pl-btn-primary', { hasText: 'Save portrait' }).click();
  await p.waitForTimeout(700);

  // 7. the composed summary thumbnail with its credit + pencil
  await shot(p.locator('.pl-thumb-credit').locator('..'), '07-summary-thumbnail');

  // 8. the inline Portrait tab
  await p.locator('.builder-tab', { hasText: /^Portrait$/ }).first().click();
  await p.waitForTimeout(500);
  await shot(p.locator('.pl-inline'), '08-portrait-tab-desktop');

  // 9. light theme, inline
  const t = p.locator('div.pointer', { hasText: 'Light Theme' }).first();
  if (await t.count()) { await t.click(); await p.waitForTimeout(600);
    await shot(p.locator('.pl-inline'), '09-portrait-tab-light'); }

  // ---------------- phone ----------------
  const pctx = await br.newContext({ userAgent: PHONE, isMobile: true, hasTouch: true,
                                     viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
  await suppressOverlays(pctx);
  const ph = await builder(pctx);
  await ph.locator('.builder-tab', { hasText: /^Portrait$/ }).first().click();
  await ph.waitForTimeout(500);
  await ph.locator('.pl-inline .pl-btn-primary', { hasText: 'Randomize' }).click();
  await ph.waitForTimeout(700);
  await ph.screenshot({ path: `${D}/10-portrait-tab-phone.png` });
  console.log('   10-portrait-tab-phone');
  // drawer on a phone
  await ph.locator('.builder-tab', { hasText: /^Description$/ }).first().click();
  await ph.waitForTimeout(400);
  await ph.locator('.pl-launcher').click();
  await ph.locator('.pl-drawer').waitFor({ state: 'visible' });
  await ph.waitForTimeout(500);
  await ph.screenshot({ path: `${D}/11-drawer-phone.png` });
  console.log('   11-drawer-phone');

  await br.close();
})().catch(e => { console.error(String(e).slice(0, 400)); process.exit(1); });
