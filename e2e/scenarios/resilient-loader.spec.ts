import { test, expect, Page } from '@playwright/test';

// One VALID source (item has :option-pack) and one INVALID source (class item
// missing the required :option-pack, so the source fails ::e5/plugin). This is
// exactly the shape that made the OLD all-or-nothing loader discard the ENTIRE
// homebrew library. See docs/HOMEBREW_DATA_LOSS.md §3A/§6.2.
const GOOD = '"Good Pack" {:orcpub.dnd.e5/classes {:artificer {:name "Artificer" :key :artificer :option-pack "Good Pack"}}}';
const BAD  = '"Bad Pack" {:orcpub.dnd.e5/classes {:broken {:name "Broken"}}}';
const STORED = `{${GOOD} ${BAD}}`;

test('resilient loader keeps valid sources, quarantines invalid', async ({ page }) => {
  const warnings: string[] = [];
  page.on('console', m => { if (m.type() === 'warning') warnings.push(m.text()); });

  // Seed localStorage BEFORE any app script runs, so the boot-time
  // (dispatch-sync [:initialize-db]) exercises the resilient loader cofx.
  await page.addInitScript((stored) => {
    window.localStorage.setItem('plugins', stored as string);
  }, STORED);

  await page.goto('/', { waitUntil: 'load' });
  await page.waitForFunction(
    () => (document.querySelector('#app')?.childElementCount ?? 0) > 0,
    null, { timeout: 60000 });
  await page.waitForTimeout(750);

  const ls = await page.evaluate(() => ({
    rejected: localStorage.getItem('plugins:rejected'),
  }));

  // PROOF the loader salvaged rather than nuked. The OLD loader returned nil and
  // dropped the whole library; the resilient loader keeps the valid source and
  // quarantines only the invalid one.
  expect(ls.rejected, 'invalid source quarantined for repair').toContain('Bad Pack');
  expect(ls.rejected, 'valid source must NOT be quarantined').not.toContain('Good Pack');
  expect(warnings.join('\n')).toMatch(/Quarantined \d+ invalid homebrew source/);
});

// B2.1/F4: the quarantine map is name-keyed, ACCUMULATES across loads, and
// SELF-CLEARS when a source is repaired. The old loader overwrote the whole
// rejected blob per load (losing earlier records) and never cleared it.
// Seed via evaluate()+reload (not addInitScript, which re-runs on every reload
// and would clobber the boot-2 state); poll localStorage so we read AFTER the
// boot-time loader has written, with no fixed-timeout flakiness under parallel load.
const mounted = (page: Page) =>
  page.waitForFunction(
    () => (document.querySelector('#app')?.childElementCount ?? 0) > 0,
    null, { timeout: 60000 });

const rejectedNow = (page: Page) =>
  page.evaluate(() => localStorage.getItem('plugins:rejected') ?? '');

test('quarantine accumulates a second bad source and clears a repaired one', async ({ page }) => {
  await page.goto('/', { waitUntil: 'load' });
  await mounted(page);

  // Boot 1: one bad source already quarantined from a prior session; the active
  // library has a DIFFERENT bad source (the first was dropped from :plugins by an
  // earlier save) plus a valid one.
  await page.evaluate(() => {
    localStorage.setItem('plugins:rejected',
      '{"Bad Pack" {:orcpub.dnd.e5/classes {:broken {:name "Broken"}}}}');
    localStorage.setItem('plugins',
      '{"Good Pack" {:orcpub.dnd.e5/classes {:artificer {:name "Artificer" :key :artificer :option-pack "Good Pack"}}} ' +
      '"Other Bad" {:orcpub.dnd.e5/spells {:zap {:name "Zap"}}}}');
  });
  await page.reload({ waitUntil: 'load' });
  await mounted(page);

  // Accumulated: BOTH the earlier record and the newly-rejected source are present.
  await expect.poll(() => rejectedNow(page), { timeout: 15000 }).toContain('Other Bad');
  expect(await rejectedNow(page), 'earlier-quarantined source retained').toContain('Bad Pack');

  // Boot 2: both bad sources are now repaired in the active library, under their
  // ORIGINAL names (now valid). Reconcile drops a quarantine entry when a source
  // of the same name is present-and-valid → both ghosts clear, the map empties,
  // and the key self-clears.
  await page.evaluate(() => {
    localStorage.setItem('plugins',
      '{"Good Pack" {:orcpub.dnd.e5/classes {:artificer {:name "Artificer" :key :artificer :option-pack "Good Pack"}}} ' +
      '"Bad Pack" {:orcpub.dnd.e5/classes {:broken {:name "Broken" :key :broken :option-pack "Bad Pack"}}} ' +
      '"Other Bad" {:orcpub.dnd.e5/spells {:zap {:name "Zap" :key :zap :option-pack "Other Bad" :level 1 :school "evocation" :spell-lists {:wizard true}}}}}');
  });
  await page.reload({ waitUntil: 'load' });
  await mounted(page);

  await expect.poll(
    () => page.evaluate(() => localStorage.getItem('plugins:rejected')),
    { timeout: 15000, message: 'all sources repaired → quarantine empties → key self-cleared' },
  ).toBeNull();
});
