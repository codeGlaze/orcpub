// Where a real race click spends its time as homebrew grows.
// Same real import + real clicks, but the instrument is a 50us CPU profile: it splits a
// click into the rebuild path (entity/build, kahn-sort, collect-modifiers-2) versus the
// render path (reagent, React reconcile/commit), so "the rebuild is slow" and "we are
// rendering an option card for every homebrew subrace" can be told apart.
//
// Prerequisites:
//   lein fig:build                                   # compile the dev CLJS build
//   lein e2e-server                                  # full stack on :8890
//   PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright
//   lein with-profile +test run -m clojure.main dev/scale_orcbrew_pack.clj   # make the packs
// Run:
//   node test/browser/homebrew_render_split_e2e.js dev-scratch/paks/pak-c1.orcbrew dev-scratch/paks/pak-c4.orcbrew ...
//   (no arguments = clean library only)
//
// NOTE: the dev build is :optimizations :none, so LOAD-time numbers from it are
// meaningless — a cold builder load measures hundreds of file fetches, not the app. Only
// the runtime numbers here are usable. See docs/kb/perf-homebrew-builder-loop.md.

const fs=require('fs'),path=require('path');const {chromium}=require('playwright');
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';try{const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();if(d){const p=path.join(b,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}return undefined;}
function agg(profile){
  const byId=new Map(); for(const n of profile.nodes) byId.set(n.id,n);
  const parent=new Map(); for(const n of profile.nodes) for(const c of (n.children||[])) parent.set(c,n.id);
  const wall=(profile.endTime-profile.startTime)/1000, ms=wall/profile.samples.length;
  const self=new Map();
  for(const s of profile.samples){const n=byId.get(s); if(!n)continue; const k=n.callFrame.functionName||'(anonymous)'; self.set(k,(self.get(k)||0)+1);}
  const incl=(re)=>{const c=new Map();const u=(id)=>{if(c.has(id))return c.get(id);const n=byId.get(id);let r=false;
    if(n){if(re.test(n.callFrame.functionName||''))r=true;else{const p=parent.get(id);r=p!==undefined?u(p):false;}}c.set(id,r);return r;};
    let h=0;for(const s of profile.samples) if(u(s))h++;return h*ms;};
  return {wall, busy: wall-(self.get('(idle)')||0)*ms, incl};
}
(async()=>{
  const browser=await chromium.launch({executablePath:findChrome()});
  console.log('pack'.padEnd(22)+'busy/click'.padStart(12)+'build'.padStart(9)+'kahn'.padStart(8)+'collect'.padStart(9)+'render'.padStart(9)+'reactDOM'.padStart(10)+'  cards on Race page');
  for(const pak of [null,...process.argv.slice(2)]){
    const ctx=await browser.newContext(); const page=await ctx.newPage();
    await page.setViewportSize({width:1400,height:1000});
    const label = pak ? `${path.basename(pak,'.orcbrew')} (${(fs.statSync(pak).size/1024/1024).toFixed(1)}MB)` : 'CLEAN';
    try{
      if(pak){
        await page.goto('http://localhost:8890/dnd/5e/my-content',{waitUntil:'networkidle',timeout:300000});
        await page.waitForTimeout(4000);
        await page.setInputFiles('input[type=file]', path.resolve(pak));
        await page.waitForFunction(()=>document.body.innerText.includes('Source Collection'),null,{timeout:900000,polling:500});
        await page.waitForTimeout(3000);
      }
      await page.goto('http://localhost:8890/pages/dnd/5e/character-builder',{waitUntil:'load',timeout:600000});
      await page.waitForFunction(()=>document.body.innerText.includes('CLICK HERE TO ADD A RACE'),null,{timeout:600000,polling:500});
      await page.waitForTimeout(3000);
      const click=async t=>{await page.locator(`text="${t}"`).first().click({timeout:25000});};
      for(const r of ['Dwarf','Elf','Human']){try{await click(r);}catch(e){} await page.waitForTimeout(700);}
      const cards=await page.evaluate(()=>document.querySelectorAll('[class*="option"], .b-1').length);
      const cdp=await page.context().newCDPSession(page);
      await cdp.send('Profiler.enable'); await cdp.send('Profiler.setSamplingInterval',{interval:50});
      await cdp.send('Profiler.start');
      const races=['Elf','Human','Tiefling','Gnome','Halfling','Half-Orc'];
      for(const r of races){try{await click(r);}catch(e){} await page.waitForTimeout(700);}
      const {profile}=await cdp.send('Profiler.stop');
      const A=agg(profile), n=races.length;
      console.log(label.padEnd(22)
        +(A.busy/n).toFixed(0).padStart(10)+'ms'
        +(A.incl(/^orcpub\$entity\$build/)/n).toFixed(0).padStart(8)+'ms'
        +(A.incl(/^orcpub\$entity\$kahn_sort/)/n).toFixed(0).padStart(7)+'ms'
        +(A.incl(/^orcpub\$entity\$collect_modifiers_2/)/n).toFixed(0).padStart(8)+'ms'
        +(A.incl(/^reagent\$/)/n).toFixed(0).padStart(8)+'ms'
        +(A.incl(/renderWithHooks|beginWork|commitRoot|performConcurrentWork|reconcileChild/)/n).toFixed(0).padStart(9)+'ms'
        +String(cards).padStart(12));
    }catch(e){ console.log(label.padEnd(22)+'  FAILED: '+String(e.message).split('\n')[0].slice(0,70)); }
    await ctx.close();
  }
  console.log('\n(ms per real race click; inclusive subtree time from a 50us CPU profile over 6 clicks)');
  await browser.close();
})().catch(e=>{console.error('FAILED',e);process.exit(1);});
