import { test, expect } from '@playwright/test';
import { waitForAppReady } from '../fixtures/test-utils';

/**
 * Homebrew save-validation feedback (feature/fix-orcbrew-errors):
 *  - the banner names only the fields that are actually a problem,
 *  - empty required fields are flagged amber, filled-but-invalid ones red,
 *  - each cue clears as its field is edited.
 * Driven on the advanced (prod) build via the real builder UI.
 */

async function readMessage(page: import('@playwright/test').Page): Promise<string> {
  // Error banner renders with bg-red; scope to it so the cookie-consent notice
  // (also a ".message") isn't picked up.
  const banner = page.locator('.message.bg-red').first();
  await banner.waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
  return (await banner.innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
}

test.describe('background builder', () => {
  let nameInput, sourceInput, saveBtn;

  test.beforeEach(async ({ page }) => {
    await page.goto('/pages/dnd/5e/background-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(1000);
    const textInputs = page.locator('input[type="text"], input:not([type])');
    nameInput = textInputs.nth(1);
    sourceInput = page.locator('input[placeholder="Default Option Source"]').first();
    saveBtn = page.getByRole('button', { name: /save to browser/i }).first();
  });

  test('names only the unfilled field', async ({ page }) => {
    await nameInput.fill('Testbg');
    await sourceInput.fill('');
    await saveBtn.click({ force: true });
    const msg = await readMessage(page);
    console.log(`NAME ONLY -> ${msg}`);
    expect(msg).toMatch(/Please fill in/i);
    expect(msg).toContain('Option Source Name');
    expect(msg).not.toMatch(/ and /); // single field, no "and"

    await page.reload();
    await waitForAppReady(page);
    await page.waitForTimeout(800);
    const n2 = page.locator('input[type="text"], input:not([type])').nth(1);
    const src2 = page.locator('input[placeholder="Default Option Source"]').first();
    await src2.fill('My Pack');
    await n2.fill('');
    await page.getByRole('button', { name: /save to browser/i }).first().click({ force: true });
    const msg2 = await readMessage(page);
    console.log(`SOURCE ONLY -> ${msg2}`);
    expect(msg2).toMatch(/Please fill in/i);
    expect(msg2).not.toContain('Option Source Name');
  });

  test('empty fields are flagged amber and clear as filled', async ({ page }) => {
    await nameInput.fill('Testflag');
    await sourceInput.fill('');
    await saveBtn.click({ force: true });
    await page.waitForTimeout(800);
    await expect(sourceInput).toHaveClass(/builder-field-unfilled/);
    await expect(nameInput).not.toHaveClass(/builder-field-unfilled/);

    await sourceInput.fill('My Pack'); // editing clears the cue
    await page.waitForTimeout(300);
    await expect(sourceInput).not.toHaveClass(/builder-field-unfilled/);
  });

  test('LIVE: a digit-led name flags red as typed, before any save', async ({ page }) => {
    await nameInput.fill('9Bad'); // no save
    await page.waitForTimeout(300);
    await expect(nameInput).toHaveClass(/builder-field-invalid/);
    await expect(page.getByText(/Name must start with a letter/i).first()).toBeVisible();
    // Fixing it clears the live cue immediately.
    await nameInput.fill('Good');
    await page.waitForTimeout(300);
    await expect(nameInput).not.toHaveClass(/builder-field-invalid/);
  });

  test('a filled-but-invalid name is flagged RED with an explanation', async ({ page }) => {
    // A name that starts with a digit is present but rejected by starts-with-letter?.
    await nameInput.fill('123Bad');
    await sourceInput.fill('My Pack');
    await saveBtn.click({ force: true });
    const msg = await readMessage(page);
    console.log(`INVALID NAME -> ${msg}`);
    expect(msg).toMatch(/must start with a letter/i);
    expect(msg).not.toMatch(/Please fill in/i); // it's filled, not missing
    await expect(nameInput).toHaveClass(/builder-field-invalid/);
    await expect(nameInput).not.toHaveClass(/builder-field-unfilled/);
  });
});

test('spell builder uses the specific-field message too', async ({ page }) => {
  await page.goto('/pages/dnd/5e/spell-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(1000);
  await page.getByRole('button', { name: /save to browser/i }).first().click({ force: true });
  const msg = await readMessage(page);
  console.log(`SPELL all-blank -> ${msg}`);
  expect(msg).toMatch(/Please fill in/i);
  expect(msg).toContain('Name');
});
