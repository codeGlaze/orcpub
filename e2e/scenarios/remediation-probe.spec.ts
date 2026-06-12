import { test } from '@playwright/test';
import { waitForAppReady, takeScreenshot } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(4000); }
}

test('PROBE: select Cleric, can we reach re-frame + see the spell-selection key?', async ({ page }, testInfo) => {
  test.setTimeout(120000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);

  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});

  // Select Cleric on Class/Level.
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1500);
  await page.locator('select.builder-option-dropdown').first().selectOption({ label: 'Cleric' }).catch((e) => console.log('SELECT_ERR=' + e.message.slice(0, 80)));
  await page.waitForTimeout(2000);
  await takeScreenshot(page, testInfo, 'cleric-selected');

  // Probe re-frame accessibility + dump the character's class option keys.
  const probe = await page.evaluate(() => {
    const w = window as any;
    const out: any = { rfPaths: [] };
    const rf = w.re_frame;
    out.has_re_frame = !!rf;
    out.has_app_db = !!(rf && rf.db && rf.db.app_db);
    out.has_dispatch = !!(rf && rf.core && rf.core.dispatch);
    out.has_read_string = !!(w.cljs && w.cljs.reader && w.cljs.reader.read_string);
    out.has_pr_str = !!(w.cljs && w.cljs.core && w.cljs.core.pr_str);
    try {
      if (rf && rf.db && rf.db.app_db && w.cljs && w.cljs.core) {
        const db = w.cljs.core.deref ? w.cljs.core.deref(rf.db.app_db) : rf.db.app_db.state;
        const charKw = w.cljs.core.keyword.call(null, 'character');
        const character = w.cljs.core.get.call(null, db, charKw);
        out.character_edn = character ? w.cljs.core.pr_str(character).slice(0, 1500) : 'no character in db';
      }
    } catch (e: any) { out.probe_err = String(e).slice(0, 200); }
    return out;
  });
  console.log('PROBE=' + JSON.stringify(probe));
});
