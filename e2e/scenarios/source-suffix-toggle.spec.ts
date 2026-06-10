import { test, expect } from '@playwright/test';
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

/**
 * "Show homebrew source on class names" toggle. OFF: just the class name; ON:
 * the source in parens. Guards the select-keys fix (plugin-source was stripped
 * before render). Source label = the imported orcbrew's source key (here the
 * fixture file name, "sourced-classes").
 */
const CUSTOM = 'Artificer';

test('toggle ON appends the homebrew source to a custom class', async ({ page }) => {
  test.setTimeout(120000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);

  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/sourced-classes.orcbrew'));
  await page.waitForTimeout(3000);

  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1500);

  const opts = async () => (await page.locator('select.builder-option-dropdown').first()
    .locator('option').allTextContents().catch(() => [] as string[])).map((s) => s.trim());

  const off = await opts();
  const customOff = off.find((o) => o.startsWith(CUSTOM));
  expect(customOff, `custom class present. Saw: ${JSON.stringify(off)}`).toBeTruthy();
  expect(customOff!.includes('('), `OFF: no source suffix, saw "${customOff}"`).toBeFalsy();

  await page.getByText('Show homebrew source on class names', { exact: false }).first().click().catch(() => {});
  await page.waitForTimeout(900);

  const on = await opts();
  const customOn = on.find((o) => o.startsWith(CUSTOM));
  console.log('TOGGLE_OFF=' + JSON.stringify(customOff) + ' TOGGLE_ON=' + JSON.stringify(customOn));
  expect(/^Artificer \(.+\)$/.test(customOn || ''), `ON: "${CUSTOM}" should show a (source) suffix, saw "${customOn}"`).toBeTruthy();
});
