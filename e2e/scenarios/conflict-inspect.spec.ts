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

test('inspect the conflict-resolution modal structure', async ({ page }, testInfo) => {
  test.setTimeout(180000);
  await page.setViewportSize({ width: 1440, height: 1100 });
  await page.goto('/');
  await passInterstitial(page);
  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/megapak.orcbrew'));
  await page.waitForTimeout(12000);
  await takeScreenshot(page, testInfo, 'conflict-modal');

  // Dump the interactive structure of whatever modal/panel is up.
  const buttons = await page.locator('button, .form-button, .link-button, input[type=submit]').allInnerTexts().catch(() => []);
  console.log('BUTTONS=' + JSON.stringify([...new Set(buttons.map((b) => b.trim()).filter(Boolean))].slice(0, 40)));

  const radios = await page.locator('input[type=radio]').count().catch(() => 0);
  const checks = await page.locator('input[type=checkbox]').count().catch(() => 0);
  const selects = await page.locator('select').count().catch(() => 0);
  console.log('CONTROLS=' + JSON.stringify({ radios, checks, selects }));

  // Look for a global/bulk resolution control + an apply button by text.
  const applyish = await page.getByText(/apply|resolve all|import|confirm|keep both|skip all|rename all/i).allInnerTexts().catch(() => []);
  console.log('APPLYISH=' + JSON.stringify([...new Set(applyish.map((s) => s.trim()))].slice(0, 30)));
});
