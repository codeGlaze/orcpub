import { test, expect } from '@playwright/test';
import { readFileSync } from 'fs';

// A1 follow-up: the Export-draft escape hatch is now wired into EVERY homebrew
// builder (not just Class), via events/builder-drafts + builder-page deriving the
// draft event from the save event. Proves a representative spread — each builder
// offers the button and produces a draft that dumps the WIP under the right
// content-type, so it re-imports like any .orcbrew.

const NAME_INPUT = '.field:has(.personality-label span:text-is("Name")) input';

const builders = [
  { label: 'Spell',      url: '/pages/dnd/5e/spell-builder',      ct: ':orcpub.dnd.e5/spells' },
  { label: 'Race',       url: '/pages/dnd/5e/race-builder',       ct: ':orcpub.dnd.e5/races' },
  { label: 'Feat',       url: '/pages/dnd/5e/feat-builder',       ct: ':orcpub.dnd.e5/feats' },
  { label: 'Background', url: '/pages/dnd/5e/background-builder',  ct: ':orcpub.dnd.e5/backgrounds' },
  { label: 'Subclass',   url: '/pages/dnd/5e/subclass-builder',    ct: ':orcpub.dnd.e5/subclasses' },
];

for (const { label, url, ct } of builders) {
  test(`Export draft works on the ${label} builder`, async ({ page }) => {
    await page.goto(url, { waitUntil: 'load' });
    const name = page.locator(NAME_INPUT).first();
    await expect(name).toBeVisible({ timeout: 30000 });
    await name.fill(`Draft ${label}`);

    const btn = page.locator('button:has-text("Export draft"):not(#sticky-header button)').first();
    await expect(btn, `${label} builder offers Export draft`).toBeVisible({ timeout: 30000 });
    const [download] = await Promise.all([page.waitForEvent('download'), btn.click()]);
    const content = readFileSync(await download.path(), 'utf8');

    // the draft is a normal single-source plugin dumping the WIP under its
    // content-type, so it can be re-imported.
    expect(content, `${label} draft carries the WIP name`).toContain(`Draft ${label}`);
    expect(content, `${label} draft dumps under the right content-type`).toContain(ct);
  });
}
