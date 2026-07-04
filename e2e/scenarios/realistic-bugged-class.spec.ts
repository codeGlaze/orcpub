import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import { BUGGED, seedClass, exportDraft, clickSave, clickSaveAnyway, gotoMyContent, plugins } from '../lib/builder';

// Realistic-content tests (not minimum-viable): a fully-built Voidcaller with a
// nested OBSCURE error (a nameless feature) and no option source.

test('A1 (realistic): Export draft preserves the FULL bugged class', async ({ page }) => {
  await seedClass(page, BUGGED.missingSource);
  const download = await exportDraft(page);
  const content = fs.readFileSync(await download.path(), 'utf8');
  expect(content).toContain('Voidcaller');
  expect(content).toContain('Void Bolt');
  expect(content).toContain('Void Path');
  expect(content).toContain(':hit-die 8');
  expect(content).toContain('Obscure feature with NO name key.');
});

test('A3 (realistic): Save anyway remediates the nested nameless trait and lands it', async ({ page }) => {
  await seedClass(page, BUGGED.missingSource);
  await clickSave(page);
  await clickSaveAnyway(page);
  await page.waitForTimeout(500);
  const p = await plugins(page);
  expect(p).toContain('Voidcaller');
  expect(p).toContain('Void Bolt');
  expect(p).toContain('Unsorted Homebrew');
  expect(p, 'nameless trait was given a placeholder name').toContain('[Missing Trait Name]');
});

test('A4 (realistic): the rescued bugged class shows in My Content, no reload', async ({ page }) => {
  await seedClass(page, BUGGED.missingSource);
  await clickSave(page);
  await clickSaveAnyway(page);
  await page.waitForTimeout(400);
  await gotoMyContent(page);
  await expect(page.getByText('Unsorted Homebrew').first()).toBeVisible({ timeout: 10000 });
});
