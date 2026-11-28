// Places on the character sheet. Locators go by role and accessible name first.

/**
 * One of the sheet's section tabs (Summary, Combat, Proficiencies, Spells, Features, Equipment).
 * @param {import('playwright').Page} page
 * @param {string} name the tab's title in Title Case
 * @returns {import('playwright').Locator}
 */
const sectionTab = (page, name) => page.getByRole('tab', { name, exact: true });

/** Census descriptors: the sheet's section tabs as the builder shows them for a new character (no
 *  class, so no Spells tab): in the desktop details column, and on a phone's Details tab. */
const census = {};
for (const name of ['Summary', 'Combat', 'Proficiencies', 'Features', 'Equipment']) {
  census[`sheet.sectionTab(${name}) builder desktop`] = {
    route: '/pages/dnd/5e/character-builder', locator: page => sectionTab(page, name),
    preconditions: { viewport: 'desktop' }, expectedCount: 1 };
  census[`sheet.sectionTab(${name}) builder phone`] = {
    route: '/pages/dnd/5e/character-builder', locator: page => sectionTab(page, name),
    preconditions: { viewport: 'phone', open: 'builder.details' }, expectedCount: 1 };
}

module.exports = { sectionTab, census };
