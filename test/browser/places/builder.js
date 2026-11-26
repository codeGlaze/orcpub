// Places in the character builder. Locators go by role and accessible name first.

/**
 * A builder section tab (Options, Description, Portrait, Details).
 * @param {import('playwright').Page} page
 * @param {string} name the tab's title
 * @returns {import('playwright').Locator}
 */
const tab = (page, name) => page.getByRole('tab', { name, exact: true });

module.exports = { tab };
