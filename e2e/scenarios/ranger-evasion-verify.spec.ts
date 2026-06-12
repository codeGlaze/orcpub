import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

/**
 * Faithful live repro of the reported "Features tab black screen" on the actual
 * root-cause path: a Ranger 15 / Hunter with the level-15 "Superior Hunter's
 * Defense" -> Evasion choice. Pre-fix the Evasion trait was defined without
 * :name, so the Features tab's `aloof-sort-by :name` hit lower-case-of-nil and
 * blanked the page. This branch (a) gives that trait a :name in classes.cljc and
 * (b) makes the sort null-safe. This test builds that exact character in the
 * real builder and confirms the Features view renders Evasion with no crash.
 */

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(3000); }
}

function readTraits(page: any) {
  return page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame;
    const tsub = rf.core.subscribe.call(null, c.vector.call(null, c.keyword.call(null, 'orcpub.dnd.e5.character/traits')));
    return c.pr_str(c.deref(tsub));
  });
}

test('Ranger 15 / Hunter / Evasion: Features tab renders, no black screen', async ({ page }) => {
  test.setTimeout(220000);
  const errs: string[] = [];
  page.on('pageerror', (e) => errs.push('PAGEERROR ' + String(e).slice(0, 200)));
  page.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE ' + m.text().slice(0, 200)); });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);
  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1200);

  // Ranger, level 15
  await page.locator('select.builder-option-dropdown').first().selectOption({ label: 'Ranger' }).catch(() => {});
  await page.waitForTimeout(1200);
  await page.locator('select.builder-option-dropdown').nth(1).selectOption({ label: '15' }).catch(() => {});
  await page.waitForTimeout(2500);

  // subclass: Hunter (Ranger Archetype selection)
  await page.getByText('Hunter', { exact: true }).first().click();
  await page.waitForTimeout(2500);

  // level-15 Superior Hunter's Defense -> Evasion (appears only after Hunter)
  await expect(page.getByText('Evasion', { exact: true }).first()).toBeVisible({ timeout: 10000 });
  await page.getByText('Evasion', { exact: true }).first().click();
  await page.waitForTimeout(2500);

  // the built character now carries a NAMED Evasion trait (the data fix)
  const traits = await readTraits(page);
  console.log('TRAITS_HAS_EVASION=' + /Evasion/.test(traits));
  console.log('EVASION_CTX=' + JSON.stringify((traits.match(/\{[^{}]*Evasion[^{}]*\}/) || ['<none>'])[0].slice(0, 200)));
  expect(traits, 'Evasion trait present in built character').toMatch(/Evasion/);
  expect(traits, 'Evasion trait carries a :name (the classes.cljc fix)').toMatch(/:name "Evasion"/);

  // open the Features tab in the live preview and confirm it renders Evasion
  // (this is the section that black-screened pre-fix)
  await page.getByText('FEATURES', { exact: false }).first().click().catch(() => {});
  await page.waitForTimeout(2000);
  const featuresText = await page.evaluate(() => (document.body.innerText || '').replace(/\s+/g, ' '));
  console.log('ERRS=' + JSON.stringify(errs.slice(0, 20)));
  await page.screenshot({ path: 'test-results/ranger-evasion-features.png', fullPage: true }).catch(() => {});

  // Features section rendered Evasion, page not blanked, no uncaught render error
  expect(featuresText, 'Features tab shows the Evasion feature').toMatch(/Evasion/);
  expect(featuresText, 'page did not black-screen (builder chrome still present)').toMatch(/Character Builder/i);
  const renderErrs = errs.filter((e) => /lower-case|toLowerCase|Cannot read|render/i.test(e));
  expect(renderErrs, 'no render/lower-case crash in console: ' + JSON.stringify(renderErrs)).toHaveLength(0);
});
