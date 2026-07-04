import { Page, expect } from '@playwright/test';

// Shared helpers + realistic bugged-content variants, so error-path tests
// exercise rich, nested content and a VARIETY of error shapes — not one easy
// case. All variants are fully-built classes (hit-die, ability levels, subclass
// title, multiple features) that differ only in how they're broken.

export const CLASS_BUILDER = '/pages/dnd/5e/class-builder';
export const NAME_INPUT =
  '.field:has(.personality-label span:text-is("Name")) input';

const TRAITS =
  '[{:level 1 :description "Obscure feature with NO name key."} ' +
  ' {:name "Void Bolt" :level 1 :description "A properly named feature."}]';

// Common rich body; callers prepend name / option-pack to vary the error.
const BODY =
  ' :hit-die 8 :ability-increase-levels [4 8 12 16 19] :subclass-level 3 ' +
  ' :subclass-title "Void Path" :traits ' + TRAITS + ' :level-modifiers []}';

/** Realistic, fully-built classes that each fail save for a different reason. */
export const BUGGED = {
  // Valid name, but NO option source -> save fails on :option-pack.
  missingSource: '{:name "Voidcaller"' + BODY,
  // Has an option source, but the name starts with a digit -> :name is INVALID
  // (must start with a letter). The "looks fine, why won't it save?" case.
  badName: '{:name "5th-Edition Voidcaller" :option-pack "Void Pack"' + BODY,
  // Everything wrong at once: no source AND a bad name AND a nameless trait.
  multiError: '{:name "9 Lives Sorcerer" ' +
    ' :hit-die 6 :ability-increase-levels [4 8 12 16 19] :subclass-level 1 ' +
    ' :subclass-title "Feline Bloodline" :traits ' + TRAITS + ' :level-modifiers []}',
  // Valid name + source but a nameless trait. NOTE: ::homebrew-class does not
  // validate traits, so this SAVES (the nameless trait is a render-time concern,
  // handled elsewhere) — kept to document that boundary.
  validDespiteNamelessTrait:
    '{:name "Voidcaller" :option-pack "Void Pack"' + BODY,
};

/** Seed the class builder-item via the same ::class5e/set-class event the app
 *  uses (dev :none build exposes cljs/re-frame on window). */
export async function seedClass(page: Page, edn: string) {
  await page.goto(CLASS_BUILDER, { waitUntil: 'load' });
  await expect(page.locator(NAME_INPUT).first()).toBeVisible({ timeout: 30000 });
  await page.evaluate((e) => {
    // @ts-ignore
    const m = cljs.reader.read_string(e);
    // @ts-ignore
    re_frame.core.dispatch(cljs.core.PersistentVector.fromArray(
      [cljs.core.keyword('orcpub.dnd.e5.classes', 'set-class'), m], true));
  }, edn);
  // wait for the builder to reflect the seed
  await page.waitForTimeout(400);
}

/** Seed app-db :plugins (the saved homebrew library) via ::e5/set-plugins, the
 *  same event a save dispatches. `edn` is a `{source-name plugin}` map. */
export async function seedPlugins(page: Page, edn: string) {
  await page.goto(CLASS_BUILDER, { waitUntil: 'load' });
  await expect(page.locator(NAME_INPUT).first()).toBeVisible({ timeout: 30000 });
  await page.evaluate((e) => {
    // @ts-ignore
    const m = cljs.reader.read_string(e);
    // @ts-ignore
    re_frame.core.dispatch(cljs.core.PersistentVector.fromArray(
      [cljs.core.keyword('orcpub.dnd.e5', 'set-plugins'), m], true));
  }, edn);
  await page.waitForTimeout(400);
}

export const SELECTION_BUILDER = '/pages/dnd/5e/selection-builder';

/** Seed the selection builder-item via ::selections5e/set-selection, the same
 *  event the app's edit flow uses. Mirrors seedClass. */
export async function seedSelection(page: Page, edn: string) {
  await page.goto(SELECTION_BUILDER, { waitUntil: 'load' });
  await expect(page.locator(NAME_INPUT).first()).toBeVisible({ timeout: 30000 });
  await page.evaluate((e) => {
    // @ts-ignore
    const m = cljs.reader.read_string(e);
    // @ts-ignore
    re_frame.core.dispatch(cljs.core.PersistentVector.fromArray(
      [cljs.core.keyword('orcpub.dnd.e5.selections', 'set-selection'), m], true));
  }, edn);
  await page.waitForTimeout(400);
}

const notSticky = (text: string) =>
  `button:has-text("${text}"):not(#sticky-header button)`;

export const clickSave = (page: Page) =>
  page.locator(notSticky('Save to Browser Storage')).first().click();

export async function clickSaveAnyway(page: Page) {
  // The banner renders twice (hidden #header-container copy + visible one).
  await page.getByText('Save anyway with placeholders', { exact: true }).last().click();
}

export async function exportDraft(page: Page) {
  const btn = page.locator(notSticky('Export draft')).first();
  await expect(btn).toBeVisible({ timeout: 30000 });
  const [download] = await Promise.all([page.waitForEvent('download'), btn.click()]);
  return download;
}

/** SPA-navigate to My Content (no reload), same [:route ...] the flyout uses. */
export async function gotoMyContent(page: Page) {
  await page.evaluate(() => {
    // @ts-ignore
    re_frame.core.dispatch(cljs.core.PersistentVector.fromArray(
      [cljs.core.keyword('route'), cljs.core.keyword('my-content-5e-page')], true));
  });
  await page.waitForTimeout(800);
}

export const plugins = (page: Page) =>
  page.evaluate(() => localStorage.getItem('plugins') ?? '');
