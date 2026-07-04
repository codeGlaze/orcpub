import { test, expect } from '@playwright/test';
import { readFileSync } from 'fs';

// Companion to roundtrip-import-export-use (which proves the HAPPY path: valid,
// crufty content builds a character). This proves genuinely-BROKEN-but-loadable
// content — the kind a user or hand-edit produces, which published paks don't
// contain — never breaks the APP when it's USED. Errors surface at use-time, so
// importing isn't enough: we drive the malformed class into a real character
// build.
//
// The bad data lives in a real, grab-able fixture (test/fixtures/broken-content
// .orcbrew). It is broken many ways at once (missing modifier :value, nil :type,
// bogus values, a nil modifier in the vector, malformed :selections with min>max,
// a spell with no `true` in :spell-lists, false/nil cruft, a wrong-typed race, a
// dangling subclass ref) — but its KEYS are valid, so it LOADS. (The separate
// keyword-trap case, where a source is quarantined for rename, lives in
// keyword-trap.orcbrew + import-keyword-trap-repair.spec.ts.)
//
// Bar: graceful degradation. The app uses what it can, ignores what it can't, and
// NEVER crashes (no pageerror, stays mounted). Meaningful console errors are
// allowed (that's the app doing its job); we assert on crashes, not log noise.

const BROKEN = readFileSync('../test/fixtures/broken-content.orcbrew', 'utf8');

test('thoroughly-broken content survives import AND use (no app crash)', async ({ page }) => {
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text().slice(0, 160)); });
  page.on('pageerror', e => pageErrors.push(e.message.slice(0, 200)));

  // ---- import the broken pak through the real cljs pipeline ----
  await page.goto('/pages/dnd/5e/class-builder', { waitUntil: 'load' });
  await page.waitForTimeout(1200);
  const importResult = await page.evaluate((edn) => {
    const w = window as any;
    try {
      w.re_frame.core.dispatch(w.cljs.core.PersistentVector.fromArray(
        [w.cljs.core.keyword('orcpub.dnd.e5', 'import-plugin'), 'Broken On Purpose', edn], true));
      return 'ok';
    } catch (e) { return 'THREW: ' + (e as Error).message; }
  }, BROKEN);
  expect(importResult, 'import must not throw on broken content').toBe('ok');
  await page.waitForTimeout(600);
  expect(await page.evaluate(() => (document.querySelector('#app')?.childElementCount ?? 0) > 0),
    'app still mounted after importing broken content').toBe(true);

  // ---- USE it: build a character with the malformed class ----
  await page.goto('/pages/dnd/5e/character-builder', { waitUntil: 'load' });
  await page.waitForTimeout(2000);
  await page.getByText('Class / Level', { exact: true }).first().click();
  await page.waitForTimeout(1000);

  const dropdown = page.locator('select:has(option:text-is("Broken Class"))').first();
  await expect(dropdown, 'broken class loaded and is offered as an option').toHaveCount(1);
  // selecting a malformed class must apply-what-it-can, not throw
  await dropdown.selectOption({ label: 'Broken Class' });
  await page.waitForTimeout(1800);

  const mounted = await page.evaluate(() => (document.querySelector('#app')?.childElementCount ?? 0) > 0);
  expect(mounted, 'app survives USING the broken class (graceful degradation)').toBe(true);
  // the one valid modifier still took effect (D8 hit die) — partial build, not blank
  const body = await page.evaluate(() => document.body.innerText);
  expect(body, 'the class still partially built (hit die applied)').toMatch(/D8/i);
  // the resilience bar: NO crashes (pageerror / unhandled exception) anywhere.
  expect(pageErrors, `no unhandled crash using broken content: ${pageErrors.join(' | ')}`)
    .toHaveLength(0);
  if (consoleErrors.length) console.log('(console errors, allowed):', consoleErrors.slice(0, 4).join(' | '));
});
