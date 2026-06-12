import { test } from '@playwright/test';
import * as path from 'path';
import { waitForAppReady } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(4000); }
}

test('debug builder selects', async ({ page }) => {
  test.setTimeout(200000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);

  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/nameless-trait-class.orcbrew'));
  await page.waitForTimeout(6000);
  const rename = page.getByText('RENAME ALL', { exact: false });
  if (await rename.count().catch(() => 0)) {
    await rename.first().click().catch(() => {});
    await page.waitForTimeout(2000);
    await page.getByText(/RESOLVE ALL/i).first().click().catch(() => {});
    await page.waitForTimeout(7000);
  }

  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1500);
  await page.screenshot({ path: 'test-results/debug-before.png', fullPage: true });

  // dump every select on the page
  const selects = await page.evaluate(() => {
    return Array.from(document.querySelectorAll('select')).map((s, i) => ({
      i, cls: s.className, value: (s as HTMLSelectElement).value,
      opts: Array.from((s as HTMLSelectElement).options).map((o) => ({ v: o.value, t: o.textContent })).slice(0, 30),
    }));
  });
  console.log('SELECTS=' + JSON.stringify(selects, null, 2));

  // try selecting Voidcaller on the first builder-option-dropdown, then dump char
  const dd = page.locator('select.builder-option-dropdown').first();
  await dd.selectOption({ label: 'Voidcaller (nameless-trait-class)' }).catch((e) => console.log('SELERR=' + e));
  await page.waitForTimeout(2500);
  await page.screenshot({ path: 'test-results/debug-after.png', fullPage: true });

  const after = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame;
    const kw = (s: string) => c.keyword.call(null, s);
    const db = c.deref(rf.db.app_db);
    const charEdn = c.pr_str(c.get.call(null, db, kw('character')));
    let traitsNoId = '<err>';
    try {
      const sub = rf.core.subscribe.call(null, c.vector.call(null, kw('orcpub.dnd.e5.character/traits')));
      traitsNoId = c.pr_str(c.deref(sub));
    } catch (e: any) { traitsNoId = 'ERR ' + String(e).slice(0, 120); }
    const ddval = (document.querySelector('select.builder-option-dropdown') as HTMLSelectElement)?.value;
    return { charLen: charEdn.length, charHead: charEdn.slice(0, 500), ddval, traitsNoId: traitsNoId.slice(0, 600) };
  });
  console.log('AFTER=' + JSON.stringify(after, null, 2));
});
