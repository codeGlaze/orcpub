// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// The floating Rest button and the Developer mode casting switch (handoff-rest-controls.md, D1, D6,
// section 9; plan-charges-and-casting.md, step 8).
//
// A level 1 fighter carrying a Wand of Fireballs and a Robe of Stars, on a 390px phone: on the
// Equipment tab a 56px Rest button sits in the bottom-right corner and the Import log button (given
// a log) sits above it at 84px; with a wand charge spent, the button opens the Rest box as a bottom
// sheet; a long rest with no time chosen asks about dawn or dusk, and while it asks tapping the
// dimmed page does not close the sheet; answering Neither and tapping the dimmed page closes
// it. On the Features tab the button is gone. With Developer mode's "Open item casting under the
// item on phones" on, the wand's Cast opens the panel under the item instead of a sheet. On a
// desktop the box opens as a popover.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/rest_fab_e2e.js`). No login.
// Runs in:   ~40s. SHOT_DIR=<dir> saves rest-fab-*.png.
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
      {:${s}/key :other-magic-items :${s}/options [{:${s}/key :wand-of-fireballs :${s}/map-value ${held}}
                                                  {:${s}/key :robe-of-stars :${s}/map-value ${held}}]}]}`;
    re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
  });
  await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
  await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')]
    .some(d => d.offsetParent && d.innerHTML.includes('backpack')), null, { timeout: 20000 }).catch(() => {});
  await page.locator('.message').first().click({ timeout: 1500 }).catch(() => {});
  page.tab = icon => page.evaluate(icon => {
    const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes(icon));
    if (t) t.click(); return !!t; }, icon);
  return page;
}

const shows = (loc, text) => loc.filter({ hasText: text }).first().waitFor({ timeout: 5000 }).then(() => true, () => false);
const box = async loc => loc.boundingBox({ timeout: 5000 }).catch(() => null);

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await open(browser, true);
  const shot = async name => { if (SHOT_DIR) { await page.waitForTimeout(250); await page.screenshot({ path: `${SHOT_DIR}/rest-fab-${name}.png` }); } };
  const fab = page.locator('button.rest-fab');
  const sheet = page.locator('.rest-fab-sheet');
  await page.tab('backpack');

  const b = await box(fab);
  check('on the Equipment tab a 56px Rest button sits in the bottom-right corner',
        !!b && b.width === 56 && Math.round(390 - b.x - b.width) === 12 && Math.round(844 - b.y - b.height) === 16,
        b && `${b.width}px, right ${Math.round(390 - b.x - b.width)}, bottom ${Math.round(844 - b.y - b.height)}`);
  await page.evaluate(() => { cljs.core.swap_BANG_.call(null, re_frame.db.app_db, db =>
    cljs.core.assoc(db, cljs.core.keyword('import-log'), cljs.reader.read_string('{:errors [{:message "x"}]}'))); });
  const il = await box(page.locator('.import-log-button'));
  check('and the Import log button sits above it, 84px up', !!il && Math.round(844 - il.y - il.height) === 84,
        il && `bottom ${Math.round(844 - il.y - il.height)}`);
  await page.evaluate(() => { cljs.core.swap_BANG_.call(null, re_frame.db.app_db, db => cljs.core.dissoc(db, cljs.core.keyword('import-log'))); });
  await shot('closed');

  // spend a wand charge, so dawn has something to bring back and the rest asks
  const wandLine = page.locator('tbody.other-magic-items tr').filter({ hasText: 'Wand of Fireballs' }).first().locator('.item-charges-line').first();
  await wandLine.locator('.item-charge-pip').first().click({ timeout: 10000 }).catch(() => {});
  check('a wand charge is spent', await shows(wandLine.locator('.item-charges-count'), '6/7'));
  await fab.click({ timeout: 5000 }).catch(() => {});
  check('the button opens the Rest box as a bottom sheet', await sheet.locator('.rest-box').waitFor({ timeout: 5000 }).then(() => true, () => false));
  await shot('sheet');
  await sheet.getByRole('button', { name: 'Long rest' }).click({ timeout: 5000 }).catch(() => {});
  check('a long rest with no time chosen asks about dawn or dusk', await shows(sheet.locator('.rest-line'), 'Did dawn or dusk pass?'));
  await page.mouse.click(195, 60);
  await page.waitForTimeout(300);
  check('while it asks, tapping the dimmed page does not close the sheet', await sheet.isVisible());
  await shot('asking');
  await sheet.locator('.rest-answers .rest-link', { hasText: 'Neither' }).click({ timeout: 5000 }).catch(() => {});
  // wait for the answer to land: "Long rest finished." is already in the question line
  await sheet.locator('.rest-line', { hasText: 'Did dawn or dusk pass?' }).waitFor({ state: 'detached', timeout: 5000 }).catch(() => {});
  await page.mouse.click(195, 60);
  check('once answered, tapping the dimmed page closes it', await sheet.waitFor({ state: 'detached', timeout: 5000 }).then(() => true, () => false));

  await page.tab('vitruvian-man');
  await page.locator('.rest-box').first().waitFor({ timeout: 10000 }).catch(() => {});
  check('on the Features tab the button is gone', await page.locator('.rest-box').count() === 1 && await fab.count() === 0);

  await page.evaluate(() => { const e = [...document.querySelectorAll('.dev-mode-label')].find(x => x.textContent === 'Developer mode'); if (e) e.click(); });
  const sw = page.locator('.dev-mode-row', { hasText: 'Open item casting under the item on phones' }).locator('.dev-mode-switch');
  await sw.click({ timeout: 5000 }).catch(() => {});
  check('Developer mode shows the casting switch, and it turns on',
        await page.locator('.dev-mode-row', { hasText: 'Open item casting under the item on phones' })
          .locator('.dev-mode-switch[aria-checked="true"]').waitFor({ timeout: 5000 }).then(() => true, () => false));
  await page.tab('backpack');
  const wand = page.locator('tbody.other-magic-items tr').filter({ hasText: 'Wand of Fireballs' }).first();
  await wand.getByRole('button', { name: 'Cast Fireball' }).click({ timeout: 10000 }).catch(() => {});
  check('with it on, Cast opens the panel under the item on a phone',
        await page.locator('tr.cast-row .cast-panel').waitFor({ timeout: 5000 }).then(() => true, () => false)
          && await page.locator('.cast-sheet').count() === 0);
  await shot('cast-inline');
  check('no page errors on the phone', page.errors.length === 0, page.errors.slice(0, 2).join(' | '));

  const desk = await open(browser, false);
  await desk.tab('backpack');
  await desk.locator('button.rest-fab').click({ timeout: 10000 }).catch(() => {});
  check('on a desktop the box opens as a popover',
        await desk.locator('.rest-fab-card .rest-box').waitFor({ timeout: 5000 }).then(() => true, () => false)
          && await desk.locator('.rest-fab-sheet').count() === 0);
  if (SHOT_DIR) await desk.screenshot({ path: `${SHOT_DIR}/rest-fab-desktop.png` });
  check('no page errors on the desktop', desk.errors.length === 0, desk.errors.slice(0, 2).join(' | '));
  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
