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

/**
 * The Description tab's Faction Image URL input. It has no accessible name (its label is a loose
 * span shared by every builder text field), so it is found by that label's text.
 * @param {import('playwright').Page} page
 * @returns {import('playwright').Locator}
 */
const factionImageUrl = page => page
  .locator('div', { has: page.locator(':scope > span.personality-label', { hasText: /^Faction Image URL/ }) })
  .locator('input').first();

/** The faction picture shown beside the Faction Image URL field (absent until the link is a web address). */
const factionThumbnail = page => page.locator('img.image-faction-thumbnail');

/** The Description tab's button that opens the picture drawer. */
const changePicture = page => page.getByRole('button', { name: 'Change picture', exact: true });

/** The picture drawer's switch to its link mode. */
const pictureLinkMode = page => page.getByRole('button', { name: /^Use an image from the web/ });

/** The picture drawer's image address field. */
const pictureLink = page => page.getByRole('textbox', { name: 'Image address (URL)', exact: true });

/** The picture drawer's button that makes the typed link the character's picture. */
const useImage = page => page.getByRole('button', { name: /^(Use this image|Switch to this image)$/ });

module.exports = { tab, census, factionImageUrl, factionThumbnail, changePicture, pictureLinkMode, pictureLink,
                   useImage };
