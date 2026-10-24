// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// Item charges on a 390px phone, in the sheet's Other Magic Items table.
//
// Loads a level 1 fighter carrying two Wands of Fireballs, a Cube of Force and a Necklace of
// Fireballs, opens the Equipment tab and checks: each wand gets its own line of 7 pips; tapping a
// lit pip spends a charge from that wand only (6/7) and tapping a dim one gives it back (7/7); the
// cube's 36 charges show as a count with − and +, and − spends one (35/36); the necklace, whose
// beads start as dice, asks to roll 1d6+3 or type a number, and typing 6 gives it 6 pips; a tap on
// a pip does not open the row's details. Then a Staff of Striking on the Combat tab: its + menu
// offers +1d6, +2d6 and +3d6 force at 1, 2 and 3 charges with what is left; picking one spends from
// the staff (10 to 9 on the Equipment tab), and once only 1 is left, +2d6 and +3d6 are greyed.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/item_charges_e2e.js`). No login.
// Runs in:   ~25s. SHOT_DIR=<dir> saves item-charges.png.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOT_DIR = process.env.SHOT_DIR;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({
    viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' });
  await ctx.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {}
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } }, null, { timeout: 60000 });
  await page.evaluate(() => {
    const rd = cljs.reader.read_string, s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
    const held = q => `{:${c}.equipment/quantity ${q} :${c}.equipment/equipped? true}`;
    const opt = (k, q) => `{:${s}/key ${k} :${s}/map-value ${held(q)}}`;
    const strict = `{:${s}/selections [
      {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
        :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
      {:${s}/key :class :${s}/options [{:${s}/key :fighter :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
      {:${s}/key :other-magic-items :${s}/options [${opt(':wand-of-fireballs', 2)} ${opt(':cube-of-force', 1)} ${opt(':necklace-of-fireballs', 1)}]}
      {:${s}/key :magic-weapons :${s}/options [${opt(':staff-of-striking', 1)}]}]}`;
    re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
  });
  // a phone shows the sheet under the Details tab, its sections behind icon tabs
  await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
  await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')]
    .some(d => d.offsetParent && d.innerHTML.includes('backpack')), null, { timeout: 20000 }).catch(() => {});
  await page.evaluate(() => {
    const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes('backpack'));
    if (t) t.click();
  });
  const row = name => page.locator('tbody.other-magic-items tr').filter({ hasText: name }).first();
  const shows = (loc, text) => loc.filter({ hasText: text }).waitFor({ timeout: 5000 }).then(() => true, () => false);
  const wand = row('Wand of Fireballs');
  if (!await wand.waitFor({ timeout: 20000 }).then(() => true, () => false)) {
    check('the Equipment tab lists the wands', false);
  } else {
    await page.locator('.message').first().click({ timeout: 1500 }).catch(() => {});
    const lines = wand.locator('.item-charges-line');
    check('each of the two wands gets its own line', await lines.count() === 2, `${await lines.count()}`);
    check('of 7 pips, all lit',
          await lines.nth(0).locator('.item-charge-pip').count() === 7 && await lines.nth(1).locator('.item-charge-pip.used').count() === 0);
    await lines.nth(0).locator('.item-charge-pip').first().click({ timeout: 5000 }).catch(() => {});
    check('tapping a lit pip spends one from that wand', await shows(lines.nth(0).locator('.item-charges-count'), '6/7'));
    check('and not from the other', /7\/7/.test(await lines.nth(1).innerText({ timeout: 3000 }).catch(() => '')));
    check('a pip tap does not open the row', await page.locator('tbody.other-magic-items .item-component, tbody.other-magic-items td[colspan="3"]').count() === 0);
    await lines.nth(0).locator('.item-charge-pip.used').first().click({ timeout: 5000 }).catch(() => {});
    check('tapping a dim pip gives it back', await shows(lines.nth(0).locator('.item-charges-count'), '7/7'));

    const cube = row('Cube of Force');
    check('the cube shows a count, not 36 pips',
          await shows(cube.locator('.item-charges-count'), '36/36') && await cube.locator('.item-charge-pip').count() === 0);
    await cube.getByRole('button', { name: 'Spend a charge' }).click({ timeout: 5000 }).catch(() => {});
    check('its minus spends one', await shows(cube.locator('.item-charges-count'), '35/36'));

    const neck = row('Necklace of Fireballs');
    check('the necklace asks to roll its beads', await shows(neck.locator('.item-charges-roll'), 'Roll 1d6+3'));
    const input = neck.locator('.item-charges-input');
    await input.fill('6', { timeout: 5000 }).catch(() => {});
    await input.press('Enter', { timeout: 5000 }).catch(() => {});
    check('typing 6 gives it 6 pips', await shows(neck.locator('.item-charges-count'), '6/6')
          && await neck.locator('.item-charge-pip').count() === 6);
    if (SHOT_DIR) {
      await page.locator('tbody.other-magic-items').evaluate(e => e.scrollIntoView({ block: 'center' }));
      await page.waitForTimeout(300);
      await page.screenshot({ path: `${SHOT_DIR}/item-charges.png` });
    }
  }
  // the Staff of Striking's charged powers, from the weapons table's + menu
  const tab = icon => page.evaluate(icon => {
    const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes(icon));
    if (t) t.click(); return !!t; }, icon);
  // an unsaved character takes one update at a time (writing-browser-tests.md rule 5): each pick
  // waits until the staff's count shows its result before the next one
  const staffShows = async want => {
    await tab('backpack');
    return shows(row('Staff of Striking').locator('.item-charges-count'), want); };
  const staffRow = page.locator('.weapon-row').filter({ has: page.locator('.weapon-name', { hasText: /^Staff of Striking$/ }) });
  const menu = async () => {
    await tab('sword-clash');
    await staffRow.locator('.weapon-plus').click({ timeout: 5000 }).catch(() => {});
    // read the menu once its charged rows have drawn (writing-browser-tests.md rule 2)
    await page.locator('[role=menuitem]', { hasText: '+3d6 force' }).waitFor({ timeout: 5000 }).catch(() => {});
    return page.locator('[role=menuitem]'); };
  const pick = async label => {
    const items = await menu();
    await items.filter({ hasText: label }).first().click({ timeout: 5000 }).catch(() => {});
    await page.locator('.weapon-sheet-done').click({ timeout: 3000 }).catch(() => {}); };
  let items = await menu();
  const texts = await items.allInnerTexts().catch(() => []);
  check('the staff\'s + menu offers +1d6, +2d6 and +3d6 force with their cost and what is left',
        ['+1d6 force', '+2d6 force', '+3d6 force'].every(l => texts.some(x => x.includes(l)))
          && texts.some(x => /1 charge\s*\u00B7\s*10 left/.test(x)), texts.filter(x => x.includes('force')).join(' | '));
  await page.keyboard.press('Escape');
  await pick('+1d6 force');
  check('picking +1d6 spends one of the staff\'s charges', await staffShows('9/10'));
  await pick('+3d6 force');
  const six = await staffShows('6/10');
  await pick('+3d6 force');
  check('two +3d6 leave 3', six && await staffShows('3/10'));
  await pick('+2d6 force');
  await staffShows('1/10');
  items = await menu();
  const disabled = async l => (await items.filter({ hasText: l }).first().getAttribute('aria-disabled', { timeout: 3000 }).catch(() => null)) === 'true';
  check('with 1 left, +2d6 and +3d6 are greyed and +1d6 is not',
        await disabled('+2d6 force') && await disabled('+3d6 force') && !(await disabled('+1d6 force')));
  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
