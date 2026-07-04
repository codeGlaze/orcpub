import { test, expect } from '@playwright/test';

// A3 — let imperfect content move forward. A class that won't pass save
// validation (e.g. no option source) can be saved into My Content via
// "Save anyway", which fills placeholders for the missing fields instead of
// stranding the work in the builder.
// See docs/HOMEBREW_REMEDIATION_ROADMAP.md A3.

const CLASS_BUILDER = '/pages/dnd/5e/class-builder';
const NAME_INPUT = '.field:has(.personality-label span:text-is("Name")) input';

test('A3 Save anyway: an imperfect class lands in My Content', async ({ page }) => {
  await page.goto(CLASS_BUILDER, { waitUntil: 'load' });
  const name = page.locator(NAME_INPUT).first();
  await expect(name).toBeVisible({ timeout: 30000 });
  await name.fill('A3 Anyway Class'); // name only, no option source -> save fails

  await page.locator('button:has-text("Save to Browser Storage"):not(#sticky-header button)')
    .first().click();

  // The save-failure banner offers a remediating escape hatch. The message
  // renders twice — a hidden copy in #header-container (first) and the visible
  // one in the content message area (last).
  const anyway = page.getByText('Save anyway with placeholders', { exact: true }).last();
  await expect(anyway).toBeVisible({ timeout: 10000 });
  await anyway.click();
  await page.waitForTimeout(500);

  const plugins = await page.evaluate(() => localStorage.getItem('plugins'));
  expect(plugins ?? '', 'imperfect class is now in My Content (plugins)')
    .toContain('A3 Anyway Class');
});
