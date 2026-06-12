import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

/**
 * DEMO (run against a TEMPORARILY un-fixed app — Evasion :name + null-safe sort
 * reverted): build the real Ranger 15 / Hunter / Evasion character and show that
 *   (a) the raw Features view now THROWS (the original black-screen crash), and
 *   (b) the branch's error-boundary catches that same throw and renders the
 *       recovery panel ("This section couldn't be displayed" + actions) instead
 *       of a blank — using the exact wrapper character-display uses.
 */

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(3000); }
}

test('reverted app: Features view throws, error-boundary shows recovery panel', async ({ page }) => {
  test.setTimeout(220000);
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
  await page.locator('select.builder-option-dropdown').first().selectOption({ label: 'Ranger' }).catch(() => {});
  await page.waitForTimeout(1200);
  await page.locator('select.builder-option-dropdown').nth(1).selectOption({ label: '15' }).catch(() => {});
  await page.waitForTimeout(2500);
  await page.getByText('Hunter', { exact: true }).first().click();
  await page.waitForTimeout(2500);
  await expect(page.getByText('Evasion', { exact: true }).first()).toBeVisible({ timeout: 10000 });
  await page.getByText('Evasion', { exact: true }).first().click();
  await page.waitForTimeout(2500);

  const r = await page.evaluate(async () => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame, rclient = w.reagent.dom.client, views = w.orcpub.dnd.e5.views;
    const kw = (s: string) => c.keyword.call(null, s);
    const vec = (...xs: any[]) => c.vector.apply(null, xs);
    const sleep = (ms: number) => new Promise((res) => setTimeout(res, ms));
    const out: any = {};

    // confirm the built Evasion trait is NAMELESS now (the reverted data)
    const tsub = rf.core.subscribe.call(null, vec(kw('orcpub.dnd.e5.character/traits')));
    const traits = c.pr_str(c.deref(tsub));
    out.evasionTraitNameless = /\{[^{}]*saving throw to take only[^{}]*\}/.test(traits) &&
      !/:name "Evasion"/.test(traits);

    const mount = async (hiccup: any) => {
      const div = document.createElement('div');
      document.body.appendChild(div);
      let threw: string | null = null;
      try {
        const root = rclient.create_root.call(null, div);
        rclient.render.call(null, root, hiccup);
      } catch (e: any) { threw = String(e).slice(0, 160); }
      await sleep(700);
      return { threw, text: (div.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 1600), htmlLen: (div.innerHTML || '').length };
    };

    // (a) RAW Features view, no boundary -> should throw (the black-screen crash)
    out.raw = await mount(vec(views.features_details, 2, null));

    // (b) SAME view wrapped exactly like character-display does -> recovery panel
    const fallback = function (error: any, retry: any) { return vec(views.feature_render_error, null, error, retry); };
    out.guarded = await mount(vec(views.error_boundary, fallback, vec(views.features_details, 2, null)));
    return out;
  });

  console.log('DEMO=' + JSON.stringify(r, null, 1));
  await page.screenshot({ path: 'test-results/recovery-panel-demo.png', fullPage: true }).catch(() => {});

  // the reverted Evasion trait is genuinely nameless
  expect(r.evasionTraitNameless, 'Evasion trait is nameless in the reverted build').toBe(true);
  // (a) the raw Features view crashes (this is the original black screen)
  const rawCrashed = r.raw.threw != null || /lower-case|toLowerCase|null/i.test(r.raw.text) || r.raw.htmlLen === 0;
  expect(rawCrashed, 'raw Features view throws/blanks without the boundary: ' + JSON.stringify(r.raw)).toBe(true);
  // (b) the boundary contains it and shows the recovery panel, not a blank
  expect(r.guarded.threw, 'boundary must not propagate the throw').toBeNull();
  expect(r.guarded.text, 'recovery panel rendered').toMatch(/section couldn.t be displayed/i);
  expect(r.guarded.text, 'recovery panel offers a retry').toMatch(/Try again/i);
});
