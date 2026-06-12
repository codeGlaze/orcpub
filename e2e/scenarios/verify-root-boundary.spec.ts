import { test, expect } from '@playwright/test';

// Goal #1: never black-screen. Verify (a) wrapping the root didn't regress normal
// rendering, and (b) the EXACT root-boundary wiring main-view uses catches a throw
// into app-error-fallback instead of unmounting the tree.
test('root boundary: app renders normally AND catches a page-level throw', async ({ page }) => {
  test.setTimeout(90000);
  const pageErrors: string[] = [];
  page.on('pageerror', (e) => pageErrors.push(String(e).slice(0, 120)));
  await page.setViewportSize({ width: 1100, height: 900 });

  // (a) smoke: the app renders real content, not a blank page
  await page.goto('/');
  await page.waitForFunction(() => {
    const w = window as any;
    return !!(w.orcpub && w.orcpub.dnd && w.orcpub.dnd.e5 && w.orcpub.dnd.e5.views
      && w.orcpub.dnd.e5.views.error_boundary && w.orcpub.dnd.e5.views.app_error_fallback
      && w.reagent && w.reagent.core);
  }, { timeout: 30000 });
  const appText = (await page.locator('#app').innerText()).trim();
  console.log('APP_RENDERS_LEN=' + appText.length);
  expect(appText.length, 'app rendered real content (no regression)').toBeGreaterThan(50);

  // (b) the exact wiring from main-view, with a page component that throws
  await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, views = w.orcpub.dnd.e5.views, rc = w.reagent.core;
    function SomePageThatThrows() { throw new Error('boom in a page region outside any inner boundary'); }
    const tree = c.vector.call(null, views.error_boundary,
      (error: any, stack: any, retry: any) =>
        c.vector.call(null, views.app_error_fallback, error, stack, retry),
      c.vector.call(null, SomePageThatThrows));
    const div = document.createElement('div');
    div.id = 'root-test';
    document.body.innerHTML = '';
    document.body.appendChild(div);
    w.ReactDOM.render(rc.as_element.call(null, tree), div);
  });
  const rt = page.locator('#root-test');
  await expect(rt).toContainText('Something went wrong on this page');  // caught, NOT black screen
  await expect(rt).toContainText('Reload page');
  expect((await rt.innerText()).length).toBeGreaterThan(40);
  console.log('PAGE_ERRORS=' + JSON.stringify(pageErrors.slice(0, 5)));
});
