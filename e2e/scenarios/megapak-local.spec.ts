import { test } from '@playwright/test';
import * as path from 'path';
import { waitForAppReady, takeScreenshot } from '../fixtures/test-utils';

/**
 * MegaPak rendered in a LOCAL browser pointed at the codespace's PUBLIC URL.
 * The codespace's own headless Chromium OOMs rendering MegaPak; this machine
 * has the RAM. Set APP_URL to the public github.dev URL when running.
 *
 * Handles the GitHub public-port interstitial ("Continue") on first load.
 */

async function dismissInterstitial(page: any) {
  // GitHub public-port warning page: "You are about to access a development
  // port..." with a green "Continue" button/link. Shown once per session.
  const cont = page.getByText('Continue', { exact: true }); // proven to work on this page
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) {
    await cont.first().click().catch(() => {});
    await page.waitForTimeout(4000); // cookie set + reload + app boot
  }
}

test('MegaPak imports and the builder renders (local browser → public URL)', async ({ page }, testInfo) => {
  test.setTimeout(300000);
  await page.setViewportSize({ width: 1440, height: 900 });
  let crashed = false;
  const errs: string[] = [];
  page.on('crash', () => { crashed = true; console.log('EVT_PAGE_CRASHED'); });
  page.on('pageerror', (e) => errs.push('PE:' + e.message.slice(0, 200)));

  await page.goto('/');
  await dismissInterstitial(page);
  await takeScreenshot(page, testInfo, 'after-interstitial');

  await page.goto('/dnd/5e/my-content');
  await dismissInterstitial(page);
  await waitForAppReady(page);
  await takeScreenshot(page, testInfo, 'my-content');

  // Import MegaPak.
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/megapak.orcbrew'));
  await page.waitForTimeout(12000);
  await takeScreenshot(page, testInfo, 'after-megapak-import');
  console.log('IMPORT_CRASHED=' + crashed);

  // The heavy part: render the builder with MegaPak content.
  await page.goto('/pages/dnd/5e/character-builder');
  await dismissInterstitial(page);
  await waitForAppReady(page);
  await page.waitForTimeout(2500);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(2500);
  await takeScreenshot(page, testInfo, 'class-level-with-megapak');

  const opts = await page.locator('select.builder-option-dropdown').first()
    .locator('option').allTextContents().catch(() => [] as string[]);
  console.log('BUILDER_CRASHED=' + crashed);
  console.log('CLASS_OPTION_COUNT=' + opts.length);
  console.log('SAMPLE_OPTS=' + JSON.stringify(opts.slice(0, 30)));
  console.log('PAGE_ERRORS=' + JSON.stringify(errs.slice(0, 8)));
});
