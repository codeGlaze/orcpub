import { test, expect } from '@playwright/test';
import { readFileSync } from 'fs';

// The real-world proof the user asked for: an "ideal export" is worthless until
// you re-import it AND *use* the content — errors surface at use-time, not import.
// This drives the FULL chain in the real compiled app against the cruft shapes
// distilled from the orcbrew catalog survey (false-cruft :spell-lists + a {nil nil}
// stray entry — test/fixtures/cruft-shapes.orcbrew):
//   import (real cljs pipeline) -> export from My Content (strip-export-blanks)
//   -> assert the exported file is IDEAL -> clear -> re-import ONLY that file
//   -> build a character with the homebrew class -> assert it builds, no errors.

const CRUFT = readFileSync('../test/fixtures/cruft-shapes.orcbrew', 'utf8');

const importPlugin = (page, name: string, edn: string) =>
  page.evaluate(({ name, edn }) => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('orcpub.dnd.e5', 'import-plugin'), name, edn], true));
  }, { name, edn });

test('import cruft → export ideal file → re-import → build a character with it', async ({ page }) => {
  const errs: string[] = [];
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  page.on('pageerror', e => errs.push('PAGEERROR: ' + e.message));

  // ---- 1. import the cruft pak through the REAL cljs import pipeline ----
  await page.goto('/pages/dnd/5e/class-builder', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await importPlugin(page, 'Cruft Shapes', CRUFT);
  await page.waitForTimeout(500);
  const stored = await page.evaluate(() => localStorage.getItem('plugins'));
  expect(stored, 'imported pak landed in plugins').toContain('Cruft Shapes');
  // import auto-cleans nils but NOT the false-cruft — that's the export's job.
  expect(stored, 'false-cruft survives import (export must strip it)').toMatch(/false/);

  // ---- 2. export from My Content → strip-export-blanks produces the file ----
  await page.evaluate(() => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('route'), w.cljs.core.keyword('my-content-5e-page')], true));
  });
  await page.waitForTimeout(800);
  const source = page.getByText('Cruft Shapes', { exact: true }).first();
  await expect(source).toBeVisible({ timeout: 10000 });
  await source.click();
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.locator('button.form-button', { hasText: /^export$/ }).first().click(),
  ]);
  const exported = readFileSync(await download.path(), 'utf8');

  // ---- 3. the exported file is IDEAL: cruft gone, real data kept ----
  expect(exported, 'nil-key cruft stripped').not.toContain('nil nil');
  expect(exported, 'false-cruft stripped').not.toContain('false');
  expect(exported, 'real spell-list kept').toContain(':wizard true');
  expect(exported, 'real skill prof kept').toContain(':athletics true');
  expect(exported, 'real content kept').toContain('Test Sidekick');
  expect(exported, 'spell kept').toContain('Test Cantrip');

  // ---- 4. re-import ONLY the exported file into a clean library ----
  await page.evaluate(() => localStorage.removeItem('plugins'));
  await page.goto('/pages/dnd/5e/class-builder', { waitUntil: 'load' });
  await page.waitForTimeout(1000);
  await importPlugin(page, 'Cruft Shapes', exported);
  await page.waitForTimeout(500);
  const reimported = await page.evaluate(() => localStorage.getItem('plugins'));
  expect(reimported, 'exported file re-imports cleanly').toContain('Test Sidekick');

  // ---- 5. USE it: build a character with the homebrew class ----
  await page.goto('/pages/dnd/5e/character-builder', { waitUntil: 'load' });
  await page.waitForTimeout(2000);
  await page.getByText('Class / Level', { exact: true }).first().click();
  await page.waitForTimeout(1000);
  const dropdown = page.locator('select:has(option:text-is("Test Sidekick"))').first();
  await expect(dropdown, 'homebrew class is an available option').toHaveCount(1);
  await dropdown.selectOption({ label: 'Test Sidekick' });
  await page.waitForTimeout(1500);

  // the character actually built from the homebrew class's data
  const body = await page.evaluate(() => document.body.innerText);
  const mounted = await page.evaluate(() => (document.querySelector('#app')?.childElementCount ?? 0) > 0);
  expect(mounted, 'app still mounted after using the content').toBe(true);
  expect(body, 'built with the class hit die (D8)').toMatch(/D8/i);
  expect(body, 'real prof from the homebrew flowed into the character').toContain('Athletics');
  expect(errs, `no console/page errors during import→export→reimport→use: ${errs.join(' | ')}`)
    .toHaveLength(0);
});
