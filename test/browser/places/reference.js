// Places on the reference pages (spells, monsters, magic items). Roles and names first.

/**
 * The Monsters page's control that shows or hides the trait filters. A phone shows only its caret,
 * so its name comes from aria-label.
 * @param {import('playwright').Page} page
 * @param {boolean} [expanded] true for the "Hide filters" state
 * @returns {import('playwright').Locator}
 */
const monsterFilterToggle = (page, expanded = false) =>
  page.getByRole('button', { name: expanded ? 'Hide filters' : 'Show filters', exact: true });

/** Census descriptors: the filter toggle on a phone, where it has no visible text. */
const census = {
  'reference.monsterFilterToggle phone': {
    route: '/pages/dnd/5e/monsters', locator: page => monsterFilterToggle(page),
    preconditions: { viewport: 'phone' }, expectedCount: 1 },
};

module.exports = { monsterFilterToggle, census };
