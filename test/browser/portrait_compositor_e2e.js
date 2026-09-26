// Portrait compositor, driven through the real builder UI.
//
// Opens the character builder, goes to the Description tab, clicks
// "Compose portrait", and exercises the drawer: randomize, pick a swatch,
// set a base color, shade one piece, save, and confirm the composed
// portrait survives into the character summary.
//
//   lein fig:build
//   lein garden once        # only needed for the screenshot
//   lein e2e-server
//   node test/browser/portrait_compositor_e2e.js
//
// Set ORCPUB_SHOT=path.png to save a picture of the open drawer.
//
// Exits non-zero on the first failed check.

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

(async () => {
  // The image ships a pinned Chromium that may not match the version this
  // Playwright build expects; point at it rather than downloading another.
  const browser = await chromium.launch({ executablePath: findChrome() });
  // Belt and braces: the runner injects overlay suppression for the sweep, and
  // this covers a by-hand run, which gets no preload. Both backdrops swallow
  // clicks, so without it the first click lands on a modal instead.
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await suppressOverlays(ctx);
  const page = await ctx.newPage();

  // JS exceptions are always the app's problem. Resource failures are tracked
  // by URL so environmental ones (uncompiled CSS, blocked webfonts) can be
  // told apart from a genuinely missing app asset -- the console message text
  // alone doesn't name the resource.
  const jsErrors = [];
  page.on('pageerror', e => jsErrors.push(String(e)));

  const IGNORABLE = /styles\.css|fonts\.googleapis|fonts\.gstatic|figwheel|favicon/i;
  const badResources = [];
  page.on('response', r => {
    if (r.status() >= 400 && !IGNORABLE.test(r.url())) {
      badResources.push(`${r.status()} ${r.url()}`);
    }
  });
  page.on('requestfailed', r => {
    if (!IGNORABLE.test(r.url())) {
      badResources.push(`${r.failure().errorText} ${r.url()}`);
    }
  });

  console.log(`\nportrait compositor e2e -- ${BASE}\n`);

  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
  await page.waitForSelector('#app', { timeout: 30000 });

  // The cookie banner overlays the drawer's footer buttons; dismiss it first.
  const cookieBtn = page.locator('#cookie-btn');
  if (await cookieBtn.count()) {
    await clickIfVisible(cookieBtn);
    await page.waitForTimeout(200);
  }

  // --- reach the launcher ---------------------------------------------
  const descTab = page.locator('.builder-tab', { hasText: /^Description$/ }).first();
  if (await descTab.count()) {
    await descTab.click();
    await page.waitForTimeout(300);
  }

  // Second entry point: with no portrait yet, the summary thumbnail is an
  // empty frame carrying the pencil, so the compositor is reachable from the
  // portrait itself and not only from the Image URL field.
  const emptyThumb = page.locator('.pl-thumb-empty');
  check('empty summary thumbnail stands in before a portrait exists',
        await emptyThumb.count() > 0);
  check('empty thumbnail carries the pencil',
        await emptyThumb.locator('.pl-thumb-edit').count() > 0);

  const launcher = page.locator('.pl-launcher');
  await launcher.waitFor({ state: 'visible', timeout: 15000 });
  check('"Compose portrait" launcher renders in Description tab', true);

  // The drawer's <style> also styles the launcher, so it must be mounted even
  // while the drawer is closed -- which is exactly when the launcher is seen.
  const launcherBgClosed = await launcher.evaluate(el => getComputedStyle(el).backgroundImage);
  check('launcher is styled while the drawer is closed',
        /gradient/.test(launcherBgClosed), launcherBgClosed);

  // --- open the drawer -------------------------------------------------
  await launcher.click();
  const drawer = page.locator('.pl-drawer');
  await drawer.waitFor({ state: 'visible', timeout: 10000 });
  check('drawer opens on launcher click', await drawer.isVisible());

  const pickerCount = await page.locator('.pl-picker').count();
  check('all 10 layer pickers render', pickerCount === 10, `saw ${pickerCount}`);

  const emptyHint = page.locator('.pl-empty-hint');
  check('empty state shown before anything is picked', await emptyHint.isVisible());

  // --- randomize -------------------------------------------------------
  await page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
  await page.waitForTimeout(400);

  const layerCount = await page.locator('.pl-portrait-frame .portrait-layer').count();
  check('randomize composes layers', layerCount > 0, `${layerCount} layers`);
  check('empty hint gone after randomize', !(await emptyHint.isVisible().catch(() => false)));

  const seed = await page.locator('.pl-seed-row code').textContent().catch(() => null);
  check('seed displayed after randomize', !!seed && seed.length > 0, `seed=${seed}`);

  // layers must be mask divs carrying a background tint -- that is the
  // mechanic that lets one asset render in any character color
  const firstLayerBg = await page.locator('.pl-portrait-frame .portrait-layer').first()
    .evaluate(el => getComputedStyle(el).backgroundColor);
  check('layer renders as tinted mask div', /^rgb/.test(firstLayerBg), firstLayerBg);

  // --- pick a specific swatch -----------------------------------------
  const headPicker = page.locator('.pl-picker').filter({ hasText: 'Head' }).first();
  const headSwatch = headPicker.locator('.pl-sw:not(.pl-sw-none)').first();
  await headSwatch.click();
  await page.waitForTimeout(200);
  check('swatch click marks it selected',
        (await headSwatch.getAttribute('class')).includes('selected'));

  // --- base color on a slot -------------------------------------------
  const hairSlot = page.locator('.pl-slot').filter({ hasText: 'Hair' }).first();
  await hairSlot.locator('.pl-slot-swatch').click();
  await page.waitForTimeout(200);

  const panel = page.locator('.pl-slot-panel');
  check('slot panel opens on chip tap (not hijacked by native picker)',
        await panel.isVisible());

  const subRows = await panel.locator('.pl-sub-row').count();
  check('hair panel lists its 4 pieces', subRows === 4, `saw ${subRows}`);

  await panel.locator('.pl-preset').first().click();
  await page.waitForTimeout(200);
  check('panel stays open after picking a preset', await panel.isVisible());

  const hairLayerBg = await page.locator('.pl-portrait-frame .portrait-layer').first()
    .evaluate(el => getComputedStyle(el).backgroundColor);
  check('base color repaints the portrait', /^rgb/.test(hairLayerBg), hairLayerBg);

  // --- shade one piece --------------------------------------------------
  const shadeSlider = panel.locator('.pl-sub-row input[type=range]').first();
  // React wires onChange for range inputs to the `input` event.
  await shadeSlider.evaluate(el => {
    const setter = Object.getOwnPropertyDescriptor(
      window.HTMLInputElement.prototype, 'value').set;
    setter.call(el, '30');
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await page.waitForTimeout(250);
  const tweakBadge = hairSlot.locator('.pl-slot-tweaks');
  check('shading a piece raises the tweak badge', await tweakBadge.count() > 0);

  if (SHOT) {
    await page.screenshot({ path: SHOT, fullPage: false });
    console.log(`\n  screenshot -> ${SHOT}`);
  }

  // --- save and verify it reaches the summary --------------------------
  await page.locator('.pl-btn-primary', { hasText: 'Save portrait' }).click();
  await page.waitForTimeout(600);
  check('drawer closes on save', !(await drawer.isVisible().catch(() => false)));

  const summaryComposite = await page.locator('.portrait-composite .portrait-layer').count();
  check('composed portrait renders in the character summary',
        summaryComposite > 0, `${summaryComposite} layers in summary`);

  // --- the pencil is a real second way in ------------------------------
  const pencil = page.locator('.portrait-composite').locator('..').locator('.pl-thumb-edit');
  check('composed summary thumbnail carries the pencil', await pencil.count() > 0);
  await pencil.first().click();
  await drawer.waitFor({ state: 'visible', timeout: 10000 });
  check('pencil opens the drawer', await drawer.isVisible());
  await page.locator('.pl-drawer-close').click();
  await page.waitForTimeout(300);

  // --- reopen: draft should rehydrate from the saved character ---------
  await page.locator('.pl-launcher').click();
  await drawer.waitFor({ state: 'visible', timeout: 10000 });
  const reopened = await page.locator('.pl-portrait-frame .portrait-layer').count();
  check('reopening rehydrates the saved portrait', reopened > 0, `${reopened} layers`);

  const hairSlotAfter = page.locator('.pl-slot').filter({ hasText: 'Hair' }).first();
  check('saved base color survives the round trip',
        await hairSlotAfter.locator('.pl-slot-swatch.unset').count() === 0);
  check('saved per-piece tweak survives the round trip',
        await hairSlotAfter.locator('.pl-slot-tweaks').count() > 0);

  // --- light theme -------------------------------------------------------
  // The drawer is a sibling of content-page, so it cannot inherit .app's
  // theme class and carries its own. Verify it actually tracks the toggle.
  const darkDrawerBg = await page.locator('.pl-drawer')
    .evaluate(el => getComputedStyle(el).backgroundColor);

  // Close the drawer so the builder's own "Light Theme" toggle is clickable.
  await page.locator('.pl-drawer-close').click();
  await page.waitForTimeout(300);

  const toggle = page.locator('div.pointer', { hasText: 'Light Theme' }).first();
  check('found the builder theme toggle', await toggle.count() > 0);
  await toggle.click();
  await page.waitForTimeout(400);

  check('app switched to light theme', await page.locator('.app.light-theme').count() > 0);

  await page.locator('.pl-launcher').click();
  await page.locator('.pl-drawer').waitFor({ state: 'visible', timeout: 10000 });
  const lightDrawerBg = await page.locator('.pl-drawer')
    .evaluate(el => getComputedStyle(el).backgroundColor);
  check('drawer root carries the theme class',
        await page.locator('.pl-root.light-theme').count() > 0);
  check('drawer repaints for light theme', lightDrawerBg !== darkDrawerBg,
        `dark=${darkDrawerBg} light=${lightDrawerBg}`);

  // The frame stays dark in both themes on purpose: it holds character
  // colours, and pale skin or blonde hair would vanish on a light ground.
  const frameBg = await page.locator('.pl-portrait-frame')
    .evaluate(el => getComputedStyle(el).backgroundImage);
  check('portrait frame stays dark in light theme', /gradient/.test(frameBg));

  // The launcher sits outside the drawer, so it needs its own light-theme
  // hook -- it was left amber in an otherwise blue theme.
  await page.locator('.pl-drawer-close').click();
  await page.waitForTimeout(300);
  const launcherLight = await page.locator('.pl-launcher')
    .evaluate(el => getComputedStyle(el).backgroundImage);
  check('launcher recolours for light theme too',
        launcherLight !== launcherBgClosed, launcherLight);

  // --- nothing broke underneath ----------------------------------------
  check('no uncaught JS errors', jsErrors.length === 0, jsErrors.slice(0, 3).join(' | '));
  check('no failed app resources', badResources.length === 0,
        badResources.slice(0, 3).join(' | '));

  await browser.close();

  console.log(`\ndone — ${failures} failing\n`);
  process.exit(failures === 0 ? 0 : 1);
})().catch(e => {
  console.error('\nharness error:', e);
  process.exit(1);
});
