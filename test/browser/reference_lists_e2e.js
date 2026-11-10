// Reference lists (Spells and Monsters pages): same-named homebrew entries stay separate rows,
// and clearing the filter costs about one frame.
//
// A homebrew pack with three spells named "Echo Bolt" (different keys), one spell that takes an
// SRD spell's own key (Fireball), two monsters named "Echo Beast" and 400 filler spells is
// imported through the real My Content import. Then:
//   - the Spells page shows each "Echo Bolt" once, Fireball once, and the SRD rows + 403 in total;
//   - typing "echo" and clearing the filter brings back the same rows (no ghost rows);
//   - opening one "Echo Bolt" row expands only that row;
//   - the Monsters page shows each "Echo Beast" once, before and after the same filter round trip;
//   - the keystroke that takes the filter from 3 characters to 2 (the full list returns) is on
//     screen within 100 ms (median of 3), because rows are drawn a frame at a time.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/reference_lists_e2e.js`). No login.
// Runs in:   ~1 min plus the production build.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const { importPack } = require('./lib/orcbrew-import');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const PACK = 'Reference Lists Pack';
const FILLER = 400;
const BUDGET_MS = 100;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

const spell = (key, name, level, school) =>
  `:${key} {:key :${key} :name "${name}" :level ${level} :school "${school}" :option-pack "${PACK}" `
  + `:description "Test spell ${key}."}`;
const monster = (key, name, die) =>
  `:${key} {:key :${key} :name "${name}" :size :medium :type :beast :alignment "unaligned" `
  + `:option-pack "${PACK}" :hit-points {:die ${die} :die-count 2 :modifier 0}}`;

// The .orcbrew text of the test pack, written to a temp file.
function writePack() {
  const filler = [];
  for (let i = 0; i < FILLER; i++) {
    const n = String(i).padStart(3, '0');
    filler.push(spell(`filler-spell-${n}`, `Filler Spell ${n}`, i % 10, 'evocation'));
  }
  const spells = [
    spell('echo-bolt-a', 'Echo Bolt', 1, 'evocation'),
    spell('echo-bolt-b', 'Echo Bolt', 2, 'abjuration'),
    spell('echo-bolt-c', 'Echo Bolt', 3, 'illusion'),
    spell('fireball', 'Fireball', 5, 'conjuration'),
    ...filler].join(' ');
  const monsters = [monster('echo-beast-a', 'Echo Beast', 8), monster('echo-beast-b', 'Echo Beast', 10)].join(' ');
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'reflists-')), 'reference-lists.orcbrew');
  fs.writeFileSync(file,
    `{"${PACK}" {:orcpub.dnd.e5/spells {${spells}} :orcpub.dnd.e5/monsters {${monsters}}}}`);
  return file;
}

const INPUT = 'input.f-s-24.h-60';
const rowCount = page => page.locator('.item-list-item').count();

// The first line of each row's title, which is the entry's name.
const rowNames = page => page.evaluate(() =>
  [...document.querySelectorAll('.item-list-item')]
    .map(r => ((r.querySelector('.f-s-24') || {}).innerText || '').split('\n')[0].trim()));
const countNamed = async (page, name) => (await rowNames(page)).filter(n => n === name).length;

// Waits until the row count has stopped changing (progressive drawing done) and returns it.
async function settled(page) {
  let last = -1, same = 0;
  const end = Date.now() + 20000;
  while (Date.now() < end) {
    const c = await rowCount(page);
    same = c === last ? same + 1 : 0;
    last = c;
    if (same >= 4 && c > 0) return c;
    await page.waitForTimeout(250);
  }
  return last;
}

const open = async (page, route) => {
  await page.goto(`${BASE}/pages/dnd/5e/${route}`, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForSelector('.item-list-item', { timeout: 60000 });
  return settled(page);
};

// Types `text` into the filter, then presses Backspace once per character to empty it again.
async function typeAndClear(page, text) {
  await page.locator(INPUT).first().click();
  await page.keyboard.type(text);
  await settled(page);
  for (let i = 0; i < text.length; i++) await page.keyboard.press('Backspace');
  return settled(page);
}

// Milliseconds from the keydown of one Backspace to the second animation frame plus a zero
// timeout after it, which is when the browser has painted what the keystroke caused.
async function timedBackspace(page) {
  await page.evaluate(() => {
    window.__t0 = null;
    document.addEventListener('keydown', () => { window.__t0 = performance.now(); }, { capture: true, once: true });
  });
  await page.keyboard.press('Backspace');
  return page.evaluate(() => new Promise(resolve =>
    requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(
      () => resolve(performance.now() - window.__t0), 0)))));
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const newContext = async () => {
    const ctx = await browser.newContext({ viewport: { width: 1400, height: 1000 } });
    await ctx.addInitScript(() => {
      try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"long-rest-update-2026"'); } catch (e) {}
    });
    return ctx;
  };
  const errors = [];

  // The SRD row count is measured on a page with no homebrew, so the expected total below does
  // not depend on the list under test.
  const clean = await newContext();
  const cleanPage = await clean.newPage();
  await cleanPage.setViewportSize({ width: 1400, height: 1000 });
  const srdRows = await open(cleanPage, 'spells');
  await clean.close();

  const ctx = await newContext();
  const page = await ctx.newPage();
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(3000);
  const imported = await importPack(page, writePack());
  if (!imported.ok) {
    check('the test pack imports', false, JSON.stringify(imported));
    await browser.close();
    process.exit(1);
  }
  await page.waitForTimeout(2000);

  // Spells page
  const expected = srdRows + 3 + FILLER; // Fireball replaces the SRD Fireball, adding no row
  const before = await open(page, 'spells');
  check('spells: three "Echo Bolt" rows before filtering', await countNamed(page, 'Echo Bolt') === 3,
        `${await countNamed(page, 'Echo Bolt')}`);
  check('spells: the Fireball that takes the SRD key shows once', await countNamed(page, 'Fireball') === 1,
        `${await countNamed(page, 'Fireball')}`);
  check('spells: row count is the SRD rows + 3 + 400', before === expected,
        `rows=${before} expected=${expected} (SRD alone ${srdRows})`);

  const after = await typeAndClear(page, 'echo');
  check('spells: three "Echo Bolt" rows after typing "echo" and clearing', await countNamed(page, 'Echo Bolt') === 3,
        `${await countNamed(page, 'Echo Bolt')}`);
  check('spells: no ghost rows after the filter round trip', after === before, `before=${before} after=${after}`);

  // Opening a row shows its "view" button; only the clicked row may show one.
  await page.locator('.item-list-item').filter({ hasText: 'Echo Bolt' }).first().locator('.pointer > div').first().click();
  await page.waitForTimeout(500);
  const opened = await page.evaluate(() =>
    [...document.querySelectorAll('.item-list-item')]
      .filter(r => /^Echo Bolt/.test(((r.querySelector('.f-s-24') || {}).innerText || '').trim())
                   && [...r.querySelectorAll('button')].some(b => b.innerText.trim().toLowerCase() === 'view')).length);
  check('spells: opening one "Echo Bolt" row expands only that row', opened === 1, `${opened} expanded`);

  // Timing: 3 characters to 2 brings the whole list back.
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForSelector('.item-list-item');
  await settled(page);
  const times = [];
  for (let i = 0; i < 3; i++) {
    await page.locator(INPUT).first().fill('');
    await settled(page);
    await page.locator(INPUT).first().click();
    await page.keyboard.type('ech');
    await settled(page);
    times.push(await timedBackspace(page));
    await settled(page);
  }
  const median = [...times].sort((a, b) => a - b)[1];
  check(`spells: clearing the filter is on screen within ${BUDGET_MS} ms (median of 3)`, median < BUDGET_MS,
        `median=${median.toFixed(1)} ms, tries=${times.map(t => t.toFixed(1)).join(', ')}`);

  // Monsters page
  const monstersBefore = await open(page, 'monsters');
  check('monsters: two "Echo Beast" rows before filtering', await countNamed(page, 'Echo Beast') === 2,
        `${await countNamed(page, 'Echo Beast')}`);
  const monstersAfter = await typeAndClear(page, 'echo');
  check('monsters: two "Echo Beast" rows after typing "echo" and clearing', await countNamed(page, 'Echo Beast') === 2,
        `${await countNamed(page, 'Echo Beast')}, rows before=${monstersBefore} after=${monstersAfter}`);

  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
