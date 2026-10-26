// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// Casting spells from charged items (plan-charges-and-casting.md on agents/develop).
//
// A level 1 fighter carries a Wand of Fireballs and a Staff of Fire. On a 390px phone: the wand's
// Cast Fireball opens a bottom sheet with DC 15, 7 charges left and levels 3rd to 9th; casting at
// 5th spends 3 (4/7) and says "Fireball · 5th level · DC 15", and Roll damage rolls 10d6 with the
// save; reopened, 7th and up are greyed (4 left); a cast then Undo gives the charge back; the
// staff's Cast offers its three spells and, for a non-caster, "your spell save DC"; the Spells
// tab's From Items lists the item spells and its Cast opens the same sheet. On a desktop the panel opens inline under the item, not as a sheet.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/item_casting_e2e.js`). No login.
// Runs in:   ~40s. SHOT_DIR=<dir> saves item-casting-*.png.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOT_DIR = process.env.SHOT_DIR;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

async function open(browser, phone) {
  const ctx = await browser.newContext(phone
    ? { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
        userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' }
    : { viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {}
  });
  const page = await ctx.newPage();
  page.errors = [];
  page.on('pageerror', e => page.errors.push(e.message));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } }, null, { timeout: 60000 });
  await page.evaluate(() => {
    const rd = cljs.reader.read_string, s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
    const held = `{:${c}.equipment/quantity 1 :${c}.equipment/equipped? true}`;
    const strict = `{:${s}/selections [
      {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
        :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
      {:${s}/key :class :${s}/options [{:${s}/key :fighter :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
      {:${s}/key :other-magic-items :${s}/options [{:${s}/key :wand-of-fireballs :${s}/map-value ${held}}]}
      {:${s}/key :magic-weapons :${s}/options [{:${s}/key :staff-of-fire :${s}/map-value ${held}}]}]}`;
    re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
  });
  return page;
}

const shows = (loc, text) => loc.filter({ hasText: text }).first().waitFor({ timeout: 5000 }).then(() => true, () => false);

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await open(browser, true);
  await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
  await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')]
    .some(d => d.offsetParent && d.innerHTML.includes('backpack')), null, { timeout: 20000 }).catch(() => {});
  const tab = icon => page.evaluate(icon => {
    const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes(icon));
    if (t) t.click(); return !!t; }, icon);
  const row = name => page.locator('tbody.other-magic-items tr').filter({ hasText: name }).first();
  const sheet = page.locator('.cast-sheet');
  const shot = async name => { if (SHOT_DIR) { await page.waitForTimeout(250); await page.screenshot({ path: `${SHOT_DIR}/item-casting-${name}.png` }); } };
  await tab('backpack');
  await page.locator('.message').first().click({ timeout: 1500 }).catch(() => {});

  await row('Wand of Fireballs').getByRole('button', { name: 'Cast Fireball' }).click({ timeout: 10000 }).catch(() => {});
  check('Cast Fireball opens a bottom sheet', await sheet.waitFor({ timeout: 5000 }).then(() => true, () => false));
  check('naming the DC and charges left', await shows(sheet.locator('.cast-head'), 'DC 15 · 7 charges left'));
  const levels = await sheet.locator('.cast-level b').allInnerTexts().catch(() => []);
  check('with levels 3rd to 9th', levels.join(' ') === '3rd 4th 5th 6th 7th 8th 9th', levels.join(' '));
  await shot('sheet');
  await sheet.getByRole('radio', { name: /5th/ }).click({ timeout: 5000 }).catch(() => {});
  check('5th costs 3 charges', await shows(sheet.locator('.cast-go'), 'Cast at 5th · 3 charges'));
  await sheet.locator('.cast-go').click({ timeout: 5000 }).catch(() => {});
  check('casting says what was cast', await shows(sheet.locator('.cast-head'), 'Fireball · 5th level · DC 15'));
  await sheet.getByRole('button', { name: 'Roll damage' }).click({ timeout: 5000 }).catch(() => {});
  check('Roll damage rolls 10d6 fire at 5th, with the save', await shows(sheet.locator('.cast-damage-part'), '10d6')
        && await shows(sheet.locator('.cast-damage'), 'DC 15 Dex save, half on a success'));
  await shot('result');
  await sheet.locator('.cast-go', { hasText: 'Done' }).click({ timeout: 5000 }).catch(() => {});
  check('and the wand has 4 left', await shows(row('Wand of Fireballs').locator('.item-charges-count'), '4/7'));

  await row('Wand of Fireballs').getByRole('button', { name: 'Cast Fireball' }).click({ timeout: 5000 }).catch(() => {});
  const greyed = async l => (await sheet.getByRole('radio', { name: new RegExp(l) }).getAttribute('aria-disabled', { timeout: 3000 }).catch(() => null)) === 'true';
  check('with 4 left, 7th is greyed and 6th is not', await greyed('7th') && !(await greyed('6th')));
  await sheet.locator('.cast-go').click({ timeout: 5000 }).catch(() => {});
  await shows(sheet.locator('.cast-head'), '3rd level');
  check('a cast at 3rd spends 1', await shows(row('Wand of Fireballs').locator('.item-charges-count'), '3/7'));
  await sheet.locator('.rest-link', { hasText: 'Undo' }).click({ timeout: 5000 }).catch(() => {});
  await sheet.locator('.cast-x').click({ timeout: 5000 }).catch(() => {});
  check('a cast then Undo leaves the wand at 4', await shows(row('Wand of Fireballs').locator('.item-charges-count'), '4/7'));

  await row('Staff of Fire').getByRole('button', { name: 'Cast', exact: true }).click({ timeout: 5000 }).catch(() => {});
  await sheet.locator('.cast-spell-pick', { hasText: 'Wall of Fire' }).waitFor({ timeout: 5000 }).catch(() => {});
  const picks = await sheet.locator('.cast-spell-pick').allInnerTexts().catch(() => []);
  check('the staff offers its three spells', picks.length === 3 && /Burning Hands/.test(picks[0]) && /Wall of Fire/.test(picks[2]), picks.join(' | '));
  check('and a non-caster sees "your spell save DC"', await shows(sheet.locator('.cast-head'), 'your spell save DC'));
  await shot('staff');
  await sheet.locator('.cast-x').click({ timeout: 5000 }).catch(() => {});

  await tab('spell-book');
  const fromItems = page.locator('tbody.items-spells');
  check('the Spells tab lists the item spells under From Items',
        await shows(fromItems.locator('tr'), 'Wand of Fireballs') && await fromItems.locator('tr').count() === 5);
  await fromItems.locator('tr', { hasText: 'Wand of Fireballs' }).getByRole('button', { name: 'Cast' }).click({ timeout: 5000 }).catch(() => {});
  check('and its Cast opens the same sheet', await shows(sheet.locator('.cast-head'), 'Fireball'));
  await shot('spells-tab');
  check('no page errors on the phone', page.errors.length === 0, page.errors.slice(0, 2).join(' | '));

  const desk = await open(browser, false);
  await desk.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')]
    .some(d => d.offsetParent && d.innerHTML.includes('backpack')), null, { timeout: 20000 }).catch(() => {});
  await desk.evaluate(() => {
    const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes('backpack'));
    if (t) t.click(); });
  await desk.locator('.message').first().click({ timeout: 1500 }).catch(() => {});
  const dRow = desk.locator('tbody.other-magic-items tr').filter({ hasText: 'Wand of Fireballs' }).first();
  await dRow.waitFor({ timeout: 20000 }).catch(() => {});
  await dRow.getByRole('button', { name: 'Cast Fireball' }).click({ timeout: 5000 }).catch(() => {});
  check('on a desktop the panel opens inline under the item',
        await desk.locator('tr.cast-row .cast-panel').waitFor({ timeout: 5000 }).then(() => true, () => false)
          && await desk.locator('.cast-sheet').count() === 0);
  check('no page errors on the desktop', desk.errors.length === 0, desk.errors.slice(0, 2).join(' | '));
  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
