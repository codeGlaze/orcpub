// Does moving a class into a source that already has its key take the class's subclasses with it?
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
//
// Two sources each hold a class keyed :e2e-artificer, each with a subclass of its own. Moving Alpha's
// class into Beta through My Content's Move / copy bar has to rename it, since Beta already holds that
// key. Alpha's subclass must follow the renamed class, Beta's must stay on Beta's class, and the moved
// class must remember its old key. Before the fix (Greptile, PR #34) the rename left Alpha's subclass on
// :e2e-artificer, where it attached to Beta's class. The rules themselves are in orcbrew_validation_test.
// Needs no account: My Content is the browser's local library.
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dismissCookieBar, dismissWhatsNew } = require('./lib');

const ALPHA = 'E2E Alpha';
const BETA = 'E2E Beta';
const LIBRARY =
  `{"${ALPHA}" {:orcpub.dnd.e5/classes {:e2e-artificer {:name "E2E Artificer" :key :e2e-artificer :option-pack "${ALPHA}"}}` +
  ` :orcpub.dnd.e5/subclasses {:e2e-alchemist {:name "E2E Alchemist" :key :e2e-alchemist :class :e2e-artificer :option-pack "${ALPHA}"}}}` +
  ` "${BETA}" {:orcpub.dnd.e5/classes {:e2e-artificer {:name "E2E Artificer" :key :e2e-artificer :option-pack "${BETA}"}}` +
  ` :orcpub.dnd.e5/subclasses {:e2e-armorer {:name "E2E Armorer" :key :e2e-armorer :class :e2e-artificer :option-pack "${BETA}"}}}}`;

// Every class and subclass in the page's library, as {source, type, key, name, class, formerKeys}.
const library = page => page.evaluate(() => {
  const c = window.cljs.core;
  const kw = (ns, n) => c.keyword(ns, n);
  const plugins = c.get(window.re_frame.db.app_db.state, kw(null, 'plugins'));
  const rows = [];
  c.run_BANG_(entry => {
    const source = c.key(entry), plugin = c.val(entry);
    for (const type of ['classes', 'subclasses']) {
      c.run_BANG_(e => { const k = c.key(e), item = c.val(e); rows.push({
        source, type, key: c.name(k),
        name: c.get(item, kw(null, 'name')),
        class: (cls => cls ? c.name(cls) : null)(c.get(item, kw(null, 'class'))),
        formerKeys: c.clj__GT_js(c.map(c.name, c.get(item, kw(null, 'former-keys')))),
      }); }, c.get(plugin, kw('orcpub.dnd.e5', type)));
    }
  }, plugins);
  return rows;
});

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  // Seed the library once, then load it; a reload later must read back what the move wrote.
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
  await dismissWhatsNew(page);
  await dismissCookieBar(page);
  await page.evaluate(lib => {
    ['plugins', 'plugins:rev', 'plugins:pre-fix', 'plugins:pre-fix-at'].forEach(k => localStorage.removeItem(k));
    localStorage.setItem('plugins', lib);
  }, LIBRARY);
  await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2000);
  await dismissWhatsNew(page);
  const before = await library(page);
  check('both sources load their class', before.filter(r => r.type === 'classes').length === 2, JSON.stringify(before));

  await page.locator('button.b-move').click();
  // Sources and the content types inside them render collapsed, each with its own "expand" link.
  const row = page.locator('.mc-selrow', { hasText: 'E2E Artificer' }).first();
  for (let i = 0; i < 10 && !(await row.isVisible().catch(() => false)); i++) {
    const expand = page.locator('span.underline', { hasText: /^expand$/ }).first();
    if (!(await expand.isVisible().catch(() => false))) break;
    await expand.click();
    await page.waitForTimeout(300);
  }
  await page.locator('.mc-selrow', { hasText: 'E2E Artificer' }).first().click();
  await page.locator('select').filter({ has: page.locator(`option[value="${BETA}"]`) }).first().selectOption(BETA);
  await page.locator('button', { hasText: 'Move here' }).click();
  await page.waitForTimeout(1500);

  const after = await library(page);
  const classes = after.filter(r => r.type === 'classes');
  const moved = classes.find(r => r.source === BETA && r.key !== 'e2e-artificer');
  const betaOwn = classes.find(r => r.source === BETA && r.key === 'e2e-artificer');
  const alchemist = after.find(r => r.key === 'e2e-alchemist');
  const armorer = after.find(r => r.key === 'e2e-armorer');

  check('the class left Alpha', !classes.some(r => r.source === ALPHA), JSON.stringify(classes));
  check('it arrived in Beta under a new key', !!moved, JSON.stringify(classes));
  check("Beta's own class is untouched", !!betaOwn && betaOwn.name === 'E2E Artificer', JSON.stringify(betaOwn));
  check("Alpha's subclass follows the moved class", !!moved && alchemist && alchemist.class === moved.key, JSON.stringify({ alchemist, moved: moved && moved.key }));
  check("Beta's subclass stays on Beta's class", !!armorer && armorer.class === 'e2e-artificer', JSON.stringify(armorer));
  check('the moved class remembers its old key', !!moved && (moved.formerKeys || []).includes('e2e-artificer'), JSON.stringify(moved));

  // The move persists like any edit: a reload reads it back from local storage.
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);
  const reloaded = await library(page);
  const alchemistAgain = reloaded.find(r => r.key === 'e2e-alchemist');
  check('and it survives a reload', !!moved && alchemistAgain && alchemistAgain.class === moved.key, JSON.stringify(alchemistAgain));
  check('no uncaught page errors', errors.length === 0, errors.join(' | '));

  await browser.close();
  process.exit(report() ? 1 : 0);
})();
