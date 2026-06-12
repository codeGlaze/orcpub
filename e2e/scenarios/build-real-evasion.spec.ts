import { test } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(3000); }
}

test('build a real Ranger 15 / Hunter / Evasion and capture traits + options', async ({ page }) => {
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
  await page.mouse.wheel(0, 3000);
  await page.waitForTimeout(1000);

  // select the Hunter archetype (clickable option, not a dropdown)
  await page.getByText('Hunter', { exact: true }).first().click().catch((e) => console.log('HUNTER_CLICK_ERR=' + e));
  await page.waitForTimeout(2500);
  await page.mouse.wheel(0, 3000);
  await page.waitForTimeout(1000);
  // Superior Hunter's Defense should now exist; pick Evasion
  await page.getByText('Evasion', { exact: true }).first().click().catch((e) => console.log('EVASION_CLICK_ERR=' + e));
  await page.waitForTimeout(2500);

  const out = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame;
    const kw = (s: string) => c.keyword.call(null, s);
    const db = c.deref(rf.db.app_db);
    const character = c.get.call(null, db, kw('character'));
    const optionsEdn = c.pr_str.call(null, c.get.call(null, character, kw('orcpub.entity/options')));
    // real traits (builder character, no id)
    const sub = rf.core.subscribe.call(null, c.vector.call(null, kw('orcpub.dnd.e5.character/traits')));
    const traits = c.deref(sub);
    const n = c.count.call(null, traits);
    const nameless: string[] = [];
    for (let i = 0; i < n; i++) {
      const t = c.nth.call(null, traits, i);
      const nm = c.get.call(null, t, kw('name'));
      if (nm === null || nm === undefined || (typeof nm === 'string' && nm.trim() === '')) nameless.push(c.pr_str.call(null, t));
    }
    return {
      totalTraits: n,
      namelessCount: nameless.length,
      nameless,
      optionsLen: optionsEdn.length,
      optionsSnippet: optionsEdn.slice(0, 1200),
    };
  });
  console.log('BUILD=' + JSON.stringify(out, null, 1));
});
