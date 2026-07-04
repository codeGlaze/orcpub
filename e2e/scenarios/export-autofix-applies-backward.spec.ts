import { test, expect } from '@playwright/test';
import { readFileSync } from 'fs';

// When export flags a missing required field, the warning modal lets you fill it
// and "Export & Auto-Fix". Previously that fix went ONLY into the exported file —
// the live library (My Content) silently kept the un-fixed version, so the next
// export re-prompted for the same field. Now the fix is applied backward: the
// same correction lands in :plugins too, so the library matches the file. The
// fields are the user's own modal input, so this only ADDS the fix (never strips).

// A spell missing :level — triggers the export warning modal.
const P = '{"Backward Test" {:orcpub.dnd.e5/spells ' +
  '{:testspell {:name "Test Spell" :key :testspell :option-pack "Backward Test" ' +
  ' :school "evocation"}}}}';

test('export auto-fix applies the fix backward to My Content', async ({ page }) => {
  await page.goto('/pages/dnd/5e/class-builder', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.evaluate((edn) => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('orcpub.dnd.e5', 'set-plugins'), w.cljs.reader.read_string(edn)], true));
  }, P);
  await page.waitForTimeout(300);

  // sanity: the library starts WITHOUT a :level
  expect(await page.evaluate(() => localStorage.getItem('plugins') || ''),
    'starts with no :level').not.toContain(':level');

  // My Content → export → the modal flags the missing :level
  await page.evaluate(() => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('route'), w.cljs.core.keyword('my-content-5e-page')], true));
  });
  await page.waitForTimeout(800);
  await page.getByText('Backward Test', { exact: true }).first().click();
  await page.locator('button.form-button', { hasText: /^export$/ }).first().click();
  await page.waitForTimeout(600);

  // fill the missing level (dropdown) → the primary button enables
  const levelSelect = page.locator('select.export-edit-select').first();
  await expect(levelSelect).toBeVisible({ timeout: 10000 });
  await levelSelect.selectOption('3');
  const autofix = page.getByText('Export & Auto-Fix', { exact: true });
  await expect(autofix).toBeVisible({ timeout: 10000 });

  const [download] = await Promise.all([page.waitForEvent('download'), autofix.click()]);
  const exported = readFileSync(await download.path(), 'utf8');

  // the exported FILE has the fix
  expect(exported, 'exported file carries the filled level').toContain(':level 3');
  // and — the new behavior — the LIVE library was updated to match
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('plugins') || ''), { timeout: 5000 })
    .toContain(':level 3');
});

// Auto-fix = the user's edits + dummy-fill for blanks. The SAME fixed data goes
// to the file AND back to the library, so My Content matches the file you
// exported (placeholders are self-labeling + flagged, like "Save anyway"). Here a
// blank :name gets dummy-filled and that fill must land in both.
const NO_NAME = '{"Blank Name" {:orcpub.dnd.e5/spells ' +
  '{:mystery {:key :mystery :option-pack "Blank Name" :school "evocation" :level 1}}}}';

test('export auto-fix persists the placeholder backward too (library == file)', async ({ page }) => {
  await page.goto('/pages/dnd/5e/class-builder', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.evaluate((edn) => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('orcpub.dnd.e5', 'set-plugins'), w.cljs.reader.read_string(edn)], true));
  }, NO_NAME);
  await page.waitForTimeout(300);

  await page.evaluate(() => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('route'), w.cljs.core.keyword('my-content-5e-page')], true));
  });
  await page.waitForTimeout(800);
  await page.getByText('Blank Name', { exact: true }).first().click();
  await page.locator('button.form-button', { hasText: /^export$/ }).first().click();
  await page.waitForTimeout(600);

  // :name is a text field (not a dropdown), so the button is already enabled —
  // export WITHOUT filling it, which dummy-fills "[Missing Name]" in the file.
  const autofix = page.getByText('Export & Auto-Fix', { exact: true });
  await expect(autofix).toBeVisible({ timeout: 10000 });
  const [download] = await Promise.all([page.waitForEvent('download'), autofix.click()]);
  const exported = readFileSync(await download.path(), 'utf8');

  // the file gets the placeholder (so it's complete/valid)...
  expect(exported, 'file dummy-fills the missing name').toContain('[Missing');
  // ...and so does the library, so My Content matches the exported file. The
  // placeholder is self-labeling ("[Missing …]") and the toast/log flag it for fixing.
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem('plugins') || ''), { timeout: 5000 })
    .toContain('[Missing');
});
