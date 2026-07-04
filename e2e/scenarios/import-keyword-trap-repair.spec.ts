import { test, expect } from '@playwright/test';
import { readFileSync } from 'fs';

// Regression for the silent-hide bug: a keyword-trap item (a name that derives a
// key not starting with a letter, e.g. "9 Lives" -> :9-lives) used to pass the
// progressive IMPORT check (which only requires :option-pack), land straight in
// :plugins, and then its homebrew classes would SILENTLY never appear in the
// character builder — imported content that vanishes with no explanation.
//
// The fix routes such a source to the SAME quarantine the boot loader uses, so
// the existing repair UI (rename -> rekey -> restore) surfaces it. This proves
// the full loop in the real app: import -> surfaced+quarantined (not silent) ->
// rename in My Content -> the class becomes usable. Reads the grab-able fixture
// test/fixtures/keyword-trap.orcbrew.

const TRAP = readFileSync('../test/fixtures/keyword-trap.orcbrew', 'utf8');
const SRC = 'Keyword Trap Pack';

test('imported keyword-trap is surfaced + repairable, not silently hidden', async ({ page }) => {
  const pageErrors: string[] = [];
  page.on('pageerror', e => pageErrors.push(e.message.slice(0, 160)));

  // ---- import the trapped content through the real cljs pipeline ----
  await page.goto('/pages/dnd/5e/class-builder', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.evaluate(({ edn, src }) => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('orcpub.dnd.e5', 'import-plugin'), src, edn], true));
  }, { edn: TRAP, src: SRC });
  await page.waitForTimeout(700);

  // NOT silently accepted into the live library; quarantined instead.
  expect(await page.evaluate(() => localStorage.getItem('plugins') || ''),
    'trapped source must NOT be silently stored in :plugins').not.toContain(SRC);
  expect(await page.evaluate(() => localStorage.getItem('plugins:rejected') || ''),
    'trapped source is quarantined for repair').toContain(SRC);

  // ---- it's surfaced in My Content with a repair (rename) UI ----
  await page.evaluate(() => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('route'), w.cljs.core.keyword('my-content-5e-page')], true));
  });
  await page.waitForTimeout(900);
  await expect(page.getByText(/number or symbol/i).first(),
    'the panel explains WHY it could not load').toBeVisible({ timeout: 10000 });
  const renameInput = page.locator('input.input').first();
  await expect(renameInput, 'a rename field is offered').toBeVisible();

  // ---- repair: rename to a valid name and restore ----
  await renameInput.fill('Nine Lives');
  await page.waitForTimeout(300);
  await page.getByText('Repair & Restore').first().click();
  await page.waitForTimeout(1000);

  // the source moved into the live library with a valid key, quarantine cleared.
  const plugins = await page.evaluate(() => localStorage.getItem('plugins') || '');
  expect(plugins, 'repaired source lands in :plugins with a valid key').toContain(':nine-lives');
  expect(plugins, 'the invalid key is gone').not.toContain(':9-lives');
  expect(await page.evaluate(() => localStorage.getItem('plugins:rejected') || '{}'),
    'quarantine cleared after repair').not.toContain('Trap Pack');

  // ---- the previously-hidden class is now usable in the builder ----
  await page.goto('/pages/dnd/5e/character-builder', { waitUntil: 'load' });
  await page.waitForTimeout(2000);
  await page.getByText('Class / Level', { exact: true }).first().click();
  await page.waitForTimeout(1000);
  await expect(page.locator('select:has(option:text-is("Nine Lives"))').first(),
    'the repaired class now appears as a selectable option').toHaveCount(1);
  await page.locator('select:has(option:text-is("Nine Lives"))').first()
    .selectOption({ label: 'Nine Lives' });
  await page.waitForTimeout(1200);
  expect(await page.evaluate(() => (document.querySelector('#app')?.childElementCount ?? 0) > 0),
    'character builds with the repaired class').toBe(true);
  expect(pageErrors, `no crashes through the whole flow: ${pageErrors.join(' | ')}`).toHaveLength(0);
});

// The import path and the boot loader now use the SAME quarantine, so a source
// quarantined on import must survive a reload unchanged — the boot loader must
// not lose it, promote it into :plugins, or double-quarantine it. (Import writes
// plugins:rejected; on boot the loader reads clean :plugins and the ::rejected
// cofx rehydrates the panel from that persisted store.)
test('a quarantined import survives a reload (import + boot-load agree)', async ({ page }) => {
  await page.goto('/pages/dnd/5e/class-builder', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  await page.evaluate(({ edn, src }) => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('orcpub.dnd.e5', 'import-plugin'), src, edn], true));
  }, { edn: TRAP, src: SRC });
  await page.waitForTimeout(700);
  expect(await page.evaluate(() => localStorage.getItem('plugins:rejected') || ''),
    'quarantined on import').toContain(SRC);

  // reload — the boot loader runs on a fresh init
  await page.goto('/', { waitUntil: 'load' });
  await page.waitForFunction(
    () => (document.querySelector('#app')?.childElementCount ?? 0) > 0, null, { timeout: 60000 });
  await page.waitForTimeout(800);

  // still exactly one quarantine record; not lost, not promoted into :plugins
  const rejected = await page.evaluate(() => localStorage.getItem('plugins:rejected') || '');
  const plugins = await page.evaluate(() => localStorage.getItem('plugins') || '');
  expect(rejected, 'still quarantined after reload').toContain(SRC);
  expect(plugins, 'must not have been promoted into the live library').not.toContain(':9-lives');
  // count actual quarantine records (top-level keys), not string hits — the
  // source name also appears as the :option-pack value, so a substring count lies.
  const recordCount = await page.evaluate(() => {
    const w = window as any;
    const s = localStorage.getItem('plugins:rejected');
    return s ? w.cljs.core.count(w.cljs.reader.read_string(s)) : 0;
  });
  expect(recordCount, 'exactly one quarantine record (not double-quarantined)').toBe(1);

  // and it's still surfaced + repairable in My Content after the reload
  await page.evaluate(() => {
    const w = window as any;
    w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
      [w.cljs.core.keyword('route'), w.cljs.core.keyword('my-content-5e-page')], true));
  });
  await page.waitForTimeout(900);
  await expect(page.getByText('Repair & Restore').first(),
    'repair UI still available after reload').toBeVisible({ timeout: 10000 });
});
