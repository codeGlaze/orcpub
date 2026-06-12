import { test, expect } from '@playwright/test';
import * as path from 'path';
import { waitForAppReady } from '../fixtures/test-utils';

/**
 * Live verification for branch claude/character-black-screen-feature-i8lvk3.
 *
 * The reported bug: opening a character's Features tab black-screened — a
 * feature whose definition omitted :name made `aloof-sort-by :name` call
 * clojure.string/lower-case on nil and throw; with no React error boundary the
 * whole component tree unmounted (blank page).
 *
 * This drives the REAL running app (compiled cljs) and asserts, against the
 * live components, that:
 *   1. aloof-sort-by over a nameless item no longer throws (the crash mechanism).
 *   2. The real Features tab view (features-details) renders a real built
 *      character without throwing or blanking.
 *   3. The full character-display sheet renders without blanking.
 *   4. The React-18 error-boundary contains a throwing child and renders its
 *      recovery fallback (NOT a blank) — the riskiest part of the fix
 *      (getDerivedStateFromError, per the branch's own self-audit).
 *   5. render-guard contains a per-item render throw and shows the item's data.
 *   6. blank-feature-name? classifies nil / "" / "x" correctly.
 *   7. Characterization: homebrew import already sanitizes a missing trait name
 *      to a placeholder, so imported content cannot reproduce the nil crash —
 *      the real nil source is built-in data (e.g. the Evasion trait this branch
 *      also fixes in classes.cljc).
 */

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(4000); }
}
async function resolveConflictsIfAny(page: any) {
  const rename = page.getByText('RENAME ALL', { exact: false });
  if (await rename.count().catch(() => 0)) {
    await rename.first().click().catch(() => {});
    await page.waitForTimeout(2000);
    await page.getByText(/RESOLVE ALL/i).first().click().catch(() => {});
    await page.waitForTimeout(7000);
  }
}

test('black-screen fix renders live (crash contained, real tab renders, boundary recovers)', async ({ page }) => {
  test.setTimeout(240000);
  const errs: string[] = [];
  page.on('pageerror', (e) => errs.push('PAGEERROR ' + String(e).slice(0, 200)));
  page.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE ' + m.text().slice(0, 200)); });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);

  // Import a homebrew class (one level-1 trait omits :name) and build a level-1
  // character with it — gives us a REAL built character to render.
  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/nameless-trait-class.orcbrew'));
  await page.waitForTimeout(6000);
  await resolveConflictsIfAny(page);

  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1500);
  const dd = page.locator('select.builder-option-dropdown').first();
  const labels = await dd.locator('option').allTextContents();
  const voidLabel = labels.find((l) => /voidcaller/i.test(l)) || 'Voidcaller';
  await dd.selectOption({ label: voidLabel }).catch(() => {});
  // selection can take a beat to apply; wait until the class lands in app-db
  await expect.poll(async () => page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame;
    return c.pr_str(c.get.call(null, c.deref(rf.db.app_db), c.keyword.call(null, 'character'))).includes(':voidcaller');
  }), { timeout: 15000, intervals: [500] }).toBe(true);

  const r = await page.evaluate(async () => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame, rdom = w.reagent.dom;
    const views = w.orcpub.dnd.e5.views, common = w.orcpub.common;
    const kw = (s: string) => c.keyword.call(null, s);
    const vec = (...xs: any[]) => c.vector.apply(null, xs);
    const sleep = (ms: number) => new Promise((res) => setTimeout(res, ms));
    const mount = async (hiccup: any, ms = 400) => {
      const div = document.createElement('div');
      document.body.appendChild(div);
      let threw: string | null = null;
      try { rdom.render(hiccup, div); } catch (e: any) { threw = String(e).slice(0, 200); }
      await sleep(ms);
      const text = (div.innerText || '').replace(/\s+/g, ' ').trim();
      return { threw, text, html: (div.innerHTML || '').length };
    };
    const out: any = {};

    // 1. crash mechanism: live aloof-sort-by over a nameless item must not throw
    try {
      const coll = vec(c.hash_map(kw('name'), 'b'), c.hash_map(kw('level'), 1), c.hash_map(kw('name'), 'a'));
      out.aloof = { ok: true, n: c.count.call(null, common.aloof_sort_by.call(null, kw('name'), coll)) };
    } catch (e: any) { out.aloof = { ok: false, err: String(e).slice(0, 200) }; }

    // 6. blank-feature-name? predicate
    out.blank = {
      nil: views.blank_feature_name_QMARK_.call(null, null),
      empty: views.blank_feature_name_QMARK_.call(null, ''),
      x: views.blank_feature_name_QMARK_.call(null, 'x'),
    };

    // 2. real Features tab view for the live built character (id=nil -> :built-character)
    out.featuresTab = await mount(vec(views.features_details, 2, null), 700);

    // 3. full character-display sheet for the live built character
    out.sheet = await mount(vec(views.character_display, null, true, 2), 900);

    // 4. error-boundary contains a throwing child and renders recovery fallback
    const boom = function () { throw new Error('e2e-boom'); };
    const fallback = function (_err: any, _retry: any) { return vec(kw('div'), 'RECOVERED_BOUNDARY'); };
    out.boundary = await mount(vec(views.error_boundary, fallback, vec(boom)), 700);

    // 5. render-guard contains a per-item throw and dumps the item data
    const guardData = c.hash_map(kw('name'), null, kw('marker'), 'GUARD_DATA_MARKER');
    out.guard = await mount(vec(views.render_guard, guardData, vec(boom)), 700);

    return out;
  });

  console.log('VERIFY=' + JSON.stringify(r, null, 2));
  console.log('ERRS=' + JSON.stringify(errs.slice(0, 30)));
  await page.screenshot({ path: 'test-results/black-screen-verify.png', fullPage: true }).catch(() => {});

  // 1. crash mechanism fixed
  expect(r.aloof.ok, 'aloof-sort-by over a nameless item must not throw').toBe(true);
  expect(r.aloof.n).toBe(3);
  // 6. predicate
  expect(r.blank).toEqual({ nil: true, empty: true, x: false });
  // 2. real Features tab renders, no throw, has content
  expect(r.featuresTab.threw, 'features-details must not throw').toBeNull();
  expect(r.featuresTab.html, 'features-details must render real DOM').toBeGreaterThan(0);
  expect(r.featuresTab.text, 'Features tab shows the built trait').toMatch(/Void Bolt/i);
  // 7. import "fill missing pieces" tip-to-tail: the trait imported WITHOUT a
  // :name reaches the rendered Features tab carrying the placeholder the import
  // layer filled in (so it displays instead of crashing).
  expect(r.featuresTab.text, 'imported nameless trait renders with the filled-in placeholder name')
    .toMatch(/Missing Trait Name/i);
  // 3. full sheet renders, not blank
  expect(r.sheet.threw, 'character-display must not throw').toBeNull();
  expect(r.sheet.html, 'character-display must render real DOM (not blank)').toBeGreaterThan(0);
  // 4. error boundary recovers (React-18 getDerivedStateFromError) — NOT a blank
  expect(r.boundary.threw, 'error-boundary must not propagate the throw').toBeNull();
  expect(r.boundary.text, 'boundary renders its recovery fallback, not a blank').toContain('RECOVERED_BOUNDARY');
  // 5. render-guard contains a per-item throw and surfaces the data
  expect(r.guard.threw, 'render-guard must not propagate the throw').toBeNull();
  expect(r.guard.text, 'guard shows the could-not-display fallback').toMatch(/couldn.t be displayed/i);
  expect(r.guard.text, 'guard dumps the offending item data').toContain('GUARD_DATA_MARKER');
});
