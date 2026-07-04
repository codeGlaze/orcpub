import { test, expect } from '@playwright/test';

// A2 follow-up: in-progress builder WIP now survives a page refresh in EVERY
// homebrew builder, not just the class builder. The persist side was already
// wired per builder; this proves the generic RESTORE (db/builder-wip-stores +
// the :local-store-builder-items cofx) actually rehydrates a spread of builders.

const NAME_INPUT = '.field:has(.personality-label span:text-is("Name")) input';

const builders = [
  { label: 'Spell',    url: '/pages/dnd/5e/spell-builder',    store: 'spell' },
  { label: 'Feat',     url: '/pages/dnd/5e/feat-builder',     store: 'feat' },
  { label: 'Race',     url: '/pages/dnd/5e/race-builder',     store: 'race' },
  { label: 'Subclass', url: '/pages/dnd/5e/subclass-builder', store: 'subclass' },
];

for (const { label, url, store } of builders) {
  test(`${label} builder WIP survives a refresh`, async ({ page }) => {
    const wip = `WIP ${label} 12345`;
    await page.goto(url, { waitUntil: 'load' });
    const name = page.locator(NAME_INPUT).first();
    await expect(name).toBeVisible({ timeout: 30000 });
    await name.fill(wip);
    // the persist interceptor writes to localStorage on the edit
    await expect
      .poll(() => page.evaluate((k) => localStorage.getItem(k) || '', store), { timeout: 5000 })
      .toContain(wip);

    // refresh — the boot restore must rehydrate the builder-item
    await page.reload({ waitUntil: 'load' });
    const restored = page.locator(NAME_INPUT).first();
    await expect(restored, `${label} WIP restored after refresh`).toHaveValue(wip, { timeout: 30000 });
  });
}
