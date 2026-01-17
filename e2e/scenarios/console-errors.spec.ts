import { test, expect } from '@playwright/test';
import {
  setupConsoleCapture,
  attachConsoleErrors,
  waitForAppReady,
  clickNavTab,
  ConsoleMessage,
} from '../fixtures/test-utils';

/**
 * Console Errors Test Suite
 *
 * Captures JavaScript console errors and warnings during page load and navigation.
 * This is the most critical test for catching runtime issues in the ClojureScript app.
 */

test.describe('Console Errors', () => {
  test('no console errors on initial page load', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/');
    await waitForAppReady(page);

    // Wait a bit for any async operations
    await page.waitForTimeout(2000);

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = errors.filter((e) => e.type === 'error');
    expect(jsErrors, `Found ${jsErrors.length} console error(s)`).toHaveLength(0);
  });

  test('no console errors during navigation', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Start at home
    await page.goto('/');
    await waitForAppReady(page);

    // Navigate through main sections
    const navItems = ['Spells', 'Monsters', 'Items', 'Characters'];

    for (const item of navItems) {
      try {
        await clickNavTab(page, item);
        await page.waitForTimeout(500);
      } catch {
        // Tab might not exist or be visible - continue
      }
    }

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = errors.filter((e) => e.type === 'error');
    expect(jsErrors, `Found ${jsErrors.length} console error(s) during navigation`).toHaveLength(0);
  });

  test('no console errors on character builder page', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/dnd/e5/character-builder');
    await waitForAppReady(page);

    // Wait for character builder to fully render
    await page.waitForTimeout(3000);

    await attachConsoleErrors(testInfo, errors);

    const jsErrors = errors.filter((e) => e.type === 'error');
    expect(jsErrors, `Found ${jsErrors.length} console error(s) on character builder`).toHaveLength(0);
  });

  test('capture all warnings for review', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/');
    await waitForAppReady(page);

    // Navigate to a few pages to collect warnings
    const pages = ['/', '/dnd/e5/spells', '/dnd/e5/character-builder'];
    for (const url of pages) {
      await page.goto(url);
      await waitForAppReady(page);
    }

    await attachConsoleErrors(testInfo, errors);

    const warnings = errors.filter((e) => e.type === 'warning');

    // Warnings don't fail the test, but they're captured for review
    if (warnings.length > 0) {
      console.log(`Captured ${warnings.length} warning(s) for review`);
    }

    // This test always passes - it's for data collection
    expect(true).toBe(true);
  });

  test('no network errors (404s)', async ({ page }, testInfo) => {
    const networkErrors: { url: string; status: number }[] = [];

    page.on('response', (response) => {
      if (response.status() >= 400) {
        networkErrors.push({
          url: response.url(),
          status: response.status(),
        });
      }
    });

    await page.goto('/');
    await waitForAppReady(page);

    // Navigate around
    await page.goto('/dnd/e5/spells');
    await waitForAppReady(page);

    await testInfo.attach('network-errors', {
      body: JSON.stringify(networkErrors),
      contentType: 'application/json',
    });

    // Filter out expected 404s (like favicon if not present)
    const criticalErrors = networkErrors.filter(
      (e) => !e.url.includes('favicon') && e.status !== 304
    );

    expect(criticalErrors, `Found ${criticalErrors.length} network error(s)`).toHaveLength(0);
  });
});
