// The compositor as a builder tab, in place rather than in the drawer.
//
// The drawer and the tab share one draft and one body component, so the
// things worth checking are the seams: that the tab seeds a draft with no
// open/close of its own, that popping the drawer from the tab carries the
// in-progress edits across instead of resetting them, and that Save here
// keeps the panel populated instead of emptying it.
//
//   lein fig:build && lein e2e-server
//   node test/browser/portrait_tab_e2e.js

const { chromium } = require('playwright');
const { suppressOverlays } = require('./lib/orcbrew-import');
const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';

// Same resolver the other probes use: the image ships a pinned Chromium whose
// build number will not match whatever playwright's npm package wants, so the
// bundled path does not exist. ORCPUB_CHROME overrides it for a one-off run.
// An untimed .click().catch() waits playwright's full 30s default and then throws
// the failure away -- silent, and the runner greps for it. See test/browser/README.md.
async function clickIfVisible(locator, { timeout = 2500 } = {}) {
  try { await locator.click({ timeout }); return true; }
  catch (_) { return false; }
}

function findChrome() {
  if (process.env.ORCPUB_CHROME) return process.env.ORCPUB_CHROME;
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = require('fs').readdirSync(base)
      .filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    if (dir) {
      const p = require('path').join(base, dir, 'chrome-linux', 'chrome');
      if (require('fs').existsSync(p)) return p;
    }
  } catch (_) {}
  return undefined;
}

const SHOT = process.env.ORCPUB_SHOT;

let failures = 0;
function check(label, ok, detail) {
  // PASS/FAIL prefixes, not 'ok': run-browser-probes.js counts these lines to
  // catch a probe that has quietly stopped asserting.
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail && !ok ? '  — ' + detail : ''}`);
}

// device-type comes from the USER AGENT, not the viewport, so a narrow window
// still renders the desktop two-column layout. Testing the phone layout means
// actually claiming to be a phone.
const PHONE_UA = 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 '
  + '(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';

async function openBuilder(ctx, width) {
  const page = await ctx.newPage();
  await page.setViewportSize({ width, height: 900 });
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  await page.waitForSelector('#app', { timeout: 30000 });
  const cookie = page.locator('#cookie-btn');
  if (await cookie.count()) { await clickIfVisible(cookie); await page.waitForTimeout(200); }
  return page;
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext();
  await suppressOverlays(ctx);  // by-hand runs get no preload
  const jsErrors = [];

  console.log(`\nportrait tab e2e -- ${BASE}\n`);

  // ---------- desktop ----------
  const page = await openBuilder(ctx, 1280);
  page.on('pageerror', e => jsErrors.push(String(e)));

  const tab = page.locator('.builder-tab', { hasText: /^Portrait$/ }).first();
  check('Portrait tab is in the builder tab bar', await tab.count() > 0);
  await tab.click();
  await page.waitForTimeout(400);

  const panel = page.locator('.pl-inline');
  check('tab renders the compositor in place', await panel.isVisible());
  check('no drawer overlaying it', await page.locator('.pl-drawer').count() === 0);
  check('all 10 pickers render inline',
        await panel.locator('.pl-picker').count() === 10);

  // a tab has no open/close, so it has to seed its own draft
  check('save starts disabled -- nothing edited yet',
        await panel.locator('.pl-btn-primary').last().isDisabled());

  await panel.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
  await page.waitForTimeout(400);
  const layers = await panel.locator('.pl-portrait-frame .portrait-layer').count();
  check('randomize composes layers inline', layers > 0, `${layers} layers`);
  check('save enables once there is an edit',
        !(await panel.locator('.pl-btn-primary').last().isDisabled()));

  if (SHOT) { await page.screenshot({ path: SHOT }); console.log(`\n  screenshot -> ${SHOT}`); }

  // ---------- the tab hands its work to the drawer ----------
  await panel.locator('.pl-btn-ghost', { hasText: 'Full screen' }).click();
  const drawer = page.locator('.pl-drawer');
  await drawer.waitFor({ state: 'visible', timeout: 10000 });
  const inDrawer = await drawer.locator('.pl-portrait-frame .portrait-layer').count();
  check('full screen carries the in-progress edits over, not a blank draft',
        inDrawer === layers, `tab=${layers} drawer=${inDrawer}`);

  // cancel discards, and must not leave the tab underneath empty
  await drawer.locator('.pl-drawer-close').click();
  await page.waitForTimeout(400);
  check('cancel closes the drawer', await page.locator('.pl-drawer').count() === 0);
  check('the tab is still rendering afterwards', await panel.isVisible());
  check('cancel discarded the edit',
        await panel.locator('.pl-portrait-frame .portrait-layer').count() === 0);

  // ---------- save keeps the panel populated ----------
  await panel.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
  await page.waitForTimeout(400);
  await panel.locator('.pl-btn-primary').last().click();
  await page.waitForTimeout(600);
  const afterSave = await panel.locator('.pl-portrait-frame .portrait-layer').count();
  check('saving does not empty the panel', afterSave > 0, `${afterSave} layers`);
  check('save goes quiet once saved',
        await panel.locator('.pl-btn-primary').last().isDisabled());

  // and it really persisted
  await page.locator('.builder-tab', { hasText: /^Options$/ }).first().click();
  await page.waitForTimeout(300);
  await tab.click();
  await page.waitForTimeout(400);
  check('the saved portrait is there when you come back',
        await panel.locator('.pl-portrait-frame .portrait-layer').count() > 0);

  // ---------- the phone layout has the tab too ----------
  const phoneCtx = await browser.newContext({ userAgent: PHONE_UA, isMobile: true,
                                              hasTouch: true, viewport: { width: 412, height: 915 } });
  await suppressOverlays(phoneCtx);
  const phone = await openBuilder(phoneCtx, 412);
  phone.on('pageerror', e => jsErrors.push(String(e)));
  const mtab = phone.locator('.builder-tab', { hasText: /^Portrait$/ }).first();
  check('Portrait tab exists on mobile', await mtab.count() > 0);
  await mtab.click();
  await phone.waitForTimeout(400);
  check('and renders the panel there', await phone.locator('.pl-inline').isVisible());
  const bodyMax = await phone.locator('.pl-inline .pl-drawer-body')
    .evaluate(el => getComputedStyle(el).maxHeight);
  check('the phone scrolls the page, not a nested panel', bodyMax === 'none', bodyMax);

  // A fourth tab overflowed the bar: at phone width the labels ran together
  // into OPTIONSDESCRIPTIONPORTRAIT with no gap between them.
  const tabBoxes = await phone.locator('.builder-tabs .builder-tab').evaluateAll(
    els => els.map(e => { const r = e.getBoundingClientRect();
                          return { l: r.left, r: r.right, t: r.top }; }));
  check('all four tabs are laid out', tabBoxes.length === 4, `${tabBoxes.length} tabs`);
  const sameRow = (a, b) => Math.abs(a.t - b.t) < 4;
  const collided = tabBoxes.some((a, i) =>
    tabBoxes.slice(i + 1).some(b => sameRow(a, b) && a.r > b.l + 0.5 && b.r > a.l + 0.5));
  check('no two tabs overlap', !collided, JSON.stringify(tabBoxes));
  const gapsOk = tabBoxes.every((a, i) => {
    const next = tabBoxes[i + 1];
    return !next || !sameRow(a, next) || next.l - a.r >= 4;
  });
  check('tabs sharing a row keep a gap between them', gapsOk, JSON.stringify(tabBoxes));

  // and the page itself must not scroll sideways because of them
  const overflow = await phone.evaluate(() =>
    document.documentElement.scrollWidth - document.documentElement.clientWidth);
  check('no horizontal overflow on the phone', overflow <= 1, `${overflow}px`);

  // ---------- pieces marked not-yet-drawn ----------
  // gap-inventory is empty in the committed registry, so this is an invariant
  // guard rather than a demonstration: it fires the moment real `no *.txt`
  // markers are recorded. The count is printed so a reader can see that.
  const gaps = await page.locator('.pl-sw-gap').count();
  console.log(`  note gap swatches currently rendered: ${gaps}`);
  const gapShapes = await page.locator('.pl-sw-gap').evaluateAll(
    els => els.map(e => ({ tag: e.tagName, title: e.getAttribute('title'),
                           selected: e.classList.contains('selected') })));
  check('a not-yet-drawn piece is never a pressable swatch',
        gapShapes.every(g => g.tag !== 'BUTTON' && !g.selected),
        JSON.stringify(gapShapes));
  check('and always says what it is',
        gapShapes.every(g => g.title && /not drawn yet/.test(g.title)),
        JSON.stringify(gapShapes));

  check('no uncaught JS errors', jsErrors.length === 0, jsErrors.slice(0, 3).join(' | '));

  await browser.close();
  console.log(`\ndone — ${failures} failing\n`);
  process.exit(failures === 0 ? 0 : 1);
})().catch(e => { console.error('\nharness error:', e); process.exit(1); });
