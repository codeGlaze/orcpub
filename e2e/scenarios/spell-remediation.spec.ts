import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(4000); }
}

/**
 * App-level proof of the cantrip/spell-selection remediation.
 *
 * Builds a real Cleric, then injects the PRODUCTION-BUG state: a poisoned
 * spell-selection key (:cleric-source-cantrips-known) holding two cantrips.
 * Dispatching :set-character runs the load-time reconciler. We assert it heals
 * the key back to canonical (:cleric-cantrips-known) WITH the cantrips intact —
 * i.e. the user does NOT have to re-pick spells.
 */
test('reconciler heals a poisoned spell-selection key and preserves the cantrips', async ({ page }) => {
  test.setTimeout(120000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);

  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1500);
  await page.locator('select.builder-option-dropdown').first().selectOption({ label: 'Cleric' }).catch(() => {});
  await page.waitForTimeout(1500);

  const result = await page.evaluate(() => {
    const w = window as any;
    const rf = w.re_frame, c = w.cljs.core;
    const kw = (s: string) => c.keyword.call(null, s);
    try {
      const db = c.deref(rf.db.app_db);
      const character = c.get.call(null, db, kw('character'));
      const edn = c.pr_str(character);
      // Inject a poisoned spell-selection key into the cleric's options map.
      const marker = ':orcpub.entity/key :cleric, :orcpub.entity/options {';
      const idx = edn.indexOf(marker);
      if (idx < 0) return { err: 'cleric marker not found', sample: edn.slice(0, 400) };
      const inject = ':cleric-source-cantrips-known [{:orcpub.entity/key :guidance} {:orcpub.entity/key :light}] ';
      const poisonedEdn = edn.slice(0, idx + marker.length) + inject + edn.slice(idx + marker.length);
      const poisonedChar = w.cljs.reader.read_string(poisonedEdn);

      // Run the load path (reconciler lives in :set-character).
      rf.core.dispatch_sync.call(null, c.vector.call(null, kw('set-character'), poisonedChar));

      const char2 = c.get.call(null, c.deref(rf.db.app_db), kw('character'));
      const edn2 = c.pr_str(char2);
      // count cantrips under the canonical key, if present
      const m = edn2.match(/:cleric-cantrips-known \[(.*?)\]/);
      const cantripCount = m ? (m[1].match(/:orcpub.entity\/key/g) || []).length : 0;
      return {
        ok: true,
        injectedPoison: poisonedEdn.includes(':cleric-source-cantrips-known'),
        healedToCanonical: edn2.includes(':cleric-cantrips-known'),
        stillPoisoned: edn2.includes(':cleric-source-cantrips-known'),
        cantripCountUnderCanonical: cantripCount,
      };
    } catch (e: any) { return { err: String(e).slice(0, 300) }; }
  });
  console.log('REMEDIATION=' + JSON.stringify(result));

  expect(result.err, 'probe should not error').toBeFalsy();
  expect(result.injectedPoison, 'poisoned key should have been injected').toBeTruthy();
  expect(result.healedToCanonical, 'reconciler should produce the canonical :cleric-cantrips-known').toBeTruthy();
  expect(result.stillPoisoned, 'the poisoned :cleric-source-cantrips-known should be gone').toBeFalsy();
  expect(result.cantripCountUnderCanonical, 'both cantrips should survive under the healed key').toBe(2);
});
