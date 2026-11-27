// The builder's checkboxes are keyboard controls.
//
// The magic item builder's checkboxes (damage types, item types, attunement classes) are the
// shared checkbox row: focusable, role checkbox, and Space or
// Enter toggles it. The feat builder's grids (comps/checkbox-grid) store what they show: a
// one-of-several grid keeps one value, a per-choice grid and a lone flag toggle their own.
// Checks, on the item builder: the focusable checkbox is the whole row (it holds
// its label text and the drawn tick box is hidden from screen readers, so a screen reader meets one
// checkbox per label); aria-checked starts false; focused and given Space it is checked and
// aria-checked is true; Enter unchecks it again.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/builder_checkboxes_e2e.js`). No login.
// Runs in:   ~20s.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { SetupFailed, BASE, checker, quietOverlays } = require('./lib/toolbox');
const itemBuilder = require('./places/item_builder');
const featBuilder = require('./places/feat_builder');

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

  watch(page, 'feat builder');
  await must('open the feat builder',
             () => page.goto(`${BASE}/pages/dnd/5e/feat-builder`, { waitUntil: 'networkidle' }));
  const speed10 = featBuilder.checkbox(page, 'Your speed is increased by 10 ft.');
  const speed5 = featBuilder.checkbox(page, 'Your speed is increased by 5 ft.');
  await must('see the speed bonus grid', () => speed10.waitFor({ timeout: 20000 }));
  await attempt('click +5 ft., then +10 ft.', async () => { await speed5.click(); await speed10.click(); });
  check('a one-of-several grid keeps the last value: +10 ft. is checked', await becomes(speed10, 'true'));
  check('and +5 ft. is no longer checked', await becomes(speed5, 'false'));
  const fire = featBuilder.checkbox(page, 'Resistance to fire damage');
  const cold = featBuilder.checkbox(page, 'Resistance to cold damage');
  await attempt('click fire resistance', () => fire.click());
  check('a per-choice grid checks the clicked choice', await becomes(fire, 'true'));
  check('and only that one', (await cold.getAttribute('aria-checked')) === 'false');
  const traps = featBuilder.checkbox(page, 'Advantage on saving throws against traps');
  await attempt('focus the traps flag and press Space', async () => { await traps.focus(); await page.keyboard.press('Space'); });
  check('a lone flag in a grid toggles from the keyboard', await becomes(traps, 'true'));

  await browser.close();
  process.exit(finish());
})().catch(e => {
  if (e instanceof SetupFailed) { console.log('STOP  ' + e.message); process.exit(finish()); }
  console.error(e); process.exit(2);
});
