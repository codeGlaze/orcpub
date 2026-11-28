// Places in the character builder. Locators go by role and accessible name first.

/**
 * A builder section tab (Options, Description, Portrait, Details).
 * @param {import('playwright').Page} page
 * @param {string} name the tab's title
 * @returns {import('playwright').Locator}
 */
const tab = (page, name) => page.getByRole('tab', { name, exact: true });

const ROUTE = '/pages/dnd/5e/character-builder';

/** Census descriptors (place_census_e2e.js): the builder tabs on each layout. Details is a tab only
 *  on a phone; a desktop shows it as a column. */
const census = {};
for (const name of ['Options', 'Description', 'Portrait']) {
  for (const viewport of ['desktop', 'phone']) {
    census[`builder.tab(${name}) ${viewport}`] = {
      route: ROUTE, locator: page => tab(page, name), preconditions: { viewport }, expectedCount: 1 };
  }
}
census['builder.tab(Details) phone'] = {
  route: ROUTE, locator: page => tab(page, 'Details'), preconditions: { viewport: 'phone' }, expectedCount: 1 };

module.exports = { tab, census };
