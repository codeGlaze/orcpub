// Kind: probe — where the boot main-thread time goes on a warm (cached) load, by trace event kind.
const { chromium } = require('playwright');
const url = 'http://localhost:8890' + (process.argv[2] || '/pages/dnd/5e/character-builder');
const cpu = +(process.argv[3] || 1);
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const ctx = await b.newContext({ viewport: { width: 390, height: 844 } });
  await ctx.addInitScript(() => { try { localStorage.setItem('orcpub:no-cookie-banner','1'); localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026')); } catch (e) {} });
  const p = await ctx.newPage();
  await p.goto(url, { waitUntil: 'load' }); await p.waitForTimeout(1500); // prime cache
  const cdp = await ctx.newCDPSession(p);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: cpu });
  const events = [];
  cdp.on('Tracing.dataCollected', d => events.push(...d.value));
  const done = new Promise(r => cdp.once('Tracing.tracingComplete', r));
  await cdp.send('Tracing.start', { categories: 'devtools.timeline,v8,v8.execute,disabled-by-default-devtools.timeline', transferMode: 'ReportEvents' });
  await p.reload({ waitUntil: 'load' }); await p.waitForTimeout(2500);
  await cdp.send('Tracing.end'); await done;
  // Main renderer thread: the one with the most RunTask time.
  const tasks = events.filter(e => e.name === 'RunTask' && e.ph === 'X');
  const byTid = {}; tasks.forEach(e => byTid[e.tid] = (byTid[e.tid] || 0) + e.dur);
  const tid = +Object.entries(byTid).sort((a, b) => b[1] - a[1])[0][0];
  const mine = events.filter(e => e.tid === tid && e.ph === 'X' && e.dur);
  const sum = n => Math.round(mine.filter(e => e.name === n).reduce((s, e) => s + e.dur, 0) / 1000);
  const kinds = ['RunTask', 'EvaluateScript', 'v8.compile', 'v8.parseOnBackground', 'v8.run', 'FunctionCall', 'TimerFire', 'FireAnimationFrame',
    'UpdateLayoutTree', 'Layout', 'Paint', 'PrePaint', 'Layerize', 'ParseHTML', 'ParseAuthorStyleSheet', 'MajorGC', 'MinorGC', 'V8.GC_SCAVENGER', 'DecodeImage', 'HitTest'];
  const out = {}; kinds.forEach(k => { const v = sum(k); if (v) out[k] = v; });
  const top = tasks.filter(e => e.tid === tid).sort((a, b) => b.dur - a.dur).slice(0, 5).map(e => Math.round(e.dur / 1000));
  // what's inside the longest task
  const big = tasks.filter(e => e.tid === tid).sort((a, b) => b.dur - a.dur)[0];
  const inside = {}; mine.filter(e => e.ts >= big.ts && e.ts + e.dur <= big.ts + big.dur && e !== big)
    .forEach(e => inside[e.name] = (inside[e.name] || 0) + e.dur);
  const insideTop = Object.entries(inside).sort((a, b) => b[1] - a[1]).slice(0, 12).map(([k, v]) => [k, Math.round(v / 1000)]);
  console.log(JSON.stringify({ url, cpu, totalsMs: out, longestTasksMs: top, insideLongestMs: insideTop }));
  await b.close();
})();
