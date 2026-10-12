// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// Does the character display show an SRD weapon's and armor's price and weight?
//
// Loads a level 1 fighter carrying a longsword, wearing chain mail and holding a shield into the
// builder, opens the display's Combat tab, and expands each row. Expected, from the SRD 5.1
// tables (p. 66, p. 64):
//   Longsword             Cost 15 gp, Weight 3 lb.
//   Chain mail + Shield   Cost 75 gp, Weight 55 lbs., Shield Cost 10 gp
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/srd_equipment_e2e.js`). No login.
// Runs in:   ~15s. SHOT_DIR=<dir> also saves a screenshot of the expanded rows.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOT_DIR = process.env.SHOT_DIR;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Puts a level 1 fighter with an equipped longsword, chain mail and shield into the builder.
const load = page => page.evaluate(() => {
  const rd = cljs.reader.read_string;
  const s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
  const held = `{:${c}.equipment/quantity 1 :${c}.equipment/equipped? true}`;
  const strict = `{:${s}/selections [
    {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
      :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
    {:${s}/key :class :${s}/options [{:${s}/key :fighter
      :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
    {:${s}/key :weapons :${s}/options [{:${s}/key :longsword :${s}/map-value ${held}}]}
    {:${s}/key :armor :${s}/options [{:${s}/key :chain-mail :${s}/map-value ${held}}
                                    {:${s}/key :shield :${s}/map-value ${held}}]}]}`;
  re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
    [rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
});

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 1000 } });
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } },
                             null, { timeout: 60000 });
  await load(page);
  await page.getByText('combat', { exact: true }).first().click();

  const weapon = page.locator('tr.weapon', { hasText: 'Longsword' }).first();
  await weapon.waitFor({ timeout: 20000 }).catch(() => {});
  if (await weapon.count()) {
    await weapon.click();
    await weapon.getByText('Two-handed?').waitFor({ timeout: 5000 }).catch(() => {});  // expanded
    const t = await weapon.innerText();
    check('the longsword shows its SRD price', /Cost:\s*15 gp/.test(t), t.replace(/\s+/g, ' ').slice(0, 120));
    check('and its weight', /Weight:\s*3 lb\./.test(t));
  } else {
    check('the combat tab lists the longsword', false, 'no row');
  }
  const armor = page.locator('tbody.armor tr.item', { hasText: 'Chain mail + Shield' }).first();
  if (await armor.count()) {
    await armor.click();
    await armor.getByText('Base AC:').waitFor({ timeout: 5000 }).catch(() => {});  // expanded
    const t = await armor.innerText();
    check('the chain mail shows its SRD price', /Cost:\s*75 gp/.test(t), t.replace(/\s+/g, ' ').slice(0, 120));
    check('and its weight', /Weight:\s*55 lbs\./.test(t));
    check('and the shield\'s price', /Shield Cost:\s*10 gp/.test(t));
  } else {
    check('the combat tab lists the chain mail', false, 'no row');
  }
  if (SHOT_DIR) await page.screenshot({ path: `${SHOT_DIR}/equipment-details.png`, fullPage: false });
  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));

  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
