import { test, expect } from '@playwright/test';
import {
  setupConsoleCapture,
  attachConsoleErrors,
  waitForAppReady,
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

    // Filter out expected errors:
    // - Figwheel WebSocket errors in production mode (no dev server running)
    const jsErrors = errors.filter((e) =>
      e.type === 'error' &&
      !e.text.includes('figwheel-ws') &&
      !e.text.includes('ws://localhost:3449')
    );
    expect(jsErrors, `Found ${jsErrors.length} console error(s)`).toHaveLength(0);
  });

  test('no console errors during navigation', async ({ page }, testInfo) => {
    test.setTimeout(60000); // Allow 60s for this multi-page test

    const errors = setupConsoleCapture(page);

    // Navigate through a couple of pages to check for navigation-related errors
    // Keep it minimal to avoid timeouts
    const pages = [
      '/',
      '/pages/dnd/5e/character-builder',
    ];

    for (const url of pages) {
      await page.goto(url);
      await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
      await page.waitForTimeout(500);
    }

    await attachConsoleErrors(testInfo, errors);

    // Filter out Figwheel WebSocket errors (expected in production mode)
    const jsErrors = errors.filter((e) =>
      e.type === 'error' &&
      !e.text.includes('figwheel-ws') &&
      !e.text.includes('ws://localhost:3449')
    );
    expect(jsErrors, `Found ${jsErrors.length} console error(s) during navigation`).toHaveLength(0);
  });

  test('no console errors on character builder page', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Note: route is /dnd/5e/ not /dnd/e5/
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);

    // Wait for character builder to fully render
    await page.waitForTimeout(3000);

    await attachConsoleErrors(testInfo, errors);

    // Filter out Figwheel WebSocket errors (expected in production mode)
    const jsErrors = errors.filter((e) =>
      e.type === 'error' &&
      !e.text.includes('figwheel-ws') &&
      !e.text.includes('ws://localhost:3449')
    );
    expect(jsErrors, `Found ${jsErrors.length} console error(s) on character builder`).toHaveLength(0);
  });

  test('capture all warnings for review', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/');
    await waitForAppReady(page);

    // Navigate to a few pages to collect warnings (note: routes use /dnd/5e/ not /dnd/e5/)
    const pages = ['/', '/pages/dnd/5e/spells', '/pages/dnd/5e/character-builder'];
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

    // Navigate around (note: routes use /dnd/5e/ not /dnd/e5/)
    await page.goto('/pages/dnd/5e/spells');
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
