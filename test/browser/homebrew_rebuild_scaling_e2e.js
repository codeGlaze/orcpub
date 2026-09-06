// How the character rebuild scales with homebrew VOLUME.
// Imports a .orcbrew through the My Content page's own file input, opens the real
// character builder, picks a race by clicking the real card, then microbenchmarks the
// rebuild internals on the LIVE character and template: entity/build, collect-modifiers-2,
// get-all-selections-aux-2, make-template-option-map, kahn-sort, apply-modifiers. Prints
// the shapes (template selections, active selections, option-map entries, modifiers,
// dependency-graph nodes) beside the timings.
//
// Warmed, min-of-5-reps. Click-triggered timings were tried first and came out
// non-monotonic across pack sizes — the debounce, rendering and GC swamp the signal — so
// they are not a valid instrument here.
//
// Prerequisites:
//   lein fig:build                                   # compile the dev CLJS build
//   lein e2e-server                                  # full stack on :8890
//   PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install playwright
//   lein with-profile +test run -m clojure.main dev/scale_orcbrew_pack.clj   # make the packs
// Run:
//   node test/browser/homebrew_rebuild_scaling_e2e.js dev-scratch/paks/pak-c1.orcbrew dev-scratch/paks/pak-c4.orcbrew ...
//   (no arguments = clean library only)
//
// NOTE: the dev build is :optimizations :none, so LOAD-time numbers from it are
// meaningless — a cold builder load measures hundreds of file fetches, not the app. Only
// the runtime numbers here are usable.

const fs=require('fs'),path=require('path');const {chromium}=require('playwright');
const { importPack } = require('./lib/orcbrew-import');
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';try{const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();if(d){const p=path.join(b,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}return undefined;}

const MEASURE = () => {
  const c=window.cljs.core, ent=window.orcpub.entity, S=window.clojure.set, kw=(n,k)=>c.keyword(n,k);
  const sub=(k)=>window.re_frame.core.subscribe(c.PersistentVector.fromArray([c.keyword(null,k)],true)).state;
  const ch=sub('character'), tm=sub('built-template');
  const bench=(f,warm,n,reps)=>{for(let i=0;i<warm;i++)f();let best=Infinity;
    for(let r=0;r<reps;r++){const s=performance.now();for(let i=0;i<n;i++)f();const t=(performance.now()-s)/n;if(t<best)best=t;}return best;};
  const flat=ent.flatten_options(c.get(ch,kw('orcpub.entity','options')));
  const pm=ent.make_path_map(ch);
  const sels=ent.get_all_selections_aux_2(tm,pm);
  const optMap=ent.make_template_option_map(sels);
  const mods=c.sort_by(x=>c.get(x,kw('orcpub.modifiers','order')), ent.collect_modifiers_2(ch,flat,tm));
  let deps=c.PersistentArrayMap.EMPTY;
  c.doall(c.map(m=>{const k=c.get(m,kw('orcpub.modifiers','key')),d=c.get(m,kw('orcpub.modifiers','deps'));
    if(d&&c.seq(d)){const cur=c.get(deps,k);deps=c.assoc(deps,k,cur?S.union(cur,d):d);}return null;},mods));
  const base=c.merge(c.get(tm,kw('orcpub.template','base')),c.get(ch,kw('orcpub.entity','values')));
  const allDeps=c.merge_with(S.union,deps,c.get(base,kw('orcpub.entity-spec','deps')));
  return {
    build:              bench(()=>ent.build(ch,tm),10,20,5),
    collect_modifiers:  bench(()=>ent.collect_modifiers_2(ch,flat,tm),10,20,5),
    get_all_selections: bench(()=>ent.get_all_selections_aux_2(tm,pm),10,20,5),
    make_option_map:    bench(()=>ent.make_template_option_map(sels),10,20,5),
    kahn_sort:          bench(()=>ent.kahn_sort(allDeps),10,20,5),
    apply_modifiers:    bench(()=>window.orcpub.modifiers.apply_modifiers(base,mods),10,20,5),
    // shapes
    n_template_selections: c.count(c.get(tm,kw('orcpub.template','selections'))),
    n_active_selections:   c.count(sels),
    n_option_map_entries:  c.count(optMap),
    n_flat_options:        c.count(flat),
    n_modifiers:           c.count(mods),
    n_graph_nodes:         c.count(allDeps),
  };
};

(async()=>{
  const browser=await chromium.launch({executablePath:findChrome()});
  const hdr=['pack','build','collect','getSels','optMap','kahn','applyMods','|','tmplSel','actSel','optMapN','mods','graph'];
  console.log(hdr[0].padEnd(24)+hdr.slice(1,7).map(h=>h.padStart(10)).join('')+'   '+hdr.slice(8).map(h=>h.padStart(9)).join(''));
  for(const pak of [null,...process.argv.slice(2)]){
    const ctx=await browser.newContext(); const page=await ctx.newPage();
    await page.setViewportSize({width:1400,height:1000});
    let label = pak ? `${path.basename(pak,'.orcbrew')} (${(fs.statSync(pak).size/1024/1024).toFixed(1)}MB)` : 'CLEAN';
    try{
      if(pak){
        await page.goto('http://localhost:8890/dnd/5e/my-content',{waitUntil:'networkidle',timeout:300000});
        await page.waitForTimeout(4000);
        const r = await importPack(page, path.resolve(pak));
        if (!r.ok) throw new Error(`import did not complete (plugins=${r.count}, modal clicked=${r.viaModal})`);
        await page.waitForTimeout(3000);
      }
      await page.goto('http://localhost:8890/pages/dnd/5e/character-builder',{waitUntil:'load',timeout:600000});
      await page.waitForFunction(()=>document.body.innerText.includes('CLICK HERE TO ADD A RACE'),null,{timeout:600000,polling:500});
      await page.waitForTimeout(3000);
      // put the character in a realistic state: pick a race, so a subrace selection is active
      try{ await page.locator('text="Dwarf"').first().click({timeout:20000}); await page.waitForTimeout(1200);}catch(e){}
      const m=await page.evaluate(MEASURE);
      console.log(label.padEnd(24)
        +[m.build,m.collect_modifiers,m.get_all_selections,m.make_option_map,m.kahn_sort,m.apply_modifiers]
           .map(v=>v.toFixed(2).padStart(10)).join('')
        +'   '+[m.n_template_selections,m.n_active_selections,m.n_option_map_entries,m.n_modifiers,m.n_graph_nodes]
           .map(v=>String(v).padStart(9)).join(''));
    }catch(e){ console.log(label.padEnd(24)+'  FAILED: '+String(e.message).split('\n')[0].slice(0,80)); }
    await ctx.close();
  }
  console.log('\n(ms, warmed, min of 5 reps x 20 iterations. Counts are shapes, not timings.)');
  await browser.close();
})().catch(e=>{console.error('FAILED',e);process.exit(1);});
