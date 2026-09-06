// Characterization pin for the spell builder, written against the BESPOKE form and re-run
// unchanged after the conversion. Asserts only what an author can observe: the controls, and the
// shape that lands in :plugins.
//
// Spell is the first conversion to need `:boolean` (Ritual, Requires Attack Roll, the three
// components) and the first to keep a bespoke widget: the "add to which class spell lists"
// checkboxes are built from a live subscription, which no field type describes. That widget is
// passed through as hiccup — the escape hatch is part of the design, not a failure of it.
//
// Prereqs:  lein garden once && lein fig:build && lein e2e-server
// Run:      node test/e2e/spell-builder.js
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, controlFor, fill, clickText,
        dismissCookieBar } = require('./lib');

const SOURCE = 'Spell Pin';
const NAME = 'Tideward';

// A labelled checkbox: the app renders an <i> glyph plus a text span, in both the bespoke form
// (comps/labeled-checkbox) and the converted one (the :boolean field renders the same component).
const toggle = (page, label) => page.evaluate((t) => {
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const span = [...document.querySelectorAll('span')]
    .find(e => e.children.length === 0 && e.textContent.trim() === t && vis(e));
  if (!span) return false;
  (span.closest('.pointer') || span.parentElement).click();
  return true;
}, label);

const isOn = (page, label) => page.evaluate((t) => {
  const span = [...document.querySelectorAll('span')]
    .find(e => e.children.length === 0 && e.textContent.trim() === t);
  if (!span) return null;
  const row = span.parentElement;
  const glyph = row && row.querySelector('i.fa-check');
  if (!glyph) return null;
  // "on" is drawn by swapping the glyph's colour classes, not by a checked attribute
  return /black/.test(glyph.className) && !/transparent/.test(glyph.className);
}, label);

async function choose(page, label, rx) {
  const sel = await controlFor(page, label);
  if (!sel) return false;
  return page.evaluate(({ e, src }) => {
    const opt = [...e.options].find(o => new RegExp(src, 'i').test(o.textContent.trim()));
    if (!opt) return false;
    const set = Object.getOwnPropertyDescriptor(window.HTMLSelectElement.prototype, 'value').set;
    set.call(e, opt.value);
    e.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  }, { e: sel, src: rx.source });
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1200, height: 1200 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  try {
    await page.goto(`${BASE}/pages/dnd/5e/spell-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1800);
    await dismissCookieBar(page);

    for (const label of ['Name', 'Option Source Name', 'Level', 'School',
                         'Casting Time', 'Range', 'Duration', 'Description']) {
      check(`field present: ${label}`, !!(await controlFor(page, label)));
    }
    for (const label of ['Ritual?', 'Requires Attack Roll?']) {
      check(`toggle present: ${label}`, (await isOn(page, label)) !== null);
      check(`and it starts OFF: ${label}`, (await isOn(page, label)) === false);
    }
    check('the bespoke spell-list widget is still here',
          /which class spell lists/i.test(await page.locator('#app').innerText()));

    check('filled Name', await fill(page, 'Name', NAME));
    check('filled Option Source Name', await fill(page, 'Option Source Name', SOURCE));
    check('chose a Level', await choose(page, 'Level', /^3rd-level$/));
    check('chose a School', await choose(page, 'School', /^abjuration$/));
    check('filled Casting Time', await fill(page, 'Casting Time', '1 action'));
    check('filled Range', await fill(page, 'Range', '30 feet'));
    check('filled Duration', await fill(page, 'Duration', '1 minute'));
    check('filled Description', await fill(page, 'Description', 'A ward of cold seawater.'));

    check('toggled Ritual on', await toggle(page, 'Ritual?'));
    await page.waitForTimeout(400);
    check('and it now reads ON', (await isOn(page, 'Ritual?')) === true);

    await page.screenshot({ path: path.join(SHOTS, 'spell-builder-filled.jpg'),
                            fullPage: true, type: 'jpeg', quality: 72 });

    check('clicked SAVE', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(1000);

    const saved = await dbAt(page, `[:plugins "${SOURCE}" :orcpub.dnd.e5.spells/spells]`);
    const anywhere = /tideward/i.test(saved) ? saved
                   : await dbAt(page, `[:plugins "${SOURCE}"]`);
    check('the spell saves under its source', /tideward/i.test(anywhere), anywhere.slice(0, 200));
    check('with its level and school', /:level 3/.test(anywhere) && /abjuration/i.test(anywhere),
          anywhere.slice(0, 240));
    check('and the toggle stored a real boolean, not a string',
          /:ritual true/.test(anywhere), anywhere.slice(0, 240));
    check('and the text fields survived',
          /1 action/.test(anywhere) && /30 feet/.test(anywhere) && /1 minute/.test(anywhere));

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
