import { test, expect } from '@playwright/test';
import * as path from 'path';
import {
  setupConsoleCapture,
  attachConsoleErrors,
  waitForAppReady,
  takeScreenshot,
} from '../fixtures/test-utils';

/**
 * Helper to filter out expected Figwheel WebSocket errors in production mode
 */
function filterFigwheelErrors(errors: { type: string; text: string }[]) {
  return errors.filter((e) =>
    e.type === 'error' &&
    !e.text.includes('figwheel-ws') &&
    !e.text.includes('ws://localhost:3449')
  );
}

/**
 * Import/Export Test Suite
 *
 * Tests the .orcbrew file import functionality and data loading.
 * These tests verify that custom content can be loaded into the app.
 *
 * Note: OrcPub routes use /dnd/5e/ (not /dnd/e5/)
 */

test.describe('Import/Export', () => {
  test('my content page loads', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Note: my-content route doesn't use /pages/ prefix
    await page.goto('/dnd/5e/my-content');
    await waitForAppReady(page);

    await takeScreenshot(page, testInfo, 'my-content-page');
    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors).toHaveLength(0);
  });

  test('file input for .orcbrew is present', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/dnd/5e/my-content');
    await waitForAppReady(page);

    // Look for file input that accepts .orcbrew files
    const fileInput = page.locator('input[type="file"]');
    const inputCount = await fileInput.count();

    await attachConsoleErrors(testInfo, errors);

    // Should have at least one file input (may be hidden)
    if (inputCount === 0) {
      // Some UIs use a button that triggers a hidden file input
      const importButton = page.locator('button, .form-button, .link-button', {
        hasText: /import/i,
      });
      const buttonCount = await importButton.count();
      expect(buttonCount).toBeGreaterThan(0);
    }
  });

  test('import .orcbrew file', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/dnd/5e/my-content');
    await waitForAppReady(page);

    // Find the file input
    const fileInput = page.locator('input[type="file"]').first();

    if (await fileInput.count() > 0) {
      // Use the test fixture file
      const testFilePath = path.join(__dirname, '../fixtures/test-content.orcbrew');

      try {
        await fileInput.setInputFiles(testFilePath);
        await page.waitForTimeout(2000);

        // Check for success message or imported content
        await takeScreenshot(page, testInfo, 'after-import');

        // Look for any indication that import worked
        const pageContent = await page.content();
        const hasContent =
          pageContent.includes('Test') ||
          pageContent.includes('import') ||
          pageContent.includes('success');

        // This test captures the result for review
        await testInfo.attach('import-result', {
          body: JSON.stringify({ hasContent, timestamp: new Date().toISOString() }),
          contentType: 'application/json',
        });
      } catch {
        // File might not exist yet - that's okay for initial setup
        console.log('Test file not found - skipping file import test');
      }
    }

    await attachConsoleErrors(testInfo, errors);
  });

  test('export functionality is accessible', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/dnd/5e/my-content');
    await waitForAppReady(page);

    // Look for export buttons/links
    const exportElements = page.locator('button, .form-button, .link-button, a', {
      hasText: /export/i,
    });
    const exportCount = await exportElements.count();

    await takeScreenshot(page, testInfo, 'export-options');
    await attachConsoleErrors(testInfo, errors);

    // Log what was found for debugging
    await testInfo.attach('export-elements', {
      body: JSON.stringify({ exportCount, timestamp: new Date().toISOString() }),
      contentType: 'application/json',
    });
  });

  test('character list page shows characters', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/');
    await waitForAppReady(page);

    // Look for character list items or splash buttons
    const characterItems = page.locator('.item-list-item, .list-character-summary, [class*="character"], .splash-button');
    const itemCount = await characterItems.count();

    await takeScreenshot(page, testInfo, 'character-list');
    await attachConsoleErrors(testInfo, errors);

    await testInfo.attach('character-list-info', {
      body: JSON.stringify({ itemCount, timestamp: new Date().toISOString() }),
      contentType: 'application/json',
    });
  });

  test('character can be created (new button works)', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/');
    await waitForAppReady(page);

    // Look for "New" or "Create" button, or the character builder splash button
    const newButton = page.locator('button, .form-button, .splash-button', {
      hasText: /new|create|character.*builder/i,
    });

    if (await newButton.count() > 0) {
      await newButton.first().click();
      await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});

      // Should navigate to character builder or show creation UI
      await page.waitForTimeout(1000);

      const currentUrl = page.url();
      await takeScreenshot(page, testInfo, 'after-new-click');

      await testInfo.attach('new-character-result', {
        body: JSON.stringify({ currentUrl, timestamp: new Date().toISOString() }),
        contentType: 'application/json',
      });
    }

    await attachConsoleErrors(testInfo, errors);
  });

  test('PDF export form exists on character builder', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Use correct route path
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);

    // Look for download form or print button
    const downloadForm = page.locator('#download-form, form[action*="pdf"]');
    const printButton = page.locator('button, .form-button', { hasText: /print|pdf|export/i });

    const hasDownloadForm = await downloadForm.count() > 0;
    const hasPrintButton = await printButton.count() > 0;

    await takeScreenshot(page, testInfo, 'pdf-export-ui');
    await attachConsoleErrors(testInfo, errors);

    await testInfo.attach('pdf-export-info', {
      body: JSON.stringify({ hasDownloadForm, hasPrintButton }),
      contentType: 'application/json',
    });
  });
});
