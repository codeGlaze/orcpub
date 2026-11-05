// Two tabs editing the homebrew library: neither loses the other's work.
//
// Tab B is held stale on purpose (its storage events are swallowed before the app sees them), so
// its save has to be merged onto tab A's rather than overwrite it. Tab A keeps its listener, so it
// has to pick up B's save without a reload.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dbAt, fill, clickText, dismissCookieBar, dismissWhatsNew } = require('./lib');

const PAK = 'Two Tab Pak';
const LANGS = `[:plugins "${PAK}" :orcpub.dnd.e5/languages]`;

const openBuilder = async page => {
  await page.goto(`${BASE}/pages/dnd/5e/language-builder`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1600);
  await dismissCookieBar(page);
  await dismissWhatsNew(page);
};

const save = async (page, name) => {
  await fill(page, 'Name', name);
  await fill(page, 'Option Source Name', PAK);
  const ok = await clickText(page, /save to browser storage/i);
  await page.waitForTimeout(1000);
  return ok;
};

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  const context = await browser.newContext({ viewport: { width: 1200, height: 1000 } });
  const a = await context.newPage();
  const b = await context.newPage();
  const errors = [];
  for (const p of [a, b]) p.on('pageerror', e => errors.push(String(e)));

  try {
    await openBuilder(a);
    await a.evaluate(() => { localStorage.removeItem('plugins'); localStorage.removeItem('plugins:rev'); });
    await a.reload({ waitUntil: 'networkidle' }); await a.waitForTimeout(1200);
    await openBuilder(b);
    // Tab B stops hearing about other tabs' writes, as if the event had not arrived yet.
    await b.evaluate(() => window.addEventListener('storage', e => e.stopImmediatePropagation(), true));

    check('tab A saves Tideward', await save(a, 'Tideward'));
    check('tab B saves Seaward while it still holds the library from before A saved',
          await save(b, 'Seaward'));

    const stored = await a.evaluate(() => localStorage.getItem('plugins') || '');
    check('storage keeps both tabs\' work',
          /Tideward/.test(stored) && /Seaward/.test(stored), stored.slice(0, 300));
    check('tab B shows both', /Tideward/.test(await dbAt(b, LANGS)) && /Seaward/.test(await dbAt(b, LANGS)));
    await a.waitForTimeout(800);
    check('tab A picked up B\'s save without a reload', /Seaward/.test(await dbAt(a, LANGS)),
          await dbAt(a, LANGS));

    await a.reload({ waitUntil: 'networkidle' }); await a.waitForTimeout(1600);
    const after = await dbAt(a, LANGS);
    check('and both are there after a reload', /Tideward/.test(after) && /Seaward/.test(after), after);
    check('no uncaught JS errors', errors.length === 0, errors.slice(0, 2).join(' | '));
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
