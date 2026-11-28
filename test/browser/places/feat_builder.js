// Places in the feat builder. Its checkbox rows are the same control as the item builder's.

const { checkbox, sectionCheckbox } = require('./item_builder');

/** Census descriptors: a value grid's row and a section-scoped row of the feat builder, and the race
 *  builder's Tool Proficiency row (the same control). */
const census = {
  'featBuilder.checkbox(speed +10 ft.)': {
    route: '/pages/dnd/5e/feat-builder',
    locator: page => checkbox(page, 'Your speed is increased by 10 ft.'),
    preconditions: { viewport: 'desktop' }, expectedCount: 1 },
  "featBuilder.sectionCheckbox(Tool Proficiency or Expertise, Thieves' Tools)": {
    route: '/pages/dnd/5e/feat-builder',
    locator: page => sectionCheckbox(page, 'Tool Proficiency or Expertise', "Thieves' Tools"),
    preconditions: { viewport: 'desktop' }, expectedCount: 1 },
  "raceBuilder.sectionCheckbox(Tool Proficiency, Thieves' Tools)": {
    route: '/pages/dnd/5e/race-builder',
    locator: page => sectionCheckbox(page, 'Tool Proficiency', "Thieves' Tools"),
    preconditions: { viewport: 'desktop' }, expectedCount: 1 },
};

module.exports = { checkbox, sectionCheckbox, census };
