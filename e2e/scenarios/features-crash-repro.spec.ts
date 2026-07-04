import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

// Reproduce the universal Features-tab crash (React #31) on the dev build, where
// the error is readable and names the offending object.
test('features tab crash repro', async ({ page }) => {
  const msgs: string[] = [];
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) msgs.push(`[${m.type()}] ${m.text()}`); });
  page.on('pageerror', (e) => msgs.push(`[pageerror] ${e.message}\n${(e.stack || '').slice(0, 1500)}`));

  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2500);

  // Give the default character some features: pick a class in the Class/Level selector.
  const classSelect = page.locator('select.builder-option-dropdown').first();
  if (await classSelect.count()) {
    await classSelect.selectOption({ label: /ranger/i }).catch(async () => {
      await classSelect.selectOption({ index: 1 }).catch(() => {});
    });
    await page.waitForTimeout(1500);
  }

  // Click the FEATURES tab in the character display (right panel).
  const featuresTab = page.getByText(/^FEATURES$/i).first();
  await featuresTab.click({ force: true }).catch(() => {});
  await page.waitForTimeout(1500);

  // Surface fail-soft's "show technical details" if the panel appeared.
  const tech = page.getByText(/show technical details/i).first();
  if (await tech.isVisible().catch(() => false)) {
    await tech.click().catch(() => {});
    await page.waitForTimeout(500);
  }

  const bodyText = (await page.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
  const panelShown = /couldn.t be displayed/i.test(bodyText);

  console.log('\n===== PANEL SHOWN: ' + panelShown + ' =====');
  console.log('\n===== CONSOLE (' + msgs.length + ') =====');
  for (const m of msgs) console.log(m.slice(0, 1600) + '\n---');
  console.log('\n===== FULL BODY =====\n' + bodyText.slice(0, 4000));
  // also pull any <pre> dumps (guard-fallback prints the offending item's pr-str)
  const pres = await page.locator('pre').allInnerTexts().catch(() => []);
  console.log('\n===== PRE DUMPS (offending item data) =====');
  for (const p of pres) console.log('  ' + p.replace(/\s+/g, ' ').slice(0, 500));

  await page.screenshot({ path: 'test-results/features-crash.png', fullPage: true });
});
