import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(3000); }
}

// Prove fault-isolation at the SELECTION level: prune a selection from the REAL
// options tree, rebuild with the pure entity/build, and see whether the failure
// (nil-name sort throw) disappears. The selection whose removal fixes it is the
// builder choice to re-pick.
test('selection-level isolation traces the failure to the real builder choice', async ({ page }) => {
  test.setTimeout(150000);
  await page.setViewportSize({ width: 1440, height: 1400 });
  await page.goto('/');
  await passInterstitial(page);
  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1200);
  const dd = page.locator('select.builder-option-dropdown');
  await dd.first().selectOption({ label: 'Ranger' }).catch(() => {});
  await page.waitForTimeout(1500);
  await dd.nth(1).selectOption({ value: 'level-15' }).catch(() => {});
  await page.waitForTimeout(2500);
  await page.mouse.wheel(0, 3000); await page.waitForTimeout(1000);
  await page.getByText('Hunter', { exact: true }).first().click().catch(() => {});
  await page.waitForTimeout(2500);
  await page.mouse.wheel(0, 3000); await page.waitForTimeout(1000);
  await page.getByText('Evasion', { exact: true }).first().click().catch(() => {});
  await page.waitForTimeout(2500);

  const result = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame;
    const kw = (s: string) => c.keyword.call(null, s);
    const OPTS = kw('orcpub.entity/options');
    const db = c.deref(rf.db.app_db);
    const character = c.get.call(null, db, OPTS) ? null : null; // placeholder
    const charEntity = c.get.call(null, db, kw('character'));
    const baseOptions = c.get.call(null, charEntity, OPTS);
    const template = c.deref(rf.core.subscribe.call(null, c.vector.call(null, kw('built-template'))));

    // does the section fail (nil-name sort throw) for a given options tree?
    const fails = (opts: any) => {
      try {
        const ch2 = c.assoc.call(null, charEntity, OPTS, opts);
        const built = w.orcpub.entity.build.call(null, ch2, template);
        const traits = w.orcpub.dnd.e5.character.traits.call(null, built);
        w.orcpub.common.aloof_sort_by.call(null, kw('name'), traits); // throws on nil name (reverted sort)
        return false;
      } catch (e: any) { return true; }
    };

    // index of level-3 in the :levels vector
    const lvlPath = c.vector.call(null, kw('class'), 0, OPTS, kw('levels'));
    const levels = c.get_in.call(null, baseOptions, lvlPath);
    let l3 = -1;
    for (let i = 0; i < c.count.call(null, levels); i++) {
      const k = c.get.call(null, c.nth.call(null, levels, i), kw('orcpub.entity/key'));
      if (k != null && c.name.call(null, k) === 'level-3') { l3 = i; break; }
    }
    const hunterOptsPath = c.vector.call(null, kw('class'), 0, OPTS, kw('levels'), l3, OPTS, kw('ranger-archetype'), OPTS);
    const l3OptsPath = c.vector.call(null, kw('class'), 0, OPTS, kw('levels'), l3, OPTS);

    const pruneEvasion = c.update_in.call(null, baseOptions, hunterOptsPath, c.dissoc, kw('superior-hunters-defense'));
    const pruneArchetype = c.update_in.call(null, baseOptions, l3OptsPath, c.dissoc, kw('ranger-archetype'));
    const pruneClass = c.dissoc.call(null, baseOptions, kw('class'));
    // control: remove an unrelated level entry (level-2, index 1) — Evasion stays
    const pruneLevel2 = c.update_in.call(null, baseOptions, lvlPath, function (v: any) {
      return c.vec.call(null, c.concat.call(null, c.subvec.call(null, v, 0, 1), c.subvec.call(null, v, 2)));
    });

    return {
      baseline_fails: fails(baseOptions),
      prune_superior_hunters_defense_evasion: fails(pruneEvasion),
      prune_ranger_archetype_hunter: fails(pruneArchetype),
      prune_class_ranger: fails(pruneClass),
      control_prune_level2: fails(pruneLevel2),
    };
  });
  console.log('SELECTION_ISOLATION=' + JSON.stringify(result, null, 2));

  // baseline must fail; pruning the Evasion-bearing selections must FIX it;
  // pruning an unrelated selection must NOT.
  expect(result.baseline_fails).toBe(true);
  expect(result.prune_superior_hunters_defense_evasion).toBe(false);
  expect(result.prune_ranger_archetype_hunter).toBe(false);
  expect(result.prune_class_ranger).toBe(false);
  expect(result.control_prune_level2).toBe(true);
});
