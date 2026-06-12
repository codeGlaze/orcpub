import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(3000); }
}

// Render the REAL character-health-warning on a REAL Ranger/Hunter/Evasion (the
// builder character, id=nil -> resolves to it). It must now show the selection
// breadcrumb, NOT the rules-text dump.
test('the banner now shows the selection to re-pick, not rules text', async ({ page }) => {
  test.setTimeout(150000);
  await page.setViewportSize({ width: 1100, height: 1200 });
  await page.goto('/');
  await passInterstitial(page);
  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1200);
  const dd = page.locator('select.builder-option-dropdown');
  await dd.first().selectOption({ label: 'Ranger' }).catch(() => {});
  await page.waitForTimeout(1500);
  await dd.nth(1).selectOption({ value: 'level-15' }).catch(() => {});
  await page.waitForTimeout(2500);
  await page.mouse.wheel(0, 3000); await page.waitForTimeout(1000);
  await page.getByText('Hunter', { exact: true }).first().click().catch(() => {});
  await page.waitForTimeout(2500);
  await page.mouse.wheel(0, 3000); await page.waitForTimeout(1000);
  await page.getByText('Evasion', { exact: true }).first().click().catch(() => {});
  await page.waitForTimeout(2500);

  await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, views = w.orcpub.dnd.e5.views, rc = w.reagent.core;
    // id=nil -> the builder character (the one we just built)
    const tree = c.vector.call(null, views.character_health_warning, null);
    const div = document.createElement('div');
    div.id = 'banner';
    (div as any).style = 'max-width:760px;margin:24px auto;color:#222;background:#fff;padding:8px;';
    document.body.innerHTML = '';
    document.body.appendChild(div);
    w.ReactDOM.render(rc.as_element.call(null, tree), div);
  });
  await page.waitForTimeout(2500); // isolation does many rebuilds

  const banner = page.locator('#banner');
  const text = await banner.innerText();
  console.log('BANNER_TEXT=' + JSON.stringify(text));
  await page.screenshot({ path: 'test-results/banner.png' });

  await expect(banner).toContainText('missing its name');
  await expect(banner).toContainText('Superior Hunters Defense'); // the traced selection
  await expect(banner).not.toContainText('red dragon');           // NOT the rules-text dump
});
