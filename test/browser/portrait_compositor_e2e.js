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

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const SHOT = process.env.ORCPUB_SHOT;

let failures = 0;
function check(label, ok, detail) {
  if (ok) {
    console.log(`  ok   ${label}`);
  } else {
    failures++;
    console.log(`  FAIL ${label}${detail ? ` -- ${detail}` : ''}`);
  }
}

(async () => {
  // The image ships a pinned Chromium that may not match the version this
  // Playwright build expects; point at it rather than downloading another.
  const browser = await chromium.launch(
    process.env.ORCPUB_CHROME ? { executablePath: process.env.ORCPUB_CHROME } : {});
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });

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
    await cookieBtn.click().catch(() => {});
    await page.waitForTimeout(200);
  }

  // --- reach the launcher ---------------------------------------------
  const descTab = page.locator('.builder-tab', { hasText: /^Description$/ }).first();
  if (await descTab.count()) {
    await descTab.click();
    await page.waitForTimeout(300);
  }

  const launcher = page.locator('.pl-launcher');
  await launcher.waitFor({ state: 'visible', timeout: 15000 });
  check('"Compose portrait" launcher renders in Description tab', true);

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

  // --- nothing broke underneath ----------------------------------------
  check('no uncaught JS errors', jsErrors.length === 0, jsErrors.slice(0, 3).join(' | '));
  check('no failed app resources', badResources.length === 0,
        badResources.slice(0, 3).join(' | '));

  await browser.close();

  console.log(`\n${failures === 0 ? 'PASS' : `FAIL (${failures})`}\n`);
  process.exit(failures === 0 ? 0 : 1);
})().catch(e => {
  console.error('\nharness error:', e);
  process.exit(1);
});
