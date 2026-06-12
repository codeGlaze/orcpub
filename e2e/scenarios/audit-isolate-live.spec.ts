import { test, expect } from '@playwright/test';

/**
 * Live demo of fault-isolation-by-re-execution.
 *  A) guarded-feature-list: a collection-level render that THROWS (a sort over a
 *     nil-name item — the aggregate failure a per-item guard can't see). The
 *     auditor bisects the input, renders the survivors, and surfaces the one
 *     offending item with its raw data.
 *  B) the recovery panel showing the failed SECTION + (in details) the error and
 *     the React component stack captured by the boundary.
 */
function mountHelper() {
  // returns a string describing which mount path worked
  const w = window as any;
  (w as any).__mount = (hiccup: any, div: any) => {
    const rd = w.reagent && w.reagent.dom, rc = w.reagent && w.reagent.core;
    if (rd && rd.render) { rd.render.call(null, hiccup, div); return 'reagent.dom'; }
    if (rc && rc.as_element && w.ReactDOM && w.ReactDOM.render) {
      w.ReactDOM.render(rc.as_element.call(null, hiccup), div); return 'ReactDOM+as_element';
    }
    throw new Error('no mount path');
  };
}

test('bisection isolator surfaces the breaking item; panel shows section + component stack', async ({ page }) => {
  test.setTimeout(120000);
  await page.setViewportSize({ width: 1000, height: 1000 });
  await page.goto('/');
  await page.waitForFunction(() => {
    const w = window as any;
    return !!(w.cljs && w.orcpub && w.orcpub.dnd && w.orcpub.dnd.e5 && w.orcpub.dnd.e5.views
      && w.orcpub.dnd.e5.views.guarded_feature_list && w.orcpub.dnd.e5.views.isolate_culprit
      && w.orcpub.dnd.e5.views.feature_render_error && w.orcpub.dnd.e5.views.error_boundary
      && w.orcpub.common && w.orcpub.common.aloof_sort_by);
  }, { timeout: 30000 });

  await page.evaluate(mountHelper);

  // sanity: confirm the (reverted) sort really throws on a nil-name item, so this
  // is a genuine aggregate failure, not a staged one.
  const sortThrows = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, kw = (s: string) => c.keyword.call(null, s);
    const coll = c.vector.call(null, c.hash_map.call(null, kw('name'), 'b'),
      c.hash_map.call(null, kw('level'), 1), c.hash_map.call(null, kw('name'), 'a'));
    try { w.orcpub.common.aloof_sort_by.call(null, kw('name'), coll); return false; }
    catch (e: any) { return String(e).slice(0, 120); }
  });
  console.log('SORT_THROWS=' + JSON.stringify(sortThrows));

  // ---- A) guarded-feature-list audits an aggregate (sort) failure ----
  const aOut = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, views = w.orcpub.dnd.e5.views;
    const kw = (s: string) => c.keyword.call(null, s);
    const mk = (o: Record<string, unknown>) => {
      let m = c.hash_map.call(null);
      for (const k in o) m = c.assoc.call(null, m, kw(k), (o as any)[k]);
      return m;
    };
    // render-coll: sorts by :name (EAGER -> throws on a nil name) then "renders"
    const renderColl = (coll: any) => {
      const sorted = w.orcpub.common.aloof_sort_by.call(null, kw('name'), coll);
      return c.vector.call(null, kw('div'),
        'Rendered ' + c.count.call(null, sorted) + ' feature(s): '
        + c.pr_str.call(null, c.vec.call(null, c.map.call(null, function (x: any) { return c.get.call(null, x, kw('name')); }, sorted))));
    };
    // the culprit: a real-shaped trait that carries locators (class/level/page) but
    // NO :name (the missing field is exactly what we can't show).
    const culprit = c.hash_map.call(null,
      kw('class-key'), kw('ranger'),
      kw('level'), 15,
      kw('page'), 93,
      kw('summary'), 'When you are subjected to an effect that allows a Dex save for half damage…');
    const items = c.vector.call(null,
      mk({ name: 'Aardvark Strike' }),
      mk({ name: 'Beholder Ward' }),
      culprit,
      mk({ name: 'Cleric Boon' }));
    const isol = views.isolate_culprit.call(null, renderColl, items);
    const tree = c.vector.call(null, views.guarded_feature_list, renderColl, items);
    const div = document.createElement('div');
    div.id = 'demo-audit';
    (div as any).style = 'max-width:900px;margin:24px auto;color:#222;background:#fff;padding:12px;';
    document.body.innerHTML = '';
    document.body.appendChild(div);
    const path = (w as any).__mount(tree, div);
    return { mountPath: path, isolatedCount: c.count.call(null, isol), isolatedEdn: c.pr_str.call(null, isol) };
  });
  console.log('AUDIT=' + JSON.stringify(aOut, null, 2));

  const audit = page.locator('#demo-audit');
  await expect(audit).toContainText('A feature in this section couldn’t be displayed'); // problem leads
  await expect(audit).toContainText('from your Ranger');                   // location, not the name
  await expect(audit).toContainText('gained at level 15');
  await expect(audit).toContainText('open it in the builder');
  await expect(audit).toContainText('Rendered 3 feature(s)');              // survivors, below
  await page.screenshot({ path: 'test-results/audit-isolate.png' });

  // ---- B) recovery panel with section name + component stack ----
  await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, views = w.orcpub.dnd.e5.views;
    function FeaturesSectionDemo() { throw new Error("Cannot read properties of null (reading 'toLowerCase')"); }
    const child = c.vector.call(null, FeaturesSectionDemo);
    const fallback = (error: any, stack: any, retry: any) =>
      c.vector.call(null, views.feature_render_error, 'Features', null, error, stack, retry);
    const tree = c.vector.call(null, views.error_boundary, fallback, child);
    const div = document.createElement('div');
    div.id = 'demo-panel';
    (div as any).style = 'max-width:900px;margin:24px auto;color:#222;background:#fff;';
    document.body.innerHTML = '';
    document.body.appendChild(div);
    (w as any).__mount(tree, div);
  });
  const panel = page.locator('#demo-panel');
  await expect(panel).toContainText('The Features section couldn’t be displayed');
  await panel.getByText('Show technical details').click();
  await expect(panel).toContainText("toLowerCase");
  // the error must be copy/pastable
  await panel.getByRole('button', { name: 'Copy' }).click();
  await expect(panel.getByRole('button', { name: 'Copied!' })).toBeVisible();
  const panelText = await panel.innerText();
  console.log('PANEL_DETAILS=' + JSON.stringify(panelText.slice(0, 700)));
  await page.screenshot({ path: 'test-results/recovery-panel-stack.png' });
});
