import { test, expect } from '@playwright/test';
import * as fs from 'fs';

// Closes the two gaps in A1's original proof:
//  1) a draft actually RE-IMPORTS (not silently skipped for being invalid)
//  2) a draft can be exported AFTER the save path has flagged validation errors
// See docs/HOMEBREW_REMEDIATION_ROADMAP.md A1.

const CLASS_BUILDER = '/pages/dnd/5e/class-builder';
const MY_CONTENT = '/dnd/5e/my-content';
const NAME_INPUT = '.field:has(.personality-label span:text-is("Name")) input';

test('A1 round-trip: an exported draft re-imports into plugins', async ({ page }) => {
  // Export a draft (name only — the kind that won't save normally).
  await page.goto(CLASS_BUILDER, { waitUntil: 'load' });
  const name = page.locator(NAME_INPUT).first();
  await expect(name).toBeVisible({ timeout: 30000 });
  await name.fill('Roundtrip Class');
  const draftBtn = page.locator('button:has-text("Export draft"):not(#sticky-header button)').first();
  await expect(draftBtn).toBeVisible({ timeout: 30000 });
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    draftBtn.click(),
  ]);
  const draftPath = await download.path();

  // Re-import it via the real My Content import input.
  await page.goto(MY_CONTENT, { waitUntil: 'load' });
  await expect(page.locator('input[type=file]')).toHaveCount(1, { timeout: 30000 });
  await page.locator('input[type=file]').setInputFiles(draftPath);
  await page.waitForTimeout(1500); // FileReader + import dispatch

  // It must actually land in :plugins (persisted), not be skipped.
  const plugins = await page.evaluate(() => localStorage.getItem('plugins'));
  expect(plugins, 'imported draft must land in plugins, not be silently dropped')
    .toContain('Roundtrip Class');
});

test('A1 error-then-export: draft still exports after save is flagged invalid', async ({ page }) => {
  await page.goto(CLASS_BUILDER, { waitUntil: 'load' });
  const name = page.locator(NAME_INPUT).first();
  await expect(name).toBeVisible({ timeout: 30000 });
  await name.fill('Flagged Class'); // name set, but no option source -> save fails

  // Normal save is rejected (validation flags it).
  await page.locator('button:has-text("Save to Browser Storage"):not(#sticky-header button)').first().click();
  await page.waitForTimeout(600);
  const afterSave = await page.evaluate(() => localStorage.getItem('plugins'));
  expect(afterSave ?? '', 'save was rejected, so it is NOT in plugins')
    .not.toContain('Flagged Class');

  // The escape hatch still works in the flagged state.
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.locator('button:has-text("Export draft"):not(#sticky-header button)').first().click(),
  ]);
  const content = fs.readFileSync(await download.path(), 'utf8');
  expect(content, 'draft exported despite the flagged save').toContain('Flagged Class');
});
