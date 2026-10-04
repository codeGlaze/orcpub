// Does the builder chug under REAL use? Thick homebrew, then click around the character
// creation options often and relatively quickly - the way someone actually builds a
// character, not the 1.5s-apart taps a measurement probe makes.
//
// Prior probes waited long enough for the 500ms build debounce to settle between clicks,
// which is the friendliest possible pacing. This one interleaves race / subrace / class /
// level changes at 120-260ms and reports what a user would feel:
//   - long tasks: count, worst, and TOTAL blocked time over the session
//   - blocked share of wall clock (how much of the time the page could not paint)
//   - click -> next-paint latency, median and worst
//   - heap before and after the churn, each after a forced GC (does churn leak?)
//   - how many entity/build and spell-help calls the churn actually caused
//
// Prerequisites: lein fig:build, lein garden once, lein e2e-server, npm install playwright.
// Run: node test/browser/builder_churn_e2e.js <pack.orcbrew>
const fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const { importPack, suppressOverlays } = require('./lib/orcbrew-import');
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';try{const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();if(d){const p=path.join(b,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}return undefined;}

const INSTRUMENT = `
window.__lt = []; window.__paint = [];
try { new PerformanceObserver(l => { for (const e of l.getEntries()) window.__lt.push(Math.round(e.duration)); })
        .observe({entryTypes:['longtask']}); } catch(e) {}
window.__mark = () => new Promise(r => {
  const t0 = performance.now();
  requestAnimationFrame(() => requestAnimationFrame(() => { window.__paint.push(performance.now()-t0); r(); }));
});
(function arm(){
  try {
    const ent = window.orcpub && window.orcpub.entity;
    const opt = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5 && window.orcpub.dnd.e5.options;
    if (!ent || !opt) return setTimeout(arm, 5);
    window.__n = {build:0, help:0};
    const b = ent.build; ent.build = function(){ window.__n.build++; return b.apply(this, arguments); };
    const h = opt.spell_help; opt.spell_help = function(){ window.__n.help++; return h.apply(this, arguments); };

  } catch(e) { setTimeout(arm, 5); }
})();
`;
const pct=(a,p)=>a.length?a.slice().sort((x,y)=>x-y)[Math.min(a.length-1,Math.floor(p*a.length))]:0;

(async()=>{
  const pak = process.argv[2];
  const browser=await chromium.launch({executablePath:findChrome()});
  const ctx=await browser.newContext(); await suppressOverlays(ctx);
  const page=await ctx.newPage(); await page.setViewportSize({width:1500,height:1100});
  await page.addInitScript(INSTRUMENT);
  const cdp=await page.context().newCDPSession(page);
  await cdp.send('HeapProfiler.enable'); await cdp.send('Runtime.enable');
  // Simulate a real user's machine. This box is an idle 4-core Xeon; nobody builds a
  // character on one. Chrome DevTools calls 4x "mid-tier mobile"; 2x is roughly a modest
  // laptop with other tabs open. Throttling amplifies CPU-bound work, which is what the
  // chug is - it does NOT amplify the memory accumulation, which is speed-independent.
  const CPU = Number(process.argv[4] || 1);
  if (CPU > 1) await cdp.send('Emulation.setCPUThrottlingRate', {rate: CPU});
  const heap=async()=>{await cdp.send('HeapProfiler.collectGarbage');
                       return (await cdp.send('Runtime.getHeapUsage')).usedSize/1048576;};

  if (pak) {
    await page.goto('http://localhost:8890/dnd/5e/my-content',{waitUntil:'networkidle',timeout:300000});
    await page.waitForTimeout(3000);
    const r = await importPack(page, path.resolve(pak));
    if (!r.ok) throw new Error('import failed: '+JSON.stringify(r.diag||{}).slice(0,200));
    console.log(`imported ${path.basename(pak)}`);
  }
  await page.goto('http://localhost:8890/pages/dnd/5e/character-builder',{waitUntil:'load',timeout:600000});
  await page.waitForFunction(()=>document.body.innerText.includes('CLICK HERE TO ADD A RACE'),null,{timeout:600000,polling:250});
  await page.waitForTimeout(3000);

  const click=async t=>{ await page.locator(`text="${t}"`).first().click({timeout:8000}); };
  const heapBefore = await heap();
  await page.evaluate(()=>{ window.__lt=[]; window.__paint=[]; window.__n={build:0,help:0}; });

  // MODE isolates what the churn touches, to attribute heap growth: "races" never opens the
  // class picker, "classes" never changes race. memoized-spell-option is a memoize with no
  // eviction, so if browsing classes is what accumulates, only the class mode should grow.
  const MODE = process.argv[3] || 'both';
  const races=['Dwarf','Elf','Human','Half-Orc','Halfling','Gnome','Tiefling','Dragonborn','Half-Elf'];
  const classes=['Wizard','Fighter','Cleric','Rogue','Barbarian','Druid','Bard','Monk'];
  const t0=Date.now(); let acted=0, missed=0;
  for (let round=0; round<5; round++) {
    if (MODE !== 'classes') {
    await click('Race').catch(()=>{}); await page.waitForTimeout(150);
    for (const r of races) {
      try { await click(r); acted++; } catch(e) { missed++; }
      await page.evaluate(()=>window.__mark()).catch(()=>{});
      await page.waitForTimeout(120 + Math.floor(Math.random()*140));   // 120-260ms, human-fast
    }
    }
    if (MODE === 'races') continue;
    try { await click('Class / Level'); } catch(e) {}
    await page.waitForTimeout(200);
    for (const c of classes) {
      try { await page.locator('select').nth(0).selectOption({label:c},{timeout:6000}); acted++; } catch(e) { missed++; }
      await page.evaluate(()=>window.__mark()).catch(()=>{});
      await page.waitForTimeout(120 + Math.floor(Math.random()*140));
      try { await page.locator('select').nth(1).selectOption({index: 1+((round*3)%15)},{timeout:6000}); acted++; } catch(e) { missed++; }
      await page.waitForTimeout(120 + Math.floor(Math.random()*140));
    }
  }
  const wall=Date.now()-t0;
  await page.waitForTimeout(1500);
  const { lt, paint, n } = await page.evaluate(()=>({lt:window.__lt, paint:window.__paint, n:window.__n}));
  const heapAfter = await heap();

  const blocked=lt.reduce((a,b)=>a+b,0);
  console.log(`\n=== ${pak?path.basename(pak,'.orcbrew'):'CLEAN'} [${MODE}, ${CPU}x cpu] — ${acted} interactions in ${(wall/1000).toFixed(0)}s (${missed} missed) ===`);
  console.log(`long tasks        ${lt.length}   worst ${Math.max(0,...lt)}ms   total blocked ${(blocked/1000).toFixed(1)}s`);
  console.log(`blocked share     ${(100*blocked/wall).toFixed(0)}% of wall clock the page could not paint`);
  console.log(`  >50ms  ${lt.filter(x=>x>50).length}    >100ms ${lt.filter(x=>x>100).length}    >200ms ${lt.filter(x=>x>200).length}    >500ms ${lt.filter(x=>x>500).length}`);
  console.log(`click->paint      median ${pct(paint,0.5).toFixed(0)}ms   p90 ${pct(paint,0.9).toFixed(0)}ms   worst ${Math.max(0,...paint).toFixed(0)}ms`);
  console.log(`entity/build      ${n.build} calls    spell-help ${n.help} calls`);

  console.log(`heap              ${heapBefore.toFixed(1)} MB -> ${heapAfter.toFixed(1)} MB  (${(heapAfter-heapBefore>=0?'+':'')}${(heapAfter-heapBefore).toFixed(1)} MB over the session)`);
  await page.screenshot({path:'dev-scratch/churn-end.png'});
  await browser.close();
})().catch(e=>{console.error('FAILED',e.message);process.exit(1);});
