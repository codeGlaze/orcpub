// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// The sheet's spell table, for a level 1 cleric in the builder's Details view. On a 344px phone
// (a Galaxy Fold's cover screen) and a desktop: no header label runs into the next ("DCMod."),
// and only a spell that makes a spell attack has a roll button (Guiding Bolt, Inflict Wounds),
// while Bless and Cure Wounds have none.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/spell_table_e2e.js`). No login.
// Runs in:   ~30s. SHOT_DIR=<dir> saves spell-table-*.png.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOT_DIR = process.env.SHOT_DIR;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Opens the builder at `w`x`h` with a level 1 cleric and shows its Spells tab; returns the page.
async function open(browser, w, h) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h }, deviceScaleFactor: 2 });
  await ctx.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"long-rest-update-2026-r2"'); } catch (e) {}
  });
  const page = await ctx.newPage();
  page.errors = [];
  page.on('pageerror', e => page.errors.push(e.message));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } }, null, { timeout: 60000 });
  await page.evaluate(() => {
    const rd = cljs.reader.read_string, s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
    const strict = `{:${s}/selections [
      {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
        :${s}/map-value {:${c}/str 10 :${c}/dex 12 :${c}/con 14 :${c}/int 8 :${c}/wis 16 :${c}/cha 13}}}
      {:${s}/key :class :${s}/options [{:${s}/key :cleric :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}]}`;
    re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
  });
  await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
  await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')]
    .some(d => d.offsetParent && d.innerHTML.includes('spell-book')), null, { timeout: 20000 }).catch(() => {});
  await page.evaluate(() => {
    const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes('spell-book'));
    if (t) t.click();
  });
  await page.locator('tr.spell').first().waitFor({ timeout: 15000 }).catch(() => {});
  return page;
}

// The smallest gap in px between one header label's text and the next, in the first spell table.
const smallestHeaderGap = page => page.evaluate(() => {
  const ths = [...document.querySelector('tbody.spells tr.f-w-b').children].filter(th => th.textContent.trim());
  const ends = ths.map(th => { const r = document.createRange(); r.selectNodeContents(th);
    const b = r.getBoundingClientRect(); return [b.left, b.right, th.textContent.trim()]; });
  let min = Infinity, at = '';
  for (let i = 1; i < ends.length; i++) {
    const gap = ends[i][0] - ends[i - 1][1];
    if (gap < min) { min = gap; at = `${ends[i - 1][2]} | ${ends[i][2]}`; }
  }
  return { min: Math.round(min), at };
});

// The roll button's text in `name`'s row, '' when the row has none, null when the row is missing.
const rollIn = (page, name) => page.evaluate(name => {
  const row = [...document.querySelectorAll('tr.spell')].find(r => r.children[0].textContent.trim() === name);
  if (!row) return null;
  const b = row.querySelector('button.roll-button');
  return b ? b.textContent.trim() : '';
}, name);

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const [tag, w, h] of [['phone', 344, 882], ['desktop', 1280, 900]]) {
    const page = await open(browser, w, h);
    const gap = await smallestHeaderGap(page);
    check(`${tag}: no header label runs into the next`, gap.min >= 8, `${gap.min}px between ${gap.at}`);
    for (const name of ['Guiding Bolt', 'Inflict Wounds']) {
      const r = await rollIn(page, name);
      check(`${tag}: ${name} has an attack roll`, /^1d20[+-]\d+$/.test(r || ''), JSON.stringify(r));
    }
    for (const name of ['Bless', 'Cure Wounds']) {
      const r = await rollIn(page, name);
      check(`${tag}: ${name} has no attack roll`, r === '', JSON.stringify(r));
    }
    if (SHOT_DIR) await page.locator('tbody.spells').first().screenshot({ path: `${SHOT_DIR}/spell-table-${tag}.png` });
    check(`${tag}: no page errors`, page.errors.length === 0, page.errors.slice(0, 2).join(' | '));
    await page.context().close();
  }
  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
