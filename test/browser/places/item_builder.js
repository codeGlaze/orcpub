// Places in the magic item builder. Roles and names first.

/**
 * A checkbox row in the item builder, found by role and its label (for example "Acid" under Damage
 * Resistances). The first match when several sections list the same label.
 * @param {import('playwright').Page} page
 * @param {string} name the checkbox's label
 * @returns {import('playwright').Locator}
 */
const checkbox = (page, name) => page.getByRole('checkbox', { name, exact: true }).first();

module.exports = { checkbox };
