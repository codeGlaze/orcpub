import { test, expect } from '@playwright/test';

/**
 * Live test of the GENERAL recovery panel (the modal that must handle failures
 * we did NOT anticipate). We mount the real compiled `error-boundary` wrapping a
 * child that throws an arbitrary error — the stand-in for an "unknown" render
 * failure — with the real `feature-render-error` fallback, and confirm:
 *   - the boundary CONTAINS the throw (no black screen),
 *   - the panel names the failing SECTION (generic locator, no cause-guessing),
 *   - recovery actions + the raw error are present.
 */
test('recovery panel contains an unknown render failure and locates the section', async ({ page }) => {
  test.setTimeout(120000);
  const consoleErrs: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') consoleErrs.push(m.text().slice(0, 200)); });

  await page.setViewportSize({ width: 1100, height: 900 });
  await page.goto('/');
  // dev app shell ready
  await page.waitForFunction(() => {
    const w = window as any;
    return !!(w.reagent && w.reagent.core && w.reagent.core.render && w.orcpub && w.orcpub.dnd
      && w.orcpub.dnd.e5 && w.orcpub.dnd.e5.views && w.orcpub.dnd.e5.views.error_boundary
      && w.orcpub.dnd.e5.views.feature_render_error);
  }, { timeout: 30000 });

  const mounted = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, views = w.orcpub.dnd.e5.views,
      rc = w.reagent.core;
    const boom = function () {
      throw new Error("TypeError: cannot read properties of undefined (reading 'name')");
    };
    const child = c.vector.call(null, boom);
    const fallback = function (error: any, retry: any) {
      return c.vector.call(null, views.feature_render_error, 'Features', null, error, retry);
    };
    const tree = c.vector.call(null, views.error_boundary, fallback, child);
    const div = document.createElement('div');
    div.id = 'bs-harness';
    (div as any).style = 'max-width:780px;margin:30px auto;';
    document.body.innerHTML = '';
    document.body.appendChild(div);
    rc.render.call(null, tree, div);
    return true;
  });
  expect(mounted).toBe(true);

  const harness = page.locator('#bs-harness');
  await expect(harness).toContainText('The Features section couldn’t be displayed', { timeout: 10000 });
  await expect(harness).toContainText('Try again');
  await expect(harness).toContainText('Reload page');

  await page.screenshot({ path: 'test-results/recovery-panel-collapsed.png' });

  // reveal technical details → the raw error must be present for a bug report
  await harness.getByText('Show technical details').click();
  await expect(harness).toContainText("cannot read properties of undefined (reading 'name')");
  await page.screenshot({ path: 'test-results/recovery-panel-expanded.png' });

  // the boundary must NOT have leaked into a blank app; harness still has content
  expect((await harness.innerText()).length).toBeGreaterThan(40);

  console.log('CONSOLE_ERRS=' + JSON.stringify(consoleErrs.slice(0, 8)));
});
