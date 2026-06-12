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
async function resolveConflictsIfAny(page: any) {
  const rename = page.getByText('RENAME ALL', { exact: false });
  if (await rename.count().catch(() => 0)) {
    await rename.first().click().catch(() => {});
    await page.waitForTimeout(2000);
    await page.getByText(/RESOLVE ALL/i).first().click().catch(() => {});
    await page.waitForTimeout(7000);
  }
}

test('OBSERVE Divine Soul sorcerer character + plugin class shape', async ({ page }) => {
  test.setTimeout(180000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);

  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/test-pak.orcbrew'));
  await page.waitForTimeout(7000);
  await resolveConflictsIfAny(page);

  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1500);
  await page.locator('select.builder-option-dropdown').first()
    .selectOption({ label: 'Sorcerer (Divine Soul)' }).catch((e) => console.log('SEL_ERR=' + e.message.slice(0, 80)));
  await page.waitForTimeout(2000);

  const probe = await page.evaluate(() => {
    const w = window as any, rf = w.re_frame, c = w.cljs.core;
    const kw = (s: string) => c.keyword.call(null, s);
    try {
      const db = c.deref(rf.db.app_db);
      const character = c.get.call(null, db, kw('character'));
      const charEdn = c.pr_str(character);
      // class entry key
      const m = charEdn.match(/:class \[\{:orcpub.entity\/key (\S+)/);
      // loaded plugin classes: db :plugins keys + class names
      const plugins = c.get.call(null, db, kw('orcpub.dnd.e5/plugins'));
      const pluginEdn = plugins ? c.pr_str(plugins) : 'none';
      const sorc = pluginEdn.match(/:sorcerer-divine-soul\S*/);
      const nameM = pluginEdn.match(/"Sorcerer \(Divine Soul\)"/);
      return {
        ok: true,
        classEntryKey: m ? m[1] : 'not found',
        sorcKeyInPlugins: sorc ? sorc[0] : 'not found',
        hasNameSorcDivineSoul: !!nameM,
        charSample: charEdn.slice(0, 700),
      };
    } catch (e: any) { return { err: String(e).slice(0, 300) }; }
  });
  console.log('DS_PROBE=' + JSON.stringify(probe));
});
