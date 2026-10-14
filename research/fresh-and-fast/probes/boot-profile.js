// Kind: probe — CPU profile of boot with a :pseudo-names bundle, self time attributed to namespaces.
const { chromium } = require('playwright');
const fs = require('fs');
const BUNDLE = process.argv[2], route = process.argv[3] || '/pages/dnd/5e/character-builder';
const src = fs.readFileSync(BUNDLE, 'utf8').split('\n');
// line -> namespace of the nearest preceding top-level `$ns$name$ = ` / `function $ns$name$(`
const owner = new Array(src.length); let cur = '?';
const nsOf = id => { const parts = id.replace(/^\$|\$$/g, '').split('$').filter(Boolean); return parts.slice(0, Math.max(1, parts.length - 1)).join('.'); };
for (let i = 0; i < src.length; i++) {
  const m = /^(?:var |function )?(\$[A-Za-z0-9_$]+\$)\s*(?:=|\()/.exec(src[i]);
  if (m && m[1].split('$').length > 3) cur = nsOf(m[1]);
  owner[i] = cur;
}
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const ctx = await b.newContext({ viewport: { width: 390, height: 844 } });
  await ctx.addInitScript(() => { try { localStorage.setItem('orcpub:no-cookie-banner','1'); localStorage.setItem('whats-new-seen', JSON.stringify('summer-patch-2026')); } catch (e) {} });
  await ctx.route('**/js/compiled/orcpub.js', r => r.fulfill({ body: fs.readFileSync(BUNDLE), contentType: 'application/javascript; charset=utf-8' }));
  const p = await ctx.newPage();
  await p.goto('http://localhost:8890' + route, { waitUntil: 'load' }); await p.waitForTimeout(1000);
  const cdp = await ctx.newCDPSession(p);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: +(process.env.CPU||1) }); await cdp.send('Profiler.enable'); await cdp.send('Profiler.setSamplingInterval', { interval: 200 });
  await cdp.send('Profiler.start');
  await p.reload({ waitUntil: 'load' }); await p.waitForTimeout(2000);
  const { profile } = await cdp.send('Profiler.stop');
  const dt = {}; const ids = profile.samples; const deltas = profile.timeDeltas;
  ids.forEach((id, i) => dt[id] = (dt[id] || 0) + (deltas[i] || 0));
  const byNs = {}, byFn = {}; let total = 0, appTotal = 0;
  for (const n of profile.nodes) {
    const t = (dt[n.id] || 0) / 1000; if (!t) continue; total += t;
    const cf = n.callFrame; if (!cf.url.includes('orcpub.js')) { byNs['<' + (cf.functionName || cf.url.split('/').pop() || 'native') + '>'] = (byNs['<' + (cf.functionName || cf.url.split('/').pop() || 'native') + '>'] || 0) + t; continue; }
    appTotal += t;
    const ns = cf.functionName ? nsOf(cf.functionName) : 'toplevel:' + owner[cf.lineNumber];
    byNs[ns] = (byNs[ns] || 0) + t;
    const fn = (cf.functionName || '(top)') + '@' + (cf.functionName ? '' : owner[cf.lineNumber]);
    byFn[fn] = (byFn[fn] || 0) + t;
  }
  // Inclusive: charge each sample to its nearest orcpub.* / app top-level ancestor.
  const parent = {}; const node = {};
  for (const n of profile.nodes) { node[n.id] = n; for (const c of (n.children || [])) parent[c] = n.id; }
  const label = n => { const cf = n.callFrame; if (!cf.url.includes('orcpub.js')) return null;
    const ns = cf.functionName ? nsOf(cf.functionName) : 'toplevel:' + owner[cf.lineNumber];
    return /orcpub|re_frame|reagent/.test(ns) ? ns + (cf.functionName ? ' :: ' + cf.functionName.slice(0, 60) : '') : null; };
  const incl = {}, inclNs = {};
  for (const [id, t0] of Object.entries(dt)) { let id2 = +id, l = null;
    while (id2 != null && !(l = label(node[id2]))) id2 = parent[id2];
    if (l) { incl[l] = (incl[l] || 0) + t0 / 1000; const ns = l.split(' :: ')[0]; inclNs[ns] = (inclNs[ns] || 0) + t0 / 1000; } }
  console.log('--- nearest app frame (by namespace) ---\n' + Object.entries(inclNs).sort((a,b)=>b[1]-a[1]).slice(0,20).map(([k,v])=>`${v.toFixed(0).padStart(6)}ms  ${k}`).join('\n'));
  console.log('--- nearest app frame (by function) ---\n' + Object.entries(incl).sort((a,b)=>b[1]-a[1]).slice(0,20).map(([k,v])=>`${v.toFixed(0).padStart(6)}ms  ${k}`).join('\n'));
  const top = o => Object.entries(o).sort((a, b) => b[1] - a[1]).slice(0, 25).map(([k, v]) => `${v.toFixed(0).padStart(6)}ms  ${k}`).join('\n');
  if (process.env.SELF) console.log(`route ${route}  total sampled ${total.toFixed(0)}ms, in app bundle ${appTotal.toFixed(0)}ms\n--- by namespace (self) ---\n${top(byNs)}\n--- by function (self) ---\n${top(byFn)}`);
  await b.close();
})();
