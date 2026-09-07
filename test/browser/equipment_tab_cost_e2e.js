// Does make-inventory-item's memoize cost anything in practice?
//
// It is memoized on [key item-map qty-input-width], and item-map is a full content map
// (::equip5e/weapons-map, armor-map, equipment-map, ::mi5e/magic-*-map) including homebrew.
// cljs.core/memoize looks its cache up with `get` on a PersistentArrayMap -- a linear scan
// comparing keys with `=` -- so that key is deep-compared on every call, once per inventory
// row. Same shape as the set-class defect that caused the builder freeze.
//
// The shape being wrong does NOT mean it is slow: if the Equipment tab renders rarely, or
// the maps are small, the cost is nil. This measures before anything is changed.
//
// Run: lein fig:build && lein e2e-server, then
//   node test/browser/equipment_tab_cost_e2e.js /path/to/pack.orcbrew [throttle]
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const { importPack, suppressOverlays } = require('./lib/orcbrew-import');

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
window.__tasks = []; window.__spy = {};
try { Error.stackTraceLimit = 200; } catch (e) {}
try { new PerformanceObserver(function(l){
  for (const e of l.getEntries()) window.__tasks.push(Math.round(e.duration));
}).observe({entryTypes:['longtask']}); } catch(e) {}
(function arm(){
  try {
    var cb = window.orcpub && window.orcpub.character_builder;
    if (!cb) return setTimeout(arm, 5);
    var wrap = function(o,n,l){ if(!o||typeof o[n]!=='function') return;
      var f=o[n]; window.__spy[l]={n:0,ms:0};
      o[n]=function(){var s=performance.now();var r=f.apply(this,arguments);
        var b=window.__spy[l];b.n++;b.ms+=performance.now()-s;return r;}; };
    wrap(cb,'make_inventory_item','makeInventoryItem');
    wrap(cb,'make_options_map','makeOptionsMap');
  } catch(e) { setTimeout(arm, 5); }
})();
`;

(async () => {
  const PACK = process.argv[2], RATE = Number(process.argv[3] || 4);
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext();
  await suppressOverlays(ctx);
  await ctx.addInitScript(OBSERVE);
  const page = await ctx.newPage();

  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  console.log('import:', JSON.stringify(await importPack(page, PACK)));
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);

  const armed = await page.evaluate(() => Object.keys(window.__spy || {}));
  console.log('spy armed for:', armed.join(', ') || 'NOTHING — counts below are meaningless');

  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: RATE });
  console.log(`\nCPU throttle ${RATE}x\n`);

  const tab = async (label, name) => {
    await page.evaluate(() => { window.__tasks = []; for (const k in window.__spy) window.__spy[k] = {n:0,ms:0}; });
    const t = Date.now();
    try { await page.locator(`text="${name}"`).first().click({ timeout: 30000 }); }
    catch (e) { console.log('  ' + label.padEnd(24), 'click failed'); return; }
    await page.waitForTimeout(1200);
    const tasks = await page.evaluate(() => window.__tasks);
    const d = await page.evaluate(() => window.__spy);
    const worst = tasks.length ? Math.max(...tasks) : 0;
    console.log('  ' + label.padEnd(24), `wall ${String(Date.now()-t-1200).padStart(5)}ms`,
      ` longest ${String(worst).padStart(5)}ms `,
      Object.entries(d || {}).filter(([,v]) => v.n).map(([k,v]) => `${k} ${v.n}x${v.ms.toFixed(0)}ms`).join(' ') || '(no calls)');
  };

  for (let i = 1; i <= 3; i++) {
    await tab(`${i}. -> Equipment`, 'Equipment');
    await tab(`${i}. -> Race`, 'Race');
  }
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
