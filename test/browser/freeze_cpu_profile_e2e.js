// CPU-profile the freeze and rank functions by self time.
//
// Attribution by counter needs a guess about which function to count. This needs none:
// it profiles the blocking switch itself and reports where the time goes. Written after
// several wrong guesses (GC, the memoize, class bodies) each cost a run to disprove.
//
// Run: lein fig:build && lein e2e-server, then
//   node test/browser/freeze_cpu_profile_e2e.js /path/to/pack.orcbrew [throttle]
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

// Inclusive (total) time per function: sum the time of every sample taken anywhere
// beneath a node. Self time is useless here -- allocation-heavy code parks its cost in
// (program)/GC, so every app frame looked small while a 1 s block went unattributed.
function totalTimes(profile) {
  const { nodes, samples, timeDeltas } = profile;
  const byId = new Map(nodes.map(n => [n.id, n]));
  const parent = new Map();
  for (const n of nodes) for (const c of (n.children || [])) parent.set(c, n.id);
  const self = new Map();
  for (let i = 0; i < samples.length; i++) self.set(samples[i], (self.get(samples[i]) || 0) + (timeDeltas[i] || 0));
  const total = new Map();
  for (const [id, us] of self) {
    const seen = new Set();
    let cur = id;
    while (cur != null) {
      const n = byId.get(cur); if (!n) break;
      const f = n.callFrame;
      const name = (f.functionName || '(anonymous)') +
        (f.url ? '  ' + f.url.split('/').slice(-2).join('/') + ':' + (f.lineNumber + 1) : '');
      if (!seen.has(name)) { total.set(name, (total.get(name) || 0) + us); seen.add(name); }
      cur = parent.get(cur);
    }
  }
  return [...total.entries()].sort((a, b) => b[1] - a[1]);
}

function selfTimes(profile) {
  const { nodes, samples, timeDeltas } = profile;
  const byId = new Map(nodes.map(n => [n.id, n]));
  const total = new Map();
  for (let i = 0; i < samples.length; i++) {
    const n = byId.get(samples[i]);
    if (!n) continue;
    const f = n.callFrame;
    const name = (f.functionName || '(anonymous)') +
      (f.url ? '  ' + f.url.split('/').slice(-2).join('/') + ':' + (f.lineNumber + 1) : '');
    total.set(name, (total.get(name) || 0) + (timeDeltas[i] || 0));
  }
  return [...total.entries()].sort((a, b) => b[1] - a[1]);
}

(async () => {
  const PACK = process.argv[2], RATE = Number(process.argv[3] || 4);
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext();
  await suppressCookieBanner(ctx);
  const page = await ctx.newPage();

  await page.goto('http://localhost:8890/dnd/5e/my-content', { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForTimeout(2500);
  console.log('import:', JSON.stringify(await importPack(page, PACK)));
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder', { waitUntil: 'load', timeout: 900000 });
  await page.waitForTimeout(14000);

  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: RATE });
  await cdp.send('Profiler.enable');
  await cdp.send('Profiler.setSamplingInterval', { interval: 200 });

  // The expensive event is the first render of a spellcasting class's options, so profile
  // landing on the Class tab and then picking a caster.
  const step = async (label, fn) => {
    await cdp.send('Profiler.start');
    const t = Date.now();
    try { await fn(); } catch (e) { console.log(label, 'failed:', e.message.split('\n')[0]); }
    await page.waitForTimeout(1500);
    const { profile } = await cdp.send('Profiler.stop');
    console.log(`\n=== ${label}  (${Date.now() - t - 1500}ms wall, ${RATE}x throttle) ===`);
    for (const [name, us] of selfTimes(profile).slice(0, 18)) {
      const ms = us / 1000;
      if (ms < 5) break;
      console.log('  ' + ms.toFixed(0).padStart(6) + 'ms  ' + name);
    }
  };

  // The freeze is intermittent, so profile a RUN of switches continuously rather than one
  // interaction: a single fast sample says nothing about the block. Reports the longest
  // task seen alongside the profile, so it is clear whether the freeze was captured.
  await page.evaluate(() => {
    window.__tasks = [];
    try { new PerformanceObserver(l => { for (const e of l.getEntries()) window.__tasks.push(Math.round(e.duration)); })
            .observe({entryTypes:['longtask']}); } catch (e) {}
  });
  // Warm to the state that precedes the freeze: one Class visit, one Race visit. The
  // block lands on the SECOND Class visit, so profile only that.
  const click = async (n) => { try { await page.locator(`text="${n}"`).first().click({ timeout: 30000 }); } catch (e) {} };
  await page.evaluate(() => {
    window.__tasks = [];
    try { new PerformanceObserver(l => { for (const e of l.getEntries()) window.__tasks.push(Math.round(e.duration)); })
            .observe({entryTypes:['longtask']}); } catch (e) {}
  });
  const TAB = process.env.TAB || 'Class / Level';
  await click(TAB);    await page.waitForTimeout(900);
  await click('Race'); await page.waitForTimeout(900);

  await page.evaluate(() => { window.__tasks = []; });
  await cdp.send('Profiler.start');
  const t = Date.now();
  await click(TAB);
  await page.waitForTimeout(1500);
  const { profile } = await cdp.send('Profiler.stop');
  const tasks = await page.evaluate(() => window.__tasks);
  const worst = tasks.length ? Math.max(...tasks) : 0;
  console.log(`\n=== 2nd visit to ${TAB} (${Date.now() - t - 1500}ms wall, ${RATE}x) ===`);
  console.log(`longest task: ${worst}ms  ${worst > 500 ? '<- FREEZE CAPTURED' : '<- NOT captured, ranking below is of a normal switch'}`);
  console.log('\n-- INCLUSIVE time (function contains the work) --');
  for (const [name, us] of totalTimes(profile).slice(0, 22)) {
    const ms = us / 1000; if (ms < 20) break;
    console.log('  ' + ms.toFixed(0).padStart(6) + 'ms  ' + name);
  }
  console.log('\n-- SELF time --');
  for (const [name, us] of selfTimes(profile).slice(0, 10)) {
    const ms = us / 1000; if (ms < 10) break;
    console.log('  ' + ms.toFixed(0).padStart(6) + 'ms  ' + name);
  }

  await browser.close();
})().catch(e => { console.error('FAILED', e); process.exit(1); });
