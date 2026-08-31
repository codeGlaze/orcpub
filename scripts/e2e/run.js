// Browser end-to-end check of the PDF export.
//
//   ./scripts/e2e/run.sh
//
// Builds characters through the real builder and exports each one, so the whole
// path runs: pdf_spec assembling the field map in the browser, the form POST,
// then routes.clj and pdf.clj. The scenarios differ in spellcasting classes,
// which is what decides how many pages the sheet needs.
//
// The PDFs land in $E2E_OUT; run.sh then inspects them with PDFBox, which is the
// only place field names can be read (they sit in compressed object streams).
//
// Console output is collected throughout and any error or warning fails the run.

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const OUT = process.env.E2E_OUT || '/tmp/e2e-pdf';
// The bundled Chromium build can be older than this playwright release expects,
// so point at what is installed rather than letting it look for a download.
const EXECUTABLE = process.env.E2E_CHROMIUM
  || ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
      '/opt/pw-browsers/chromium/chrome-linux/chrome'].find(p => fs.existsSync(p));

const noise = [
  /favicon/i,
  // No outbound network in the sandbox, so the webfont never loads. That is the
  // environment, not the app; the page renders in its fallback stack.
  /fonts\.googleapis\.com/i,
  /ERR_CONNECTION_RESET/i,
];

// A character worth exporting, and what its sheet should need. pdf_spec emits a
// spellcasting section per class that HAS SPELLS, so a caster with none chosen
// produces no spell page -- hence `spells` on the casting scenarios.
const SCENARIOS = [
  { name: 'barbarian',  classes: [['Barbarian', '20']], spells: 0, minPages: 3,
    note: 'no spellcasting, so no spell pages' },
  { name: 'wizard-20',  classes: [['Wizard', '20']], spells: 10, minPages: 4,
    note: 'one caster with spells chosen at every level' },
  { name: 'multiclass', classes: [['Wizard', '5'], ['Cleric', '5'], ['Sorcerer', '5']],
    spells: 12, minPages: 6, abilities: true,
    note: 'three casting classes: several spell pages, and a class whose list ' +
          'outgrows one page takes another' },
];

function record(page, sink, label) {
  page.on('console', msg => {
    const type = msg.type();
    if (type !== 'error' && type !== 'warning') return;
    const text = msg.text();
    if (noise.some(re => re.test(text))) return;
    sink.push(`${label}: console.${type}: ${text}`);
  });
  page.on('pageerror', err => sink.push(`${label}: pageerror: ${err.message}`));
  page.on('requestfailed', req => {
    if (noise.some(re => re.test(req.url()))) return;
    sink.push(`${label}: request failed: ${req.url()} (${req.failure()?.errorText})`);
  });
}

// Several nodes carry the same label and some are hidden -- the mobile layout
// renders its own copy -- so .first() often lands on one nothing can click.
async function firstVisible(scope, selector) {
  const all = scope.locator(selector);
  const count = await all.count();
  for (let i = 0; i < count; i++) {
    const el = all.nth(i);
    if (await el.isVisible()) return el;
  }
  return null;
}

async function clickVisible(scope, selector) {
  const el = await firstVisible(scope, selector);
  if (el) await el.click();
  return Boolean(el);
}

// The builder's step tabs sit in the left options panel; the same words appear in
// the top navigation, so pick by position rather than by text alone.
async function openBuilderTab(page, label) {
  // The tab row is near the top of the options panel, and these helpers scroll
  // down to reach fields, so return there before looking for it.
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(400);
  const all = page.locator(`text=${label}`);
  for (let i = 0; i < await all.count(); i++) {
    const el = all.nth(i);
    if (!(await el.isVisible())) continue;
    const box = await el.boundingBox();
    if (box && box.x < 650 && box.y > 400) { await el.click(); return true; }
  }
  return false;
}

// Class rows gain extra dropdowns as levels rise -- a Wizard at 5 picks an
// Arcane Tradition -- so the class and level selects cannot be found by position.
// Classify them by what they contain instead: a class select offers class names,
// a level select offers only numbers.
const CLASS_NAMES = ['Barbarian', 'Bard', 'Cleric', 'Druid', 'Fighter', 'Monk',
                     'Paladin', 'Ranger', 'Rogue', 'Sorcerer', 'Warlock', 'Wizard'];

async function classifySelects(page) {
  const selects = page.locator('select');
  const classSelects = [];
  const levelSelects = [];
  for (let i = 0; i < await selects.count(); i++) {
    const el = selects.nth(i);
    if (!(await el.isVisible())) continue;
    const options = (await el.locator('option').allTextContents()).map(o => o.trim());
    // A second class row omits the classes already taken, so no single name is
    // guaranteed -- count how many of them appear instead.
    const classish = options.filter(o => CLASS_NAMES.includes(o)).length;
    if (classish >= 3) classSelects.push(el);
    else if (options.length > 1 && options.every(o => /^\d+$/.test(o))) levelSelects.push(el);
  }
  return { classSelects, levelSelects };
}

// Multiclassing into a caster requires the relevant mental score at 13, and the
// default array leaves WIS at 10 and CHA at 8 -- so every caster is refused.
// Manual Entry exposes the six base scores as editable inputs.
async function raiseAbilities(page, check) {
  check(await openBuilderTab(page, 'Ability Scores'), 'the builder has an Ability Scores step');
  await page.waitForTimeout(1500);
  check(await clickVisible(page, 'text=Manual Entry'), 'abilities can be entered by hand');
  await page.waitForTimeout(1500);

  // The first six number inputs are the base scores, in STR DEX CON INT WIS CHA
  // order; the rest are the computed totals and are read-only.
  const inputs = page.locator('input[type=number]');
  let set = 0;
  for (let i = 0; i < await inputs.count() && set < 6; i++) {
    const el = inputs.nth(i);
    if (!(await el.isVisible())) continue;
    await el.fill('15');
    set += 1;
    await page.waitForTimeout(200);
  }
  check(set === 6, `set ${set} ability scores to 15`);
  await page.waitForTimeout(1000);
}

async function setClasses(page, classes, check) {
  await openBuilderTab(page, 'Class / Level');
  await page.waitForTimeout(1200);

  for (let i = 0; i < classes.length; i++) {
    if (i > 0) {
      // Adds the first class not already taken; its dropdown is then re-pointed.
      const added = await clickVisible(page, 'text=Add Levels in Another Class');
      check(added, `added a slot for class ${i + 1}`);
      await page.waitForTimeout(1500);
    }
    const [className, level] = classes[i];
    let { classSelects, levelSelects } = await classifySelects(page);
    check(classSelects.length > i, `class ${i + 1} has a class dropdown`);

    // Multiclass options carry their prerequisite in the label -- "Cleric
    // (Requires WIS 13 or higher)" -- so match on the leading class name.
    const classOptions = (await classSelects[i].locator('option').allTextContents())
      .map(o => o.trim());
    const optionIndex = classOptions.findIndex(o => o.startsWith(className));
    check(optionIndex >= 0,
          `${className} is offered for class ${i + 1}` +
          (optionIndex >= 0 ? '' : ` (offered: ${classOptions.join(', ')})`));
    await classSelects[i].selectOption({ index: optionIndex, timeout: 10000 });
    await page.waitForTimeout(1200);

    // Re-read: changing the class re-renders the row and its level dropdown.
    ({ levelSelects } = await classifySelects(page));
    check(levelSelects.length > i, `class ${i + 1} has a level dropdown`);
    const levelOptions = (await levelSelects[i].locator('option').allTextContents())
      .map(o => o.trim());
    check(levelOptions.includes(level),
          `level ${level} is offered for class ${i + 1}` +
          (levelOptions.includes(level) ? '' : ` (offered up to ${levelOptions.slice(-1)})`));
    await levelSelects[i].selectOption({ label: level, timeout: 10000 });
    await page.waitForTimeout(1200);
  }
}

// Spell rows are not real checkboxes -- the square is styled markup -- so the
// row label is what gets clicked. Its class distinguishes it from, say, the
// "Light Theme" toggle, which a text match on "Light" would otherwise hit.
async function selectSpells(page, wanted, check) {
  check(await openBuilderTab(page, 'Spells'), 'the builder has a Spells step');
  await page.waitForTimeout(2000);
  await page.mouse.wheel(0, 600);
  await page.waitForTimeout(800);

  const rows = page.locator('span.f-w-b.f-s-1.flex-grow-1');
  const available = await rows.count();
  let selected = 0;
  for (let i = 0; i < available && selected < wanted; i++) {
    const row = rows.nth(i);
    if (!(await row.isVisible())) continue;
    await row.click({ timeout: 4000 }).catch(() => {});
    selected += 1;
    await page.waitForTimeout(250);
  }
  check(selected >= wanted, `chose ${selected} spells of ${wanted} wanted`);
}

async function exportPdf(page, context, check) {
  let pdfBytes = null;
  // Chromium hands a PDF response to its internal viewer, so response.body()
  // returns the viewer's wrapper HTML rather than the document. Intercepting the
  // route runs the page's own request and yields the real bytes.
  const handler = async route => {
    const res = await route.fetch();
    pdfBytes = await res.body();
    await route.fulfill({ response: res });
  };
  await context.route('**/character.pdf', handler);

  check(await clickVisible(page, 'text=Export'), 'the builder offers an Export control');
  await page.waitForTimeout(1800);

  // "Create PDF" stays disabled until a sheet is chosen: print-button-enabled in
  // views.cljs gates on print-character-sheet-style? being set.
  const styles = await firstVisible(page, 'select');
  check(styles !== null, 'the panel offers a character sheet dropdown');
  await styles.selectOption({ index: 1 });
  await page.waitForTimeout(500);

  const createPdf = await firstVisible(page, 'button:has-text("Create PDF")');
  check(createPdf !== null, 'the export panel offers a clickable "Create PDF"');
  check(await createPdf.isEnabled(), 'and it is enabled once a sheet is chosen');

  // The sticky header overlays whatever playwright scrolls to.
  await createPdf.evaluate(el => el.scrollIntoView({ block: 'center' }));
  await page.waitForTimeout(500);

  const [response] = await Promise.all([
    context.waitForEvent('response',
      { predicate: r => r.url().includes('character.pdf'), timeout: 90000 }),
    createPdf.click({ timeout: 10000 }).catch(() => createPdf.click({ force: true })),
  ]);
  check(response.status() === 200, `the export answers 200 (got ${response.status()})`);
  await page.waitForTimeout(1500);
  await context.unroute('**/character.pdf', handler);
  return pdfBytes;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  if (!EXECUTABLE) {
    console.error('no chromium found under /opt/pw-browsers; set E2E_CHROMIUM');
    process.exit(1);
  }
  const problems = [];
  const failures = [];
  const check = (ok, message) => {
    console.log(`    ${ok ? 'ok  ' : 'FAIL'}  ${message}`);
    if (!ok) failures.push(message);
  };

  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const context = await browser.newContext({ acceptDownloads: true });

  for (const scenario of SCENARIOS) {
    console.log(`\n${scenario.name}: ${scenario.classes.map(c => c.join(' ')).join(' / ')}`);
    console.log(`  (${scenario.note})`);
    const page = await context.newPage();
    record(page, problems, scenario.name);
    try {
      await page.goto(`${BASE}/pages/dnd/5e/character-builder`,
                      { waitUntil: 'networkidle', timeout: 60000 });
      await page.waitForTimeout(2500);
      await clickVisible(page, 'text=Got it!');   // cookie banner covers the controls

      if (scenario.abilities) await raiseAbilities(page, check);
      await setClasses(page, scenario.classes, check);
      if (scenario.spells > 0) await selectSpells(page, scenario.spells, check);
      await page.screenshot({ path: path.join(OUT, `${scenario.name}-built.png`) });

      const bytes = await exportPdf(page, context, check);
      check(bytes !== null && bytes.length > 50000,
            `the export came back (${bytes ? Math.round(bytes.length / 1024) : 0} KB)`);
      if (bytes) {
        check(bytes.slice(0, 5).toString() === '%PDF-', 'and is a PDF');
        fs.writeFileSync(path.join(OUT, `${scenario.name}.pdf`), bytes);
        // run.sh inspects each PDF with PDFBox; the expected page count travels
        // alongside so it can be asserted there.
        fs.writeFileSync(path.join(OUT, `${scenario.name}.min-pages`),
                         String(scenario.minPages));
      }
    } catch (err) {
      failures.push(`${scenario.name} threw: ${err.message.split('\n')[0]}`);
      await page.screenshot({ path: path.join(OUT, `${scenario.name}-failure.png`) })
        .catch(() => {});
    }
    await page.close();
  }

  await browser.close();

  console.log('\nconsole output while signed out:');
  console.log(problems.length ? problems.map(p => `  ${p}`).join('\n') : '  none');
  if (failures.length) {
    console.log('\nfailures:');
    failures.forEach(f => console.log(`  ${f}`));
  }
  console.log(`\n${failures.length} check(s) failed, ${problems.length} console problem(s)`);
  process.exit(failures.length + problems.length === 0 ? 0 : 1);
})();
