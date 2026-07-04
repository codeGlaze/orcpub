import { test, expect } from '@playwright/test';
import { BUGGED, seedClass, clickSave, clickSaveAnyway, plugins } from '../lib/builder';

// Rigor across error SHAPES — the rescue paths shouldn't only handle the easy
// "missing option source" case. Each variant is a fully-built class broken a
// different way.

test('bad name (starts with a digit): Save anyway still rescues it', async ({ page }) => {
  // "Looks fine, why won't it save?" — :name must start with a letter.
  await seedClass(page, BUGGED.badName);
  await clickSave(page);
  await clickSaveAnyway(page);
  await page.waitForTimeout(500);
  // Best-effort: content is preserved in My Content, not stranded (the resilient
  // loader will quarantine-for-repair on next load if the name is still invalid).
  const p = await plugins(page);
  expect(p, 'rich class preserved despite bad name').toContain('5th-Edition Voidcaller');
  expect(p, 'features preserved').toContain('Void Bolt');
});

test('multiple errors at once (no source + bad name + nameless trait): Save anyway lands it', async ({ page }) => {
  await seedClass(page, BUGGED.multiError);
  await clickSave(page);
  await clickSaveAnyway(page);
  await page.waitForTimeout(500);
  const p = await plugins(page);
  expect(p, 'class preserved').toContain('9 Lives Sorcerer');
  expect(p, 'placeholder source applied').toContain('Unsorted Homebrew');
  expect(p, 'nameless trait remediated').toContain('[Missing Trait Name]');
});

test('nameless trait alone does NOT block a normal save (auto-filled)', async ({ page }) => {
  // Boundary doc: ::homebrew-class does not validate traits, and reg-save-homebrew
  // runs fill-all-missing-fields before validating, so a valid-named/sourced class
  // with a nameless feature saves normally with the feature auto-named.
  await seedClass(page, BUGGED.validDespiteNamelessTrait);
  await clickSave(page);
  await page.waitForTimeout(500);
  const p = await plugins(page);
  expect(p, 'saved under its real source').toContain('Void Pack');
  expect(p, 'nameless feature auto-named on normal save').toContain('[Missing Trait Name]');
});
