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
        dismissCookieBar, chipIsOn, chipClick, pickOption, optionsOf } = require('./lib');

const SOURCE = 'Spell Pin';
const NAME = 'Tideward';

// Toggles are chips (lib.js/chipIsOn, chipClick). They were glyph checkboxes when this pin was
// written against the bespoke form; the representation changed deliberately, so the helpers did
// too. What the pin asserts is unchanged: the toggle is present, starts off, turns on, and stores
// a real boolean.
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
      check(`toggle present: ${label}`, (await chipIsOn(page, label)) !== null);
      check(`and it starts OFF: ${label}`, (await chipIsOn(page, label)) === false);
    }
    check('the bespoke spell-list widget is still here',
          /which class spell lists/i.test(await page.locator('#app').innerText()));

    check('filled Name', await fill(page, 'Name', NAME));
    check('filled Option Source Name', await fill(page, 'Option Source Name', SOURCE));
    check('chose a Level', await pickOption(page, 'Level', /^3rd-level$/));
    check('chose a School', await pickOption(page, 'School', /^abjuration$/));
    check('filled Casting Time', await fill(page, 'Casting Time', '1 action'));
    check('filled Range', await fill(page, 'Range', '30 feet'));
    check('filled Duration', await fill(page, 'Duration', '1 minute'));
    check('filled Description', await fill(page, 'Description', 'A ward of cold seawater.'));

    check('toggled Ritual on', await chipClick(page, 'Ritual?'));
    await page.waitForTimeout(400);
    check('and it now reads ON', (await chipIsOn(page, 'Ritual?')) === true);

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

    // ── beyond the conversion: fields the bespoke form never had ────────────────────
    // Kept separate from the pin above on purpose. The checks before this line describe the form
    // that was replaced; these describe deliberate additions to it.
    check('Page is authorable (real spells carry :page; no control ever wrote it)',
          !!(await controlFor(page, 'Page')));
    check('filled Page', await fill(page, 'Page', '212'));

    const combo = await page.evaluate(() => {
      const out = {};
      for (const [label, id] of [['Casting Time', 'combo-casting-time'],
                                 ['Range', 'combo-range'], ['Duration', 'combo-duration']]) {
        const dl = document.getElementById(id);
        out[label] = dl ? dl.options.length : 0;
      }
      const mat = [...document.querySelectorAll('input')].find(i => /powdered rhubarb/i.test(i.placeholder || ''));
      out.placeholder = !!mat;
      return out;
    });
    check('Casting Time suggests the values real spells use',  combo['Casting Time'] === 13, JSON.stringify(combo));
    check('Range suggests the values real spells use',         combo['Range'] === 29);
    check('Duration suggests the values real spells use',      combo['Duration'] === 29);
    check('and a combo still accepts free text', await fill(page, 'Casting Time', '3 rounds and a wink'));
    check('Material Component has a worked example as placeholder', combo.placeholder);

    check('clicked SAVE again', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(900);
    const saved2 = await dbAt(page, `[:plugins "${SOURCE}"]`);
    check('the page number saves as a number', /:page 212/.test(saved2), saved2.slice(0, 240));
    check('and the free-text casting time saves verbatim',
          /3 rounds and a wink/.test(saved2), saved2.slice(0, 240));

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
