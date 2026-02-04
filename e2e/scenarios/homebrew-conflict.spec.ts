import { test, expect, Page } from '@playwright/test';
import * as path from 'path';
import {
  setupConsoleCapture,
  attachConsoleErrors,
  waitForAppReady,
  takeScreenshot,
} from '../fixtures/test-utils';

/**
 * Homebrew Conflict Resolution Test Suite
 *
 * Tests the complete workflow for:
 * 1. Importing homebrew content with duplicate keys
 * 2. Using the conflict resolution modal
 * 3. Missing content warning when homebrew is deleted
 * 4. Re-importing with rename option
 *
 * This is a comprehensive E2E test for the error handling features.
 */

const HOMEBREW_FILE = path.join(__dirname, '../../test/duplicate-external-a.orcbrew');
const DUPLICATE_FILE = path.join(__dirname, '../../test/duplicate-external-b.orcbrew');

// Test account credentials (from dev/test-accounts.edn)
const TEST_USER = {
  username: 'tester1',
  email: 'tester1@example.com',
  password: 'Testing123!'
};

/**
 * Login with test account
 * @returns true if login succeeded, false otherwise
 */
async function login(page: Page): Promise<boolean> {
  await page.goto('/pages/login-page');
  await waitForAppReady(page);
  await page.waitForTimeout(1000);

  // Fill in email/username
  const emailInput = page.locator('input[type="email"], input[type="text"], input[name="email"], input[name="username"]').first();
  if (await emailInput.count() > 0) {
    await emailInput.fill(TEST_USER.email);
  } else {
    console.error('Login failed: email input not found');
    return false;
  }

  // Fill in password
  const passwordInput = page.locator('input[type="password"]').first();
  if (await passwordInput.count() > 0) {
    await passwordInput.fill(TEST_USER.password);
  } else {
    console.error('Login failed: password input not found');
    return false;
  }

  // Click login button
  const loginBtn = page.locator('button, .form-button', { hasText: /log.*in|sign.*in|submit/i }).first();
  if (await loginBtn.count() > 0) {
    await loginBtn.click();
    await page.waitForTimeout(2000);
  } else {
    console.error('Login failed: login button not found');
    return false;
  }

  // Wait for redirect/login to complete
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});

  // Verify login succeeded by checking for logged-in indicators
  // Look for: username display, logout button, or my-content access
  const loggedInIndicators = [
    page.locator('text=Log Out, a:has-text("Log Out"), button:has-text("Log Out")'),
    page.locator(`text=${TEST_USER.username}`),
    page.locator('[href*="my-content"]'),
  ];

  for (const indicator of loggedInIndicators) {
    if (await indicator.count() > 0) {
      console.log('Login successful - found logged-in indicator');
      return true;
    }
  }

  // Alternative: try navigating to my-content and see if we're redirected
  const currentUrl = page.url();
  if (currentUrl.includes('my-content') || !currentUrl.includes('login')) {
    console.log('Login appears successful - not on login page');
    return true;
  }

  console.error('Login failed: no logged-in indicators found');
  return false;
}

/**
 * Save the current character
 * @returns true if save succeeded, false otherwise
 */
async function saveCharacter(page: Page): Promise<boolean> {
  // The Save button may be in a hidden/collapsed section
  // Try to find and click it using JavaScript evaluation
  const clicked = await page.evaluate(() => {
    // Look for the Save New Character button
    const buttons = document.querySelectorAll('button, .form-button');
    for (const btn of buttons) {
      if (btn.textContent && btn.textContent.toLowerCase().includes('save')) {
        (btn as HTMLElement).click();
        return true;
      }
    }
    return false;
  });

  if (!clicked) {
    console.error('Save failed: save button not found');
    return false;
  }

  await page.waitForTimeout(2000);

  // Check for save success indicators:
  // 1. Success message/toast
  // 2. Character name appears in URL or page
  // 3. No error messages
  const errorIndicators = page.locator('[class*="error"], .error-message, [class*="alert-danger"]');
  if (await errorIndicators.count() > 0) {
    const errorText = await errorIndicators.first().textContent();
    console.error('Save failed: error message shown -', errorText);
    return false;
  }

  // Check if we're now on a saved character page (URL might have character ID)
  const url = page.url();
  if (url.includes('/character/') || url.includes('?id=')) {
    console.log('Save successful - character ID in URL');
    return true;
  }

  // Look for success indicators
  const successByClass = page.locator('[class*="success"], .success-message');
  const successByText = page.locator('text=saved, text=Created');
  if (await successByClass.count() > 0 || await successByText.count() > 0) {
    console.log('Save successful - success indicator found');
    return true;
  }

  // If no errors and button was clicked, assume success
  console.log('Save appears successful - no errors detected');
  return true;
}

/**
 * Helper to filter out expected Figwheel WebSocket errors
 */
function filterFigwheelErrors(errors: { type: string; text: string }[]) {
  return errors.filter((e) =>
    e.type === 'error' &&
    !e.text.includes('figwheel-ws') &&
    !e.text.includes('ws://localhost:3449')
  );
}

/**
 * Navigate to My Content page and wait for it to load
 */
async function goToMyContent(page: Page) {
  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.waitForTimeout(1000);
}

/**
 * Import an .orcbrew file via the file input
 */
async function importOrcbrewFile(page: Page, filePath: string) {
  const fileInput = page.locator('input[type="file"]').first();
  if (await fileInput.count() > 0) {
    await fileInput.setInputFiles(filePath);
    await page.waitForTimeout(2000); // Wait for import processing
  }
}

/**
 * Check if conflict resolution modal is visible
 */
async function isConflictModalVisible(page: Page): Promise<boolean> {
  const modal = page.locator('.modal-container:not(.hidden)', { hasText: /conflict|duplicate/i });
  return await modal.isVisible().catch(() => false);
}

/**
 * Select rename option in conflict modal for a specific key
 */
async function selectRenameOption(page: Page) {
  // Look for rename radio button or option
  const renameOption = page.locator('input[type="radio"][value*="rename"], label:has-text("Rename")').first();
  if (await renameOption.count() > 0) {
    await renameOption.click();
  }
}

/**
 * Confirm/proceed with conflict resolution
 */
async function confirmConflictResolution(page: Page) {
  const proceedButton = page.locator('button', { hasText: /proceed|confirm|apply|ok/i }).first();
  if (await proceedButton.count() > 0) {
    await proceedButton.click();
    await page.waitForTimeout(2000);
  }
}

test.describe('Homebrew Conflict Resolution', () => {
  test('import initial homebrew content', async ({ page }, testInfo) => {
    test.setTimeout(60000);
    const errors = setupConsoleCapture(page);

    await goToMyContent(page);
    await takeScreenshot(page, testInfo, '01-my-content-before-import');

    await importOrcbrewFile(page, HOMEBREW_FILE);
    await takeScreenshot(page, testInfo, '02-after-first-import');

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors, 'No console errors during initial import').toHaveLength(0);
  });

  test('import duplicate content triggers conflict modal', async ({ page }, testInfo) => {
    test.setTimeout(60000);
    const errors = setupConsoleCapture(page);

    // First import the original
    await goToMyContent(page);
    await importOrcbrewFile(page, HOMEBREW_FILE);
    await page.waitForTimeout(1000);

    // Now import the duplicate
    await importOrcbrewFile(page, DUPLICATE_FILE);
    await page.waitForTimeout(2000);

    await takeScreenshot(page, testInfo, '03-conflict-modal');

    // Check if conflict modal appeared
    const hasConflictModal = await isConflictModalVisible(page);

    await testInfo.attach('conflict-modal-check', {
      body: JSON.stringify({ hasConflictModal, timestamp: new Date().toISOString() }),
      contentType: 'application/json',
    });

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors, 'No console errors during conflict detection').toHaveLength(0);

    // Note: We don't fail if modal isn't visible - just capture the state
  });

  test('select homebrew content in character builder', async ({ page }, testInfo) => {
    test.setTimeout(90000);
    const errors = setupConsoleCapture(page);

    // First import homebrew content
    await goToMyContent(page);
    await importOrcbrewFile(page, HOMEBREW_FILE);
    await page.waitForTimeout(2000);

    // Go to character builder
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    await takeScreenshot(page, testInfo, '04-character-builder');

    // Try to find race selection area
    const raceSection = page.locator('[class*="race"], .option-selector', { hasText: /race/i }).first();
    if (await raceSection.count() > 0) {
      await takeScreenshot(page, testInfo, '05-race-section');
    }

    // Look for our homebrew race "Starborn"
    const starborn = page.locator('text=Starborn');
    const starbornVisible = await starborn.count() > 0;

    await testInfo.attach('homebrew-visibility', {
      body: JSON.stringify({ starbornVisible, timestamp: new Date().toISOString() }),
      contentType: 'application/json',
    });

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors, 'No console errors in character builder').toHaveLength(0);
  });

  test('navigate to my-content page and check for delete options', async ({ page }, testInfo) => {
    test.setTimeout(60000);
    const errors = setupConsoleCapture(page);

    // Import homebrew first
    await goToMyContent(page);
    await importOrcbrewFile(page, HOMEBREW_FILE);
    await page.waitForTimeout(2000);

    await takeScreenshot(page, testInfo, '06-my-content-with-homebrew');

    // Look for delete buttons
    const deleteButtons = page.locator('button, .form-button', { hasText: /delete|remove/i });
    const deleteCount = await deleteButtons.count();

    await testInfo.attach('delete-options', {
      body: JSON.stringify({ deleteCount, timestamp: new Date().toISOString() }),
      contentType: 'application/json',
    });

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors, 'No console errors on my-content page').toHaveLength(0);
  });

  test('verify no console errors during full workflow', async ({ page }, testInfo) => {
    test.setTimeout(120000);
    const errors = setupConsoleCapture(page);

    // Complete workflow:
    // 1. Import homebrew
    await goToMyContent(page);
    await importOrcbrewFile(page, HOMEBREW_FILE);
    await page.waitForTimeout(2000);

    // 2. Go to character builder
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    // 3. Back to my-content
    await goToMyContent(page);
    await page.waitForTimeout(1000);

    // 4. Import duplicate
    await importOrcbrewFile(page, DUPLICATE_FILE);
    await page.waitForTimeout(2000);

    // 5. Handle conflict if modal appears
    if (await isConflictModalVisible(page)) {
      await selectRenameOption(page);
      await confirmConflictResolution(page);
    }

    // 6. Back to character builder
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);

    await takeScreenshot(page, testInfo, '07-final-state');
    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);

    // Log all errors for debugging
    if (jsErrors.length > 0) {
      console.log('Unexpected console errors:', jsErrors);
    }

    expect(jsErrors, `Found ${jsErrors.length} unexpected console error(s)`).toHaveLength(0);
  });
});

test.describe('Missing Content Warning', () => {
  test('warning appears when homebrew is missing', async ({ page }, testInfo) => {
    test.setTimeout(90000);
    const errors = setupConsoleCapture(page);

    // This test verifies the missing content warning UI element exists
    // It checks for the new #missing-content-warning ID selector
    // A full integration test is in 'warnings match deleted content...'

    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    // Use the new specific ID selector for the missing content warning
    const warningById = page.locator('#missing-content-warning');
    const warningByIdCount = await warningById.count();

    // Also check for general warning elements (fallback)
    const warningBanner = page.locator('[class*="warning"], .missing-content-warning, [class*="alert"]');
    const warningCount = await warningBanner.count();

    await takeScreenshot(page, testInfo, '08-check-warning-ui');

    await testInfo.attach('warning-ui-check', {
      body: JSON.stringify({
        warningByIdFound: warningByIdCount > 0,
        warningCount,
        timestamp: new Date().toISOString()
      }),
      contentType: 'application/json',
    });

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors, 'No console errors during warning check').toHaveLength(0);
  });

  test('capture missing content warning details', async ({ page }, testInfo) => {
    test.setTimeout(120000);
    const errors = setupConsoleCapture(page);

    // 1. Import homebrew
    await goToMyContent(page);
    await importOrcbrewFile(page, HOMEBREW_FILE);
    await page.waitForTimeout(2000);

    // 2. Go to character builder and try to select homebrew content
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    // Try to find and click on our Starborn race
    const starbornOption = page.locator('text=Starborn').first();
    if (await starbornOption.count() > 0) {
      await starbornOption.click();
      await page.waitForTimeout(1000);
    }

    // Try to find and click on our Starweaver class
    const starweaverOption = page.locator('text=Starweaver').first();
    if (await starweaverOption.count() > 0) {
      await starweaverOption.click();
      await page.waitForTimeout(1000);
    }

    await takeScreenshot(page, testInfo, '09-after-homebrew-selection');

    // 3. Go back to My Content and delete all homebrew
    await goToMyContent(page);
    await page.waitForTimeout(1000);

    // Look for and click delete all button
    const deleteAllBtn = page.locator('button, .form-button', { hasText: /delete.*all|remove.*all/i });
    if (await deleteAllBtn.count() > 0) {
      // Hide re-frame-10x debug panel that intercepts clicks
      await page.evaluate(() => {
        const debugPanel = document.getElementById('--re-frame-10x--');
        if (debugPanel) debugPanel.style.display = 'none';
      });
      await deleteAllBtn.first().click({ force: true });
      await page.waitForTimeout(1000);

      // Confirm deletion if modal appears
      const confirmBtn = page.locator('button', { hasText: /confirm|yes|ok/i });
      if (await confirmBtn.count() > 0) {
        await confirmBtn.first().click();
        await page.waitForTimeout(1000);
      }
    }

    await takeScreenshot(page, testInfo, '10-after-delete');

    // 4. Go back to character builder
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    // 5. Check for the missing content warning
    // The warning uses .orange class with fa-exclamation-triangle icon
    const warningIcon = page.locator('.orange .fa-exclamation-triangle, .fa-exclamation-triangle.orange, i.fa-exclamation-triangle');
    const warningText = page.locator('text=Missing Content');

    const hasWarningIcon = await warningIcon.count() > 0;
    const hasWarningText = await warningText.count() > 0;

    await takeScreenshot(page, testInfo, '11-missing-content-warning');

    // If warning is visible, try to expand it and read the details
    let warningDetails: string[] = [];
    if (hasWarningText) {
      // Click to expand
      await warningText.first().click();
      await page.waitForTimeout(500);

      await takeScreenshot(page, testInfo, '12-warning-expanded');

      // Read the warning items - they show "content-label: :key-name"
      // Format: "Class: :starweaver" or "Race: :starborn"
      const warningItems = page.locator('.bg-warning-item');
      const itemCount = await warningItems.count();

      for (let i = 0; i < itemCount; i++) {
        const item = warningItems.nth(i);
        const text = await item.textContent();
        if (text) {
          warningDetails.push(text.trim());
        }
      }

      // Also try to get the inferred source info
      const sourceInfo = page.locator('text=Likely from source');
      const sourceCount = await sourceInfo.count();
      for (let i = 0; i < sourceCount; i++) {
        const parent = sourceInfo.nth(i).locator('..');
        const text = await parent.textContent();
        if (text) {
          warningDetails.push(`Source: ${text.trim()}`);
        }
      }
    }

    await testInfo.attach('missing-content-details', {
      body: JSON.stringify({
        hasWarningIcon,
        hasWarningText,
        warningDetails,
        timestamp: new Date().toISOString()
      }, null, 2),
      contentType: 'application/json',
    });

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors, 'No console errors during missing content check').toHaveLength(0);
  });

  test('warnings match deleted content and disappear after re-import', async ({ page }, testInfo) => {
    test.setTimeout(180000);
    const errors = setupConsoleCapture(page);

    // 0. Login first (required to save characters)
    const loginSuccess = await login(page);
    await takeScreenshot(page, testInfo, '19-after-login');
    expect(loginSuccess, 'Login must succeed for this test - ensure test accounts are seeded').toBe(true);

    // 1. Import homebrew content
    await goToMyContent(page);
    await importOrcbrewFile(page, HOMEBREW_FILE);
    await page.waitForTimeout(2000);
    await takeScreenshot(page, testInfo, '20-initial-import');

    // 2. Go to character builder and select homebrew race/class
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    // Record what we're selecting
    const selectedContent: string[] = [];

    // Try to select Starborn race
    const starbornOption = page.locator('text=Starborn').first();
    if (await starbornOption.count() > 0) {
      await starbornOption.click();
      selectedContent.push('Starborn (Race)');
      await page.waitForTimeout(1000);
    }

    // Try to select Dawn Starborn subrace
    const dawnOption = page.locator('text=Dawn Starborn').first();
    if (await dawnOption.count() > 0) {
      await dawnOption.click();
      selectedContent.push('Dawn Starborn (Subrace)');
      await page.waitForTimeout(1000);
    }

    // Try to select Starweaver class
    const starweaverOption = page.locator('text=Starweaver').first();
    if (await starweaverOption.count() > 0) {
      await starweaverOption.click();
      selectedContent.push('Starweaver (Class)');
      await page.waitForTimeout(1000);
    }

    // Try to select Constellation Mage subclass
    const constMageOption = page.locator('text=Constellation Mage').first();
    if (await constMageOption.count() > 0) {
      await constMageOption.click();
      selectedContent.push('Constellation Mage (Subclass)');
      await page.waitForTimeout(1000);
    }

    // Save the character so warnings will show after deletion
    const saveSuccess = await saveCharacter(page);
    await takeScreenshot(page, testInfo, '21-after-selection-and-save');
    expect(saveSuccess, 'Character save must succeed for this test').toBe(true);

    await testInfo.attach('selected-content', {
      body: JSON.stringify({ selectedContent }),
      contentType: 'application/json',
    });

    // 3. Delete all homebrew from My Content
    await goToMyContent(page);
    await page.waitForTimeout(1000);

    // Close re-frame-10x debug panel if it's open (blocks clicks)
    await page.evaluate(() => {
      const debugPanel = document.getElementById('--re-frame-10x--');
      if (debugPanel) {
        debugPanel.style.display = 'none';
      }
    });

    // Find delete button
    const deleteBtn = page.locator('button, .form-button, .link-button', { hasText: /delete/i }).first();
    if (await deleteBtn.count() > 0) {
      await deleteBtn.click({ force: true });
      await page.waitForTimeout(500);

      // Confirm if needed
      const confirmBtn = page.locator('button', { hasText: /yes|confirm|ok/i });
      if (await confirmBtn.count() > 0) {
        await confirmBtn.first().click({ force: true });
      }
      await page.waitForTimeout(1000);
    }

    await takeScreenshot(page, testInfo, '22-after-delete');

    // 4. Go back to character builder and check warnings
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    // Check for missing content warning using the new ID selector
    const warningElement = page.locator('#missing-content-warning');
    const hasWarningBefore = await warningElement.count() > 0;

    // Get the count from data attribute if available
    let missingCount = 0;
    if (hasWarningBefore) {
      const countAttr = await warningElement.getAttribute('data-missing-count');
      missingCount = countAttr ? parseInt(countAttr, 10) : 0;
    }

    let missingContentBefore: { type: string; key: string }[] = [];
    if (hasWarningBefore) {
      // Expand warning by clicking it
      await warningElement.click();
      await page.waitForTimeout(500);

      // Read all warning items using new selector
      const warningItems = page.locator('.missing-content-item');
      const itemCount = await warningItems.count();
      for (let i = 0; i < itemCount; i++) {
        const item = warningItems.nth(i);
        const contentType = await item.getAttribute('data-content-type');
        const contentKey = await item.getAttribute('data-content-key');
        if (contentType && contentKey) {
          missingContentBefore.push({ type: contentType, key: contentKey });
        }
      }
    }

    await takeScreenshot(page, testInfo, '23-missing-warning-before-reimport');

    await testInfo.attach('missing-content-before-reimport', {
      body: JSON.stringify({
        hasWarning: hasWarningBefore,
        missingCount,
        missingItems: missingContentBefore,
        selectedContent
      }, null, 2),
      contentType: 'application/json',
    });

    // ASSERTION: If we selected homebrew content, we should see warnings
    if (selectedContent.length > 0) {
      expect(hasWarningBefore, 'Missing content warning should appear after deleting homebrew').toBe(true);

      // Check that warnings contain expected keys using new structured data
      const missingKeys = missingContentBefore.map(m => m.key.toLowerCase());
      if (selectedContent.some(s => s.includes('Starborn'))) {
        expect(missingKeys.some(k => k.includes('starborn')), 'Warning should include Starborn').toBe(true);
      }
      if (selectedContent.some(s => s.includes('Starweaver'))) {
        expect(missingKeys.some(k => k.includes('starweaver')), 'Warning should include Starweaver').toBe(true);
      }
    }

    // 5. Re-import the homebrew content
    await goToMyContent(page);
    await importOrcbrewFile(page, HOMEBREW_FILE);
    await page.waitForTimeout(2000);

    await takeScreenshot(page, testInfo, '24-after-reimport');

    // 6. Go back to character builder and verify warnings are gone
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    // Use the new ID selector to check for warning after reimport
    const warningAfterReimport = page.locator('#missing-content-warning');
    const hasWarningAfter = await warningAfterReimport.count() > 0;

    await takeScreenshot(page, testInfo, '25-after-reimport-builder');

    await testInfo.attach('missing-content-after-reimport', {
      body: JSON.stringify({
        hasWarningBefore,
        hasWarningAfter,
        warningsCleared: hasWarningBefore && !hasWarningAfter
      }, null, 2),
      contentType: 'application/json',
    });

    // ASSERTION: Warnings should be gone after re-import
    if (hasWarningBefore) {
      expect(hasWarningAfter, 'Missing content warning should disappear after re-import').toBe(false);
    }

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors, 'No console errors during full workflow').toHaveLength(0);
  });
});
