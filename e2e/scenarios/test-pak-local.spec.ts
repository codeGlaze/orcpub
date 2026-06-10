import { test, expect } from '@playwright/test';
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

async function resolveConflictsIfAny(page: any, testInfo: any) {
  const rename = page.getByText('RENAME ALL', { exact: false });
  if (await rename.count().catch(() => 0)) {
    await takeScreenshot(page, testInfo, 'conflict-modal');
    await rename.first().click().catch(() => {});
    await page.waitForTimeout(2000);
    await page.getByText(/RESOLVE ALL/i).first().click().catch(() => {});
    await page.waitForTimeout(8000);
  }
}

test('test-PAK imports (rename-all) and adds the Divine Soul base class', async ({ page }, testInfo) => {
  test.setTimeout(200000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  let crashed = false;
  page.on('crash', () => { crashed = true; console.log('EVT_PAGE_CRASHED'); });

  await page.goto('/');
  await passInterstitial(page);

  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/test-pak.orcbrew'));
  await page.waitForTimeout(7000);
  await resolveConflictsIfAny(page, testInfo);
  await takeScreenshot(page, testInfo, 'after-import');
  console.log('IMPORT_CRASHED=' + crashed);

  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2500);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(2500);

  const classes = (await page.locator('select.builder-option-dropdown').first()
    .locator('option').allTextContents().catch(() => [] as string[])).map((c) => c.trim());
  console.log('BUILDER_CRASHED=' + crashed);
  console.log('CLASS_COUNT=' + classes.length);
  console.log('CLASS_LIST=' + JSON.stringify(classes));
  const hasDivineSoul = classes.some((c) => /divine soul/i.test(c));
  console.log('HAS_DIVINE_SOUL=' + hasDivineSoul);

  expect(classes.length, 'pak should add base classes beyond the 12 SRD').toBeGreaterThan(12);
  expect(hasDivineSoul, `class dropdown should include Divine Soul sorcerer. Saw: ${JSON.stringify(classes)}`).toBeTruthy();
});
