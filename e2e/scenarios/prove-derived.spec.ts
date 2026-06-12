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

// Show the derivation chain: RAW keywords the app pulls from the live character ->
// kw-to-name -> the assembled label. No hardcoded strings anywhere in the path.
test('the breadcrumb is derived from real keywords, step by step', async ({ page }) => {
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

  const chain = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame, views = w.orcpub.dnd.e5.views, common = w.orcpub.common;
    const kw = (s: string) => c.keyword.call(null, s);
    const db = c.deref(rf.db.app_db);
    const character = c.get.call(null, db, kw('character'));
    const opts = c.get.call(null, character, kw('orcpub.entity/options'));
    const template = c.deref(rf.core.subscribe.call(null, c.vector.call(null, kw('built-template'))));

    // 1) RAW keys the app computes (no strings involved)
    const site = views.isolate_culprit_selection.call(null, character, template, views.features_section_fails_QMARK_);
    const classKey = c.get.call(null, c.first.call(null, c.get.call(null, opts, kw('class'))), kw('orcpub.entity/key'));
    const selKey = c.get.call(null, site, kw('sel'));
    const choiceKey = c.get.call(null, site, kw('choice'));

    // 2) kw-to-name of each RAW key (the mechanical transform)
    const knm = (k: any) => (k == null ? null : common.kw_to_name.call(null, k));

    // 3) the final label the banner shows
    const label = views.culprit_selection_label.call(null, opts, site);

    return {
      raw_keywords: { class: c.pr_str.call(null, classKey), sel: c.pr_str.call(null, selKey), choice: c.pr_str.call(null, choiceKey) },
      kw_to_name: { class: knm(classKey), sel: knm(selKey), choice: knm(choiceKey) },
      final_label: label,
    };
  });
  console.log('CHAIN=' + JSON.stringify(chain, null, 2));

  // the raw keys are real keywords; the label is just their kw-to-name, title-cased
  expect(chain.raw_keywords.sel).toBe(':superior-hunters-defense');
  expect(chain.kw_to_name.sel).toBe('superior hunters defense');
  expect(chain.final_label.toLowerCase()).toContain('superior hunters defense');
});
