// The builder's checkboxes are keyboard controls.
//
// The magic item builder's checkboxes (damage types, item types, attunement classes) are the
// shared checkbox row: focusable, role checkbox, and Space or
// Enter toggles it. Checks, on the item builder: the focusable checkbox is the whole row (it holds
// its label text and the drawn tick box is hidden from screen readers, so a screen reader meets one
// checkbox per label); aria-checked starts false; focused and given Space it is checked and
// aria-checked is true; Enter unchecks it again.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/builder_checkboxes_e2e.js`). No login.
// Runs in:   ~15s.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { SetupFailed, BASE, checker, quietOverlays } = require('./lib/toolbox');
const itemBuilder = require('./places/item_builder');

const { check, attempt, must, watch, finish } = checker();

/**
 * Waits up to 3s for `box`'s aria-checked to be `want`, then returns whether it is.
 * @param {import('playwright').Locator} box
 * @param {string} want "true" or "false"
 * @returns {Promise<boolean>}
 */
async function becomes(box, want) {
  for (let i = 0; i < 30; i++) {
    if ((await box.getAttribute('aria-checked')) === want) return true;
    await box.page().waitForTimeout(100);
  }
  return false;
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await quietOverlays(ctx);
  const page = await ctx.newPage();
  watch(page, 'item builder');
  await must('open the magic item builder',
             () => page.goto(`${BASE}/pages/dnd/5e/magic-item-builder`, { waitUntil: 'networkidle' }));
  const box = itemBuilder.checkbox(page, 'Acid');
  await must('see the Acid checkbox', () => box.waitFor({ timeout: 20000 }));

  check('the checkbox is the whole row: it holds its label text',
        (await box.evaluate(e => e.textContent.trim())) === 'Acid');
  check('the drawn tick box inside is hidden from screen readers',
        (await box.locator('.checkbox[aria-hidden=true]').count()) === 1);
  check('the checkbox has role checkbox and starts unchecked',
        (await box.getAttribute('aria-checked')) === 'false');
  await attempt('focus the checkbox', () => box.focus());
  check('the checkbox takes focus (it is in the Tab order)',
        await box.evaluate(e => document.activeElement === e));
  await attempt('press Space', () => page.keyboard.press('Space'));
  check('Space checks it: aria-checked is true', await becomes(box, 'true'));
  check('the tick box inside is drawn checked', await box.locator('.checkbox.checked').count() === 1);
  await attempt('press Enter', () => page.keyboard.press('Enter'));
  check('Enter unchecks it: aria-checked is false again', await becomes(box, 'false'));

  await browser.close();
  process.exit(finish());
})().catch(e => {
  if (e instanceof SetupFailed) { console.log('STOP  ' + e.message); process.exit(finish()); }
  console.error(e); process.exit(2);
});
