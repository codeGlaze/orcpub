import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import { BUGGED, NAME_INPUT, seedClass, exportDraft } from '../lib/builder';

// A2 — refresh safety, with REALISTIC content. In-progress builder work is
// written to localStorage on every edit but was never restored on boot, so a
// refresh wiped it. This proves a fully-built class survives a reload INTACT —
// not just its name.

test('class builder WIP survives a refresh (full content restored)', async ({ page }) => {
  await seedClass(page, BUGGED.validDespiteNamelessTrait); // rich Voidcaller
  // sanity: the seed is reflected before reload
  await expect(page.locator(NAME_INPUT).first()).toHaveValue('Voidcaller');

  await page.reload({ waitUntil: 'load' });

  // Name restored...
  const restored = page.locator(NAME_INPUT).first();
  await expect(restored).toBeVisible({ timeout: 30000 });
  await expect(restored, 'in-progress class restored after reload').toHaveValue('Voidcaller');

  // ...and the FULL rich content too — export the restored builder-item and
  // confirm every nested field came back, not just the name.
  const download = await exportDraft(page);
  const content = fs.readFileSync(await download.path(), 'utf8');
  expect(content, 'features restored').toContain('Void Bolt');
  expect(content, 'subclass title restored').toContain('Void Path');
  expect(content, 'numeric fields restored').toContain(':hit-die 8');
  expect(content, 'nameless feature text restored').toContain('Obscure feature with NO name key.');
});
