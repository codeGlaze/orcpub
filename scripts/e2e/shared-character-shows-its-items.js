// Does a character that uses its owner's custom items show them to someone else?
//
//   ./scripts/e2e/run.sh shared-character-shows-its-items.js
//
// e2e-boot seeds a fighter owned by kaylee with one of her custom items equipped and prints its
// id; run.sh passes that on as E2E_CHARACTER_ID. The page is opened three times: logged out, as
// zoe, and as kaylee. The server sends the equipped item with the character, so each viewer should
// see it in the Equipment tab's Other Magic Items table. For logged-out and zoe it comes from the
// copy kept for that character; kaylee's comes from her own list, and no copy is kept for her.
const { chromium } = require('playwright');

const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const ID = process.env.E2E_CHARACTER_ID;
const EXECUTABLE = process.env.E2E_CHROMIUM || undefined;
const ITEM = 'Kaylee Seeded Alpha';

const kept = (page, id) => page.evaluate(id => {
  const c = window.cljs.core, s = window.re_frame.db.app_db.state;
  const nameKw = c.keyword('orcpub.dnd.e5.magic-items', 'name');
  const names = items => (items == null ? [] : c.clj__GT_js(c.vec(c.map(i => c.get(i, nameKw), items))));
  const overlay = c.deref(window.re_frame.core.subscribe(
    c.PersistentVector.fromArray([c.keyword('orcpub.dnd.e5.magic-items', 'shared-custom-items')], true)));
  return {
    keptForThisCharacter: names(c.get(c.get(s, c.keyword(null, 'character-custom-items')), parseInt(id))),
    overlay: names(overlay),
  };
}, id);

async function view(browser, who) {
  const ctx = await browser.newContext();
  // The cookie banner and What's New panel would take the clicks.
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026'));
    } catch (e) {}
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  let read = null;
  page.on('response', async r => {
    if (new URL(r.url()).pathname !== `/dnd/5e/characters/${ID}`) return;
    try {
      const body = await r.text();
      read = { status: r.status(), carriesItem: body.includes(ITEM), carriesEmail: body.includes('@example.com') };
    } catch (e) {}
  });
  if (who) {
    await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('input');
    await page.locator('input').nth(0).fill(who.user);
    await page.locator('input').nth(1).fill(who.pass);
    await page.locator('button.form-button').click();
    await page.waitForTimeout(3500);
  }
  await page.goto(`${BASE}/pages/dnd/5e/characters/${ID}`, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(4000);
  const state = await kept(page, ID);
  // The tab labels are lowercase text styled as capitals; switch the way the tab's click does.
  await page.evaluate(() => {
    const c = window.cljs.core;
    window.re_frame.core.dispatch(c.PersistentVector.fromArray(
      [c.keyword('orcpub.dnd.e5.character', 'set-selected-display-tab'), 'equipment'], true));
  });
  await page.waitForTimeout(2000);
  const table = page.locator('tbody.other-magic-items');
  const tableText = (await table.count()) ? (await table.first().innerText()).replace(/\s+/g, ' ') : '';
  await ctx.close();
  return { name: who ? who.user : 'logged out', read, tableText, errors, ...state };
}

(async () => {
  if (!ID) {
    console.error('No E2E_CHARACTER_ID: run this through scripts/e2e/run.sh, which reads it from e2e-boot.');
    process.exit(2);
  }
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const results = [
    await view(browser, null),
    await view(browser, { user: 'zoe', pass: 'washburne7' }),
    await view(browser, { user: 'kaylee', pass: 'serenity99' }),
  ];
  await browser.close();

  let failures = 0;
  const check = (ok, label, detail) => {
    if (!ok) failures++;
    console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  -- ' + detail : ''}`);
  };
  for (const r of results) {
    console.log(`\n${r.name}:`);
    check(r.read && r.read.status === 200, 'the character read succeeded');
    check(r.read && r.read.carriesItem, 'the character read carried the equipped item');
    check(r.read && !r.read.carriesEmail, 'the character read carried no email address');
    check(r.tableText.includes(ITEM), "the item is in the sheet's Other Magic Items table", r.tableText);
    check(r.errors.length === 0, 'no uncaught page errors', r.errors.join(' | '));
    if (r.name === 'kaylee') {
      check(r.keptForThisCharacter.length === 0, 'her own character keeps no server copy', JSON.stringify(r.keptForThisCharacter));
    } else {
      check(JSON.stringify(r.keptForThisCharacter) === JSON.stringify([ITEM]), 'the item is kept for this character', JSON.stringify(r.keptForThisCharacter));
      check(r.overlay.includes(ITEM), "the item is in the sheet's overlay", JSON.stringify(r.overlay));
    }
  }
  console.log(failures ? `\nFAILURES: ${failures}` : '\nall checks passed');
  process.exit(failures ? 1 : 0);
})();
