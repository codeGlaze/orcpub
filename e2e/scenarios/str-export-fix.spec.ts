import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import { BUGGED, seedClass, clickSave } from '../lib/builder';

// THE ORIGINAL BUG (str-fix). After a successful homebrew save, the warning
// banner's "click here to export" link dispatched ::e5/export-plugin with
// (str plugin) — a STRING. validate-before-export then ran its `map?` spec
// check against a string, which failed with the user-facing
// "Export validation failed ... cljs.core/map?" and produced NO export.
//
// The fix passes the plugin MAP itself (events.cljs:734,873). This proves the
// in-app post-save export link actually yields a valid .orcbrew in the real
// frontend — the one flow the str bug broke and that had no e2e until now.

test('post-save "export here" link exports a valid .orcbrew (str-bug regression)', async ({ page }) => {
  // A valid, fully-built class that saves normally under its own source, so the
  // post-save warning banner (with the "here" export link) appears.
  await seedClass(page, BUGGED.validDespiteNamelessTrait);
  await clickSave(page);

  // The save warning banner's "here" link calls ::e5/export-plugin with the
  // saved plugin. The `.black` underline link is unique to this warning banner
  // (error/save-anyway links use `.f-w-b`); `.last()` picks the visible copy.
  const hereLink = page.locator('span.pointer.underline.black', { hasText: /^here$/ }).last();
  await expect(hereLink).toBeVisible({ timeout: 10000 });

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    hereLink.click(),
  ]);
  const content = fs.readFileSync(await download.path(), 'utf8');

  // Must be EDN MAP data — not a stringified map (the bug would serialize a
  // quoted string), and not an error. Before the fix, no download fired at all.
  expect(content.trimStart().startsWith('{'),
    'export is a map literal, not a quoted string').toBeTruthy();
  expect(content).toContain('Voidcaller');
  expect(content).toContain(':orcpub.dnd.e5/classes');

  // And the str-bug's failure banner must NOT have appeared.
  await expect(page.getByText('contains invalid data')).toHaveCount(0);
});
