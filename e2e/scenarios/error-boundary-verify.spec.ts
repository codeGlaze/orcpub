import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

/**
 * Defense-in-depth: prove the React-18 error boundary and per-item render-guard
 * actually contain a throwing child and render their recovery UI instead of
 * unmounting the tree (the black screen). The branch's own self-audit flagged
 * this as the #1 runtime risk: a componentDidCatch+atom fallback does NOT
 * re-render under React 18; only getDerivedStateFromError does. We mount the
 * real compiled components with a deliberately-throwing child and read the DOM.
 */

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(3000); }
}

test('error-boundary + render-guard contain a throwing child (no black screen)', async ({ page }) => {
  test.setTimeout(120000);
  await page.goto('/');
  await passInterstitial(page);
  await waitForAppReady(page);

  const r = await page.evaluate(async () => {
    const w = window as any, c = w.cljs.core, rdom = w.reagent.dom, views = w.orcpub.dnd.e5.views;
    const kw = (s: string) => c.keyword.call(null, s);
    const vec = (...xs: any[]) => c.vector.apply(null, xs);
    const sleep = (ms: number) => new Promise((res) => setTimeout(res, ms));
    const mount = async (hiccup: any) => {
      const div = document.createElement('div');
      document.body.appendChild(div);
      let threw: string | null = null;
      try { rdom.render(hiccup, div); } catch (e: any) { threw = String(e).slice(0, 200); }
      await sleep(600);
      return { threw, text: (div.innerText || '').replace(/\s+/g, ' ').trim(), htmlLen: (div.innerHTML || '').length };
    };
    const boom = function () { throw new Error('e2e-boom'); };

    const out: any = {};
    // error-boundary: child throws -> fallback (error retry) renders
    const fallback = function (_e: any, _retry: any) { return vec(kw('div'), 'RECOVERED_BOUNDARY'); };
    out.boundary = await mount(vec(views.error_boundary, fallback, vec(boom)));
    // render-guard: per-item throw -> guard-fallback shows "couldn't be displayed" + the item data
    const data = c.hash_map(kw('name'), null, kw('marker'), 'GUARD_DATA_MARKER');
    out.guard = await mount(vec(views.render_guard, data, vec(boom)));
    // sanity: a non-throwing child renders normally through the boundary
    out.healthy = await mount(vec(views.error_boundary, fallback, vec(kw('div'), 'HEALTHY_CHILD')));
    return out;
  });

  console.log('BOUNDARY=' + JSON.stringify(r, null, 1));
  await page.screenshot({ path: 'test-results/error-boundary-verify.png' }).catch(() => {});

  // boundary catches the throw and renders the recovery fallback (not a blank)
  expect(r.boundary.threw, 'boundary must not propagate the throw').toBeNull();
  expect(r.boundary.text, 'boundary renders recovery fallback').toContain('RECOVERED_BOUNDARY');
  // render-guard contains a per-item throw and surfaces the offending data
  expect(r.guard.threw, 'render-guard must not propagate the throw').toBeNull();
  expect(r.guard.text, 'guard shows could-not-display fallback').toMatch(/couldn.t be displayed/i);
  expect(r.guard.text, 'guard dumps the offending item data').toContain('GUARD_DATA_MARKER');
  // healthy child renders straight through
  expect(r.healthy.text, 'non-throwing child renders normally').toContain('HEALTHY_CHILD');
});
