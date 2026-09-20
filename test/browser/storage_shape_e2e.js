// What is actually in localStorage after a real import.
//
// The library is per-source everywhere the user touches it (import, export, delete,
// disable) but plugins->local-store flattens the whole map with `str` into one key, and
// set-item is a bare .setItem. Measured with MegaPak: 13 sources -> 1 key -> 2,166,081
// chars. So one source toggle rewrites 2.07 MB and builder open reads it all back through
// one read-string. Quarantine cannot help that: it runs on the map that exists only after
// the parse returns.
//
// Run: lein e2e-server, then
//   node test/browser/storage_shape_e2e.js /path/to/pack.orcbrew
const { chromium } = require('playwright');
const { importPack, suppressOverlays } = require('./lib/orcbrew-import.js');

const PACK = process.argv[2];
const URL = 'http://localhost:8890/dnd/5e/my-content';

const dump = (page) => page.evaluate(() => {
  const out = [];
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    out.push([k, (localStorage.getItem(k) || '').length]);
  }
  out.sort((a, b) => b[1] - a[1]);
  return out;
});

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || undefined });
  const ctx = await browser.newContext();
  await suppressOverlays(ctx);
  const page = await ctx.newPage();
  await page.goto(URL, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(3000);

  console.log('--- BEFORE import ---');
  console.table(await dump(page));

  const r = await importPack(page, PACK);
  console.log('import:', JSON.stringify(r));
  await page.waitForTimeout(4000);

  console.log('--- AFTER import ---');
  const after = await dump(page);
  console.table(after);

  // How many sources are in the library, and how big is the "plugins" key really?
  const detail = await page.evaluate(() => {
    const c = window.cljs.core;
    const p = c.get(window.re_frame.db.app_db.state, c.keyword(null, 'plugins'));
    const names = p ? c.clj__GT_js(c.vec(c.keys(p))) : [];
    const raw = localStorage.getItem('plugins') || '';
    return { sourceCount: names.length, names,
             pluginsKeyChars: raw.length,
             head: raw.slice(0, 120), tail: raw.slice(-60) };
  });
  console.log('sources in library:', detail.sourceCount, detail.names);
  console.log('"plugins" key length (chars):', detail.pluginsKeyChars,
              '=', (detail.pluginsKeyChars / 1048576).toFixed(2), 'MB');
  console.log('head:', JSON.stringify(detail.head));
  console.log('tail:', JSON.stringify(detail.tail));

  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
