import { test, expect } from '@playwright/test';
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

/**
 * App-level remediation proof for a homebrew spellcaster.
 * Import test-PAK (Divine Soul sorcerer), build a character with it, inject a
 * poisoned spell-selection key holding 2 cantrips, dispatch :set-character, and
 * assert the reconciler heals it to the canonical key (with the cantrips intact).
 *
 * Canonical derives from the class KEY, so we don't hardcode it — we find the
 * surviving *-cantrips-known key under the class entry by pattern.
 */
test('reconciler heals a poisoned homebrew spell key + keeps the cantrips', async ({ page }) => {
  test.setTimeout(200000);
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
    .selectOption({ label: 'Sorcerer (Divine Soul)' }).catch(() => {});
  await page.waitForTimeout(2000);

  const result = await page.evaluate(() => {
    const w = window as any, rf = w.re_frame, c = w.cljs.core;
    const kw = (s: string) => c.keyword.call(null, s);
    try {
      const db = c.deref(rf.db.app_db);
      const edn = c.pr_str(c.get.call(null, db, kw('character')));
      const km = edn.match(/:class \[\{:orcpub.entity\/key (\S+?),/);
      if (!km) return { err: 'class key not found', sample: edn.slice(0, 400) };
      const classKey = km[1];                          // e.g. :sorcerer-divine-soul-
      const ckName = classKey.replace(/^:/, '');       // sorcerer-divine-soul-
      const poisonedKey = ':' + ckName + 'xanathars-cantrips-known'; // pre-fix orphan, same suffix

      const marker = ':class [{:orcpub.entity/key ' + classKey + ', :orcpub.entity/options {';
      const idx = edn.indexOf(marker);
      if (idx < 0) return { err: 'marker not found', marker, sample: edn.slice(0, 400) };
      const inject = poisonedKey + ' [{:orcpub.entity/key :guidance} {:orcpub.entity/key :light}] ';
      const poisonedEdn = edn.slice(0, idx + marker.length) + inject + edn.slice(idx + marker.length);

      rf.core.dispatch_sync.call(null, c.vector.call(null, kw('set-character'), w.cljs.reader.read_string(poisonedEdn)));

      const edn2 = c.pr_str(c.get.call(null, c.deref(rf.db.app_db), kw('character')));
      const re = new RegExp('(:' + ckName.replace(/[-]/g, '\\-') + '\\S*?cantrips-known) \\[(.*?)\\]', 'g');
      const found = [...edn2.matchAll(re)].map((m: any) => ({ key: m[1], count: (m[2].match(/:orcpub.entity\/key/g) || []).length }));
      const healed = found.find((k: any) => k.key !== poisonedKey);
      return {
        ok: true, classKey, poisonedKey,
        stillPoisoned: edn2.includes(poisonedKey),
        healedKey: healed ? healed.key : null,
        cantripCount: healed ? healed.count : 0,
        allCantripKeys: found.map((f: any) => f.key),
      };
    } catch (e: any) { return { err: String(e).slice(0, 300) }; }
  });
  console.log('HB_REMEDIATION=' + JSON.stringify(result));

  expect(result.err, 'no probe error').toBeFalsy();
  expect(result.stillPoisoned, 'poisoned key should be gone').toBeFalsy();
  expect(result.healedKey, 'a class-key-derived cantrips-known key should remain').toBeTruthy();
  expect(result.cantripCount, 'both cantrips preserved under the healed key').toBe(2);
});
