import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

// Required fields rendered by non-text components (dropdowns, checkbox groups)
// must show the same amber/red cue as the text fields.

async function readMessage(page: import('@playwright/test').Page): Promise<string> {
  const banner = page.locator('.message.bg-red').first();
  await banner.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
  return (await banner.innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
}

function fillCommon(page: import('@playwright/test').Page) {
  const name = page.locator('input[type="text"], input:not([type])').nth(1);
  const source = page.locator('input[placeholder="Default Option Source"]').first();
  return { name, source };
}

test('monster builder flags missing Hit Points (dropdowns)', async ({ page }) => {
  await page.goto('/pages/dnd/5e/monster-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(1200);

  const { name, source } = fillCommon(page);
  await name.fill('Testbeast');
  await source.fill('My Pack');
  await page.getByRole('button', { name: /save to browser/i }).first().click({ force: true });

  const msg = await readMessage(page);
  console.log(`MONSTER no-HP -> ${msg}`);
  expect(msg).toMatch(/Hit Points/i);
  // At least one HP dropdown wrapper is flagged amber.
  await expect(page.locator('.builder-field-unfilled').first()).toBeVisible();

  // Selecting a die count (the dropdown inside the flagged wrapper) clears cues.
  const beforeCount = await page.locator('.builder-field-unfilled').count();
  await page.locator('.builder-field-unfilled select').first().selectOption({ index: 2 });
  await page.waitForTimeout(300);
  const afterCount = await page.locator('.builder-field-unfilled').count();
  expect(afterCount).toBeLessThan(beforeCount);
});

test('spell builder flags spell-lists when no class is selected', async ({ page }) => {
  await page.goto('/pages/dnd/5e/spell-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(1200);

  const { name, source } = fillCommon(page);
  await name.fill('Testcantrip');
  await source.fill('My Pack');

  // Uncheck every class in the "Class Spell Lists?" group (default all checked).
  const toggles = page.locator('.flex.flex-wrap.p-5.b-rad-5 > div');
  const n = await toggles.count();
  expect(n).toBeGreaterThan(0);
  for (let i = 0; i < n; i++) await toggles.nth(i).click();
  await page.waitForTimeout(300);

  await page.getByRole('button', { name: /save to browser/i }).first().click({ force: true });
  const msg = await readMessage(page);
  console.log(`SPELL no-class -> ${msg}`);
  expect(msg).toMatch(/Class Spell Lists/i);
  // The checkbox group itself is flagged.
  await expect(page.locator('.flex.flex-wrap.p-5.b-rad-5.builder-field-unfilled')).toBeVisible();
});
