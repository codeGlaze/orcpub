// When is the character template built, and what does that cost?
//
//   ./scripts/e2e/run.sh template-on-open.js
//
// The template cache feeds the save, and the heal of a renamed pick waits for it (offered-keys).
// Pages with no character must not build it. Opening a character must start it, so the heal lands
// without a save, and must not make the builder noticeably slower. Prints timings so a change can
// be compared against the run before it.
const { chromium } = require('playwright');
const { BASE, EXECUTABLE, checker } = require('./lib');

const OLD_KEY = 'half-elf-phb';
const NEW_KEY = 'half-elf-ua';
const PLUGINS = `{"Heal Pak" {:orcpub.dnd.e5/races {:${NEW_KEY} {:key :${NEW_KEY} :name "Half-Elf (UA)" :option-pack "Heal Pak" :former-key :${OLD_KEY}}}}}`;
const CACHE = '[:orcpub.dnd.e5.autosave-fx/cached-template]';
const OFFERED = '[:orcpub.dnd.e5.content-reconciliation/offered-keys]';
const RACE = '[:character :orcpub.entity/options :race :orcpub.entity/key]';

async function context(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(plugins => {
    try {
      localStorage.setItem('plugins', plugins);
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
    window.__blocked = 0;
    try {
      new PerformanceObserver(l => l.getEntries().forEach(e => { window.__blocked += e.duration; }))
        .observe({ type: 'longtask', buffered: true });
    } catch (e) {}
  }, PLUGINS);
  return ctx;
}

const appReady = page => page.waitForFunction(() => {
  try { return cljs.core.contains_QMARK_(cljs.core.deref(re_frame.db.app_db), cljs.reader.read_string(':plugins')); }
  catch (e) { return false; }
}, null, { timeout: 60000 });

const isSet = (page, p) => page.evaluate(p => {
  const v = cljs.core.get_in(cljs.core.deref(re_frame.db.app_db), cljs.reader.read_string(p));
  return v != null;
}, p);

const dbAt = (page, p) => page.evaluate(p =>
  cljs.core.pr_str(cljs.core.get_in(cljs.core.deref(re_frame.db.app_db), cljs.reader.read_string(p))), p);

async function until(page, fn, ms) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { if (await fn()) return Date.now() - t0; await page.waitForTimeout(100); }
  return null;
}

(async () => {
  const { check, failures } = checker();
  const browser = await chromium.launch({ executablePath: EXECUTABLE });

  // Pages with no character build nothing.
  for (const [label, url] of [['login', '/pages/login-page'], ['character list', '/pages/dnd/5e/characters']]) {
    const ctx = await context(browser);
    const page = await ctx.newPage();
    await page.goto(`${BASE}${url}`, { waitUntil: 'domcontentloaded' });
    await appReady(page);
    await page.waitForTimeout(4000);
    check(!(await isSet(page, CACHE)) && !(await isSet(page, OFFERED)), `${label}: no template built`);
    console.log(`  ${label}: main thread blocked ${Math.round(await page.evaluate(() => window.__blocked))} ms`);
    await ctx.close();
  }

  // Opening a character with a renamed pick: when does it heal, and what does the builder cost?
  const runs = [];
  for (let i = 0; i < 3; i++) {
    const ctx = await context(browser);
    const page = await ctx.newPage();
    const t0 = Date.now();
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
    await appReady(page);
    await page.waitForFunction(() => /Ability Scores/.test(document.body.innerText), null, { timeout: 60000 });
    const builderMs = Date.now() - t0;
    const blockedBefore = await page.evaluate(() => window.__blocked);
    await page.evaluate(oldKey => {
      const rd = cljs.reader.read_string;
      const db = cljs.core.deref(re_frame.db.app_db);
      const broken = cljs.core.assoc_in(cljs.core.get(db, rd(':character')),
        rd('[:orcpub.entity/options :race]'), rd(`{:orcpub.entity/key :${oldKey}}`));
      re_frame.core.dispatch_sync(cljs.core.conj(rd('[:orcpub.dnd.e5.character/open-character]'), broken));
    }, OLD_KEY);
    const healMs = await until(page, async () => (await dbAt(page, RACE)) === `:${NEW_KEY}`, 15000);
    const cacheMs = await until(page, () => isSet(page, CACHE), 1000);
    await page.waitForTimeout(500);
    const openBlocked = Math.round((await page.evaluate(() => window.__blocked)) - blockedBefore);
    runs.push({ builderMs, healMs, openBlocked });
    console.log(`  run ${i + 1}: builder ready ${builderMs} ms, heal after open ${healMs === null ? 'NONE in 15s' : healMs + ' ms'}, ` +
                `cache ${cacheMs === null ? 'absent' : 'present'}, main thread blocked after open ${openBlocked} ms`);
    await ctx.close();
  }
  check(runs.every(r => r.healMs !== null), 'opening a character heals its renamed pick without a save',
        runs.map(r => r.healMs).join(', '));

  await browser.close();
  console.log(failures() ? `\nFAILURES: ${failures()}` : '\nall checks passed');
  process.exit(failures() ? 1 : 0);
})();
