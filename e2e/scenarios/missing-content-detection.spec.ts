import { test, expect, Page } from '@playwright/test';
import * as path from 'path';
import {
  setupConsoleCapture,
  attachConsoleErrors,
  waitForAppReady,
  takeScreenshot,
} from '../fixtures/test-utils';

/**
 * Missing Content Detection Test Suite
 *
 * Tests that the missing content warning correctly detects:
 * - Missing races
 * - Missing classes
 * - Missing subclasses
 *
 * Uses the REAL test fixtures from test/ directory.
 * These tests are designed to FAIL if the detection logic has bugs.
 */

// Use the REAL fixtures - do not modify these files!
const FIXTURE_A = path.join(__dirname, '../../test/duplicate-external-a.orcbrew');
const FIXTURE_B = path.join(__dirname, '../../test/duplicate-external-b.orcbrew');

// Test account credentials
const TEST_USER = {
  email: 'tester1@example.com',
  password: 'Testing123!'
};

/**
 * Login with test account
 */
async function login(page: Page): Promise<boolean> {
  await page.goto('/pages/login-page');
  await waitForAppReady(page);
  await page.waitForTimeout(1000);

  // Fill in email/username - use same pattern as homebrew-conflict
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

  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  return !page.url().includes('login');
}

async function goToMyContent(page: Page) {
  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.waitForTimeout(1000);
}

async function importOrcbrewFile(page: Page, filePath: string) {
  const fileInput = page.locator('input[type="file"]').first();
  if (await fileInput.count() > 0) {
    await fileInput.setInputFiles(filePath);
    await page.waitForTimeout(2000);
  }
}

async function deleteAllHomebrew(page: Page) {
  // Navigate to My Content first
  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.waitForTimeout(1000);

  // Hide re-frame-10x debug panel that intercepts clicks
  await page.evaluate(() => {
    const debugPanel = document.getElementById('--re-frame-10x--');
    if (debugPanel) debugPanel.style.display = 'none';
    // Also hide by class selector
    const debugPanels = document.querySelectorAll('[class*="--re-frame-10x"]');
    debugPanels.forEach(p => (p as HTMLElement).style.display = 'none');
  });

  // Look for delete all button
  const deleteAllBtn = page.locator('button, .form-button', { hasText: /delete.*all|remove.*all/i });

  if (await deleteAllBtn.count() > 0) {
    await deleteAllBtn.first().click({ force: true });
    await page.waitForTimeout(1000);

    // Confirm deletion - the confirmation has a "delete" link-button (not button)
    // Text is just "delete" and it's a span.link-button
    const confirmBtn = page.locator('.link-button, button, span', { hasText: /^delete$/i });
    if (await confirmBtn.count() > 0) {
      await confirmBtn.first().click({ force: true });
      await page.waitForTimeout(1000);
    }
    return;
  }

  // Fallback: try individual delete buttons (delete each plugin one by one)
  let deleteCount = 0;
  const maxDeletes = 10;
  while (deleteCount < maxDeletes) {
    const deleteBtn = page.locator('button, .form-button, .link-button', { hasText: /delete/i }).first();
    if (await deleteBtn.count() === 0) break;

    await deleteBtn.click({ force: true });
    await page.waitForTimeout(500);

    const confirmBtn = page.locator('button', { hasText: /yes|confirm|ok/i });
    if (await confirmBtn.count() > 0) {
      await confirmBtn.first().click({ force: true });
    }
    await page.waitForTimeout(500);
    deleteCount++;
  }
}

async function getMissingContentItems(page: Page): Promise<{type: string; key: string}[]> {
  const items: {type: string; key: string}[] = [];
  const warning = page.locator('#missing-content-warning');
  if (await warning.count() === 0) return items;

  await warning.click();
  await page.waitForTimeout(500);

  const warningItems = page.locator('.missing-content-item');
  const count = await warningItems.count();

  for (let i = 0; i < count; i++) {
    const item = warningItems.nth(i);
    const contentType = await item.getAttribute('data-content-type');
    const contentKey = await item.getAttribute('data-content-key');
    if (contentType && contentKey) {
      items.push({ type: contentType, key: contentKey });
    }
  }

  return items;
}

test.describe('Missing Content Detection - Bug Detection', () => {

  test('MUST detect Race, Class, AND Subclass as missing', async ({ page }, testInfo) => {
    test.setTimeout(180000);
    const errors = setupConsoleCapture(page);

    // Capture ALL console messages for debugging
    const allConsoleLogs: string[] = [];
    page.on('console', msg => {
      allConsoleLogs.push(`[${msg.type()}] ${msg.text()}`);
    });

    // 1. Login
    const loginSuccess = await login(page);
    expect(loginSuccess, 'Login must succeed - ensure test accounts are seeded').toBe(true);

    // 2. Import fixture B (has custom-lineage race, artificer class, alchemist subclass)
    await goToMyContent(page);
    await importOrcbrewFile(page, FIXTURE_B);
    await page.waitForTimeout(2000);
    await takeScreenshot(page, testInfo, '01-after-import');

    // 3. Go to character builder and select homebrew content
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    const selectedContent: string[] = [];

    // Select Custom Lineage race (from fixture B)
    const customLineage = page.locator('text=Custom Lineage').first();
    if (await customLineage.count() > 0) {
      await customLineage.click();
      selectedContent.push('custom-lineage (Race)');
      await page.waitForTimeout(1000);
    }

    // Select Artificer class (from fixture B)
    const artificer = page.locator('text=Artificer').first();
    if (await artificer.count() > 0) {
      await artificer.click();
      selectedContent.push('artificer (Class)');
      await page.waitForTimeout(1000);
    }

    // Select Alchemist subclass (from fixture B)
    const alchemist = page.locator('text=Alchemist').first();
    if (await alchemist.count() > 0) {
      await alchemist.click();
      selectedContent.push('alchemist (Subclass)');
      await page.waitForTimeout(1000);
    }

    await takeScreenshot(page, testInfo, '02-after-selection');

    // 4. Save the character
    const saveClicked = await page.evaluate(() => {
      const buttons = document.querySelectorAll('button, .form-button');
      for (const btn of buttons) {
        if (btn.textContent && btn.textContent.toLowerCase().includes('save')) {
          (btn as HTMLElement).click();
          return true;
        }
      }
      return false;
    });
    await page.waitForTimeout(3000);

    await testInfo.attach('selected-content', {
      body: JSON.stringify({ selectedContent, saveClicked }),
      contentType: 'application/json',
    });

    // 5. Delete all homebrew
    await goToMyContent(page);
    await deleteAllHomebrew(page);
    await takeScreenshot(page, testInfo, '03-after-delete');

    // 6. Go back to character builder - this triggers the missing content detection
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(3000);

    await takeScreenshot(page, testInfo, '04-missing-content-check');

    // 7. Extract debug logs
    const debugLogs = allConsoleLogs.filter(l => l.includes('[DEBUG]'));
    const orcpubLogs = allConsoleLogs.filter(l => l.includes('[OrcPub]'));

    await testInfo.attach('all-debug-logs', {
      body: debugLogs.join('\n'),
      contentType: 'text/plain',
    });

    await testInfo.attach('orcpub-logs', {
      body: orcpubLogs.join('\n'),
      contentType: 'text/plain',
    });

    // 8. Get missing content from UI
    const missingItems = await getMissingContentItems(page);

    await testInfo.attach('missing-items-detected', {
      body: JSON.stringify({
        selectedContent,
        missingItems,
        missingCount: missingItems.length,
        debugLogCount: debugLogs.length,
      }, null, 2),
      contentType: 'application/json',
    });

    // 9. ASSERTIONS - These should FAIL if there's a bug

    // First: warning should appear if we selected anything
    if (selectedContent.length > 0) {
      const hasWarning = await page.locator('#missing-content-warning').count() > 0;
      expect(hasWarning, `Missing content warning should appear. Selected: ${selectedContent.join(', ')}`).toBe(true);
    }

    // Get types that were detected
    const detectedTypes = new Set(missingItems.map(m => m.type));
    const detectedKeys = missingItems.map(m => m.key.toLowerCase());

    // CRITICAL: If we selected a Race, it MUST be detected as missing
    if (selectedContent.some(s => s.includes('Race'))) {
      const hasRace = detectedTypes.has('Race') || detectedKeys.some(k => k.includes('custom-lineage'));
      expect(hasRace, `BUG: Race was selected but not detected as missing! Detected: ${JSON.stringify(missingItems)}`).toBe(true);
    }

    // CRITICAL: If we selected a Class, it MUST be detected as missing
    if (selectedContent.some(s => s.includes('Class'))) {
      const hasClass = detectedTypes.has('Class') || detectedKeys.some(k => k.includes('artificer'));
      expect(hasClass, `BUG: Class was selected but not detected as missing! Detected: ${JSON.stringify(missingItems)}. Debug logs: ${debugLogs.slice(0, 5).join(' | ')}`).toBe(true);
    }

    // CRITICAL: If we selected a Subclass, it MUST be detected as missing
    if (selectedContent.some(s => s.includes('Subclass'))) {
      const hasSubclass = detectedTypes.has('Subclass') || detectedKeys.some(k => k.includes('alchemist'));
      expect(hasSubclass, `BUG: Subclass was selected but not detected as missing! Detected: ${JSON.stringify(missingItems)}. Debug logs: ${debugLogs.slice(0, 5).join(' | ')}`).toBe(true);
    }

    // Verify counts
    const expectedMinCount = selectedContent.length;
    expect(missingItems.length, `Expected at least ${expectedMinCount} missing items for ${selectedContent.join(', ')}, but got ${missingItems.length}`).toBeGreaterThanOrEqual(expectedMinCount);

    await attachConsoleErrors(testInfo, errors);
  });

  test('debug: capture extracted keys from character', async ({ page }, testInfo) => {
    test.setTimeout(120000);
    const errors = setupConsoleCapture(page);

    // Capture console with special handling for cljs objects
    const consoleMessages: { type: string; text: string; args: string[] }[] = [];
    page.on('console', async msg => {
      const args: string[] = [];
      for (const arg of msg.args()) {
        try {
          const val = await arg.jsonValue();
          args.push(JSON.stringify(val));
        } catch {
          args.push(await arg.evaluate(a => String(a)));
        }
      }
      consoleMessages.push({
        type: msg.type(),
        text: msg.text(),
        args
      });
    });

    // Login
    await login(page);

    // Import and select content
    await goToMyContent(page);
    await importOrcbrewFile(page, FIXTURE_B);
    await page.waitForTimeout(2000);

    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(2000);

    // Record what we selected
    const selectedContent: string[] = [];

    // Select Custom Lineage (Variant) race - this should be visible on the Race page
    const customLineage = page.locator('text=Custom Lineage (Variant)').first();
    if (await customLineage.count() > 0) {
      await customLineage.click();
      selectedContent.push('custom-lineage (Race)');
      await page.waitForTimeout(1000);
    }

    // Navigate to Class section by clicking on the section header
    const classSection = page.locator('text=Class / Level').first();
    if (await classSection.count() > 0) {
      await classSection.click();
      await page.waitForTimeout(1000);
    }

    // Class is selected via a dropdown - use value instead of label regex
    // The option value is the class key like "artificer"
    const classDropdown = page.locator('select').filter({ hasText: /Artificer|Barbarian|Fighter/i }).first();
    if (await classDropdown.count() > 0) {
      await classDropdown.selectOption('artificer');
      selectedContent.push('artificer (Class)');
      await page.waitForTimeout(1000);
    }

    // Subclass might also be in a dropdown after class is selected
    await page.waitForTimeout(500);
    const subclassDropdown = page.locator('select').filter({ hasText: /Alchemist|Artillerist/i }).first();
    if (await subclassDropdown.count() > 0) {
      await subclassDropdown.selectOption('alchemist');
      selectedContent.push('alchemist (Subclass)');
      await page.waitForTimeout(1000);
    }

    console.log('[TEST] Selected content:', selectedContent);

    // Save
    await page.evaluate(() => {
      const buttons = document.querySelectorAll('button, .form-button');
      for (const btn of buttons) {
        if (btn.textContent && btn.textContent.toLowerCase().includes('save')) {
          (btn as HTMLElement).click();
        }
      }
    });
    await page.waitForTimeout(2000);

    // Delete all
    await goToMyContent(page);
    await deleteAllHomebrew(page);

    // Trigger missing content detection
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(3000);

    // Attach console messages for debugging if test fails
    await testInfo.attach('console-messages', {
      body: JSON.stringify(consoleMessages.slice(-50), null, 2),
      contentType: 'application/json',
    });

    // This test is for debugging - always report what we found
    const missingItems = await getMissingContentItems(page);

    // Print diagnostic summary
    console.log('\n=== DIAGNOSTIC SUMMARY ===');
    console.log(`Console messages captured: ${consoleMessages.length}`);
    console.log(`Missing items detected by UI: ${JSON.stringify(missingItems)}`);
    console.log(`Selected content: ${JSON.stringify(selectedContent)}`);
    console.log('=== END DIAGNOSTIC ===\n');

    await testInfo.attach('diagnostic-report', {
      body: JSON.stringify({
        consoleMessageCount: consoleMessages.length,
        selectedContent: selectedContent,
        missingItemsDetected: missingItems,
      }, null, 2),
      contentType: 'application/json',
    });

    // Verify missing content detection is working
    // If we selected homebrew content, it should be detected as missing after delete
    if (selectedContent.length > 0) {
      expect(missingItems.length,
        `Expected missing items for ${selectedContent.join(', ')} but got ${JSON.stringify(missingItems)}`
      ).toBeGreaterThan(0);
    }

    await attachConsoleErrors(testInfo, errors);
  });
});
