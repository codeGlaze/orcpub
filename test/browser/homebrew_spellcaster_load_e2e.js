// Are spells built into the character builder when you are NOT looking at spells?
//
// Scales homebrew SPELLCASTING classes/subclasses (each with a full spell list) plus custom
// spells, and measures the cost of reaching and using the RACE tab — nowhere near Spells.
// Instrumentation is installed with addInitScript BEFORE the builder page renders, because
// the template is built once and cached: measure it late and you measure nothing.
//
// Reports, for the first render of the Race page: busy JS (idle stripped, so the
// :optimizations :none file-fetch wait is excluded), and call counts + time for
// class-option, make-levels, spellcaster-subclass-levels, spell-selection and
// spells-known-selections. Then does real non-spell interactions (race card, class
// dropdown, level dropdown) with the counters reset, to show whether any of that work
// recurs or was all paid up front.
//
// Prerequisites:
//   lein fig:build
//   lein e2e-server
//   PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright
//   lein with-profile +test run -m clojure.main dev/spellcaster_pack.clj   # make the packs
// Run:
//   node test/browser/homebrew_spellcaster_load_e2e.js dev-scratch/paks/spell-8.orcbrew ...
//
// The probe prints "*** NOT PERSISTED ***" if a pack blew the localStorage quota — a pack
// that does not persist measures a builder with no homebrew in it. Keep packs under ~2.5 MB.

const fs=require('fs'),path=require('path');const {chromium}=require('playwright');
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';try{const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();if(d){const p=path.join(b,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}return undefined;}

// Instrument BEFORE the builder page ever renders, via an init script, so the FIRST
// template build is captured — that is the one the user waits on.
const INSTRUMENT = `
window.__spy = {};
window.__spyReady = false;
(function arm(){
  try {
    var t5e = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5 && window.orcpub.dnd.e5.template;
    var opt = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5 && window.orcpub.dnd.e5.options;
    var ss  = window.orcpub && window.orcpub.dnd && window.orcpub.dnd.e5 && window.orcpub.dnd.e5.spell_subs;
    var ent = window.orcpub && window.orcpub.entity;
    if (!t5e || !opt || !ent || !ss) { return setTimeout(arm, 5); }
    var wrap = function(obj, name, label){
      if (!obj || typeof obj[name] !== 'function') return;
      var f = obj[name];
      window.__spy[label] = {n:0, ms:0};
      obj[name] = function(){ var s=performance.now(); var r=f.apply(this,arguments);
        var b=window.__spy[label]; b.n++; b.ms+=performance.now()-s; return r; };
    };
    wrap(t5e,'template_selections','template-selections');
    wrap(t5e,'template','template');
    wrap(opt,'class_option','class-option');
    wrap(opt,'spell_selection','spell-selection');
    wrap(opt,'spells_known_selections','spells-known-selections');
    wrap(ss,'make_levels','make-levels');
    wrap(ss,'spellcaster_subclass_levels','spellcaster-subclass-levels');
    wrap(ent,'build','entity/build');
    window.__spyReady = true;
  } catch(e) { setTimeout(arm, 5); }
})();
`;

(async()=>{
  const browser=await chromium.launch({executablePath:findChrome()});
  console.log('pack'.padEnd(26)+'   FIRST RACE PAGE      | on the RACE tab, before touching Spells');
  console.log(''.padEnd(26)+'   busyJS    interactive | tmplSel  classOpt  makeLvls  spellSel  spellsKnown  subclsLvls  build');
  for(const pak of process.argv.slice(2)){
    const ctx=await browser.newContext(); const page=await ctx.newPage();
    await page.setViewportSize({width:1400,height:1000});
    const label=path.basename(pak,'.orcbrew');
    try{
      await page.goto('http://localhost:8890/dnd/5e/my-content',{waitUntil:'networkidle',timeout:300000});
      await page.waitForTimeout(4000);
      await page.setInputFiles('input[type=file]', path.resolve(pak));
      await page.waitForFunction(()=>document.body.innerText.includes('Spellcaster Pack'),null,{timeout:900000,polling:500});
      await page.waitForTimeout(3000);
      // confirm it actually persisted (quota) before believing anything downstream
      const persisted=await page.evaluate(()=>{let kb=0;try{for(let i=0;i<localStorage.length;i++)kb+=(localStorage.key(i).length+(localStorage.getItem(localStorage.key(i))||'').length);}catch(e){}return Math.round(kb/1024);});

      await page.addInitScript(INSTRUMENT);
      const cdp=await page.context().newCDPSession(page);
      await cdp.send('Profiler.enable'); await cdp.send('Profiler.setSamplingInterval',{interval:200});
      await cdp.send('Profiler.start');
      const t0=Date.now();
      await page.goto('http://localhost:8890/pages/dnd/5e/character-builder',{waitUntil:'load',timeout:900000});
      await page.waitForFunction(()=>document.body.innerText.includes('CLICK HERE TO ADD A RACE'),null,{timeout:900000,polling:250});
      const interactive=Date.now()-t0;
      const {profile}=await cdp.send('Profiler.stop');
      // busy JS = non-idle sample time; strips the dev build's file-fetch wait
      const byId=new Map(); for(const n of profile.nodes) byId.set(n.id,n);
      let idle=0; const ms=((profile.endTime-profile.startTime)/1000)/profile.samples.length;
      for(const s of profile.samples){const n=byId.get(s); if(n&&(n.callFrame.functionName==='(idle)')) idle++;}
      const busy=((profile.endTime-profile.startTime)/1000)-idle*ms;

      const spy=await page.evaluate(()=>window.__spy||{});
      const g=k=>spy[k]?`${spy[k].n}x${spy[k].ms.toFixed(0)}ms`:'-';
      // now: real clicks that have NOTHING to do with spells, with the counters reset
      const reset=()=>page.evaluate(()=>{for(const k in window.__spy) window.__spy[k]={n:0,ms:0};});
      const click=async t=>{await page.locator(`text="${t}"`).first().click({timeout:25000});};
      const step=async(name,fn)=>{await reset(); const t=Date.now(); let ok=true;
        try{await fn();}catch(e){ok=false;} await page.waitForTimeout(1500);
        const d=await page.evaluate(()=>window.__spy);
        const f=k=>d[k]&&d[k].n?`${d[k].n}x${d[k].ms.toFixed(0)}ms`:'-';
        return `${name}${ok?'':'[MISS]'}: wall ${Date.now()-t-1500}ms  classOpt ${f('class-option')}  spellSel ${f('spell-selection')}  tmplSel ${f('template-selections')}  build ${f('entity/build')}`;};
      const lines=[];
      for(const r of ['Dwarf','Elf']){try{await click(r);}catch(e){} await page.waitForTimeout(800);}
      lines.push(await step('  race Half-Orc', ()=>click('Half-Orc')));
      lines.push(await step('  tab Class/Level', ()=>click('Class / Level')));
      lines.push(await step('  class -> Wizard', async()=>{await page.locator('select').nth(0).selectOption({label:'Wizard'});}));
      lines.push(await step('  level -> 5', async()=>{await page.locator('select').nth(1).selectOption({label:'5'});}));
      global.__lines = lines;
      console.log(label.padEnd(26)+'   '+`${busy.toFixed(0)}ms`.padStart(8)+`${interactive}ms`.padStart(12)+'  |'
        +g('template-selections').padStart(11)+g('class-option').padStart(10)+g('make-levels').padStart(11)
        +g('spell-selection').padStart(12)+g('spells-known-selections').padStart(13)
        +g('spellcaster-subclass-levels').padStart(12)+g('entity/build').padStart(11)
        +(persisted?'':'   *** NOT PERSISTED ***'));
      for(const l of (global.__lines||[])) console.log(l);
    }catch(e){console.log(label.padEnd(26)+'   FAILED: '+String(e.message).split('\n')[0].slice(0,70));}
    await ctx.close();
  }
  console.log('\nbusyJS strips the :optimizations :none file-fetch wait; "interactive" is wall clock and includes it.');
  await browser.close();
})().catch(e=>{console.error('FAILED',e);process.exit(1);});
