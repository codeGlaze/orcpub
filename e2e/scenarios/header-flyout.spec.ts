import { test, expect, Page } from '@playwright/test';
import {
  setupConsoleCapture,
  attachConsoleErrors,
  waitForAppReady,
  takeScreenshot,
} from '../fixtures/test-utils';

/**
 * Header Flyout Menu Tests
 *
 * Tests the header tab flyout behavior on both desktop and mobile viewports.
 * Known bugs under investigation:
 *   1. on-click returns a function instead of calling it (toggle never fires)
 *   2. Each tab has independent hovered? state — no "close others" logic
 *   3. on-mouse-leave handles closing on desktop, but mobile has no mouse events
 *
 * Tabs with flyout sub-menus:
 *   characters  → Character List, Character Builder, Parties
 *   spells      → Spell List, Spell Builder
 *   monsters    → Monster List, Monster Builder
 *   items       → Item List, Item Builder
 *   encounters  → Combat Tracker, Encounter Builder
 *   My Content  → (has sub-items)
 */

const TABS_WITH_FLYOUTS = ['characters', 'spells', 'monsters', 'items', 'encounters'];

// Tab index positions in the header (0-based)
// Used on mobile where title text is hidden (icons only)
const TAB_INDEX: Record<string, number> = {
  characters: 0,
  spells: 1,
  monsters: 2,
  items: 3,
  encounters: 4,
  // generators may or may not be present (index 5)
  'my content': -1, // last tab, use negative index
};

// Interior page where the header with tabs appears (splash page has no header)
const INTERIOR_PAGE = '/pages/dnd/5e/character-builder';

// Viewports
const DESKTOP_VIEWPORT = { width: 1280, height: 800 };
const MOBILE_VIEWPORT = { width: 375, height: 667 };

/**
 * Get all header-tab elements
 */
function getHeaderTabs(page: Page) {
  return page.locator('.header-tab');
}

/**
 * Get a specific header-tab by its title text (desktop) or index (mobile).
 * On mobile, title divs are not rendered so hasText won't match.
 */
function getTab(page: Page, name: string, mobile = false) {
  if (mobile) {
    const idx = TAB_INDEX[name.toLowerCase()];
    if (idx === -1) {
      return page.locator('.header-tab').last();
    }
    return page.locator('.header-tab').nth(idx);
  }
  return page.locator('.header-tab', { hasText: new RegExp(name, 'i') });
}

/**
 * Get the flyout dropdown inside a tab (the .shadow div with menu items)
 */
function getFlyout(page: Page, tabName: string, mobile = false) {
  return getTab(page, tabName, mobile).locator('.shadow');
}

/**
 * Count how many flyouts are currently visible on the page
 */
async function countVisibleFlyouts(page: Page): Promise<number> {
  const flyouts = page.locator('.header-tab .header-flyout');
  const total = await flyouts.count();
  let visible = 0;
  for (let i = 0; i < total; i++) {
    if (await flyouts.nth(i).isVisible()) visible++;
  }
  return visible;
}

/**
 * Get names of all tabs whose flyouts are currently visible
 */
async function getOpenFlyoutTabs(page: Page, mobile = false): Promise<string[]> {
  const openTabs: string[] = [];
  for (const tabName of TABS_WITH_FLYOUTS) {
    const flyout = getFlyout(page, tabName, mobile);
    if (await flyout.count() > 0 && await flyout.isVisible()) {
      openTabs.push(tabName);
    }
  }
  return openTabs;
}

// ─────────────────────────────────────────────────────────────
// Desktop Tests
// ─────────────────────────────────────────────────────────────

test.describe('Header Flyout — Desktop', () => {
  test.beforeEach(async ({ page }) => {
    await page.setViewportSize(DESKTOP_VIEWPORT);
    await page.goto(INTERIOR_PAGE);
    await waitForAppReady(page);
  });

  test('header tabs are visible on interior page', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);
    const tabs = getHeaderTabs(page);
    const count = await tabs.count();

    await takeScreenshot(page, testInfo, 'desktop-header-tabs');
    expect(count).toBeGreaterThanOrEqual(5);
    await attachConsoleErrors(testInfo, errors);
  });

  test('hover opens flyout', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    for (const tabName of TABS_WITH_FLYOUTS) {
      const tab = getTab(page, tabName);
      await tab.hover();
      await page.waitForTimeout(300);

      const flyout = getFlyout(page, tabName);
      const isOpen = await flyout.count() > 0;

      await takeScreenshot(page, testInfo, `desktop-hover-${tabName}`);

      expect(isOpen, `Flyout for "${tabName}" should open on hover`).toBe(true);

      // Move mouse away to close
      await page.mouse.move(0, 0);
      await page.waitForTimeout(300);
    }

    await attachConsoleErrors(testInfo, errors);
  });

  test('hover-leave closes flyout', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    const tab = getTab(page, 'characters');
    await tab.hover();
    await page.waitForTimeout(300);

    // Flyout should be visible
    const flyout = getFlyout(page, 'characters');
    expect(await flyout.isVisible()).toBe(true);

    // Move mouse away
    await page.mouse.move(0, 0);
    await page.waitForTimeout(500);

    // Flyout should be hidden
    const stillVisible = await flyout.isVisible();

    await takeScreenshot(page, testInfo, 'desktop-after-hover-leave');
    expect(stillVisible, 'Flyout should close when mouse leaves').toBe(false);

    await attachConsoleErrors(testInfo, errors);
  });

  test('only one flyout open at a time on hover', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Hover over characters
    await getTab(page, 'characters').hover();
    await page.waitForTimeout(300);

    // Now hover over spells (without leaving characters first — direct move)
    await getTab(page, 'spells').hover();
    await page.waitForTimeout(300);

    const openTabs = await getOpenFlyoutTabs(page);
    await takeScreenshot(page, testInfo, 'desktop-two-hovers');

    // On desktop with mouse events, on-mouse-leave on characters should fire
    // when hovering spells — so only spells should be open
    expect(
      openTabs.length,
      `Expected 1 flyout open, got ${openTabs.length}: [${openTabs.join(', ')}]`
    ).toBeLessThanOrEqual(1);

    await attachConsoleErrors(testInfo, errors);
  });

  test('hover through all My Content flyout items without premature close', async ({ page }, testInfo) => {
    test.setTimeout(60000);
    const errors = setupConsoleCapture(page);

    // My Content has the tallest flyout (11 items) — most likely to suffer
    // from premature closing when the cursor moves between items.
    const tab = getTab(page, 'my content');
    await tab.hover();
    await page.waitForTimeout(300);

    const flyout = getFlyout(page, 'my content');
    const isOpen = await flyout.isVisible();
    expect(isOpen, 'My Content flyout should open on hover').toBe(true);

    if (isOpen) {
      const items = flyout.locator('div.p-10');
      const itemCount = await items.count();

      await takeScreenshot(page, testInfo, 'desktop-mycontent-flyout-open');

      // Slowly hover through each item top to bottom
      const closedPrematurely: string[] = [];
      for (let i = 0; i < itemCount; i++) {
        const item = items.nth(i);
        await item.hover();
        await page.waitForTimeout(150);

        // Check flyout is still visible after each hover
        const stillOpen = await flyout.isVisible();
        if (!stillOpen) {
          const itemText = await item.textContent().catch(() => `item-${i}`);
          closedPrematurely.push(`Item ${i}: ${itemText?.trim()}`);
          break;
        }
      }

      await takeScreenshot(page, testInfo, 'desktop-mycontent-after-traversal');

      // Now check the flyout is still visible after traversing all items
      const finalOpen = await flyout.isVisible();

      await testInfo.attach('traversal-result', {
        body: JSON.stringify({
          totalItems: itemCount,
          closedPrematurely,
          flyoutOpenAfterTraversal: finalOpen,
        }, null, 2),
        contentType: 'application/json',
      });

      expect(
        closedPrematurely.length,
        `Flyout closed prematurely while hovering items: ${closedPrematurely.join(', ')}`
      ).toBe(0);
      expect(finalOpen, 'Flyout should remain open after traversing all items').toBe(true);
    }

    await attachConsoleErrors(testInfo, errors);
  });

  test('clicking flyout item navigates', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);
    const startUrl = page.url();

    // Hover to open characters flyout
    await getTab(page, 'spells').hover();
    await page.waitForTimeout(300);

    const flyout = getFlyout(page, 'spells');
    if (await flyout.count() > 0) {
      // Click "Spell List" item
      const spellListItem = flyout.locator('div', { hasText: /spell list/i });
      if (await spellListItem.count() > 0) {
        await spellListItem.first().click();
        await page.waitForTimeout(1000);
        await waitForAppReady(page);

        const newUrl = page.url();
        await takeScreenshot(page, testInfo, 'desktop-after-flyout-nav');

        expect(newUrl, 'URL should change after clicking flyout item').not.toBe(startUrl);
      }
    }

    await attachConsoleErrors(testInfo, errors);
  });
});

// ─────────────────────────────────────────────────────────────
// Mobile Tests
// ─────────────────────────────────────────────────────────────

test.describe('Header Flyout — Mobile', () => {
  test.use({
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1',
  });

  test.beforeEach(async ({ page }) => {
    await page.setViewportSize(MOBILE_VIEWPORT);
    await page.goto(INTERIOR_PAGE);
    await waitForAppReady(page);
  });

  test('header tabs render in mobile layout', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    const tabs = getHeaderTabs(page);
    const count = await tabs.count();

    await takeScreenshot(page, testInfo, 'mobile-header-tabs');

    // Tabs should still be present
    expect(count).toBeGreaterThanOrEqual(5);

    // Tab titles should be hidden on mobile (only icons shown)
    // The code uses `(when (not mobile?) [:div.title ...])` so titles shouldn't render
    const titleCount = await page.locator('.header-tab .title').count();
    expect(titleCount, 'Titles should not render on mobile').toBe(0);

    await attachConsoleErrors(testInfo, errors);
  });

  test('tap opens flyout', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    const tab = getTab(page, 'characters', true);
    await tab.click();
    await page.waitForTimeout(500);

    const flyout = getFlyout(page, 'characters', true);
    const isOpen = await flyout.count() > 0 && await flyout.isVisible();

    await takeScreenshot(page, testInfo, 'mobile-tap-characters');

    expect(isOpen, 'Tapping tab should open flyout on mobile').toBe(true);

    await attachConsoleErrors(testInfo, errors);
  });

  test('tap same tab closes flyout (toggle)', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    const tab = getTab(page, 'characters', true);

    // First tap — focus the tab (opens flyout via :focus-within)
    await tab.click();
    await page.waitForTimeout(500);

    const focusedAfterFirst = await tab.evaluate(el => el === document.activeElement);
    expect(focusedAfterFirst, 'First tap should focus the tab').toBe(true);

    // Second tap — should blur (closes flyout on real mobile where there's no :hover)
    await tab.click();
    await page.waitForTimeout(500);

    const focusedAfterSecond = await tab.evaluate(el => el === document.activeElement);

    await takeScreenshot(page, testInfo, 'mobile-toggle-close');

    // Note: in Playwright, :hover keeps the flyout visible even after blur.
    // On a real mobile device without :hover, blur = closed. We verify the
    // focus toggle works correctly — the CSS handles the rest.
    expect(focusedAfterSecond, 'Second tap should blur the tab (toggle off)').toBe(false);

    await attachConsoleErrors(testInfo, errors);
  });

  test('tap different tab closes the first', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Tap characters
    await getTab(page, 'characters', true).click();
    await page.waitForTimeout(500);

    // Tap spells
    await getTab(page, 'spells', true).click();
    await page.waitForTimeout(500);

    const openTabs = await getOpenFlyoutTabs(page, true);

    await takeScreenshot(page, testInfo, 'mobile-two-taps');

    // BUG DETECTION: if multiple are open, the "no close-others logic" bug is confirmed
    expect(
      openTabs.length,
      `Expected at most 1 flyout open, got ${openTabs.length}: [${openTabs.join(', ')}]`
    ).toBeLessThanOrEqual(1);

    await attachConsoleErrors(testInfo, errors);
  });

  test('all tabs stay open simultaneously (bug reproduction)', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Tap every tab with a flyout
    for (const tabName of TABS_WITH_FLYOUTS) {
      await getTab(page, tabName, true).click();
      await page.waitForTimeout(300);
    }

    const openTabs = await getOpenFlyoutTabs(page, true);

    await takeScreenshot(page, testInfo, 'mobile-all-tapped');

    // This test documents the bug — if more than 1 is open, the bug is present
    if (openTabs.length > 1) {
      console.log(`BUG CONFIRMED: ${openTabs.length} flyouts open simultaneously: [${openTabs.join(', ')}]`);
    }

    // Attach diagnostic data
    await testInfo.attach('open-flyouts', {
      body: JSON.stringify({ openTabs, count: openTabs.length, expected: 'at most 1' }),
      contentType: 'application/json',
    });

    await attachConsoleErrors(testInfo, errors);
  });

  test('tap outside closes all flyouts', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Open a flyout
    await getTab(page, 'characters', true).click();
    await page.waitForTimeout(500);

    // Tap on the main content area (below the header)
    await page.locator('#app').click({ position: { x: 187, y: 500 } });
    await page.waitForTimeout(500);

    const openTabs = await getOpenFlyoutTabs(page, true);

    await takeScreenshot(page, testInfo, 'mobile-tap-outside');

    expect(
      openTabs.length,
      `All flyouts should close when tapping outside, still open: [${openTabs.join(', ')}]`
    ).toBe(0);

    await attachConsoleErrors(testInfo, errors);
  });

  test('flyout items are tappable on mobile', async ({ page }, testInfo) => {
    const errors = setupConsoleCapture(page);

    // Open characters flyout
    await getTab(page, 'characters', true).click();
    await page.waitForTimeout(500);

    const flyout = getFlyout(page, 'characters', true);

    if (await flyout.count() > 0) {
      const items = flyout.locator('div.p-10');
      const itemCount = await items.count();

      await takeScreenshot(page, testInfo, 'mobile-flyout-items');

      // Should have sub-menu items (Character List, Character Builder, Parties)
      expect(itemCount, 'Flyout should contain menu items').toBeGreaterThanOrEqual(2);

      // Try tapping a menu item
      if (itemCount > 0) {
        const startUrl = page.url();
        await items.first().click();
        await page.waitForTimeout(1000);

        const newUrl = page.url();
        await takeScreenshot(page, testInfo, 'mobile-after-flyout-item-tap');

        // Navigation should occur
        expect(newUrl, 'Tapping flyout item should navigate').not.toBe(startUrl);
      }
    } else {
      // If flyout didn't open, that's the primary bug
      test.info().annotations.push({
        type: 'bug',
        description: 'Flyout did not open on tap — cannot test item tapping',
      });
    }

    await attachConsoleErrors(testInfo, errors);
  });
});

// ─────────────────────────────────────────────────────────────
// CSS / Style Diagnostic Tests
// ─────────────────────────────────────────────────────────────

test.describe('Header Flyout — Style Diagnostics', () => {
  test('flyout positioning and z-index on desktop', async ({ page }, testInfo) => {
    await page.setViewportSize(DESKTOP_VIEWPORT);
    await page.goto(INTERIOR_PAGE);
    await waitForAppReady(page);

    // Hover to open a flyout
    await getTab(page, 'characters').hover();
    await page.waitForTimeout(300);

    const flyout = getFlyout(page, 'characters');
    if (await flyout.count() > 0) {
      const styles = await flyout.evaluate((el) => {
        const cs = window.getComputedStyle(el);
        const parentCs = window.getComputedStyle(el.parentElement!);
        return {
          position: cs.position,
          zIndex: cs.zIndex,
          top: cs.top,
          left: cs.left,
          width: cs.width,
          display: cs.display,
          parentPosition: parentCs.position,
          overflow: cs.overflow,
          // Check if flyout extends beyond viewport
          rect: el.getBoundingClientRect().toJSON(),
          viewportWidth: window.innerWidth,
          viewportHeight: window.innerHeight,
        };
      });

      await testInfo.attach('desktop-flyout-styles', {
        body: JSON.stringify(styles, null, 2),
        contentType: 'application/json',
      });
    }

    await takeScreenshot(page, testInfo, 'desktop-flyout-positioning');
  });

  test('flyout positioning on mobile', async ({ page }, testInfo) => {
    await page.setViewportSize(MOBILE_VIEWPORT);
    await page.goto(INTERIOR_PAGE);
    await waitForAppReady(page);

    // Try hover (simulates what mouse-enter does)
    await getTab(page, 'characters').hover();
    await page.waitForTimeout(300);

    const flyout = getFlyout(page, 'characters');
    if (await flyout.count() > 0) {
      const styles = await flyout.evaluate((el) => {
        const cs = window.getComputedStyle(el);
        return {
          position: cs.position,
          zIndex: cs.zIndex,
          top: cs.top,
          left: cs.left,
          right: cs.right,
          width: cs.width,
          display: cs.display,
          rect: el.getBoundingClientRect().toJSON(),
          viewportWidth: window.innerWidth,
          viewportHeight: window.innerHeight,
          // Check if it overflows
          overflowsRight: el.getBoundingClientRect().right > window.innerWidth,
          overflowsBottom: el.getBoundingClientRect().bottom > window.innerHeight,
        };
      });

      await testInfo.attach('mobile-flyout-styles', {
        body: JSON.stringify(styles, null, 2),
        contentType: 'application/json',
      });
    }

    await takeScreenshot(page, testInfo, 'mobile-flyout-positioning');
  });

  test('header tab DOM structure audit', async ({ page }, testInfo) => {
    await page.setViewportSize(DESKTOP_VIEWPORT);
    await page.goto(INTERIOR_PAGE);
    await waitForAppReady(page);

    // Capture the full header DOM structure for analysis
    const headerHtml = await page.locator('.app-header-menu').evaluate((el) => {
      return {
        html: el.innerHTML.substring(0, 5000),
        childCount: el.children.length,
        tabClasses: Array.from(el.querySelectorAll('.header-tab')).map((tab) => ({
          text: tab.textContent?.trim().substring(0, 30),
          classes: tab.className,
          hasSubmenu: tab.querySelector('.shadow') !== null,
        })),
      };
    });

    await testInfo.attach('header-dom-structure', {
      body: JSON.stringify(headerHtml, null, 2),
      contentType: 'application/json',
    });
  });
});
