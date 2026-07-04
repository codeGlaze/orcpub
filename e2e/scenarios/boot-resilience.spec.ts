import { test, expect, Page } from '@playwright/test';

// Everyday-experience guards: the real compiled app must BOOT (no black screen)
// and not lose valid content across the cases the loader runs on every page load.

const appMounted = (page: Page) =>
  page.waitForFunction(
    () => (document.querySelector('#app')?.childElementCount ?? 0) > 0,
    null, { timeout: 60000 });

const seed = (page: Page, value: string | null) =>
  page.addInitScript((v) => {
    if (v === null) window.localStorage.removeItem('plugins');
    else window.localStorage.setItem('plugins', v as string);
  }, value);

const VALID =
  '{"Pack One" {:orcpub.dnd.e5/classes {:artificer {:name "Artificer" :key :artificer :option-pack "Pack One"}}} ' +
  ' "Pack Two" {:orcpub.dnd.e5/spells {:fireball {:name "Fireball" :key :fireball :option-pack "Pack Two"}}}}';

test('valid library: app boots and NOTHING is quarantined (everyday user unaffected)', async ({ page }) => {
  const warnings: string[] = [];
  page.on('console', m => { if (m.type() === 'warning') warnings.push(m.text()); });
  await seed(page, VALID);
  await page.goto('/', { waitUntil: 'load' });
  await appMounted(page);
  await page.waitForTimeout(500);

  const ls = await page.evaluate(() => ({
    rejected: localStorage.getItem('plugins:rejected'),
  }));
  expect(ls.rejected, 'valid library must NOT quarantine anything').toBeNull();
  expect(warnings.join('\n')).not.toMatch(/Quarantined/);
});

test('brand-new user (no stored plugins): app boots clean', async ({ page }) => {
  await seed(page, null);
  await page.goto('/', { waitUntil: 'load' });
  await appMounted(page);
  await page.waitForTimeout(300);
  const ls = await page.evaluate(() => ({
    rejected: localStorage.getItem('plugins:rejected'),
  }));
  expect(ls.rejected).toBeNull();
});

test('corrupt non-map storage: app still boots (no black screen) and raw is preserved', async ({ page }) => {
  await seed(page, ':not-a-plugins-map');
  await page.goto('/', { waitUntil: 'load' });
  await appMounted(page); // the real guard: app mounts, no white screen
  await page.waitForTimeout(400);
  const ls = await page.evaluate(() => ({
    corrupt: localStorage.getItem('plugins:corrupt'),
    rejected: localStorage.getItem('plugins:rejected'),
  }));
  // B2.1: a parsed-but-not-a-map blob goes to :corrupt, NOT :rejected (which is
  // reserved for the clean name-keyed quarantine map and must not be clobbered).
  expect(ls.corrupt, 'raw corrupt value preserved, not discarded').toContain('not-a-plugins-map');
  expect(ls.rejected, 'name-keyed quarantine map not clobbered by a raw blob').toBeNull();
});

// B2.0/F5: an UNREADABLE (parse-error) blob — e.g. a write cut off mid-stream by
// a quota error — used to be DELETED on read, destroying the homebrew before the
// loader could preserve it. Now it's moved to plugins:corrupt and the active slot
// cleared, so the app boots and the data survives for recovery.
const TRUNCATED =
  '{"Pack" {:orcpub.dnd.e5/classes {:artificer {:name "Artif';

test('unreadable (truncated) storage: app boots and the raw blob is preserved, not deleted', async ({ page }) => {
  await seed(page, TRUNCATED);
  await page.goto('/', { waitUntil: 'load' });
  await appMounted(page); // boots despite an unparseable library
  await page.waitForTimeout(400);
  const ls = await page.evaluate(() => ({
    active: localStorage.getItem('plugins'),
    corrupt: localStorage.getItem('plugins:corrupt'),
  }));
  expect(ls.corrupt, 'truncated blob preserved verbatim for recovery').toBe(TRUNCATED);
  expect(ls.active, 'active slot cleared so the poison value cannot brick boot').toBeNull();
});
