// Places in the sheet's Weapons table and its roll result sheet. Roles and names where the markup
// has them; otherwise the class selector, kept only in this file. `scope` is a page or a locator.

const { escapeRegExp } = require('../lib/toolbox');

// Class selectors for parts with no role or name, and for layout parts only measured inside
// page.evaluate (pass `parts` in as the evaluate argument). Suites read them from here.
const parts = {
  section: '.weapons-section',
  sectionDesktop: '.weapons-section.desktop',
  row: '.weapon-row',
  name: '.weapon-name',
  nameLine: '.weapon-name-line',
  head: '.weapon-head > span',
  hitTile: '.weapon-hit-tile',
  hitStrip: '.weapon-hit-tile .weapon-strip',
  damageTile: '.weapon-damage-tile',
  noRoll: '.weapon-no-roll',
  damageOrNoRoll: '.weapon-damage-tile, .weapon-no-roll',
  smallCandidates: '.weapon-cell-name, .weapon-tile',
  rowDescendants: '.weapon-row *',
  greyLine: '.weapon-grey-line',
  tooltipHitTile: '.tooltip .weapon-hit-tile',
  switchPulse: '.weapon-switch.pulse',
  switchDisabled: '.weapon-switch.disabled',
  linesSwitch: '.weapon-lines .weapon-switch',
  greyLineSwitch: '.weapon-grey-line .weapon-switch',
  opened: '.weapon-opened',
  plus: '.weapon-plus',
  menu: '.weapon-menu',
  menuUp: '.weapon-menu.up',
  menuInSheet: '.weapon-menu-sheet .weapon-menu',
  reason: '.weapons-reason',
  masteryBand: '.weapon-mastery-band',
  handPill: '.weapon-hand-pill',
  nameLineHandPill: '.weapon-name-line .weapon-hand-pill',
  flipped: '.fa-flip-horizontal',
  needsHands: '.needs-hands',
  sheet: '.weapon-sheet',
  sheetCard: '.weapon-sheet.card',
  sheetTitle: '.weapon-sheet-title',
  sheetOption: '.weapon-sheet-option',
  sheetHistoryRow: '.weapon-sheet-history-row',
  sheetD20: '.weapon-sheet-d20',
  sheetD20Struck: '.weapon-sheet-d20 s',
  sheetD20Kept: '.weapon-sheet-d20 b',
};

/** The Weapons table (all that are mounted: phone and desktop layouts may both exist). */
const section = scope => scope.locator(parts.section);

/** The table row of the weapon named exactly `name`, within `scope`. */
const row = (scope, name) => {
  // `has` is matched inside each row, so its locator must start from the page, not from `scope`
  const root = typeof scope.page === 'function' ? scope.page() : scope;
  return scope.locator(parts.row)
    .filter({ has: root.locator(parts.name, { hasText: new RegExp(`^${escapeRegExp(name)}$`) }) });
};

/** The to-hit tile of a row. */
const hitTile = r => r.locator(parts.hitTile);

/** The damage tile of a row. */
const damageTile = r => r.locator(parts.damageTile);

/** The button on a row's name line that opens the row. */
const nameLine = r => r.locator('.weapon-name-line');

/** A radio of a row's switch group, by its label (1H, 2H, Ablaze...). */
const radio = (r, name) => r.getByRole('radio', { name });

/** A row's + menu button. */
const plus = r => r.locator('.weapon-plus');

/** An item of a row's + menu, by name (string or regexp). */
const menuItem = (r, name) => r.getByRole('menuitem', { name });

/** The roll result sheet (bottom sheet on a phone, a card on desktop). */
const rollSheet = scope => scope.locator('.weapon-sheet');

/** The sheet's Done button. */
const done = scope => rollSheet(scope).getByRole('button', { name: 'Done', exact: true });

/** The sheet's close button, which ends the turn. */
const close = scope => rollSheet(scope).getByRole('button', { name: 'End the turn' });

/** The sheet's corner tile that rolls the next attack. */
const next = scope => rollSheet(scope).getByRole('button', { name: /^Roll attack/ });

/** A damage option in the sheet whose text contains `text`. */
const option = (scope, text) => rollSheet(scope).locator('.weapon-sheet-option', { hasText: text });

/** The sheet's highlighted (primary) damage option. */
const primaryOption = scope => rollSheet(scope).locator('.weapon-sheet-option.primary');

module.exports = { parts, section, row, hitTile, damageTile, nameLine, radio, plus, menuItem,
                   rollSheet, done, close, next, option, primaryOption };
