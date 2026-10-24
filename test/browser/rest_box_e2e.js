// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// The Rest box on a 390px phone (handoff-rest-controls.md on agents/develop).
//
// Loads a level 1 fighter carrying two Wands of Fireballs and a Robe of Stars, spends 2 charges
// from the first wand, and checks on the Features tab: the box comes before Damage Resistances;
// time of day starts at None with Now unavailable; choosing Dawn names what it would recharge and
// makes Now available; Now recharges the wand, says so, rolls back to None, and Undo puts the 2
// spent charges back; a Long Rest with no time chosen asks "Did dawn or dusk pass?", and Dawn
// recharges the wand; typing 1 over the rolled amount leaves the wand at 6/7. Then a fighter with
// no dawn or dusk item sees only the two rests.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/rest_box_e2e.js`). No login.
// Runs in:   ~40s. SHOT_DIR=<dir> saves rest-box-*.png.
const { chromium } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOT_DIR = process.env.SHOT_DIR;

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

const load = (page, withItems) => page.evaluate(withItems => {
  const rd = cljs.reader.read_string, s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
  const held = q => `{:${c}.equipment/quantity ${q} :${c}.equipment/equipped? true}`;
  const opt = (k, q) => `{:${s}/key ${k} :${s}/map-value ${held(q)}}`;
  const strict = `{:${s}/selections [
    {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
      :${s}/map-value {:${c}/str 15 :${c}/dex 14 :${c}/con 13 :${c}/int 12 :${c}/wis 10 :${c}/cha 8}}}
    {:${s}/key :class :${s}/options [{:${s}/key :fighter :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
    ${withItems ? `{:${s}/key :other-magic-items :${s}/options [${opt(':wand-of-fireballs', 2)} ${opt(':robe-of-stars', 1)}]}` : ''}]}`;
  re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
}, withItems);

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
  await load(page, true);
  await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
  await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')]
    .some(d => d.offsetParent && d.innerHTML.includes('vitruvian-man')), null, { timeout: 20000 }).catch(() => {});
  const tab = icon => page.evaluate(icon => {
    const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes(icon));
    if (t) t.click(); return !!t; }, icon);
  const shows = (loc, text) => loc.filter({ hasText: text }).first().waitFor({ timeout: 5000 }).then(() => true, () => false);
  const wandLine = () => page.locator('tbody.other-magic-items tr').filter({ hasText: 'Wand of Fireballs' }).first().locator('.item-charges-line').first();
  const wandShows = async want => { await tab('backpack'); return shows(wandLine().locator('.item-charges-count'), want); };
  const box = page.locator('.rest-box');
  const shot = async name => { if (SHOT_DIR) { await box.evaluate(e => e.scrollIntoView({ block: 'center' })); await page.waitForTimeout(250);
    await page.screenshot({ path: `${SHOT_DIR}/rest-box-${name}.png` }); } };

  // spend 2 charges from the first wand, one update at a time (writing-browser-tests.md rule 5)
  await tab('backpack');
  await page.locator('.message').first().click({ timeout: 1500 }).catch(() => {});
  await wandLine().locator('.item-charge-pip').first().click({ timeout: 10000 }).catch(() => {});
  await shows(wandLine().locator('.item-charges-count'), '6/7');
  await wandLine().locator('.item-charge-pip').first().click({ timeout: 5000 }).catch(() => {});
  check('two charges spent from the first wand', await shows(wandLine().locator('.item-charges-count'), '5/7'));

  await tab('vitruvian-man');
  check('the Rest box is on the Features tab', await box.waitFor({ timeout: 10000 }).then(() => true, () => false));
  check('before Damage Resistances', await page.evaluate(() => {
    const b = document.querySelector('.rest-box');
    const d = [...document.querySelectorAll('span')].find(e => e.textContent.trim() === 'Damage Resistances');
    return !!b && (!d || (b.compareDocumentPosition(d) & Node.DOCUMENT_POSITION_FOLLOWING) > 0); }));
  const seg = label => box.getByRole('radio', { name: label });
  const now = box.locator('.rest-now');
  check('time of day starts at None, Now unavailable',
        await seg('None').getAttribute('aria-checked', { timeout: 5000 }).catch(() => null) === 'true'
          && await now.getAttribute('aria-disabled', { timeout: 3000 }).catch(() => null) === 'true');
  await shot('rest');
  await seg('Dawn').click({ timeout: 5000 }).catch(() => {});
  check('choosing Dawn names what it would recharge', await shows(box.locator('.rest-line.hint'), 'Dawn recharges 1 thing'));
  check('and Now becomes available', await now.getAttribute('aria-disabled', { timeout: 3000 }).catch(() => null) === 'false');
  await shot('dawn-chosen');
  await now.click({ timeout: 5000 }).catch(() => {});
  check('Now says dawn passed and recharged the wand', await shows(box.locator('.rest-line'), 'Dawn passed: 1 item recharged.'));
  check('time of day goes back to None', await seg('None').getAttribute('aria-checked', { timeout: 3000 }).catch(() => null) === 'true');
  await shot('now-result');
  check('and the wand is full again', await wandShows('7/7'));
  await tab('vitruvian-man');
  await box.locator('.rest-link', { hasText: 'Undo' }).click({ timeout: 5000 }).catch(() => {});
  check('Undo puts the spent charges back', await wandShows('5/7'));

  await tab('vitruvian-man');
  await box.getByRole('button', { name: 'Long rest' }).click({ timeout: 5000 }).catch(() => {});
  check('a Long Rest with no time chosen asks about dawn and dusk', await shows(box.locator('.rest-line'), 'Did dawn or dusk pass?'));
  await shot('question');
  await box.locator('.rest-answers .rest-link', { hasText: 'Dawn' }).first().click({ timeout: 5000 }).catch(() => {});
  check('answering Dawn recharges the wand', await shows(box.locator('.rest-line'), 'Long rest finished. Dawn passed: 1 item recharged.'));
  const amount = box.locator('.rest-amount').first();
  await amount.fill('1', { timeout: 5000 }).catch(() => {});
  await amount.press('Enter', { timeout: 3000 }).catch(() => {});
  await page.waitForTimeout(300);
  await shot('typed');
  check('typing 1 over the roll leaves the wand at 6/7', await wandShows('6/7'));

  // a character with no dawn or dusk item: close the line first, it belongs to the last one
  await tab('vitruvian-man');
  await box.locator('.rest-close').click({ timeout: 5000 }).catch(() => {});
  check('closing the line removes it', await box.locator('.rest-line').waitFor({ state: 'detached', timeout: 5000 }).then(() => true, () => false));
  await load(page, false);
  await tab('vitruvian-man');
  check('without dawn or dusk items the box shows only the two rests',
        await box.locator('.rest-seg').first().waitFor({ state: 'detached', timeout: 10000 }).then(() => true, () => false)
          && await shows(box.locator('.rest-btn'), 'Long rest'));
  await shot('plain');
  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
