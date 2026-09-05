// Reproduce the reported freeze: flipping between the Race and Class tabs, on a machine
// that is also running the server.
//
// Earlier probes missed it by measuring the wrong thing twice: the class DROPDOWN rather
// than the tab switch, and on an unthrottled headless container rather than a contended
// laptop. Both matter -- the dropdown is ~10 ms and throttling is what surfaces the block.
//
// Reports the LONGEST single task per interaction, not totals: a freeze is one long task.
// CPU throttle defaults to 4x (Chrome's "mid-tier mobile"); pass a rate to change it.
//
// Run: lein fig:build && lein e2e-server, then
//   node test/browser/tab_switch_freeze_e2e.js /path/to/pack.orcbrew [throttle]
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

const OBSERVE = `
window.__tasks = [];
window.__spy = {};
(function arm(){
  try {
    var e5 = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5;
    var opt = e5 && e5.options, ss = e5 && e5.spell_subs, ent = window.orcpub && window.orcpub.entity;
    if (!opt || !ss || !ent) return setTimeout(arm, 5);
    var wrap = function(o,n,l){ if(!o||typeof o[n]!=='function') return;
      var f=o[n]; window.__spy[l]={n:0,ms:0};
      o[n]=function(){var s=performance.now();var r=f.apply(this,arguments);
        var b=window.__spy[l];b.n++;b.ms+=performance.now()-s;return r;}; };
    wrap(opt,'class_option','classOpt');
    wrap(opt,'memoized_spell_option','memoSpellOpt');
    wrap(opt,'spell_selection','spellSel');
    wrap(ss,'make_levels','makeLevels');
    wrap(ent,'build','build');
  } catch(e) { setTimeout(arm, 5); }
})();
try {
  new PerformanceObserver(function(l){
    for (const e of l.getEntries()) window.__tasks.push(Math.round(e.duration));
  }).observe({entryTypes:['longtask']});
} catch(e) {}
`;

(async () => {
  const PACK = process.argv[2];
  const RATE = Number(process.argv[3] || 4);
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext();
  await suppressCookieBanner(ctx);
  await ctx.addInitScript(OBSERVE);
  const page = await ctx.newPage();

  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  console.log('import:', JSON.stringify(await importPack(page, PACK)));

  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);

  // Sanity: prove the spy armed, so "no calls" is a finding rather than a dead probe.
  const armed = await page.evaluate(() => Object.keys(window.__spy || {}));
  console.log('spy armed for:', armed.join(', ') || 'NOTHING -- counts below are meaningless');

  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: RATE });
  console.log(`\nCPU throttle ${RATE}x  (models a laptop also running the server)\n`);

  // Heap sampled WITHOUT forcing GC: a drop across a switch means a collection ran,
  // which is the leading explanation for an occasional multi-second block.
  const heapMB = async () => ((await cdp.send('Runtime.getHeapUsage')).usedSize / 1048576);

  const tab = async (label, name) => {
    await page.evaluate(() => { window.__tasks = []; for (const k in window.__spy) window.__spy[k] = {n:0,ms:0}; });
    const h0 = await heapMB();
    const t = Date.now();
    try { await page.locator(`text="${name}"`).first().click({ timeout: 30000 }); }
    catch (e) { console.log('  ' + label.padEnd(22), 'click failed'); return; }
    await page.waitForTimeout(1200);
    const tasks = await page.evaluate(() => window.__tasks);
    const worst = tasks.length ? Math.max(...tasks) : 0;
    const total = tasks.reduce((a, b) => a + b, 0);
    const h1 = await heapMB();
    const drop = h0 - h1;
    console.log('  ' + label.padEnd(22),
                `wall ${String(Date.now() - t - 1200).padStart(5)}ms`,
                ` longest ${String(worst).padStart(5)}ms`,
                ` heap ${h0.toFixed(0)}->${h1.toFixed(0)}MB`,
                drop > 5 ? ` GC? -${drop.toFixed(0)}MB` : '',
                ' ' + Object.entries(await page.evaluate(() => window.__spy))
                        .filter(([, v]) => v.n).map(([k, v]) => `${k} ${v.n}x${v.ms.toFixed(0)}ms`).join(' '));
  };

  // Positive control: a class dropdown change is known to call memoized-spell-option.
  // SKIP_CONTROL=1 omits it -- the control REALISES the expensive content up front and so
  // suppresses the very freeze this probe exists to catch. Runs compared against each
  // other must agree on this flag.
  if (!process.env.SKIP_CONTROL) {
    await tab('control: -> Class tab', 'Class / Level');
    await page.evaluate(() => { for (const k in window.__spy) window.__spy[k] = {n:0,ms:0}; });
    try {
      await page.locator('select').nth(0).selectOption({ label: 'Wizard' });
      await page.waitForTimeout(1200);
      const d = await page.evaluate(() => window.__spy);
      console.log('  control: pick Wizard    ',
        Object.entries(d).filter(([, v]) => v.n).map(([k, v]) => `${k} ${v.n}x${v.ms.toFixed(0)}ms`).join(' ') || 'no calls');
    } catch (e) { console.log('  control failed'); }
  }

  // Flip back and forth the way a user comparing options would.
  for (let i = 1; i <= 5; i++) {
    await tab(`${i}. -> Class / Level`, 'Class / Level');
    await tab(`${i}. -> Race`, 'Race');
  }
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
