// E1 characterization pin — the language builder, the cheapest tier-1 form in the app.
//
// Why this exists: Track E converts the 16 hand-written builder forms to the declarative
// `simple-content-builder`. The conversion recipe is pin → swap → the SAME test green, and this
// is the pin for the first one. It asserts only what a user can observe — the three fields, the
// save, and the shape that lands in :plugins — so it is indifferent to how the form is built and
// stays honest across the swap.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server   (port 8890)
// Run:      node test/e2e/language-builder.js
// Exit 0 = pass.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, controlFor, fill, clickText } = require('./lib');

const SOURCE = 'E1 Source';
const NAME = 'Thieves Argot';
const DESC = 'A clipped trade cant of the dockside crews.';

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1400, height: 1000 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });

  try {
    await page.goto(`${BASE}/pages/dnd/5e/language-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);

    const body = await page.textContent('body');
    check('page renders', /language/i.test(body), `body starts: ${body.trim().slice(0, 60)}`);

    // The whole of tier 1: a name, where it came from, and what it is.
    for (const label of ['Name', 'Option Source Name', 'Description']) {
      check(`field present: ${label}`, !!(await controlFor(page, label)));
    }

    check('filled Name', await fill(page, 'Name', NAME));
    check('filled Option Source Name', await fill(page, 'Option Source Name', SOURCE));
    check('filled Description', await fill(page, 'Description', DESC));
    await page.screenshot({ path: path.join(SHOTS, 'language-builder-filled.png'), fullPage: true });

    check('clicked SAVE', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(800);

    const saved = await dbAt(page, `[:plugins "${SOURCE}" :orcpub.dnd.e5/languages]`);
    check('language saves into :plugins under its source',
          /thieves/i.test(saved), saved.slice(0, 200));
    check('and it keeps its description',
          /dockside crews/.test(saved), saved.slice(0, 200));
    // A homebrew item is addressed by a stable key derived from the name (D10) — an item without
    // one cannot be referenced, exported, or overridden later.
    check('and it has a key', /:key\s+:/.test(saved), saved.slice(0, 200));

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
