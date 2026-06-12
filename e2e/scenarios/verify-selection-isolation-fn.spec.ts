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

// Verify the REAL isolate-culprit-selection + label on a REAL Ranger/Hunter/Evasion.
test('isolate-culprit-selection traces the real builder choice', async ({ page }) => {
  test.setTimeout(150000);
  await page.setViewportSize({ width: 1440, height: 1400 });
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

  const out = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame, views = w.orcpub.dnd.e5.views;
    const kw = (s: string) => c.keyword.call(null, s);
    const db = c.deref(rf.db.app_db);
    const character = c.get.call(null, db, kw('character'));
    const template = c.deref(rf.core.subscribe.call(null, c.vector.call(null, kw('built-template'))));
    const site = views.isolate_culprit_selection.call(null, character, template, views.features_section_fails_QMARK_);
    const label = site
      ? views.culprit_selection_label.call(null, c.get.call(null, character, kw('orcpub.entity/options')), site)
      : null;
    return { site: site ? c.pr_str.call(null, site) : null, label };
  });
  console.log('ISOLATION_FN=' + JSON.stringify(out, null, 2));

  expect(out.site, 'a culprit selection is found').toBeTruthy();
  expect(out.label, 'label points at Superior Hunter\'s Defense / Evasion').toMatch(/Superior Hunter.*Evasion/i);
});
