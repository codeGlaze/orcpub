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

// Build a real Ranger 15 / Hunter / Evasion and dump the REAL trait fields.
test('inspect the real Hunter Evasion trait', async ({ page }) => {
  test.setTimeout(180000);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);
  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1200);

  // class = Ranger, level = 15
  const dd = page.locator('select.builder-option-dropdown');
  await dd.first().selectOption({ label: 'Ranger' }).catch(() => {});
  await page.waitForTimeout(1500);
  // level dropdown (2nd builder-option-dropdown on the Class/Level row)
  await dd.nth(1).selectOption({ value: 'level-15' }).catch(async () => {
    await dd.nth(1).selectOption({ label: '15' }).catch(() => {});
  });
  await page.waitForTimeout(2000);

  // dump every select so we can see the subclass + superior-defense choices
  const selects = await page.evaluate(() =>
    Array.from(document.querySelectorAll('select')).map((s, i) => ({
      i, cls: (s as HTMLSelectElement).className.split(' ').slice(0, 2).join(' '),
      value: (s as HTMLSelectElement).value,
      opts: Array.from((s as HTMLSelectElement).options).map((o) => o.textContent).slice(0, 25),
    })));
  console.log('SELECTS=' + JSON.stringify(selects, null, 1));

  // try to pick Hunter and Evasion wherever they appear
  for (const wanted of ['Hunter', 'Evasion']) {
    const sels = await page.locator('select').all();
    for (const s of sels) {
      const labels = await s.locator('option').allTextContents();
      if (labels.some((l) => new RegExp(wanted, 'i').test(l))) {
        await s.selectOption({ label: labels.find((l) => new RegExp(wanted, 'i').test(l))! }).catch(() => {});
        await page.waitForTimeout(1500);
        break;
      }
    }
  }

  // read the REAL traits (builder character = no id) and find the nameless one(s)
  const traits = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame;
    const kw = (s: string) => c.keyword.call(null, s);
    const sub = rf.core.subscribe.call(null, c.vector.call(null, kw('orcpub.dnd.e5.character/traits')));
    const v = c.deref(sub);
    const all = c.pr_str.call(null, v);
    // find maps with no :name or blank :name
    const nameless: string[] = [];
    const n = c.count.call(null, v);
    for (let i = 0; i < n; i++) {
      const t = c.nth.call(null, v, i);
      const nm = c.get.call(null, t, kw('name'));
      if (nm === null || nm === undefined || (typeof nm === 'string' && nm.trim() === '')) {
        nameless.push(c.pr_str.call(null, t));
      }
    }
    return { totalTraits: n, nameless, allLen: all.length };
  });
  console.log('REAL_TRAITS=' + JSON.stringify(traits, null, 2));
});
