import { test } from '@playwright/test';
import * as path from 'path';
import { waitForAppReady } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(4000); }
}

test('MegaPak content actually loaded (local → public URL)', async ({ page }) => {
  test.setTimeout(180000);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto('/');
  await passInterstitial(page);

  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/megapak.orcbrew'));
  await page.waitForTimeout(12000);

  // import-log panel text + whether My Content now lists real WotC sources
  const importLog = await page.locator('[class*="import-log"], .modal, [class*="conflict"]').allInnerTexts().catch(() => []);
  console.log('IMPORT_LOG=' + JSON.stringify(importLog).slice(0, 600));

  const body = (await page.locator('body').innerText()).slice(0, 6000);
  const sources = ['Xanathar', 'Tasha', 'Volo', 'Mordenkainen', 'Wildemount', 'Fizban'].filter((s) => body.includes(s));
  console.log('SOURCES_VISIBLE=' + JSON.stringify(sources));

  // Pick Wizard, go to subclass — MegaPak should add archetypes there.
  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  // count subclass-ish options across the builder after selecting a class
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(2000);
  const allOptionTexts = await page.locator('select option').allTextContents().catch(() => []);
  console.log('TOTAL_OPTION_COUNT=' + allOptionTexts.length);
  console.log('HAS_MEGAPAK_SUBCLASS=' + /war magic|bladesinging|chronurgy|echo knight|gunslinger|hexblade/i.test(allOptionTexts.join('|')));
});
