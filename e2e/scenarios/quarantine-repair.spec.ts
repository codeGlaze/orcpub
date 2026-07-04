import { test, expect, Page } from '@playwright/test';
import { gotoMyContent } from '../lib/builder';

// B2.4/B2.5 — the full quarantine-for-repair loop in the real frontend.
// Two sources are quarantined: A is the keyword trap (a class named "9 Lives" →
// invalid key :9-lives) and is repairable by rename; B is missing an option-pack
// (valid key) and isn't auto-repairable, so it stays and offers raw export.
// Repairing A must move it into the live library and out of quarantine, while B
// remains — and that state must survive a reload.

const QUARANTINED =
  '{"Trapped Pack" {:orcpub.dnd.e5/classes ' +
  '   {:9-lives {:name "9 Lives" :key :9-lives :option-pack "Trapped Pack"}}} ' +
  ' "Sourceless Pack" {:orcpub.dnd.e5/classes ' +
  '   {:wanderer {:name "Wanderer" :key :wanderer}}}}'; // no :option-pack

const mounted = (page: Page) =>
  page.waitForFunction(
    () => (document.querySelector('#app')?.childElementCount ?? 0) > 0,
    null, { timeout: 60000 });

const ls = (page: Page, key: string) =>
  page.evaluate((k) => localStorage.getItem(k) ?? '', key);

test('quarantined source is surfaced, repaired by rename, and restored to the library', async ({ page }) => {
  await page.goto('/', { waitUntil: 'load' });
  await mounted(page);
  // Seed the quarantine map, then reload so the boot loader reads it into app-db.
  await page.evaluate((q) => localStorage.setItem('plugins:rejected', q), QUARANTINED);
  await page.reload({ waitUntil: 'load' });
  await mounted(page);

  await gotoMyContent(page);

  // The panel surfaces BOTH quarantined sources with a humanized headline.
  await expect(page.getByText('2 quarantined sources', { exact: false })).toBeVisible({ timeout: 10000 });
  await expect(page.getByText('Trapped Pack', { exact: true })).toBeVisible();
  await expect(page.getByText('Sourceless Pack', { exact: true })).toBeVisible();

  // A's repairable trap item shows its name in an editable input; B has none
  // (not auto-repairable) — so there is exactly one repair input on the page.
  const nameInput = page.locator('input[type="text"]');
  await expect(nameInput).toHaveValue('9 Lives');

  // Live validation: a still-invalid name is flagged and blocks restore.
  await nameInput.fill('1 Bad');
  await expect(page.getByText('must start with a letter', { exact: false })).toBeVisible();

  // A valid name clears the error; fix and restore.
  await nameInput.fill('Nine Lives');
  await expect(page.getByText('must start with a letter', { exact: false })).toHaveCount(0);
  await page.getByRole('button', { name: 'Repair & Restore' }).click();
  await page.waitForTimeout(400);

  // A landed in the live library (re-keyed) and left quarantine; B remains.
  const plugins = await ls(page, 'plugins');
  expect(plugins, 'repaired source persisted to the library').toContain('Trapped Pack');
  expect(plugins, 'item re-keyed from its corrected name').toContain(':nine-lives');
  const rejected = await ls(page, 'plugins:rejected');
  expect(rejected, 'repaired source removed from quarantine').not.toContain('Trapped Pack');
  expect(rejected, 'the still-broken source stays quarantined').toContain('Sourceless Pack');

  // The panel now shows only the remaining one.
  await expect(page.getByText('1 quarantined source', { exact: false })).toBeVisible();

  // State holds across a reload: A stays in the library, B stays quarantined.
  await page.reload({ waitUntil: 'load' });
  await mounted(page);
  await gotoMyContent(page);
  await expect(page.getByText('1 quarantined source', { exact: false })).toBeVisible({ timeout: 10000 });
  await expect(page.getByText('Sourceless Pack', { exact: true })).toBeVisible();
  expect(await ls(page, 'plugins'), 'repaired source survived reload').toContain(':nine-lives');
});
