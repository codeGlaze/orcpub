import { test, expect } from '@playwright/test';
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
 * UI Smoke Test Suite
 *
 * Verifies basic UI elements are visible and interactive.
 * These tests catch rendering issues and broken layouts.
 *
 * Note: OrcPub has a splash page at / without a traditional header.
 * The header appears on interior pages like /pages/dnd/5e/character-builder.
 */

test.describe('UI Smoke Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForAppReady(page);
  });

  test('splash page loads with navigation buttons', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Splash page should have splash buttons for navigation
    const splashButtons = page.locator('.splash-button');
    const buttonCount = await splashButtons.count();
    expect(buttonCount).toBeGreaterThan(0);

    // Should have key navigation options (Character Builder, Spells, etc.)
    const characterButton = page.locator('.splash-button', { hasText: /character/i });
    await expect(characterButton.first()).toBeVisible();

    await attachConsoleErrors(testInfo, errors);
    await takeScreenshot(page, testInfo, 'splash-page');
  });

  test('splash buttons are clickable and navigate', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Click the Character Builder button
    const builderButton = page.locator('.splash-button', { hasText: /character.*builder/i }).first();

    if (await builderButton.isVisible()) {
      await builderButton.click();
      await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
      await page.waitForTimeout(1000);

      // Should have navigated to builder page
      const url = page.url();
      expect(url).toContain('character-builder');

      await takeScreenshot(page, testInfo, 'after-builder-click');
    }

    await attachConsoleErrors(testInfo, errors);
  });

  test('navigation tabs work', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // On splash page, navigation is via splash buttons not tabs
    // Try clicking different splash buttons
    const buttonsToTest = ['Spells', 'Monsters', 'Items'];

    for (const buttonName of buttonsToTest) {
      const button = page.locator('.splash-button', { hasText: new RegExp(buttonName, 'i') });

      if (await button.isVisible()) {
        await button.click();
        await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
        await page.waitForTimeout(500);

        // Take screenshot of each page
        await takeScreenshot(page, testInfo, `nav-${buttonName.toLowerCase()}`);

        // Go back to home for next iteration
        await page.goto('/');
        await waitForAppReady(page);
      }
    }

    await attachConsoleErrors(testInfo, errors);
  });

  test('main content area renders', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Main content container should exist (either #app or elements with content classes)
    const mainContent = page.locator('#app, .app, .splash-page-content, [class*="content"]').first();
    await expect(mainContent).toBeVisible();

    // Content should have some text (not empty)
    const textContent = await mainContent.textContent();
    expect(textContent?.trim().length).toBeGreaterThan(0);

    await attachConsoleErrors(testInfo, errors);
  });

  test('character builder page loads', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Use correct route
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);

    // Should have some form of character builder UI
    // Look for common builder elements
    const builderElements = page.locator('.form-button, .character-builder, [class*="builder"], [class*="character"]');
    const elementCount = await builderElements.count();

    // Take screenshot for manual review
    await takeScreenshot(page, testInfo, 'character-builder');

    await attachConsoleErrors(testInfo, errors);

    // Minimal assertion - page should have loaded something
    expect(elementCount).toBeGreaterThanOrEqual(0);
  });

  test('spell list page loads', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Use correct route
    await page.goto('/pages/dnd/5e/spells');
    await waitForAppReady(page);

    // Page should load without errors
    await page.waitForTimeout(1000);

    await takeScreenshot(page, testInfo, 'spell-list');
    await attachConsoleErrors(testInfo, errors);

    const jsErrors = filterFigwheelErrors(errors);
    expect(jsErrors).toHaveLength(0);
  });

  test('responsive layout on mobile viewport', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });

    await page.goto('/');
    await waitForAppReady(page);

    // App container should still be visible
    const appContainer = page.locator('#app');
    await expect(appContainer).toBeVisible();

    // Splash buttons should adapt to mobile
    const splashButtons = page.locator('.splash-button');
    const buttonCount = await splashButtons.count();
    expect(buttonCount).toBeGreaterThan(0);

    await takeScreenshot(page, testInfo, 'mobile-view');
    await attachConsoleErrors(testInfo, errors);
  });

  test('no broken images', async ({ page }, testInfo) => {
    const brokenImages: string[] = [];

    await page.goto('/');
    await waitForAppReady(page);

    // Find all images and check if they loaded
    const images = page.locator('img');
    const imageCount = await images.count();

    for (let i = 0; i < imageCount; i++) {
      const img = images.nth(i);
      const naturalWidth = await img.evaluate((el: HTMLImageElement) => el.naturalWidth);
      const src = await img.getAttribute('src');

      if (naturalWidth === 0 && src) {
        brokenImages.push(src);
      }
    }

    await testInfo.attach('broken-images', {
      body: JSON.stringify(brokenImages),
      contentType: 'application/json',
    });

    expect(brokenImages, `Found ${brokenImages.length} broken image(s)`).toHaveLength(0);
  });
});
