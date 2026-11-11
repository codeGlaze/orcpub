// Reference lists (Spells and Monsters pages): same-named homebrew entries stay separate rows,
// and clearing the filter costs about one frame.
//
// A homebrew library of three sources is imported through the real My Content import:
//   - "Reference Lists Pack": one "Echo Bolt", Fireball (takes the SRD spell's own key), two
//     monsters named "Echo Beast" and 400 filler spells;
//   - "Arcane Collection" (stored :abbreviation "ARC"): one "Echo Bolt" and a spell with a very
//     long name;
//   - "Echo Pack" (no stored abbreviation, so its tag is derived): one "Echo Bolt".
// Then:
//   - the Spells page shows each "Echo Bolt" once, Fireball once, and the SRD rows + 404 in total;
//   - typing "echo" and clearing the filter brings back the same rows (no ghost rows);
//   - opening one "Echo Bolt" row expands only that row;
//   - the Monsters page shows each "Echo Beast" once, before and after the same filter round trip;
//   - each row ends in a source tag (`span.source-tag`): the three "Echo Bolt" rows show three
//     different tags, "ARC" (stored) and "EoPk" (derived from "Echo Pack") among them; an SRD
//     row shows "SRD"; the filtered Spells list draws no beer-stein icon; the Monsters page's
//     "Echo Beast" rows show a homebrew tag;
//   - at a 344 px phone width the long-named row's top line is as tall as a short-named row's
//     (the name truncates instead of wrapping) and its tag stays inside the viewport;
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
const ARCANE = 'Arcane Collection';
const ECHO = 'Echo Pack';
const LONG_NAME = "Bigby's Extraordinarily Persistent Grasping Hand of the Archmage";
const FILLER = 400;
const BUDGET_MS = 100;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

const spell = (key, name, level, school, pack = PACK) =>
  `:${key} {:key :${key} :name "${name}" :level ${level} :school "${school}" :option-pack "${pack}" `
  + `:description "Test spell ${key}."}`;
const monster = (key, name, die) =>
  `:${key} {:key :${key} :name "${name}" :size :medium :type :beast :alignment "unaligned" `
  + `:option-pack "${PACK}" :hit-points {:die ${die} :die-count 2 :modifier 0}}`;

// The .orcbrew text of the test library (three sources), written to a temp file.
function writePack() {
  const filler = [];
  for (let i = 0; i < FILLER; i++) {
    const n = String(i).padStart(3, '0');
    filler.push(spell(`filler-spell-${n}`, `Filler Spell ${n}`, i % 10, 'evocation'));
  }
  const spells = {
    main: [spell('echo-bolt-a', 'Echo Bolt', 1, 'evocation'),
           spell('fireball', 'Fireball', 5, 'conjuration'),
           ...filler].join(' '),
    arcane: [spell('echo-bolt-b', 'Echo Bolt', 2, 'abjuration', ARCANE),
             spell('long-name-hand', LONG_NAME, 5, 'evocation', ARCANE)].join(' '),
    echo: spell('echo-bolt-c', 'Echo Bolt', 3, 'illusion', ECHO)};
  const monsters = [monster('echo-beast-a', 'Echo Beast', 8), monster('echo-beast-b', 'Echo Beast', 10)].join(' ');
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'reflists-')), 'reference-lists.orcbrew');
  fs.writeFileSync(file,
    `{"${PACK}" {:orcpub.dnd.e5/spells {${spells.main}} :orcpub.dnd.e5/monsters {${monsters}}} `
    + `"${ARCANE}" {:abbreviation "ARC" :orcpub.dnd.e5/spells {${spells.arcane}}} `
    + `"${ECHO}" {:orcpub.dnd.e5/spells {${spells.echo}}}}`);
  return file;
}

const INPUT = 'input.f-s-24.h-60';
const rowCount = page => page.locator('.item-list-item').count();

// The first line of each row's title, which is the entry's name.
const rowNames = page => page.evaluate(() =>
  [...document.querySelectorAll('.item-list-item')]
    .map(r => ((r.querySelector('.f-s-24') || {}).innerText || '').split('\n')[0].trim()));
// Each row's source tag (`span.source-tag`) beside its name, as {name, tag} pairs.
const rowTags = page => page.evaluate(() =>
  [...document.querySelectorAll('.item-list-item')].map(r => ({
    name: ((r.querySelector('.f-s-24') || {}).innerText || '').split('\n')[0].trim(),
    tag: ((r.querySelector('.source-tag') || {}).innerText || '').trim()})));
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
  const expected = srdRows + 4 + FILLER; // Fireball replaces the SRD Fireball, adding no row
  const before = await open(page, 'spells');
  check('spells: three "Echo Bolt" rows before filtering', await countNamed(page, 'Echo Bolt') === 3,
        `${await countNamed(page, 'Echo Bolt')}`);
  check('spells: the Fireball that takes the SRD key shows once', await countNamed(page, 'Fireball') === 1,
        `${await countNamed(page, 'Fireball')}`);
  check('spells: row count is the SRD rows + 4 + 400', before === expected,
        `rows=${before} expected=${expected} (SRD alone ${srdRows})`);

  const after = await typeAndClear(page, 'echo');
  check('spells: three "Echo Bolt" rows after typing "echo" and clearing', await countNamed(page, 'Echo Bolt') === 3,
        `${await countNamed(page, 'Echo Bolt')}`);
  check('spells: no ghost rows after the filter round trip', after === before, `before=${before} after=${after}`);

  // Source tags: filter to "bolt" so the three Echo Bolts and the SRD bolt spells are all listed.
  await page.locator(INPUT).first().fill('');
  await settled(page);
  await page.locator(INPUT).first().click();
  await page.keyboard.type('bolt');
  await page.waitForTimeout(1000);
  await settled(page);
  const tags = await rowTags(page);
  const echoTags = tags.filter(t => t.name === 'Echo Bolt').map(t => t.tag);
  check('spells: the three "Echo Bolt" rows show three different tags',
        echoTags.length === 3 && new Set(echoTags).size === 3 && echoTags.every(t => t), echoTags.join(', '));
  check('spells: the stored abbreviation "ARC" is the tag of the Arcane Collection row',
        echoTags.includes('ARC'), echoTags.join(', '));
  check('spells: the Echo Pack row shows its derived tag "EoPk"', echoTags.includes('EoPk'), echoTags.join(', '));
  const srdTags = tags.filter(t => /^(Fire Bolt|Lightning Bolt)$/.test(t.name)).map(t => `${t.name}=${t.tag}`);
  check('spells: SRD rows show "SRD"', srdTags.length > 0 && srdTags.every(t => t.endsWith('=SRD')), srdTags.join(', '));
  const stein = await page.locator('.item-list-item img[src*="beer-stein"]').count();
  check('spells: no row draws the beer-stein icon', stein === 0, `${stein} icons`);
  await page.locator(INPUT).first().fill('');
  await settled(page);

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

  const beastTags = (await rowTags(page)).filter(t => t.name === 'Echo Beast').map(t => t.tag);
  check('monsters: each "Echo Beast" row shows a homebrew tag',
        beastTags.length === 2 && beastTags.every(t => t && t !== 'SRD'), beastTags.join(', '));

  // Phone width: a long name truncates, so its row's top line is as tall as a short name's.
  const phone = await browser.newContext({
    viewport: { width: 344, height: 800 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' });
  await phone.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"long-rest-update-2026"'); } catch (e) {}
  });
  const pp = await phone.newPage();
  await pp.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'networkidle', timeout: 120000 });
  await pp.waitForTimeout(3000);
  const phoneImport = await importPack(pp, writePack());
  await pp.waitForTimeout(2000);
  // A failed import would leave the phone checks waiting on rows that never come, so it is
  // reported here and the phone checks are skipped.
  if (!phoneImport.ok) {
    check('phone 344 px: the test pack imports', false, JSON.stringify(phoneImport));
  } else {
    await open(pp, 'spells');
    const lines = await pp.evaluate(([longName, shortName]) => {
      const rows = [...document.querySelectorAll('.item-list-item')];
      const find = n => rows.find(r => ((r.querySelector('.f-s-24') || {}).innerText || '').split('\n')[0].trim().startsWith(n));
      const info = n => {
        const r = find(n);
        if (!r) return null;
        const line = r.querySelector('.pointer > div');
        const tag = r.querySelector('.source-tag');
        return { h: line.getBoundingClientRect().height, tagRight: tag ? tag.getBoundingClientRect().right : null };
      };
      return { long: info(longName), short: info(shortName), width: window.innerWidth };
    }, [LONG_NAME, 'Fireball']);
    check('phone 344 px: the long-named row\'s top line is one line tall (same as a short name\'s)',
          !!lines.long && !!lines.short && Math.abs(lines.long.h - lines.short.h) <= 2,
          JSON.stringify(lines));
    check('phone 344 px: the long-named row\'s tag stays inside the viewport',
          !!lines.long && lines.long.tagRight !== null && lines.long.tagRight <= lines.width, JSON.stringify(lines.long));
  }
  await phone.close();

  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
