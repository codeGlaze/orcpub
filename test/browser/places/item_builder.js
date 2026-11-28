// Places in the magic item builder. Roles and names first.

const { escapeRegExp } = require('../lib/toolbox');

/**
 * A checkbox row in the item builder, found by role and its label (for example "Acid" under Damage
 * Resistances). The first match when several sections list the same label.
 * @param {import('playwright').Page} page
 * @param {string} name the checkbox's label
 * @returns {import('playwright').Locator}
 */
const checkbox = (page, name) => page.getByRole('checkbox', { name, exact: true }).first();

/**
 * A checkbox row inside one titled section of a homebrew builder (the section whose heading reads
 * `section` exactly), so a label that several sections list is found in the right one.
 * @param {import('playwright').Page} page
 * @param {string} section the section heading, for example "Tool Proficiency"
 * @param {string} name the checkbox's label
 * @returns {import('playwright').Locator}
 */
const sectionCheckbox = (page, section, name) => page
  .locator('div.m-b-20', { has: page.locator(':scope > div.f-w-b', { hasText: new RegExp(`^${escapeRegExp(section)}$`) }) })
  .getByRole('checkbox', { name, exact: true }).first();

/** Census descriptors: one checkbox row of the item builder. */
const census = {
  'itemBuilder.checkbox(Acid)': {
    route: '/pages/dnd/5e/magic-item-builder', locator: page => checkbox(page, 'Acid'),
    preconditions: { viewport: 'desktop' }, expectedCount: 1 },
};

module.exports = { checkbox, sectionCheckbox, census };
