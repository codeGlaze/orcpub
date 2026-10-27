// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// The attack mode, both ways (handoff-weapons-table.md build step 3; owner, 2026-10-26).
//
// A level 1 fighter with a longsword, on a 390px phone: the Weapons heading's switch starts on
// Normal (shown as a d20) and an attack rolls one d20; choosing Adv makes the next attack roll
// two d20s; a long press on the attack tile opens the modes in a bottom sheet, and Disadvantage
// there rolls two d20s for that one roll while the switch stays on Adv.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/attack_mode_e2e.js`). No login.
// Runs in:   ~30s. SHOT_DIR=<dir> saves attack-mode-*.png.
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
    const held = `{:${c}.equipment/quantity 1 :${c}.equipment/equipped? true}`;
    const strict = `{:${s}/selections [
      {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
        :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
      {:${s}/key :class :${s}/options [{:${s}/key :fighter :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
      {:${s}/key :weapons :${s}/options [{:${s}/key :longsword :${s}/map-value ${held}}]}]}`;
    re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
  });
  await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
  await page.evaluate(() => re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
    [cljs.reader.read_string(':orcpub.dnd.e5.character/set-selected-display-tab'), 'combat'], true)));
  await page.locator('.message').first().click({ timeout: 1500 }).catch(() => {});
  const shot = async name => { if (SHOT_DIR) { await page.waitForTimeout(250); await page.screenshot({ path: `${SHOT_DIR}/attack-mode-${name}.png` }); } };
  const modes = page.locator('.weapon-mode');
  const tile = page.locator('.weapon-row').filter({ hasText: 'Longsword' }).first().locator('.weapon-hit-tile');
  const sheet = page.locator('.weapon-sheet');
  // the d20 detail: "d20 [14] + 4" for one die, "d20 [14, 6] + 4" for two
  const dice = async () => {
    const t = await sheet.innerText({ timeout: 5000 }).catch(() => '');
    const m = t.match(/d20 \[([^\]]*)\]/);
    return m ? m[1].split(',').length : 0;
  };
  const done = async () => {
    await page.locator('.weapon-sheet-done').click({ timeout: 5000 }).catch(() => {});
    await sheet.waitFor({ state: 'detached', timeout: 5000 }).catch(() => {});
  };

  check('Normal shows a d20', await modes.getByRole('radio', { name: 'Normal' }).locator('.fa-dice-d20').count() === 1);
  check('the heading switch starts on Normal',
        await modes.getByRole('radio', { name: 'Normal' }).getAttribute('aria-checked', { timeout: 15000 }).catch(() => null) === 'true');
  await tile.click({ timeout: 10000 }).catch(() => {});
  check('a Normal attack rolls one d20', await dice() === 1);
  await done();

  await modes.getByRole('radio', { name: 'Advantage', exact: true }).click({ timeout: 5000 }).catch(() => {});
  await modes.locator('[aria-checked="true"]', { hasText: 'Adv' }).waitFor({ timeout: 5000 }).catch(() => {});
  await shot('switch');
  await tile.click({ timeout: 5000 }).catch(() => {});
  check('with Adv on, the attack rolls two d20s', await dice() === 2);
  await shot('advantage');
  await done();

  const box = await tile.boundingBox({ timeout: 5000 }).catch(() => null);
  if (box) {
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    await page.waitForTimeout(700);
    await page.mouse.up();
  }
  const menu = page.locator('.weapon-menu');
  check('a long press opens the modes', await menu.getByRole('menuitem', { name: /Disadvantage/ }).waitFor({ timeout: 5000 }).then(() => true, () => false));
  check('in the bottom sheet, on a phone', await page.locator('.weapon-menu-sheet .weapon-menu').count() === 1);
  check('and does not roll', await sheet.count() === 0);
  await shot('long-press');
  await menu.getByRole('menuitem', { name: /Disadvantage/ }).click({ timeout: 5000 }).catch(() => {});
  const lastMode = () => page.evaluate(() => {
    const chain = cljs.core.get(cljs.core.deref(re_frame.db.app_db), cljs.core.keyword('weapon-roll/chain'));
    return cljs.core.name(cljs.core.get_in(cljs.core.peek(chain), cljs.core.PersistentVector.fromArray([cljs.core.keyword('attack'), cljs.core.keyword('mode')], true)));
  }).catch(() => null);
  check('Disadvantage from it rolls two d20s, kept as disadvantage', await dice() === 2 && await lastMode() === 'dis');
  await done();
  check('and the switch stays on Adv',
        await modes.getByRole('radio', { name: 'Advantage', exact: true }).getAttribute('aria-checked', { timeout: 3000 }).catch(() => null) === 'true');
  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
