// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// The builder's Class / Level section keeps its class drop-down when the built character has no
// class. A character whose picked class is not in the loaded content (as when its homebrew source
// is held back) still shows that class's row, with a drop-down and a line saying the class is not
// applied, and choosing Wizard from it applies Wizard. Barbarian, then Wizard, keeps the drop-down.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/class_picker_e2e.js`). No login.
// Runs in:   ~30s. SHOT_DIR=<dir> saves class-picker-*.png.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOT_DIR = process.env.SHOT_DIR;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Opens the builder with a level 1 character of class `cls` (a keyword name) on the Class / Level page.
async function open(browser, cls) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"long-rest-update-2026-r2"'); } catch (e) {}
  });
  const page = await ctx.newPage();
  page.errors = [];
  page.on('pageerror', e => page.errors.push(e.message));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } }, null, { timeout: 60000 });
  await page.evaluate(cls => {
    const rd = cljs.reader.read_string, s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
    const strict = `{:${s}/selections [
      {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
        :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
      {:${s}/key :class :${s}/options [{:${s}/key :${cls} :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}]}`;
    re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
  }, cls);
  await page.waitForTimeout(2500);
  // the options column opens on Race; its Class / Level page holds the class rows
  await page.evaluate(() => {
    const t = [...document.querySelectorAll('div,span')].find(e => e.offsetParent && e.children.length === 0 && e.textContent.trim() === 'Class / Level');
    if (t) t.click();
  });
  await page.waitForTimeout(1000);
  return page;
}

// The visible class drop-down (the select offering Barbarian), or null.
const classSelect = page => page.evaluate(() => {
  const sel = [...document.querySelectorAll('select')].find(s => s.offsetParent && [...s.options].some(o => o.value === 'barbarian'));
  return sel ? { value: sel.value, chosen: sel.options[sel.selectedIndex] ? sel.options[sel.selectedIndex].text : null } : null;
});

// Chooses `value` in the class drop-down and waits for the build.
async function choose(page, value) {
  const sel = page.locator('select').filter({ has: page.locator('option[value="barbarian"]') }).first();
  await sel.selectOption(value);
  await page.waitForTimeout(2500);
}

// The class keys of the built character's levels.
const builtClasses = page => page.evaluate(() => {
  const c = window.cljs.core, built = c.deref(re_frame.core.subscribe(cljs.reader.read_string('[:built-character]')));
  const lv = orcpub.dnd.e5.character.levels(built);
  return lv ? c.clj__GT_js(c.keys(lv) || c.PersistentVector.EMPTY).map(String) : [];
});

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });

  const gone = await open(browser, 'no-such-class');
  const before = await classSelect(gone);
  check('a picked class missing from the content keeps its drop-down', !!before, JSON.stringify(before));
  check('and the drop-down shows that class', !!before && before.value === 'no-such-class', before && before.chosen);
  const note = await gone.locator('.class-not-applied').count();
  check('the row says the class is not applied', note === 1, `${note} notes`);
  if (SHOT_DIR) { await gone.locator(".class-not-applied").first().scrollIntoViewIfNeeded().catch(() => {}); await gone.screenshot({ path: `${SHOT_DIR}/class-picker-missing.png` }); }
  if (before) {
    await choose(gone, 'wizard');
    const after = await classSelect(gone);
    check('choosing Wizard from it applies Wizard', !!after && after.value === 'wizard' && (await builtClasses(gone)).some(k => /wizard/.test(k)),
          JSON.stringify(after));
    check('and the not-applied line goes', await gone.locator('.class-not-applied').count() === 0);
  }
  check('no page errors with the missing class', gone.errors.length === 0, gone.errors.slice(0, 2).join(' | '));

  const srd = await open(browser, 'barbarian');
  await choose(srd, 'wizard');
  const w = await classSelect(srd);
  check('Barbarian, then Wizard, keeps the drop-down on Wizard', !!w && w.value === 'wizard', JSON.stringify(w));
  check('no page errors on Barbarian to Wizard', srd.errors.length === 0, srd.errors.slice(0, 2).join(' | '));

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
