import { Page, TestInfo } from '@playwright/test';

/**
 * Shared test utilities for OrcPub E2E tests
 */

export interface ConsoleMessage {
  type: 'error' | 'warning' | 'log' | 'info';
  text: string;
  url?: string;
  lineNumber?: number;
}

/**
 * Sets up console message capture on a page.
 * Returns an array that will be populated with console messages.
 */
export function setupConsoleCapture(page: Page): ConsoleMessage[] {
  const messages: ConsoleMessage[] = [];

  page.on('console', (msg) => {
    const type = msg.type() as ConsoleMessage['type'];
    if (['error', 'warning'].includes(type)) {
      messages.push({
        type,
        text: msg.text(),
        url: msg.location().url,
        lineNumber: msg.location().lineNumber,
      });
    }
  });

  page.on('pageerror', (error) => {
    messages.push({
      type: 'error',
      text: error.message,
    });
  });

  return messages;
}

/**
 * Attaches console errors to the test result for the agent reporter.
 */
export async function attachConsoleErrors(
  testInfo: TestInfo,
  errors: ConsoleMessage[]
): Promise<void> {
  await testInfo.attach('console-errors', {
    body: JSON.stringify(errors),
    contentType: 'application/json',
  });
}

/**
 * Waits for the OrcPub app to fully load.
 * The app uses ClojureScript/Reagent which may take time to hydrate.
 */
export async function waitForAppReady(page: Page): Promise<void> {
  // Wait for the main app container
  await page.waitForSelector('#app-header', { timeout: 30000 });

  // Wait for any loading spinners to disappear
  await page.waitForFunction(
    () => !document.querySelector('.loading, .spinner'),
    { timeout: 10000 }
  ).catch(() => {
    // Ignore if no loading indicator exists
  });

  // Give Reagent a moment to finish rendering
  await page.waitForTimeout(500);
}

/**
 * Navigates to a specific route in the app.
 */
export async function navigateTo(
  page: Page,
  route: 'characters' | 'spells' | 'monsters' | 'items' | 'encounters' | 'my-content' | 'builder'
): Promise<void> {
  const routes: Record<string, string> = {
    characters: '/',
    spells: '/dnd/e5/spells',
    monsters: '/dnd/e5/monsters',
    items: '/dnd/e5/items',
    encounters: '/dnd/e5/my-encounters',
    'my-content': '/dnd/e5/my-content',
    builder: '/dnd/e5/character-builder',
  };

  await page.goto(routes[route] || '/');
  await waitForAppReady(page);
}

/**
 * Clicks a navigation tab in the header.
 */
export async function clickNavTab(page: Page, tabName: string): Promise<void> {
  const tab = page.locator('.header-tab', { hasText: tabName });
  await tab.click();
  await waitForAppReady(page);
}

/**
 * Opens the user menu dropdown.
 */
export async function openUserMenu(page: Page): Promise<void> {
  await page.click('#user-header');
  await page.waitForSelector('#user-menu', { state: 'visible' });
}

/**
 * Checks if a modal is currently visible.
 */
export async function isModalVisible(page: Page): Promise<boolean> {
  const modal = page.locator('.modal-container:not(.hidden)');
  return await modal.isVisible();
}

/**
 * Closes any open modal by clicking cancel or the overlay.
 */
export async function closeModal(page: Page): Promise<void> {
  const cancelButton = page.locator('.modal button.form-button', { hasText: /cancel/i });
  if (await cancelButton.isVisible()) {
    await cancelButton.click();
  } else {
    // Click overlay to close
    await page.click('.modal-container');
  }
  await page.waitForSelector('.modal-container.hidden, .modal-container:not(:visible)', {
    timeout: 5000,
  }).catch(() => {});
}

/**
 * Gets the current route/page title from the app.
 */
export async function getCurrentPageTitle(page: Page): Promise<string> {
  return await page.title();
}

/**
 * Takes a screenshot with a descriptive name.
 */
export async function takeScreenshot(
  page: Page,
  testInfo: TestInfo,
  name: string
): Promise<void> {
  const screenshot = await page.screenshot();
  await testInfo.attach(name, {
    body: screenshot,
    contentType: 'image/png',
  });
}
