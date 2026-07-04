import { test, expect } from '@playwright/test';
import { seedSelection, clickSave } from '../lib/builder';

// Meaningful-errors: a NESTED failure must name the specific element. A user
// builds a selection whose 2nd option name is "9 Lives" (looks fine, derives the
// invalid keyword :9-lives). The save banner used to say a generic "Name"; it now
// pinpoints "Option 2 Name must start with a letter" so the user knows exactly
// what to fix. Proven against the real selection builder + its real save handler.

const SELECTION_WITH_BAD_OPTION =
  '{:name "Mystic Disciplines" :option-pack "Void Pack" ' +
  ' :options [{:name "Good Discipline" :description "fine"} ' +
  '           {:name "9 Lives" :description "bad name, derives :9-lives"}]}';

test('selection save banner names the specific bad option (Option 2)', async ({ page }) => {
  await seedSelection(page, SELECTION_WITH_BAD_OPTION);
  await clickSave(page);

  // The banner pinpoints the offending nested element, not a bare "Name".
  await expect(
    page.getByText('Option 2 Name', { exact: false }).last(),
    'banner localises the failure to Option 2',
  ).toBeVisible({ timeout: 10000 });
  await expect(
    page.getByText('must start with a letter', { exact: false }).last(),
    'banner explains WHY it failed',
  ).toBeVisible();
});
