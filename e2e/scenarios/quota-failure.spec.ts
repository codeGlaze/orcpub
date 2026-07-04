import { test, expect } from '@playwright/test';

// B3/S4: a localStorage write that fails on a full quota used to be swallowed
// silently — `set-item` caught the error and moved on — so the just-saved
// homebrew lived only in memory and vanished on the next refresh with NO warning.
// Now the save path surfaces the failure and offers an immediate raw backup.

test('a full-storage save is surfaced with a backup offer', async ({ page }) => {
  // Make writes to the 'plugins' key throw (quota full), leave others writable.
  await page.addInitScript(() => {
    const orig = window.localStorage.setItem.bind(window.localStorage);
    window.localStorage.setItem = (k: string, v: string) => {
      if (k === 'plugins') {
        throw new DOMException('exceeded the quota', 'QuotaExceededError');
      }
      return orig(k, v);
    };
  });

  await page.goto('/pages/dnd/5e/class-builder', { waitUntil: 'load' });
  await page.waitForFunction(
    () => (document.querySelector('#app')?.childElementCount ?? 0) > 0,
    null, { timeout: 60000 });

  // Persist a valid library — the write to 'plugins' will throw inside
  // plugins->local-store, which must surface the failure (not swallow it).
  await page.evaluate(() => {
    const m = (window as any).cljs.reader.read_string(
      '{"Pack" {:orcpub.dnd.e5/classes {:artificer {:name "Artificer" :key :artificer :option-pack "Pack"}}}}');
    (window as any).re_frame.core.dispatch(
      (window as any).cljs.core.PersistentVector.fromArray(
        [(window as any).cljs.core.keyword('orcpub.dnd.e5', 'set-plugins'), m], true));
  });

  // The failure is surfaced with the escape hatch.
  await expect(
    page.getByText("Couldn't save to browser storage", { exact: false }).last(),
    'quota failure is surfaced, not swallowed',
  ).toBeVisible({ timeout: 10000 });
  await expect(
    page.getByText('Download a full backup now', { exact: true }).last(),
    'an immediate raw backup is offered',
  ).toBeVisible();
});
