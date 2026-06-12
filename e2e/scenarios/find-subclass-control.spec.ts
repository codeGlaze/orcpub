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

test('locate the subclass + superior-defense controls', async ({ page }) => {
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
  // scroll to bottom to force-render lazy sections
  await page.mouse.wheel(0, 4000);
  await page.waitForTimeout(1500);

  const info = await page.evaluate(() => {
    const txt = (document.body.innerText || '');
    const grab = (re: RegExp) => (txt.match(re) || []).slice(0, 3);
    return {
      selects: Array.from(document.querySelectorAll('select')).map((s: any) => ({
        cls: s.className.split(' ').slice(0, 2).join(' '),
        value: s.value,
        opts: Array.from(s.options).map((o: any) => o.textContent).slice(0, 20),
      })),
      sectionTabs: Array.from(document.querySelectorAll('[class*=tab],[class*=section-header]'))
        .map((e: any) => (e.innerText || '').trim()).filter(Boolean).slice(0, 30),
      mentionsConclave: /conclave|archetype/i.test(txt),
      mentionsHunter: /hunter/i.test(txt),
      mentionsSuperior: /superior hunter/i.test(txt),
      hunterContext: grab(/.{0,40}(conclave|archetype|hunter).{0,40}/gi),
    };
  });
  console.log('BUILDER=' + JSON.stringify(info, null, 1));
  await page.screenshot({ path: 'test-results/builder-ranger15.png', fullPage: true });
});
