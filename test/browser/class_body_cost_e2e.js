// What do class BODIES cost — at builder open, and per class switch?
//
// plugin-classes runs make-levels for every homebrew class, and ::classes5e/classes runs
// class-option over every one of them, whether or not the character has taken it. This is
// the before-number for making those lazy.
//
// Reports call counts and time for make-levels / class-option / spell-selection at first
// builder render, then again per class switch with counters reset, plus retained heap
// (measured after a forced GC) across a browsing session.
//
// Run: lein fig:build && lein e2e-server, then
//   node test/browser/class_body_cost_e2e.js /path/to/pack.orcbrew
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressCookieBanner } = require('./lib/orcbrew-import');

function findChrome() {
  if (process.env.CHROME_PATH) return process.env.CHROME_PATH;
  const b = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const d = fs.readdirSync(b).filter(x => x.startsWith('chromium-') && !x.includes('headless')).sort().pop();
    if (d) { const p = path.join(b, d, 'chrome-linux', 'chrome'); if (fs.existsSync(p)) return p; }
  } catch (_) {}
  return undefined;
}

// Armed before the builder renders: the template is built once and cached, so instrumenting
// late measures nothing.
const INSTRUMENT = `
window.__noMemo = ${process.env.NOMEMO ? 'true' : 'false'};
window.__spy = {};
(function arm(){
  try {
    var e5  = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5;
    var opt = e5 && e5.options, ss = e5 && e5.spell_subs, t5e = e5 && e5.template;
    var ent = window.orcpub && window.orcpub.entity;
    if (!opt || !ss || !ent || !t5e) return setTimeout(arm, 5);
    var wrap = function(obj, name, label){
      if (!obj || typeof obj[name] !== 'function') return;
      var f = obj[name];
      window.__spy[label] = {n:0, ms:0};
      obj[name] = function(){ var s=performance.now(); var r=f.apply(this,arguments);
        var b=window.__spy[label]; b.n++; b.ms+=performance.now()-s; return r; };
    };
    // NOMEMO=1: replace the memoized wrapper with a passthrough, to test whether
    // the memoize is what retains the heap across class browsing.
    if (window.__noMemo && typeof opt.spell_option === 'function') {
      opt.memoized_spell_option = function(){ return opt.spell_option.apply(this, arguments); };
    }
    wrap(opt,'class_option','class-option');
    // The memoized wrapper, not spell_option: spell_option is captured at
    // definition time by the memoize, so wrapping it intercepts nothing.
    wrap(opt,'memoized_spell_option','memoized-spell-option');
    wrap(opt,'spell_selection','spell-selection');
    wrap(ss,'make_levels','make-levels');
    wrap(t5e,'template_selections','template-selections');
    wrap(ent,'build','entity/build');
  } catch(e) { setTimeout(arm, 5); }
})();
`;

const fmt = (d, k) => (d[k] && d[k].n ? `${d[k].n}x${d[k].ms.toFixed(0)}ms` : '-');

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext();
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();

  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  console.log('import:', JSON.stringify(await importPack(page, process.argv[2])));

  await ctx.addInitScript(INSTRUMENT);
  const cdp = await ctx.newCDPSession(page);
  const heapMB = async () => {
    await cdp.send('HeapProfiler.collectGarbage');
    const { usedSize } = await cdp.send('Runtime.getHeapUsage');
    return usedSize / 1048576;
  };

  const t0 = Date.now();
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);
  const open = await page.evaluate(() => window.__spy);
  console.log(`\nBUILDER OPEN (${Date.now() - t0}ms wall)`);
  console.log('  tmplSel', fmt(open, 'template-selections'), ' classOpt', fmt(open, 'class-option'),
              ' makeLevels', fmt(open, 'make-levels'), ' spellSel', fmt(open, 'spell-selection'),
              ' memoSpellOpt', fmt(open, 'memoized-spell-option'), ' build', fmt(open, 'entity/build'));

  const heapStart = await heapMB();
  console.log('  heap after open:', heapStart.toFixed(1), 'MB');

  try { await page.locator('text="Class / Level"').first().click({ timeout: 25000 }); } catch (e) {}
  await page.waitForTimeout(1500);

  console.log('\nPER CLASS SWITCH (counters reset each time)');
  for (const c of ['Wizard', 'Cleric', 'Druid', 'Bard', 'Sorcerer']) {
    await page.evaluate(() => { for (const k in window.__spy) window.__spy[k] = { n: 0, ms: 0 }; });
    const t = Date.now();
    try { await page.locator('select').nth(0).selectOption({ label: c }); }
    catch (e) { console.log('  ' + c.padEnd(9), 'select failed'); continue; }
    await page.waitForTimeout(1500);
    const d = await page.evaluate(() => window.__spy);
    console.log('  ' + c.padEnd(9), `wall ${String(Date.now() - t - 1500).padStart(5)}ms`,
                ' classOpt', fmt(d, 'class-option'), ' makeLevels', fmt(d, 'make-levels'),
                ' spellSel', fmt(d, 'spell-selection'), ' memoSpellOpt', fmt(d, 'memoized-spell-option'),
                ' build', fmt(d, 'entity/build'));
  }

  const heapEnd = await heapMB();
  console.log(`\nheap ${heapStart.toFixed(1)} -> ${heapEnd.toFixed(1)} MB (+${(heapEnd - heapStart).toFixed(1)}) after 5 class switches`);
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
