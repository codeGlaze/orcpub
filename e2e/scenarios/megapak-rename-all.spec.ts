import { test } from '@playwright/test';
import * as path from 'path';
import { waitForAppReady, takeScreenshot } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(4000); }
}

test('MegaPak: rename-all conflict resolution => import completes => class list', async ({ page }, testInfo) => {
  test.setTimeout(240000);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto('/');
  await passInterstitial(page);

  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/megapak.orcbrew'));
  await page.waitForTimeout(12000);

  // Resolve all conflicts by renaming, then apply.
  await page.getByText('RENAME ALL', { exact: false }).first().click().catch(() => {});
  await page.waitForTimeout(2500);
  await takeScreenshot(page, testInfo, 'after-rename-all');
  await page.getByText(/RESOLVE ALL/i).first().click().catch(() => {});
  await page.waitForTimeout(10000); // import applies the full pack
  await takeScreenshot(page, testInfo, 'after-resolve');

  const importLog = await page.locator('[class*="import-log"], .modal').allInnerTexts().catch(() => []);
  console.log('POST_RESOLVE_LOG=' + JSON.stringify(importLog).slice(0, 400));

  // Now the class dropdown should include any MegaPak base classes.
  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2500);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(2500);

  const classes = await page.locator('select.builder-option-dropdown').first()
    .locator('option').allTextContents().catch(() => [] as string[]);
  console.log('CLASS_COUNT=' + classes.length);
  console.log('CLASS_LIST=' + JSON.stringify(classes.map((c) => c.trim())));
});
