// Places on the character sheet. Locators go by role and accessible name first.

/**
 * One of the sheet's section tabs (Summary, Combat, Proficiencies, Spells, Features, Equipment).
 * @param {import('playwright').Page} page
 * @param {string} name the tab's title in Title Case
 * @returns {import('playwright').Locator}
 */
const sectionTab = (page, name) => page.getByRole('tab', { name, exact: true });

module.exports = { sectionTab };
