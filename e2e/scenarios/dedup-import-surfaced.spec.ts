import { test, expect } from '@playwright/test';
import { CLASS_BUILDER, NAME_INPUT, gotoMyContent } from '../lib/builder';

// When an import auto-dedups duplicate options within a Selection's choice list,
// the user must be able to SEE what happened (options derive their key from their
// name, so same-named options collide and one would silently override the other).
// Dedup used to land as raw EDN in the collapsed "Advanced Details"; it now shows
// a readable line in the visible "Data Cleanup" section so the user can rename a
// "Foo 2" to something better if they want.

// A source whose Selection lists "Defense" twice (identical) + one "Dueling".
const ORCBREW =
  '{:orcpub.dnd.e5/selections ' +
  ' {:fighting-style {:option-pack "DupTest" :name "Fighting Style" ' +
  '   :options [{:name "Defense" :description "AC +1"} ' +
  '             {:name "Defense" :description "AC +1"} ' +
  '             {:name "Dueling" :description "damage +2"}]}}}';

test('import dedup is surfaced readably in the import log (not raw EDN)', async ({ page }) => {
  await page.goto(CLASS_BUILDER, { waitUntil: 'load' });
  await expect(page.locator(NAME_INPUT).first()).toBeVisible({ timeout: 30000 });
  await gotoMyContent(page);

  // Drive the real .orcbrew file-input import path.
  await page.locator('input[type="file"]').setInputFiles({
    name: 'DupTest.orcbrew',
    mimeType: 'application/octet-stream',
    buffer: Buffer.from(ORCBREW),
  });

  // The import-log panel auto-opens (there were changes). The dedup shows up in
  // the human "Data Cleanup" section with a readable description — not pr-str.
  await expect(
    page.getByText('Data Cleanup', { exact: false }),
    'dedup is promoted to a visible, named section',
  ).toBeVisible({ timeout: 10000 });
  await expect(
    page.getByText('Deduplicated', { exact: false }),
    'readable headline, not raw EDN',
  ).toBeVisible();
  await expect(
    page.getByText("Removed 1 duplicate option(s) named 'Defense'", { exact: false }),
    'names the specific option and what was done',
  ).toBeVisible();

  // And the import actually landed the deduped source in the library.
  const plugins = await page.evaluate(() => localStorage.getItem('plugins') ?? '');
  expect(plugins).toContain('Fighting Style');
});
