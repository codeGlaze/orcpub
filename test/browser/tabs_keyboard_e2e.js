// The WAI-ARIA tabs pattern on the sheet's section tabs and the builder's tabs.
//
// On each tablist: exactly one tab is in the Tab order (tabindex 0) and it is the selected one;
// ArrowRight focuses and selects the next tab and its panel shows; ArrowLeft goes back; End and
// Home jump to the last and first; ArrowRight on the last wraps to the first. The shown panel has
// role tabpanel and is labelled by the selected tab's id, and the selected tab aria-controls it.
// Phone layout (390px): the builder's Options/Description/Portrait/Details tabs, then, under
// Details, the sheet's six section tabs.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/tabs_keyboard_e2e.js`). No login.
// Runs in:   ~20s.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { SetupFailed, BASE, checker, phoneContext, quietOverlays } = require('./lib/toolbox');
const builder = require('./places/builder');
const sheetTabs = require('./places/sheet');

const { check, attempt, must, watch, finish } = checker();

// The state of every tab in `list`: tabindex, selected, id, aria-controls.
const tabState = list => list.getByRole('tab').evaluateAll(els => els.map(e => ({
  name: e.getAttribute('aria-label'), ti: e.getAttribute('tabindex'),
  sel: e.getAttribute('aria-selected') === 'true', id: e.id, controls: e.getAttribute('aria-controls'),
  focused: document.activeElement === e })));

// Checks one tablist: roving tabindex, arrows, Home, End, wrap, and the panel's labelling.
async function tablistChecks(page, label, list, listSel, panelSel, first, second, last) {
  const st = await tabState(list);
  const sel = st.filter(t => t.sel);
  check(`${label}: exactly one tab has tabindex 0, the selected one, the rest -1`,
        sel.length === 1 && sel[0].ti === '0' && st.filter(t => t.ti === '0').length === 1
        && st.filter(t => !t.sel).every(t => t.ti === '-1'), JSON.stringify(st.map(t => t.ti)));
  const panel = page.locator(panelSel);
  const labelled = async () => {
    const s = (await tabState(list)).find(t => t.sel);
    return await panel.count() === 1 && await panel.getAttribute('role') === 'tabpanel'
      && s && s.id && await panel.getAttribute('aria-labelledby') === s.id && s.controls === await panel.getAttribute('id');
  };
  check(`${label}: the panel is a tabpanel labelled by the selected tab, which controls it`, await labelled());
  const press = async (key, name) => {
    await page.keyboard.press(key);
    // The selection is a re-frame event applied on the next frame: wait for it to land.
    await attempt(`${label}: ${key} selects "${name}"`, page.waitForFunction(
      ([sel, n]) => { const e = document.querySelector(`${sel} [role=tab][aria-selected=true]`); return e && e.getAttribute('aria-label') === n; },
      [listSel, name], { timeout: 3000 }));
    const t = await tabState(list);
    const s = t.find(x => x.sel);
    check(`${label}: ${key} focuses and selects "${name}"`,
          !!s && s.name === name && s.focused && s.ti === '0', JSON.stringify(s));
    check(`${label}: after ${key} the panel is labelled by "${name}"`, await labelled());
  };
  await attempt(`${label}: focus the first tab`, list.getByRole('tab', { name: first, exact: true }).focus());
  await press('ArrowRight', second);
  await press('ArrowLeft', first);
  await press('End', last);
  await press('ArrowRight', first);
  await press('End', last);
  await press('Home', first);
}

// Every id on the page that more than one element carries.
const duplicateIds = page => page.evaluate(() => {
  const seen = {};
  document.querySelectorAll('[id]').forEach(e => { seen[e.id] = (seen[e.id] || 0) + 1; });
  return Object.keys(seen).filter(k => seen[k] > 1);
});

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await phoneContext(browser);
  await quietOverlays(ctx);
  const page = await ctx.newPage();
  const w = watch(page, 'phone page');
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await must('see the builder tabs', builder.tab(page, 'Options').waitFor({ timeout: 60000 }));

  await tablistChecks(page, 'builder', page.getByRole('tablist', { name: 'Builder sections' }),
                      '[role=tablist][aria-label="Builder sections"]',
                      '#builder-tabpanel', 'Options', 'Description', 'Details');
  await must('open the builder\'s Details tab', builder.tab(page, 'Details').click({ timeout: 20000 }));
  await must('see the sheet tabs', sheetTabs.sectionTab(page, 'Combat').waitFor({ timeout: 20000 }));
  await tablistChecks(page, 'sheet', page.getByRole('tablist', { name: 'Character sheet sections' }),
                      '[role=tablist][aria-label="Character sheet sections"]',
                      '[role=tabpanel][id^=sheet-tabpanel-]', 'Summary', 'Combat', 'Equipment');

  const dups = await duplicateIds(page);
  check('no id is used twice on the page', dups.length === 0, dups.slice(0, 5).join(' '));
  const sheetIds = await page.getByRole('tablist', { name: 'Character sheet sections' }).getByRole('tab')
    .evaluateAll(els => els.map(e => e.id));
  check('the sheet tab ids carry the sheet\'s scope (the character id, or "new")',
        sheetIds.length > 0 && sheetIds.every(i => /^sheet-tab-(new|\d+)-/.test(i)), sheetIds.join(' '));

  check('no page errors', w.pageErrors.length === 0, w.pageErrors.slice(0, 2).join(' | '));
  await browser.close();
  process.exit(finish());
})().catch(e => {
  if (e instanceof SetupFailed) { console.log('STOP  ' + e.message); process.exit(finish()); }
  console.error(e); process.exit(2);
});
