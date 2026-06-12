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

test('probe: locate subclass + Evasion clickable options for Ranger 15', async ({ page }) => {
  test.setTimeout(200000);
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
  await page.locator('select.builder-option-dropdown').first().selectOption({ label: 'Ranger' }).catch(() => {});
  await page.waitForTimeout(1200);
  await page.locator('select.builder-option-dropdown').nth(1).selectOption({ label: '15' }).catch(() => {});
  await page.waitForTimeout(2500);

  const info = await page.evaluate(() => {
    const out: any = {};
    const body = document.body.innerText || '';
    // contexts around the relevant feature names
    const ctx = (kw: string) => { const i = body.indexOf(kw); return i < 0 ? null : body.slice(Math.max(0, i - 40), i + 80).replace(/\n/g, ' | '); };
    out.ctx = {
      conclave: ctx('Conclave'), archetype: ctx('Archetype'), hunter: ctx('Hunter'),
      superior: ctx("Superior Hunter"), evasion: ctx('Evasion'),
      stand: ctx('Stand Against'), uncanny: ctx('Uncanny Dodge'),
    };
    // for each exact-text match of Hunter/Evasion, climb to the nearest clickable ancestor
    const clickableInfo = (label: string) => {
      const hits: any[] = [];
      document.querySelectorAll('*').forEach((el: any) => {
        const own = Array.from(el.childNodes).filter((n: any) => n.nodeType === 3).map((n: any) => n.textContent).join('').trim();
        if (own === label) {
          let p: any = el, depth = 0, clickable = null;
          while (p && depth < 6) {
            const cs = getComputedStyle(p);
            if (cs.cursor === 'pointer' || p.onclick || /pointer|clickable|option-row|builder-option/.test((p.className || '').toString())) {
              clickable = { tag: p.tagName, cls: (p.className || '').toString().slice(0, 70), cursor: cs.cursor };
              break;
            }
            p = p.parentElement; depth++;
          }
          hits.push({ tag: el.tagName, cls: (el.className || '').toString().slice(0, 50), clickableAncestor: clickable });
        }
      });
      return hits.slice(0, 6);
    };
    out.hunter = clickableInfo('Hunter');
    out.evasion = clickableInfo('Evasion');
    return out;
  });
  console.log('INFO=' + JSON.stringify(info, null, 1));
  await page.screenshot({ path: 'test-results/ranger-l15-state.png', fullPage: true }).catch(() => {});
});
