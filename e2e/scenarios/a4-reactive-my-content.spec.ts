import { test, expect } from '@playwright/test';

// A4 — reactive propagation to My Content. Investigation conclusion: My Content
// renders @(subscribe [::e5/plugins]), and ::e5/plugins is a plain reactive sub
// over (:plugins db) with NO init-only cache in between. So a save reflects in
// My Content via in-app navigation, WITHOUT a full browser reload. The original
// "not in My Content / had to refresh" was the stuck-in-builder problem (content
// never reached :plugins), fixed by A1/A3. This proves the reactive path.
// See docs/HOMEBREW_REMEDIATION_ROADMAP.md A4 / O4.

const CLASS_BUILDER = '/pages/dnd/5e/class-builder';
const NAME_INPUT = '.field:has(.personality-label span:text-is("Name")) input';

test('A4: a save reflects in My Content via in-app nav (no reload)', async ({ page }) => {
  await page.goto(CLASS_BUILDER, { waitUntil: 'load' });
  const name = page.locator(NAME_INPUT).first();
  await expect(name).toBeVisible({ timeout: 30000 });
  await name.fill('A4 Reactive Class');

  // Land it in :plugins via Save anyway (imperfect content; A3).
  await page.locator('button:has-text("Save to Browser Storage"):not(#sticky-header button)')
    .first().click();
  await page.getByText('Save anyway with placeholders', { exact: true }).last().click();
  await page.waitForTimeout(400);

  // SPA-navigate to My Content (NO page reload): same [:route ...] the flyout
  // dispatches. Dev (:none) build exposes re-frame/cljs on window.
  await page.evaluate(() => {
    // @ts-ignore
    re_frame.core.dispatch(cljs.core.PersistentVector.fromArray(
      [cljs.core.keyword('route'), cljs.core.keyword('my-content-5e-page')], true));
  });
  await page.waitForTimeout(800);

  // My Content reflects the just-saved source with no reload.
  await expect(page.getByText('Unsorted Homebrew').first())
    .toBeVisible({ timeout: 10000 });
});
