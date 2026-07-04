import { test, expect } from '@playwright/test';
import { seedPlugins, gotoMyContent } from '../lib/builder';

// B6: a NORMAL export must not ship meaningless blanks (false flags left by
// toggling on/off, :disabled? false, empty collections) — but must keep the real
// data. Proven against the real My Content "export" button.

const CRUFT =
  '{"Cruft Pack" {:orcpub.dnd.e5/feats ' +
  '  {:lucky {:option-pack "Cruft Pack" :name "Lucky" :key :lucky ' +
  '          :disabled? false ' +                         // meaningless -> drop
  '          :props {:skill-prof {:athletics true ' +     // real -> keep
  '                               :stealth false ' +       // cruft -> drop
  '                               :arcana false}}}}}}';     // cruft -> drop

test('normal export strips false/empty cruft but keeps real data', async ({ page }) => {
  await seedPlugins(page, CRUFT);
  await gotoMyContent(page);

  const source = page.getByText('Cruft Pack', { exact: true }).first();
  await expect(source).toBeVisible({ timeout: 10000 });
  await source.click(); // expand the source

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.locator('button.form-button', { hasText: /^export$/ }).first().click(),
  ]);
  const fs = require('fs');
  const content = fs.readFileSync(await download.path(), 'utf8');

  // real data is kept
  expect(content, 'real proficiency kept').toContain(':athletics true');
  expect(content, 'name kept').toContain('Lucky');
  // meaningless blanks are gone
  expect(content, 'false skill-prof cruft stripped').not.toContain('stealth');
  expect(content, 'false skill-prof cruft stripped').not.toContain('arcana');
  expect(content, ':disabled? false stripped').not.toContain('disabled?');
});
