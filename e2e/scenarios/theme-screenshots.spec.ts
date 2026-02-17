import { test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {
  setupConsoleCapture,
  attachConsoleErrors,
  waitForAppReady,
} from '../fixtures/test-utils';

/**
 * Theme Screenshots Test Suite
 *
 * Takes screenshots of each theme for visual review.
 * Clicks the theme toggle (shows "Theme: <name>") on the character builder to cycle.
 */

const THEMES = [
  'dark-theme',
  'nord-theme',
  'midnight-theme',
  'forest-theme',
  'slate-theme',
  'crimson-theme',
  'light-theme',
  'light-plus-theme',
  'sunset-theme',
  'arctic-aurora-theme',
  'parchment-theme',
];

// Ensure screenshots directory exists
const screenshotsDir = path.join(__dirname, '..', 'screenshots');
if (!fs.existsSync(screenshotsDir)) {
  fs.mkdirSync(screenshotsDir, { recursive: true });
}

test.describe('Theme Screenshots', () => {
  test.setTimeout(180000);

  test('capture all themes on character builder', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Navigate to character builder once
    await page.goto('/pages/dnd/5e/character-builder');
    await waitForAppReady(page);
    await page.waitForTimeout(1000);

    // Take screenshots, clicking theme toggle to cycle
    for (let i = 0; i < THEMES.length; i++) {
      const theme = THEMES[i];

      // Wait for UI to settle
      await page.waitForTimeout(500);

      // Save screenshot
      const filename = `${String(i + 1).padStart(2, '0')}-${theme}.png`;
      await page.screenshot({ path: path.join(screenshotsDir, filename), fullPage: false });

      // Click theme toggle to cycle to next theme (except on last iteration)
      if (i < THEMES.length - 1) {
        // The toggle shows "Theme: <name>" - click on "Theme:" text
        await page.getByText('Theme:').click({ timeout: 5000 });
      }
    }

    await attachConsoleErrors(testInfo, errors);
  });
});
