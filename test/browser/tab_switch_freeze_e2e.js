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

  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: RATE });
  console.log(`\nCPU throttle ${RATE}x  (models a laptop also running the server)\n`);

  const tab = async (label, name) => {
    await page.evaluate(() => { window.__tasks = []; });
    const t = Date.now();
    try { await page.locator(`text="${name}"`).first().click({ timeout: 30000 }); }
    catch (e) { console.log('  ' + label.padEnd(22), 'click failed'); return; }
    await page.waitForTimeout(1200);
    const tasks = await page.evaluate(() => window.__tasks);
    const worst = tasks.length ? Math.max(...tasks) : 0;
    const total = tasks.reduce((a, b) => a + b, 0);
    console.log('  ' + label.padEnd(22),
                `wall ${String(Date.now() - t - 1200).padStart(5)}ms`,
                ` longest task ${String(worst).padStart(5)}ms`,
                ` blocked ${String(total).padStart(5)}ms in ${tasks.length} tasks`);
  };

  // Flip back and forth the way a user comparing options would.
  for (let i = 1; i <= 4; i++) {
    await tab(`${i}. -> Class / Level`, 'Class / Level');
    await tab(`${i}. -> Race`, 'Race');
  }
  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
