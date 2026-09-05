// How finely can the homebrew library actually be split? Per source, and within a source.
//
// WHY: the storage plan twice hit a wall that turned out to be self-imposed — a source too
// big to migrate under the quota, and a "chunking can never beat the largest source" perf
// cap. Both assumed the chunk must be a whole source. It need not: a source is
// {qualified-keyword content-type {item-key item}} plus non-content scalars, and
// e5/merge-plugins (merge-with merge) already reassembles it exactly.
//
// Measured on MegaPak: largest source 383,817 chars, whose largest content group is 366,488.
// Moving a chunk of size c while the legacy blob holds L peaks at L + c, so migration only
// needs c <= ceiling - L (~2.18 M for a 3 M library). Content groups are far under that, and
// items are kilobytes — so the constraint stops binding once chunks go below source level.
//
// Also note Monster Manual's ":disabled? 5": sources carry non-content scalars, which need
// their own meta chunk when a source is split.
//
// Run: lein e2e-server, then
//   node test/browser/library_chunk_granularity_e2e.js /path/to/pack.orcbrew
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import.js');

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || undefined });
  const ctx = await browser.newContext();
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();
  await page.goto('http://localhost:8890/dnd/5e/my-content',
                  { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  console.log('import:', JSON.stringify(await importPack(page, process.argv[2])));
  await page.waitForTimeout(4000);

  const sources = await page.evaluate(() => {
    const c = window.cljs.core;
    const plugins = c.get(window.re_frame.db.app_db.state, c.keyword(null, 'plugins'));
    const out = [];
    c.doall(c.map(function (k) {
      const v = c.get(plugins, k);
      const groups = [];
      c.doall(c.map(function (gk) {
        groups.push([String(gk), c.pr_str(c.get(v, gk)).length]);
        return null;
      }, c.keys(v)));
      groups.sort((a, b) => b[1] - a[1]);
      out.push({ name: String(k), chars: c.pr_str(v).length, groups });
      return null;
    }, c.keys(plugins)));
    out.sort((a, b) => b.chars - a.chars);
    return out;
  });

  console.log('source'.padEnd(35), 'chars'.padStart(8), '  top content groups');
  for (const s of sources) {
    console.log(s.name.slice(0, 34).padEnd(35), String(s.chars).padStart(8), '  ',
      s.groups.slice(0, 4).map(g => g[0].replace('orcpub.dnd.e5/', '') + ':' + g[1]).join(' '));
  }
  const big = sources[0];
  const total = sources.reduce((a, s) => a + s.chars, 0);
  console.log('\nlibrary total          :', total);
  console.log('largest source         :', big.chars, `(${(100 * big.chars / total).toFixed(1)}% of library)`);
  console.log('its largest group      :', big.groups[0][1]);
  console.log('=> finest chunk needed is well under any plausible quota headroom');
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
