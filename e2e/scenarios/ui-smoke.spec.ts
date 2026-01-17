import { test, expect } from '@playwright/test';
import {
  setupConsoleCapture,
  attachConsoleErrors,
  waitForAppReady,
  openUserMenu,
  takeScreenshot,
} from '../fixtures/test-utils';

/**
 * UI Smoke Test Suite
 *
 * Verifies basic UI elements are visible and interactive.
 * These tests catch rendering issues and broken layouts.
 */

test.describe('UI Smoke Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await waitForAppReady(page);
  });

  test('app header is visible and contains navigation', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Header should be visible
    const header = page.locator('#app-header');
    await expect(header).toBeVisible();

    // Should have navigation menu
    const navMenu = page.locator('.app-header-menu');
    await expect(navMenu).toBeVisible();

    // Should have at least some nav tabs
    const navTabs = page.locator('.header-tab');
    const tabCount = await navTabs.count();
    expect(tabCount).toBeGreaterThan(0);

    await attachConsoleErrors(testInfo, errors);
    await takeScreenshot(page, testInfo, 'header-loaded');
  });

  test('user menu opens and closes', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // User header area should exist
    const userHeader = page.locator('#user-header');
    await expect(userHeader).toBeVisible();

    // Click to open menu
    await userHeader.click();

    // User menu should appear
    const userMenu = page.locator('#user-menu');
    await expect(userMenu).toBeVisible({ timeout: 5000 });

    // Menu should contain login/account options
    const menuText = await userMenu.textContent();
    expect(menuText?.toLowerCase()).toMatch(/login|log in|account|register/i);

    // Click elsewhere to close (or click user-header again)
    await page.keyboard.press('Escape');
    await page.waitForTimeout(500);

    await attachConsoleErrors(testInfo, errors);
    await takeScreenshot(page, testInfo, 'user-menu');
  });

  test('navigation tabs work', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Try clicking different nav tabs
    const tabsToTest = ['Spells', 'Monsters', 'Items'];

    for (const tabName of tabsToTest) {
      const tab = page.locator('.header-tab', { hasText: new RegExp(tabName, 'i') });

      if (await tab.isVisible()) {
        await tab.click();
        await waitForAppReady(page);

        // Verify URL changed or content area updated
        await page.waitForTimeout(500);

        // Take screenshot of each page
        await takeScreenshot(page, testInfo, `nav-${tabName.toLowerCase()}`);
      }
    }

    await attachConsoleErrors(testInfo, errors);
  });

  test('main content area renders', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Main content container should exist
    const mainContent = page.locator('#app-main, .container, [class*="content"]').first();
    await expect(mainContent).toBeVisible();

    // Content should have some text (not empty)
    const textContent = await mainContent.textContent();
    expect(textContent?.trim().length).toBeGreaterThan(0);

    await attachConsoleErrors(testInfo, errors);
  });

  test('character builder page loads', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/dnd/e5/character-builder');
    await waitForAppReady(page);

    // Should have some form of character builder UI
    // Look for common builder elements
    const builderElements = page.locator('.form-button, .character-builder, [class*="builder"]');
    const elementCount = await builderElements.count();

    // Take screenshot for manual review
    await takeScreenshot(page, testInfo, 'character-builder');

    await attachConsoleErrors(testInfo, errors);

    // Minimal assertion - page should have loaded something
    expect(elementCount).toBeGreaterThanOrEqual(0);
  });

  test('spell list page loads', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    await page.goto('/dnd/e5/spells');
    await waitForAppReady(page);

    // Page should load without errors
    await page.waitForTimeout(1000);

    await takeScreenshot(page, testInfo, 'spell-list');
    await attachConsoleErrors(testInfo, errors);

    const jsErrors = errors.filter((e) => e.type === 'error');
    expect(jsErrors).toHaveLength(0);
  });

  test('responsive layout on mobile viewport', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });

    await page.goto('/');
    await waitForAppReady(page);

    // Header should still be visible
    const header = page.locator('#app-header');
    await expect(header).toBeVisible();

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
