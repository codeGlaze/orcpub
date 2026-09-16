// E4, first consumer: the grant node on the feat builder, driven through the real UI.
//
// What it proves: an author adds grants from the add-bar (which lists registered pools — no pool is
// named in the feat's schema), toggles one to "a specific one", and the saved feat carries
// `:grants [{:pool :languages :count 2} {:pool :skills :key :athletics}]` — one key, one vector,
// the decided vocabulary. Use on a character is pinned at the JVM level
// (fighting_style_grant_matrix_test builds characters through feat-option-from-cfg with :grants).
//
// Prereqs:  lein fig:build && lein e2e-server
// Run:      node test/e2e/feat-grants.js
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, controlFor, fill, clickText,
        dismissCookieBar, dismissWhatsNew, pickOption, chipIsOn } = require('./lib');

const SOURCE = 'Grant Pin';
const NAME = 'Wyrmblooded';

// Click a chip by text INSIDE the grant row whose header is `rowTitle`. Two rows carry identical
// mode chips, so a page-wide shortest-match would always hit the first row.
const clickInRow = (page, rowTitle, chipText) => page.evaluate(({ t, c }) => {
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const row = [...document.querySelectorAll('.effect-row')].find(r => {
    const h = r.querySelector('.effect-row-header span');
    return h && h.textContent.trim().toLowerCase() === t.toLowerCase() && vis(r);
  });
  if (!row) return false;
  const b = [...row.querySelectorAll('.chip')].find(e => e.textContent.trim() === c);
  if (!b) return false;
  b.click();
  return true;
}, { t: rowTitle, c: chipText });

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1200, height: 1400 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  try {
    await page.goto(`${BASE}/pages/dnd/5e/feat-builder`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1800);
    await dismissCookieBar(page);
    await dismissWhatsNew(page);

    // the add-bar IS the registry: every pool offerable-by :feat, nothing else
    const bar = await page.evaluate(() =>
      [...document.querySelectorAll('.addbar .chip')].map(e => e.textContent.trim()).filter(t => t.startsWith('+ ')));
    check('the add-bar lists the registered pools', bar.length >= 9, bar.join(' | '));
    for (const p of ['+ Language', '+ Skill', '+ Skill or Tool', '+ Weapon', '+ Armor', '+ Damage Resistance', '+ Fighting Style'])
      check(`  offers ${p}`, bar.includes(p));
    check('  and NOT a monster-only or unregistered pool', !bar.some(t => /spell|monster/i.test(t)));

    check('filled Name', await fill(page, 'Name', NAME));
    check('filled Option Source Name', await fill(page, 'Option Source Name', SOURCE));

    check('added a Language grant', await clickText(page, /^\+ Language$/));
    await page.waitForTimeout(300);
    check('it starts as "let the player choose"', (await chipIsOn(page, 'let the player choose')) === true);
    check('set How many = 2', await fill(page, 'How many', '2'));

    check('added a Skill grant', await clickText(page, /^\+ Skill$/));
    await page.waitForTimeout(300);
    check('toggled the Skill row to "a specific one"', await clickInRow(page, 'Skill', 'a specific one'));
    await page.waitForTimeout(300);
    check('chose Athletics', await pickOption(page, 'Which', /^athletics$/i));

    await page.screenshot({ path: path.join(SHOTS, 'feat-grants.jpg'), fullPage: true, type: 'jpeg', quality: 72 });

    check('clicked SAVE', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(1000);

    const saved = await dbAt(page, `[:plugins "${SOURCE}" :orcpub.dnd.e5/feats]`);
    check('the feat saved', /wyrmblooded/i.test(saved), saved.slice(0, 160));
    check('with :grants — one key, a vector', /:grants \[/.test(saved), saved.slice(0, 300));
    check('the language grant is a CHOICE of 2', /\{:pool :languages,? :count 2\}/.test(saved), saved.slice(0, 300));
    check('the skill grant is FIXED to athletics', /\{:pool :skills,? :key :athletics\}/.test(saved), saved.slice(0, 300));
    check('and no :mode or other UI-only key leaked into the data', !/:mode/.test(saved));

    // remove the Language row and confirm only the skill grant remains
    const removed = await page.evaluate(() => {
      const row = [...document.querySelectorAll('.effect-row')].find(r =>
        r.querySelector('.effect-row-header span')?.textContent.trim() === 'Language');
      const x = row && row.querySelector('.fa-times');
      if (!x) return false; x.click(); return true;
    });
    check('removed the Language row', removed);
    await page.waitForTimeout(300);
    check('clicked SAVE again', await clickText(page, /save to browser storage/i));
    await page.waitForTimeout(800);
    const saved2 = await dbAt(page, `[:plugins "${SOURCE}" :orcpub.dnd.e5/feats]`);
    check('the vector shrank to the one remaining grant',
          /:grants \[\{:pool :skills,? :key :athletics\}\]/.test(saved2), saved2.slice(0, 300));

    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
