// Places in the app's settings controls. Locators go by role and accessible name first.

/**
 * The Light Theme switch (aria-checked tells which theme is on).
 * @param {import('playwright').Page} page
 * @returns {import('playwright').Locator}
 */
const lightTheme = page => page.getByRole('switch', { name: 'Light Theme' }).first();

module.exports = { lightTheme };
