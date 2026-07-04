import { test, expect } from '@playwright/test';
import * as fs from 'fs';

// A1 — the paralysis-breaker. Content built in the class builder that is NOT
// saved to :plugins (so it's absent from My Content and a normal plugin export)
// must STILL be rescuable via "Export draft", which dumps the in-progress
// builder-item with no validation. See docs/HOMEBREW_REMEDIATION_ROADMAP.md A1.

const CLASS_BUILDER = '/pages/dnd/5e/class-builder';

test('Export draft rescues unsaved, unvalidated work-in-progress', async ({ page }) => {
  await page.goto(CLASS_BUILDER, { waitUntil: 'load' });
  // Builder rendered (Name field present).
  await expect(page.locator('.field:has(.personality-label span:text-is("Name")) input').first())
    .toBeVisible({ timeout: 30000 });

  // Type a name only — no option-pack, never saved. This is exactly the WIP that
  // can't go out the normal door.
  await page.locator('.field:has(.personality-label span:text-is("Name")) input').first()
    .fill('E2E Draft Class');

  // The escape hatch must exist (button is mirrored in main + sticky header).
  const draftBtn = page.locator('button:has-text("Export draft"):not(#sticky-header button)').first();
  await expect(draftBtn).toBeVisible();

  // ...and produce a file containing the WIP.
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    draftBtn.click(),
  ]);

  const content = fs.readFileSync(await download.path(), 'utf8');
  expect(download.suggestedFilename()).toContain('draft');
  expect(content, 'draft file contains the WIP class name').toContain('E2E Draft Class');
  expect(content, 'serialized under the right content-type, so it re-imports')
    .toContain(':orcpub.dnd.e5/classes');
});
